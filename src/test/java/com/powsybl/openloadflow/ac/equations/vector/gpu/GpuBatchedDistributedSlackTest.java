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
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
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
 * Distributed slack on the batched full-loadflow GPU path — an outer loop a bare fixed-iteration N-1 batch lacks. A meshed
 * 3-bus network with a generation deficit: the slack bus absorbs it, then the distributed-slack outer loop
 * spreads it across the participating generators (PROPORTIONAL_TO_GENERATION_P_MAX) exactly as OLF's
 * {@link com.powsybl.openloadflow.ac.outerloop.DistributedSlackOuterLoop}. The GPU bus voltages must match a
 * CPU {@code LoadFlow.run} (distributed slack on) — to machine precision once the reference's inner Newton is
 * driven tight ({@code convEpsPerEq=1e-12}), not just load-flow tolerance. Generators have wide P limits so
 * none saturates (the one-shot device distribution is exact vs OLF's iterative one only without saturation).
 *
 * <p>Skipped unless the native lib is available (built by native/build-gpu.sh with cuDSS).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedDistributedSlackTest {

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
    void slackMismatchDistributedAcrossGenerators() {
        // CPU reference: distributed slack ON (default, PROPORTIONAL_TO_GENERATION_P_MAX), reactive limits OFF.
        Network cpu = threeBusDeficit();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters lfp = new LoadFlowParameters().setDistributedSlack(true).setUseReactiveLimits(false);
        // TIGHT inner-Newton reference (convEpsPerEq=1e-12): OLF's default UNIFORM_CRITERIA (1e-4) stops ~e-4
        // short of the true root; drive it to the root so this is a machine-precision comparison. Outer-loop
        // slack tolerance stays at OLF's default (matched on the device, 1e-2) — see GpuBatchedL1L2DiagnosticTest.
        OpenLoadFlowParameters.create(lfp).setNewtonRaphsonConvEpsPerEq(1e-12);
        assertTrue(runner.run(cpu, lfp).isFullyConverged(), "CPU reference must converge");

        // GPU: batched full-loadflow over a single base-case scenario; the distributed-slack outer loop must
        // spread the slack mismatch on its own. Reactive limits off (empty pack) to isolate distributed slack.
        Network gpu = threeBusDeficit();
        LfNetwork net = Networks.load(gpu, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector())).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        cpuReference(es, target, eq, sv);                    // converge the base case (no distribution) as the hot start
        double[] baseState = sv.get().clone();

        GpuAcData d = GpuAcDataExtractor.extract(net, es);
        assertTrue(d.getDistributedSlackBusCount() > 0, "the network must have participating generators to distribute to");
        GpuAcActivation act = GpuAcActivation.fromEquationSystem(net, es);
        double[] stableTargets = act.applyTargets(target.getArray());
        boolean[][] disabledMask = {new boolean[d.getTotalElementCount()]};
        int[][] rowModes = {act.rowMode()};

        double[][] batch = GpuAcNewtonSolver.solveBatch(baseState, stableTargets, rowModes, disabledMask,
                GpuBatchedSecurityAnalysisSolver.resolveBatchMode(), MAX_ITER, TOL, 1e-4, 1e-2,
                d.cbIn(), d.cbIdx(), d.obIn(), d.obIdx(), d.shIn(), d.shIdx(), d.hvIn(), d.hvIdx(),
                new int[0], new double[0], new double[0], new double[0], new double[0],   // reactive limits off
                d.dsPhiRow(), d.dsFactor(), d.dsSlackPhiRow(), d.dsSlackTargetP(),
                d.dsBaseTargetP(), d.dsMaxDelta(), d.dsMinDelta(),
                d.dqElem(), d.dqSide(), d.dqWeight(), d.dqRow(), d.dqKind(),
                d.drRow(), d.drCol(), d.drCoef(), d.ziIn(), d.ziIdx());
        int n = baseState.length;
        int status = (int) batch[0][n + 2];
        System.out.printf("GPU distributed slack [3-bus deficit]: status=%d, %d iters, ||F||inf %.2e, %d participating buses%n",
                status, (int) batch[0][n], batch[0][n + 1], d.getDistributedSlackBusCount());
        assertTrue(status == 0, "GPU full-loadflow must converge with the slack distribution (status=" + status + ")");

        sv.set(java.util.Arrays.copyOf(batch[0], n));
        AcSolverUtil.updateNetwork(net, es);

        double worst = 0;
        int compared = 0;
        for (Bus cpuBus : cpu.getBusView().getBuses()) {
            LfBus gpuBus = net.getBusById(cpuBus.getId());
            if (gpuBus == null) {
                continue;
            }
            worst = Math.max(worst, Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - gpuBus.getV()));
            worst = Math.max(worst, Math.abs(Math.toRadians(cpuBus.getAngle()) - gpuBus.getAngle()));
            compared++;
        }
        System.out.printf("  %d buses compared, max|gpu - cpu| = %.2e%n", compared, worst);
        assertTrue(compared >= 3, "must compare all three buses");
        assertTrue(worst < 1e-12, "GPU distributed-slack solution must match the CPU LoadFlow (max diff = " + worst + ")");
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

    /** Meshed b1(slack)-b2-b3, all three with a voltage-controlling generator; b2/b3 carry loads that exceed
     *  the scheduled generation, so the slack must absorb a large deficit to be distributed to b2/b3. */
    private static Network threeBusDeficit() {
        Network n = Network.create("three-bus-distributed-slack", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("vl" + i + "_0").add();
        }
        Generator g1 = gen(n, "g1", "vl1", 20);
        // The slack generator does not participate, so OLF and the GPU both distribute to b2/b3 only —
        // removing the reference-generator-share subtlety (the GPU has no P target at the slack to update).
        g1.newExtension(ActivePowerControlAdder.class).withParticipate(false).withDroop(4).add();
        gen(n, "g2", "vl2", 40);
        gen(n, "g3", "vl3", 40);
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("vl2_0").setConnectableBus("vl2_0").setP0(90).setQ0(20).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("vl3_0").setConnectableBus("vl3_0").setP0(90).setQ0(20).add();
        line(n, "l12", "vl1", "vl2");
        line(n, "l23", "vl2", "vl3");
        line(n, "l13", "vl1", "vl3");
        return n;
    }

    private static Generator gen(Network n, String id, String vl, double targetP) {
        Generator g = n.getVoltageLevel(vl).newGenerator().setId(id).setBus(vl + "_0").setConnectableBus(vl + "_0")
                .setMinP(0).setMaxP(400).setTargetP(targetP).setTargetV(100).setVoltageRegulatorOn(true).add();
        g.newMinMaxReactiveLimits().setMinQ(-400).setMaxQ(400).add();   // wide: reactive limits never bind here
        return g;
    }

    private static void line(Network n, String id, String vlA, String vlB) {
        n.newLine().setId(id)
                .setVoltageLevel1(vlA).setBus1(vlA + "_0").setConnectableBus1(vlA + "_0")
                .setVoltageLevel2(vlB).setBus2(vlB + "_0").setConnectableBus2(vlB + "_0")
                .setR(1.0).setX(10.0).setG1(0).setB1(0).setG2(0).setB2(0)
                .add();
    }
}
