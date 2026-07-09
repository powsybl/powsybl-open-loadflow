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
 * Distributed-slack ROBUSTNESS on the batched full-loadflow GPU path: the two cases the earlier one-shot
 * distribution got wrong, now matching OLF.
 *
 * <ol>
 *   <li><b>Generator saturation drop-out</b> — a participating generator with a tight active-power limit
 *   saturates at its Pmax during distribution; the remaining mismatch must be redistributed to the
 *   unsaturated generators (OLF's iterative water-filling). The GPU reproduces this by clamping each bus's
 *   cumulative distributed delta to its headroom and letting the residual reappear at the slack for the next
 *   outer pass.</li>
 *   <li><b>Participating slack generator</b> — when the slack/reference bus itself has a participating
 *   generator, OLF gives it a share of the mismatch. The GPU keeps that share at the slack (no row to
 *   retarget) by including the slack's factor in the normalization denominator, so the non-slack buses get
 *   exactly their f_i/Σ_all share rather than the full mismatch.</li>
 * </ol>
 *
 * <p>Both compare the GPU full-loadflow state to a CPU {@code LoadFlow.run} (distributed slack on) to
 * load-flow tolerance. Skipped unless the native lib is available.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedDistributedSlackSaturationTest {

    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-8;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU batched native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    @Test
    void generatorSaturationRedistributesToUnsaturated() {
        // b2's generator can only rise from 40 to 45 MW (5 MW headroom) before hitting Pmax; the ~80 MW deficit
        // pushes its proportional share well past that, so it must saturate and the remainder flow to b3. The
        // device water-fills the FULL mismatch among the unsaturated buses (re-normalizing each round) before a
        // re-solve, exactly like OLF's ActivePowerDistribution — so the saturated redistribution lands on the
        // SAME state as OLF (machine precision), not the old ~5e-4 slack-tolerance band.
        runAndCompare(false, 45, "saturation", /*assertSaturatedGen*/ "g2", 45, 1e-8);
    }

    @Test
    void participatingSlackGeneratorKeepsItsShare() {
        // The slack generator participates (wide limits, nobody saturates). OLF gives it a share; the GPU must
        // leave that share at the slack and NOT over-distribute to b2/b3. One-shot, so it matches tightly.
        runAndCompare(true, 400, "participating-slack", null, 0, 1e-8);
    }

    private void runAndCompare(boolean slackParticipates, double g2MaxP, String label,
                               String saturatedGenId, double saturatedMaxP, double agreeTol) {
        Network gpuNet = threeBus(slackParticipates, g2MaxP);
        // CPU reference: distributed slack ON (default PROPORTIONAL_TO_GENERATION_P_MAX, useActiveLimits on),
        // reactive limits off, FIRST slack — the exact scope the GPU extractor sees.
        Network cpu = threeBus(slackParticipates, g2MaxP);
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters lfp = new LoadFlowParameters().setDistributedSlack(true).setUseReactiveLimits(false);
        assertTrue(runner.run(cpu, lfp).isFullyConverged(), label + ": CPU reference must converge");
        if (saturatedGenId != null) {
            double genP = -cpu.getGenerator(saturatedGenId).getTerminal().getP();        // injection (MW)
            assertTrue(Math.abs(genP - saturatedMaxP) < 1e-2,
                    label + ": the scenario must actually saturate " + saturatedGenId + " at " + saturatedMaxP
                            + " MW (was " + genP + ") — otherwise it does not exercise drop-out");
        }

        // GPU: batched full loadflow over a single base-case scenario with the distributed-slack outer loop.
        LfNetwork net = Networks.load(gpuNet, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector())).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        cpuReference(es, target, eq, sv);                    // converge the base case (no distribution) as the hot start
        double[] baseState = sv.get().clone();

        GpuAcData d = GpuAcDataExtractor.extract(net, es);
        assertTrue(d.getDistributedSlackBusCount() > 0, label + ": must have participating non-slack buses");
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
        System.out.printf("GPU distributed slack [%s]: status=%d, %d iters, ||F||inf %.2e%n",
                label, status, (int) batch[0][n], batch[0][n + 1]);
        assertTrue(status == 0, label + ": GPU full-loadflow must converge (status=" + status + ")");

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
        System.out.printf("  max|gpu - cpu| = %.2e (tol %.0e)%n", worst, agreeTol);
        assertTrue(worst < agreeTol, label + ": GPU distributed-slack solution must match the CPU LoadFlow (max diff = " + worst + ")");
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

    /**
     * Meshed b1(slack)-b2-b3 with a generation deficit absorbed by the slack then distributed to b2/b3.
     * @param slackParticipates whether the slack generator g1 participates in distribution
     * @param g2MaxP             b2 generator Pmax (tight → saturates; wide → never binds)
     */
    private static Network threeBus(boolean slackParticipates, double g2MaxP) {
        Network n = Network.create("three-bus-distributed-slack-saturation", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("vl" + i + "_0").add();
        }
        Generator g1 = gen(n, "g1", "vl1", 20, 400);
        g1.newExtension(ActivePowerControlAdder.class).withParticipate(slackParticipates).withDroop(4).add();
        gen(n, "g2", "vl2", 40, g2MaxP);
        gen(n, "g3", "vl3", 40, 400);
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("vl2_0").setConnectableBus("vl2_0").setP0(90).setQ0(20).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("vl3_0").setConnectableBus("vl3_0").setP0(90).setQ0(20).add();
        line(n, "l12", "vl1", "vl2");
        line(n, "l23", "vl2", "vl3");
        line(n, "l13", "vl1", "vl3");
        return n;
    }

    private static Generator gen(Network n, String id, String vl, double targetP, double maxP) {
        return n.getVoltageLevel(vl).newGenerator().setId(id).setBus(vl + "_0").setConnectableBus(vl + "_0")
                .setMinP(0).setMaxP(maxP).setTargetP(targetP).setTargetV(100).setVoltageRegulatorOn(true).add();
    }

    private static void line(Network n, String id, String vlA, String vlB) {
        n.newLine().setId(id)
                .setVoltageLevel1(vlA).setBus1(vlA + "_0").setConnectableBus1(vlA + "_0")
                .setVoltageLevel2(vlB).setBus2(vlB + "_0").setConnectableBus2(vlB + "_0")
                .setR(1.0).setX(10.0).setG1(0).setB1(0).setG2(0).setB2(0)
                .add();
    }
}
