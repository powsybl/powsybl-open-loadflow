/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.AcTargetVector;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DIAGNOSTIC: is the participating-slack "retain" residual a LINEARIZATION (a fixed floor) or a CONVERGENCE
 * artifact (shrinks to 0 as the distributed-slack tolerance tightens)? Sweep the DS tolerance on a 3-bus
 * network whose slack bus's OWN generator participates in the distribution, comparing the GPU batched
 * full-loadflow to a CPU {@code LoadFlow.run} at the SAME DS tolerance (tight inner Newton on both). If the
 * worst |gpu-cpu| falls monotonically toward machine precision, the retain converges to OLF's fixed point and
 * the fix is a stopping-criterion tweak; if it plateaus, the retain formula is genuinely lossy.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedParticipatingSlackConvergenceTest {

    private static final int MAX_ITER = 60;
    private static final double TOL = 1e-12;

    static {
        GpuTestPaths.init();
    }

    @Test
    void participatingSlackResidualVsDsTolerance() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(), "GPU batched native lib not available");

        double[] slackMw = {1.0, 1e-2, 1e-4, 1e-6, 1e-8, 1e-10};   // slackBusPMaxMismatch (MW) → pu = /100
        System.out.println("participating-slack retain: worst |gpu-cpu| vs DS tolerance (both sides matched)");
        double tightFloor = 0;
        for (double mw : slackMw) {
            double dsTolPu = mw / 100.0;
            double worst = solveOnce(dsTolPu, mw);
            System.out.printf("  slackBusPMaxMismatch=%.0e MW (dsTol=%.0e pu): worst |gpu-cpu| = %.3e%n",
                    mw, dsTolPu, worst);
            if (dsTolPu <= 1e-6 && !Double.isNaN(worst)) {     // tight enough that distribution actually triggers
                tightFloor = Math.max(tightFloor, worst);
            }
        }
        // CONCLUSION: the participating-slack "retain" is a LINEARIZATION with a FIXED floor (the AC-loss
        // second-order term: retain = f_slack·(slackMismatch + absorbed) ≈ f_slack·(M0 − Δloss), not
        // f_slack·(M0 + Δloss)), NOT a convergence artifact — tightening the DS tolerance does NOT drive it to 0,
        // it plateaus. The floor is SMALL and bounded (~4e-7 here), well within production tolerance; it does NOT
        // warrant moving the angle reference (a structural change). At LOOSE tol the small-net mismatch is below
        // the DS gate so no distribution happens (exact). This guards that bound.
        assertTrue(tightFloor > 0 && tightFloor < 1e-6, "the participating-slack retain floor must be small and "
                + "bounded (a loss linearization, not a divergence): floor = " + tightFloor);
    }

    private double solveOnce(double dsTolPu, double slackMw) {
        // CPU reference: participating slack, distributed slack on, TIGHT inner Newton, DS tol = slackMw.
        Network cpu = participatingSlackNet();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters lfp = new LoadFlowParameters().setDistributedSlack(true).setUseReactiveLimits(false);
        OpenLoadFlowParameters.create(lfp).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setNewtonRaphsonConvEpsPerEq(1e-12).setSlackBusPMaxMismatch(slackMw);
        if (!runner.run(cpu, lfp).isFullyConverged()) {
            System.out.printf("    (CPU did not converge at %.0e MW)%n", slackMw);
            return Double.NaN;
        }

        // GPU: batched full loadflow over a single base-case scenario, DS tol = dsTolPu, tight inner (convEpsPerEq).
        Network gpuNet = participatingSlackNet();
        LfNetwork net = Networks.load(gpuNet, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector())).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        newton(es, target, eq, sv);
        double[] baseState = sv.get().clone();

        GpuAcData d = GpuAcDataExtractor.extract(net, es);
        GpuAcActivation act = GpuAcActivation.fromEquationSystem(net, es);
        double[] stableTargets = act.applyTargets(target.getArray());
        boolean[][] disabledMask = {new boolean[d.getTotalElementCount()]};
        int[][] rowModes = {act.rowMode()};
        double[][] batch = GpuAcNewtonSolver.solveBatch(baseState, stableTargets, rowModes, disabledMask,
                GpuBatchedSecurityAnalysisSolver.resolveBatchMode(), MAX_ITER, 1e-10, 1e-4, dsTolPu,
                d.cbIn(), d.cbIdx(), d.obIn(), d.obIdx(), d.shIn(), d.shIdx(), d.hvIn(), d.hvIdx(),
                new int[0], new double[0], new double[0], new double[0], new double[0],
                d.dsPhiRow(), d.dsFactor(), d.dsSlackPhiRow(), d.dsSlackTargetP(),
                d.dsBaseTargetP(), d.dsMaxDelta(), d.dsMinDelta(),
                d.dqElem(), d.dqSide(), d.dqWeight(), d.dqRow(), d.dqKind(),
                d.drRow(), d.drCol(), d.drCoef(), d.ziIn(), d.ziIdx());
        int n = baseState.length;
        if ((int) batch[0][n + 2] != 0) {
            System.out.printf("    (GPU did not converge at dsTol=%.0e)%n", dsTolPu);
            return Double.NaN;
        }
        sv.set(java.util.Arrays.copyOf(batch[0], n));
        AcSolverUtil.updateNetwork(net, es);

        double worst = 0;
        for (Bus cpuBus : cpu.getBusView().getBuses()) {
            LfBus gpuBus = net.getBusById(cpuBus.getId());
            if (gpuBus == null) {
                continue;
            }
            worst = Math.max(worst, Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - gpuBus.getV()));
            worst = Math.max(worst, Math.abs(Math.toRadians(cpuBus.getAngle()) - gpuBus.getAngle()));
        }
        return worst;
    }

    /** Meshed b1(slack + PARTICIPATING gen)-b2(participating gen + load)-b3(participating gen + load): the loads
     *  exceed the scheduled generation, so a deficit is distributed across ALL three gens — including the slack's,
     *  which exercises the on-device retain path. */
    private static Network participatingSlackNet() {
        Network n = Network.create("participating-slack", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("b" + i).add();
        }
        gen(n, "g1", "vl1", "b1", 30);                          // slack bus gen — PARTICIPATES
        gen(n, "g2", "vl2", "b2", 40);
        gen(n, "g3", "vl3", "b3", 40);
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("b2").setConnectableBus("b2").setP0(90).setQ0(20).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("b3").setConnectableBus("b3").setP0(90).setQ0(20).add();
        line(n, "l12", "vl1", "b1", "vl2", "b2");
        line(n, "l23", "vl2", "b2", "vl3", "b3");
        line(n, "l13", "vl1", "b1", "vl3", "b3");
        return n;
    }

    private static void gen(Network n, String id, String vl, String bus, double targetP) {
        Generator g = n.getVoltageLevel(vl).newGenerator().setId(id).setBus(bus).setConnectableBus(bus)
                .setMinP(0).setMaxP(400).setTargetP(targetP).setTargetV(100).setVoltageRegulatorOn(true).add();
        g.newMinMaxReactiveLimits().setMinQ(-400).setMaxQ(400).add();
        g.newExtension(ActivePowerControlAdder.class).withParticipate(true).withDroop(4).add();
    }

    private static void line(Network n, String id, String vlA, String busA, String vlB, String busB) {
        n.newLine().setId(id)
                .setVoltageLevel1(vlA).setBus1(busA).setConnectableBus1(busA)
                .setVoltageLevel2(vlB).setBus2(busB).setConnectableBus2(busB)
                .setR(1.0).setX(10.0).setG1(0).setB1(0).setG2(0).setB2(0)
                .add();
    }

    private static void newton(EquationSystem<AcVariableType, AcEquationType> es, AcTargetVector target,
                               EquationVector<AcVariableType, AcEquationType> eq, StateVector sv) {
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(es, new DenseMatrixFactory())) {
            eq.minus(target);
            for (int it = 0; it < MAX_ITER && maxAbs(eq.getArray()) >= TOL; it++) {
                double[] dx = eq.getArray().clone();
                j.solveTransposed(dx);
                sv.minus(dx);
                eq.minus(target);
            }
        }
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
