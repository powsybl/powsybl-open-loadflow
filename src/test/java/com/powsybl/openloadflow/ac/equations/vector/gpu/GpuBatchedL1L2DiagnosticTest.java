/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
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
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchContingency;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchScenarioResult;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonParameters;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DIAGNOSTIC: is the ~3.3e-4 IEEE14 line-1-2 (L1-2-1) full-loadflow gap the SAME benign reference slack the
 * {@link GpuBatchedGenOverrideDiagnosticTest} proved for the gen contingency — i.e. {@code LoadFlow.run}'s
 * inner-Newton stopping criterion ({@code UNIFORM_CRITERIA} stops at {@code ‖F‖₂ < convEpsPerEq·√n}, default
 * {@code convEpsPerEq=1e-4} ⇒ ~5e-4 residual ⇒ ~e-4 state slack) — rather than a GPU error?
 *
 * <p>L1-2-1 runs the RL + DS outer loops, so unlike the gen case there is no single closed-form root to check.
 * Instead this compares the GPU (inner Newton driven tight, outer tolerances = OLF defaults) against two
 * {@code LoadFlow.run} references on the genuinely line-removed network, identical except for the inner-Newton
 * epsilon: the DEFAULT loose one ({@code convEpsPerEq=1e-4}) and a TIGHT one ({@code convEpsPerEq=1e-12}). The
 * outer-loop tolerances ({@code slackBusPMaxMismatch}, {@code maxReactivePowerMismatch}) are kept at OLF
 * defaults on BOTH references and matched on the device, so only the inner-Newton precision differs.
 *
 * <p>If the gap collapses against the TIGHT reference (and only the loose reference shows ~3.3e-4), the L1-2-1
 * discrepancy is the same {@code LoadFlow.run} convergence slack — not a GPU bug — and the parity threshold can
 * be tightened by re-basing on a tight reference.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedL1L2DiagnosticTest {

    private static final int MAX_ITER = 40;
    private static final double TOL = 1e-12;
    private static final String LINE_1_2 = "L1-2-1";

    static {
        GpuTestPaths.init();
    }

    @Test
    void l1l2GapIsLoadFlowInnerNewtonSlack() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU batched native lib not available (build it with native/build-gpu.sh + cuDSS)");

        LfNetwork probe = load(ieee14()).get(0);
        String slackGenId = probe.getSlackBus().getGenerators().stream().map(LfGenerator::getId).findFirst().orElseThrow();

        // GPU: converged base case, then the batched full loadflow (RL + DS) for the L1-2-1 outage, inner Newton
        // driven tight (1e-10), outer tolerances = OLF defaults (1e-2 slack, 1e-4 reactive).
        Network gpuNet = ieee14(slackGenId);
        LfNetwork net = load(gpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        newton(es, target, eq, sv);
        double[] baseState = sv.get().clone();
        int n = baseState.length;

        List<BatchContingency> conts = List.of(new BatchContingency(LINE_1_2, net.getBranchById(LINE_1_2)));
        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters().setMaxIterations(MAX_ITER), 1e-10, true, true,
                GpuBatchedSecurityAnalysisSolver.DEFAULT_REACTIVE_LIMIT_TOL,
                GpuBatchedSecurityAnalysisSolver.DEFAULT_SLACK_DIST_TOL, ReportNode.NO_OP);
        BatchScenarioResult r = results.get(0);
        assumeTrue(!r.needsCpuFallback() && r.hasConverged(),
                "GPU must solve L1-2-1 on device (status=" + r.convergenceStatus() + ", ||F||=" + r.finalMismatch() + ")");
        sv.set(java.util.Arrays.copyOf(r.state(), n));
        AcSolverUtil.updateNetwork(net, es);

        double looseGap = loadFlowGap(net, slackGenId, 1e-4);    // OLF DEFAULT inner Newton (the current reference)
        double tightGap = loadFlowGap(net, slackGenId, 1e-12);   // TIGHT inner Newton (same outer tolerances)

        System.out.printf("%nL1-2-1 GPU vs LoadFlow.run (RL + DS):%n");
        System.out.printf("  vs LOOSE reference (convEpsPerEq=1e-4, OLF default): max|gpu - cpu| = %.3e%n", looseGap);
        System.out.printf("  vs TIGHT reference (convEpsPerEq=1e-12)            : max|gpu - cpu| = %.3e%n", tightGap);
        System.out.printf("  => inner-Newton slack accounts for a %.0fx reduction%n",
                tightGap > 0 ? looseGap / tightGap : Double.NaN);
        System.out.printf("VERDICT: %s%n", tightGap < looseGap / 10
                ? "L1-2-1 gap is dominated by LoadFlow.run inner-Newton slack (same as the gen case)."
                : "tightening the inner Newton did NOT collapse the gap -> a genuine RL+DS outer-loop band difference.");

        // The loose reference is expected to carry the ~e-4 inner-Newton slack; the tight one must be far closer.
        assertTrue(looseGap > 1e-5, "the default LoadFlow.run reference should show its inner-Newton slack "
                + "(loose gap = " + looseGap + ")");
        assertTrue(tightGap < looseGap / 5, "tightening LoadFlow.run's inner Newton must substantially close the "
                + "L1-2-1 gap if it is reference slack (tight = " + tightGap + ", loose = " + looseGap + ")");
    }

    /** Run LoadFlow.run on the line-removed IEEE14 with the given inner-Newton epsilon and return the worst
     *  |V|,|phi| vs the GPU state currently on {@code net}. Outer-loop tolerances stay at OLF defaults. */
    private static double loadFlowGap(LfNetwork net, String slackGenId, double convEpsPerEq) {
        Network cpu = ieee14(slackGenId);
        cpu.getLine(LINE_1_2).disconnect();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters lfp = new LoadFlowParameters().setUseReactiveLimits(true).setDistributedSlack(true);
        OpenLoadFlowParameters.create(lfp)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setNewtonRaphsonConvEpsPerEq(convEpsPerEq);
        assertTrue(runner.run(cpu, lfp).isFullyConverged(), "CPU reference for L1-2-1 must converge (eps=" + convEpsPerEq + ")");
        double worst = 0;
        for (Bus cpuBus : cpu.getBusView().getBuses()) {
            LfBus gpuBus = net.getBusById(cpuBus.getId());
            if (gpuBus == null || Double.isNaN(cpuBus.getV())) {
                continue;
            }
            worst = Math.max(worst, Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - gpuBus.getV()));
            worst = Math.max(worst, Math.abs(Math.toRadians(cpuBus.getAngle()) - gpuBus.getAngle()));
        }
        return worst;
    }

    private static List<LfNetwork> load(Network iidm) {
        return Networks.load(iidm, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector()));
    }

    private static Network ieee14() {
        return IeeeCdfNetworkFactory.create14();
    }

    private static Network ieee14(String slackGenId) {
        Network n = IeeeCdfNetworkFactory.create14();
        Generator g = n.getGenerator(slackGenId);
        if (g != null) {
            g.newExtension(ActivePowerControlAdder.class).withParticipate(false).withDroop(4).add();
        }
        return n;
    }

    private static void newton(EquationSystem<AcVariableType, AcEquationType> es,
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
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
