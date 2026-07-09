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
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reactive limits (PV→PQ) on the batched full-loadflow GPU path — an outer loop a bare fixed-iteration N-1 batch lacks.
 * A two-bus network where the b2 generator must inject more reactive than its tight {@code maxQ} to hold
 * its voltage target: the full-loadflow batched solve must switch it to PQ (pinned at maxQ, voltage
 * released) exactly as OLF's {@link com.powsybl.openloadflow.ac.outerloop.ReactiveLimitsOuterLoop}, so the
 * GPU bus voltages must match a CPU {@code LoadFlow.run} (reactive limits on) — to machine precision once the
 * reference's inner Newton is driven tight ({@code convEpsPerEq=1e-12}), not just load-flow tolerance.
 *
 * <p>Skipped unless the native lib is available (built by native/build-gpu.sh with cuDSS).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedReactiveLimitsTest {

    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-10;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU batched native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    @Test
    void generatorHitsMaxQSwitchesToPq() {
        // CPU reference: a plain LoadFlow.run with reactive limits ON (default) — b2's gen pins at maxQ.
        Network cpu = twoBusTightQ();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters lfp = new LoadFlowParameters().setUseReactiveLimits(true).setDistributedSlack(false);
        // TIGHT inner-Newton reference (convEpsPerEq=1e-12): OLF's default UNIFORM_CRITERIA (1e-4) stops ~e-4
        // short of the true root; drive it to the root so this is a machine-precision comparison, not a
        // load-flow-tolerance one (see GpuBatchedL1L2DiagnosticTest).
        OpenLoadFlowParameters.create(lfp).setNewtonRaphsonConvEpsPerEq(1e-12);
        assertTrue(runner.run(cpu, lfp).isFullyConverged(), "CPU reference must converge");

        // GPU: batched full-loadflow (tol > 0) over a single base-case scenario; the reactive-limits outer
        // loop must switch b2 PV→PQ on its own.
        Network gpu = twoBusTightQ();
        LfNetwork net = Networks.load(gpu, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector())).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        cpuReference(es, target, eq, sv);                    // converge the base case (PV, no limits) as the hot start
        double[] baseState = sv.get().clone();

        GpuAcData d = GpuAcDataExtractor.extract(net, es);
        assertTrue(d.getReactiveLimitCount() > 0, "the network must have a PV controller subject to reactive limits");
        GpuAcActivation act = GpuAcActivation.fromEquationSystem(net, es);
        double[] stableTargets = act.applyTargets(target.getArray());
        boolean[][] disabledMask = {new boolean[d.getTotalElementCount()]};   // one scenario, base case
        int[][] rowModes = {act.rowMode()};

        double[][] batch = GpuAcNewtonSolver.solveBatch(baseState, stableTargets, rowModes, disabledMask,
                GpuBatchedSecurityAnalysisSolver.resolveBatchMode(), MAX_ITER, TOL, 1e-4, 1e-2,
                d.cbIn(), d.cbIdx(), d.obIn(), d.obIdx(), d.shIn(), d.shIdx(), d.hvIn(), d.hvIdx(),
                d.rlVrow(), d.rlMinQ(), d.rlMaxQ(), d.rlLoadQ(), d.rlVtarget(),
                new int[0], new double[0], -1, 0.0,    // distributed slack off (CPU reference has it off too)
                new double[0], new double[0], new double[0],
                new int[0], new int[0], new double[0], new int[0], new int[0],
                new int[0], new int[0], new double[0], new double[0], new int[0]);
        int n = baseState.length;
        int status = (int) batch[0][n + 2];
        System.out.printf("GPU reactive limits [2-bus tight maxQ]: status=%d, %d iters, ||F||inf %.2e%n",
                status, (int) batch[0][n], batch[0][n + 1]);
        assertTrue(status == 0, "GPU full-loadflow must converge with the reactive-limits switch (status=" + status + ")");

        sv.set(java.util.Arrays.copyOf(batch[0], n));
        AcSolverUtil.updateNetwork(net, es);

        double worst = 0;
        int compared = 0;
        double cpuB2V = Double.NaN;
        for (Bus cpuBus : cpu.getBusView().getBuses()) {
            LfBus gpuBus = net.getBusById(cpuBus.getId());
            if (gpuBus == null) {
                continue;
            }
            double cpuVpu = cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV();
            worst = Math.max(worst, Math.abs(cpuVpu - gpuBus.getV()));
            compared++;
            if (cpuBus.getVoltageLevel().getId().equals("vl2")) {
                cpuB2V = cpuBus.getV();
            }
        }
        System.out.printf("  %d buses compared, max|v_gpu - v_cpu| = %.2e (CPU b2 V = %.4f kV, below 100 ⇒ maxQ pinned)%n",
                compared, worst, cpuB2V);
        assertTrue(compared >= 2, "must compare both buses");
        assertTrue(cpuB2V < 99.9, "the CPU reference must actually pin b2 at maxQ (V below target), else the test is vacuous");
        assertTrue(worst < 1e-12, "GPU reactive-limits solution must match the CPU LoadFlow (max dV = " + worst + " pu)");
    }

    private static double[] cpuReference(EquationSystem<AcVariableType, AcEquationType> es,
                                         AcTargetVector target, EquationVector<AcVariableType, AcEquationType> eq,
                                         StateVector sv) {
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(es, new DenseMatrixFactory())) {
            eq.minus(target);
            for (int it = 0; it < MAX_ITER && maxAbs(eq.getArray()) >= TOL; it++) {
                double[] dx = eq.getArray().clone();
                j.solveTransposed(dx);
                sv.minus(dx);
                eq.minus(target);
            }
        }
        return sv.get().clone();
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }

    /** Slack b1 (loose Q) — line — b2 (gen, targetV 1.0 pu, TIGHT maxQ) with a reactive load it cannot hold. */
    private static Network twoBusTightQ() {
        Network n = Network.create("two-bus-reactive-limit", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 2; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("vl" + i + "_0").add();
        }
        Generator g1 = n.getVoltageLevel("vl1").newGenerator().setId("g1").setBus("vl1_0").setConnectableBus("vl1_0")
                .setMinP(0).setMaxP(500).setTargetP(100).setTargetV(100).setVoltageRegulatorOn(true).add();
        g1.newMinMaxReactiveLimits().setMinQ(-500).setMaxQ(500).add();
        // b2 generator: it would need a lot of reactive to hold V=1.0 pu against the b2 load, but maxQ is tight.
        Generator g2 = n.getVoltageLevel("vl2").newGenerator().setId("g2").setBus("vl2_0").setConnectableBus("vl2_0")
                .setMinP(0).setMaxP(200).setTargetP(40).setTargetV(100).setVoltageRegulatorOn(true).add();
        g2.newMinMaxReactiveLimits().setMinQ(-30).setMaxQ(30).add();
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("vl2_0").setConnectableBus("vl2_0")
                .setP0(120).setQ0(120).add();
        n.newLine().setId("l12")
                .setVoltageLevel1("vl1").setBus1("vl1_0").setConnectableBus1("vl1_0")
                .setVoltageLevel2("vl2").setBus2("vl2_0").setConnectableBus2("vl2_0")
                .setR(1.0).setX(10.0).setG1(0).setB1(0).setG2(0).setB2(0)
                .add();
        return n;
    }
}
