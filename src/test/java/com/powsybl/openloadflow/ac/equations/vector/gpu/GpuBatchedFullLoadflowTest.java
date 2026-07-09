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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end batched FULL loadflow on the GPU: reactive limits AND distributed slack together, over a real
 * N-1 set (IEEE14). This is the combination a bare fixed-iteration N-1 batch lacks entirely. It verifies (1) that hard
 * contingencies which did NOT converge with reactive limits alone — notably the line 1-2 outage, where the
 * active imbalance must be redistributed for the reactive-limit-switched system to settle — now CONVERGE
 * with distributed slack, and (2) that each post-contingency state matches a CPU {@code LoadFlow.run}
 * (reactive limits + distributed slack on) to load-flow tolerance.
 *
 * <p>Both sides use FIRST-slack selection; the slack generator is made non-participating so the
 * distributed-slack reference-generator share is unambiguous (the GPU has no P target at the slack).
 *
 * <p>Skipped unless the native lib is available (built by native/build-gpu.sh with cuDSS).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedFullLoadflowTest {

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
    void ieee14ReactiveLimitsPlusDistributedSlackMatchCpu() {
        // The first-slack generator's id (so it can be made non-participating consistently on both sides).
        LfNetwork probe = load(ieee14()).get(0);
        String slackGenId = probe.getSlackBus().getGenerators().stream().map(LfGenerator::getId).findFirst().orElseThrow();

        // GPU: converged base case, then the batched full loadflow over a handful of line outages incl. line 1-2.
        Network gpuNet = ieee14(slackGenId);
        LfNetwork net = load(gpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        cpuReference(es, target, eq, sv);
        double[] baseState = sv.get().clone();
        int n = baseState.length;

        List<String> lineIds = new ArrayList<>();
        for (var line : gpuNet.getLines()) {
            lineIds.add(line.getId());
            if (lineIds.size() >= 6) {
                break;
            }
        }
        List<BatchContingency> conts = new ArrayList<>();
        for (String id : lineIds) {
            conts.add(new BatchContingency(id, net.getBranchById(id)));
        }
        // Drive the device inner Newton tight (1e-10); outer-loop tolerances stay at OLF defaults (1e-2 slack,
        // 1e-4 reactive) so the device and the CPU reference run the SAME outer loops.
        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters().setMaxIterations(MAX_ITER), 1e-10, true, true,
                GpuBatchedSecurityAnalysisSolver.DEFAULT_REACTIVE_LIMIT_TOL,
                GpuBatchedSecurityAnalysisSolver.DEFAULT_SLACK_DIST_TOL, ReportNode.NO_OP);

        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters lfp = new LoadFlowParameters().setUseReactiveLimits(true).setDistributedSlack(true);
        // TIGHT inner-Newton reference: OLF's default UNIFORM_CRITERIA stops at ‖F‖₂ < convEpsPerEq·√n with
        // convEpsPerEq=1e-4 (~e-4 state slack — what used to masquerade as a GPU gap). Drive it to 1e-12 so the
        // reference lands on the true root, not ~3e-4 short of it (see GpuBatchedL1L2DiagnosticTest).
        OpenLoadFlowParameters.create(lfp).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setNewtonRaphsonConvEpsPerEq(1e-12);

        double worst = 0;
        int checked = 0;
        for (int i = 0; i < conts.size(); i++) {
            String id = conts.get(i).contingencyId();
            BatchScenarioResult r = results.get(i);
            if (r.needsCpuFallback()) {
                continue;                                        // islanding etc. — handled by the CPU path
            }
            assertTrue(r.hasConverged(), "contingency " + id + " must converge with reactive limits + distributed slack "
                    + "(status=" + r.convergenceStatus() + ", ||F||=" + r.finalMismatch() + ")");

            sv.set(java.util.Arrays.copyOf(r.state(), n));
            AcSolverUtil.updateNetwork(net, es);

            // CPU reference: the same outage on a fresh network, full LoadFlow.run.
            Network cpu = ieee14(slackGenId);
            cpu.getLine(id).disconnect();
            assertTrue(runner.run(cpu, lfp).isFullyConverged(), "CPU reference for " + id + " must converge");

            double w = 0;
            for (Bus cpuBus : cpu.getBusView().getBuses()) {
                LfBus gpuBus = net.getBusById(cpuBus.getId());
                if (gpuBus == null || Double.isNaN(cpuBus.getV())) {
                    continue;
                }
                w = Math.max(w, Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - gpuBus.getV()));
                w = Math.max(w, Math.abs(Math.toRadians(cpuBus.getAngle()) - gpuBus.getAngle()));
            }
            System.out.printf("  %-10s converged=%b, max|gpu - cpu| = %.2e%n", id, r.hasConverged(), w);
            worst = Math.max(worst, w);
            checked++;
        }
        System.out.printf("GPU batched full loadflow [IEEE14, RL + distributed slack]: %d contingencies checked, "
                + "worst max|gpu - cpu| = %.2e%n", checked, worst);
        assertTrue(checked >= 4, "must check several contingencies");
        // The device runs the outer loops SEQUENTIALLY in OLF's order (distributed slack fully stabilized, then
        // reactive limits, repeating) and water-fills the slack exactly like OLF. With the inner Newton driven
        // tight on BOTH sides (device 1e-10, reference convEpsPerEq=1e-12) and identical outer-loop tolerances,
        // every contingency — including the hardest, line 1-2 — matches to ~machine precision. The former ~3e-4
        // line-1-2 "gap" was entirely LoadFlow.run's DEFAULT inner-Newton stopping slack (UNIFORM_CRITERIA at
        // convEpsPerEq=1e-4 stops ~3e-4 short of the true root), NOT a GPU error: GpuBatchedL1L2DiagnosticTest
        // shows it collapsing 3.28e-4 → 2.4e-15 when the reference is tightened.
        assertTrue(worst < 1e-10, "every GPU full-loadflow post-contingency state must match the "
                + "CPU LoadFlow (worst = " + worst + ")");
    }

    private static List<LfNetwork> load(Network iidm) {
        return Networks.load(iidm, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector()));
    }

    private static Network ieee14() {
        return IeeeCdfNetworkFactory.create14();
    }

    /** IEEE14 with the given slack generator made non-participating (unambiguous distributed slack). */
    private static Network ieee14(String slackGenId) {
        Network n = IeeeCdfNetworkFactory.create14();
        Generator g = n.getGenerator(slackGenId);
        if (g != null) {
            g.newExtension(ActivePowerControlAdder.class).withParticipate(false).withDroop(4).add();
        }
        return n;
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
}
