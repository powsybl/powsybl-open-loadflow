/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcTargetVector;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchContingency;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchScenarioResult;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonParameters;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DIAGNOSTIC (not a gate): localize the one PEGASE-9241 full-loadflow outlier (TWT-14-2925, ~1e-3) — is it a
 * PV→PQ switch-SET disagreement between the device batched reactive-limits pass and OLF's outer loop, or a
 * within-tolerance distribution difference? Runs the single contingency on the GPU and a CPU {@code LoadFlow.run}
 * (identical params + tight criteria), then prints (1) the top differing buses and (2) every voltage-controlled
 * bus whose PV/PQ classification differs between the two (V held at target ⇒ PV; released ⇒ PQ at a Q limit).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedPegaseOutlierDiagnosticTest {

    private static final Path CASES = GpuTestPaths.caseDir();
    private static final String CASE = "case9241pegase.xiidm.gz";
    private static final String CONT = System.getenv().getOrDefault("OLF_GPU_OUTLIER", "TWT-14-2925");
    private static final double PV_EPS = 1e-6;                  // |V - targetV| below this ⇒ still voltage-controlled

    static {
        GpuTestPaths.init();
    }

    private static final boolean NO_DS = System.getenv("OLF_GPU_NO_DS") != null;
    private static final boolean NO_RL = System.getenv("OLF_GPU_NO_RL") != null;
    // DS tolerance (pu) applied on BOTH sides: GPU slackDistTol + CPU slackBusPMaxMismatch = SLACK_TOL_PU * SB.
    private static final double SLACK_TOL_PU = Double.parseDouble(System.getenv().getOrDefault("OLF_GPU_SLACK_TOL", "1e-2"));

    private static LoadFlowParameters gpuScopeParameters() {
        LoadFlowParameters lfp = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES)
                .setUseReactiveLimits(!NO_RL)
                .setDistributedSlack(!NO_DS);
        OpenLoadFlowParameters.create(lfp)
                .setVoltageRemoteControl(false)
                .setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE)
                .setNewtonRaphsonConvEpsPerEq(1e-12)
                .setSlackBusPMaxMismatch(SLACK_TOL_PU * 100.0);
        return lfp;
    }

    @Test
    void localizeOutlier() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(), "GPU native lib not available");
        assumeTrue(Files.exists(CASES.resolve(CASE)), CASE + " not found");
        System.setProperty(GpuBatchedSecurityAnalysisSolver.BATCH_MODE_PROPERTY, "3");

        Network iidm = Network.read(CASES.resolve(CASE));
        LoadFlowParameters lfp = gpuScopeParameters();
        OpenLoadFlowParameters olfp = OpenLoadFlowParameters.get(lfp);
        AcLoadFlowParameters acp = OpenLoadFlowParameters.createAcParameters(iidm, lfp, olfp,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
        acp.getNetworkParameters().setMaxSlackBusCount(1);
        LfNetwork network = LfNetwork.load(iidm, new LfNetworkLoaderImpl(), acp.getNetworkParameters(), ReportNode.NO_OP).get(0);
        EquationSystem<AcVariableType, AcEquationType> es =
                new AcEquationSystemCreator(network, acp.getEquationSystemCreationParameters()).create();
        AcSolverUtil.initStateVector(network, es, acp.getVoltageInitializer());
        AcTargetVector target = new AcTargetVector(network, es);
        try (AcLoadFlowContext context = new AcLoadFlowContext(network, acp)) {
            new AcloadFlowEngine(context).run();
        }
        AcSolverUtil.initStateVector(network, es, new PreviousValueVoltageInitializer());
        double[] baseState = es.getStateVector().get().clone();
        int n = baseState.length;

        // Dump the device RL data for the mis-switching controller bus (VL-7616_0) vs the true values.
        GpuAcData dbg = GpuAcDataExtractor.extract(network, es, !NO_DS, true);
        String probeBusId = System.getenv().getOrDefault("OLF_GPU_PROBE_BUS", "VL-7616_0");
        var probeBus = network.getBusById(probeBusId);
        if (probeBus != null) {
            int vrow = es.getVariableSet().getVariable(probeBus.getNum(), AcVariableType.BUS_V).getRow();
            int[] rlVrow = dbg.rlVrow();
            boolean inRlList = false;
            for (int i = 0; i < rlVrow.length; i++) {
                if (rlVrow[i] == vrow) {
                    inRlList = true;
                    System.out.printf("DEVICE RL DATA for %s: rlVtarget=%.6f rlMaxQ=%.4f rlMinQ=%.4f rlLoadQ=%.4f%n",
                            probeBusId, dbg.rlVtarget()[i], dbg.rlMaxQ()[i], dbg.rlMinQ()[i], dbg.rlLoadQ()[i]);
                    break;
                }
            }
            boolean vcEnabled = probeBus.isGeneratorVoltageControlEnabled();
            double vcTarget = probeBus.getGeneratorVoltageControl().map(vc -> vc.getTargetValue()).orElse(Double.NaN);
            System.out.printf("BASE-CASE status of %s: inDeviceRlList=%b, generatorVoltageControlEnabled(PV)=%b, "
                    + "baseV=%.6f target=%.6f (PV⇔V==target) | hasGVC=%b, getMaxQ=%.4f%n",
                    probeBusId, inRlList, vcEnabled, probeBus.getV(), vcTarget,
                    probeBus.getGeneratorVoltageControl().isPresent(), probeBus.getMaxQ());
        }

        var lb = network.getBranchById(CONT);
        assumeTrue(lb != null, CONT + " not in the Lf network");
        List<BatchContingency> conts = List.of(new BatchContingency(CONT, lb));
        double convEps = 1e-12;
        NewtonRaphsonParameters params = new NewtonRaphsonParameters().setMaxIterations(30);
        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(
                network, baseState, conts, es, target, params, 1e-10, !NO_DS, !NO_RL,
                convEps, SLACK_TOL_PU, convEps, ReportNode.NO_OP);
        BatchScenarioResult r = results.get(0);
        System.out.printf("%n=== %s === GPU status=%d, ||F||inf=%.2e, needsCpuFallback=%b%n",
                CONT, r.convergenceStatus(), r.finalMismatch(), r.needsCpuFallback());
        assumeTrue(!r.needsCpuFallback() && r.hasConverged(), "GPU must batch+converge the outlier");
        es.getStateVector().set(java.util.Arrays.copyOf(r.state(), n));
        AcSolverUtil.updateNetwork(network, es);

        // CPU reference for the same outage, identical params.
        Network cpu = Network.read(CASES.resolve(CASE));
        Branch<?> br = cpu.getBranch(CONT);
        br.getTerminal1().disconnect();
        br.getTerminal2().disconnect();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new SparseMatrixFactory()));
        boolean cpuOk = runner.run(cpu, gpuScopeParameters()).isFullyConverged();
        System.out.printf("CPU reference converged=%b%n", cpuOk);
        assumeTrue(cpuOk, "CPU reference must converge");

        // (1) top differing buses
        List<double[]> diffs = new ArrayList<>();               // [dv, da, busHash] — hash only for stable print via map
        List<String> ids = new ArrayList<>();
        double worst = 0;
        for (Bus cpuBus : cpu.getBusView().getBuses()) {
            var gpuBus = network.getBusById(cpuBus.getId());
            if (gpuBus == null || Double.isNaN(cpuBus.getV())) {
                continue;
            }
            double dv = Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - gpuBus.getV());
            double da = Math.abs(Math.toRadians(cpuBus.getAngle()) - gpuBus.getAngle());
            diffs.add(new double[] {dv, da});
            ids.add(cpuBus.getId());
            worst = Math.max(worst, Math.max(dv, da));
        }
        System.out.printf("worst bus |Δ| = %.3e over %d buses%n", worst, diffs.size());
        Integer[] order = new Integer[diffs.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, Comparator.comparingDouble((Integer i) ->
                -Math.max(diffs.get(i)[0], diffs.get(i)[1])));
        System.out.println("top differing buses (id: dV pu, dAngle rad):");
        for (int k = 0; k < Math.min(8, order.length); k++) {
            int i = order[k];
            System.out.printf("  %-14s dV=%.3e dA=%.3e%n", ids.get(i), diffs.get(i)[0], diffs.get(i)[1]);
        }

        // (2) PV/PQ switch-set disagreement: a voltage-controlling generator bus holds V==targetV while PV; once
        // it hits a Q limit it switches to PQ and V is released. Classify on BOTH sides and diff.
        int switchDiffs = 0;
        for (Generator g : cpu.getGenerators()) {
            if (!g.isVoltageRegulatorOn() || g.getTerminal().getBusView().getBus() == null) {
                continue;
            }
            Bus cpuBus = g.getTerminal().getBusView().getBus();
            var gpuBus = network.getBusById(cpuBus.getId());
            if (gpuBus == null || Double.isNaN(cpuBus.getV())) {
                continue;
            }
            double targetVpu = g.getTargetV() / cpuBus.getVoltageLevel().getNominalV();
            boolean cpuPv = Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - targetVpu) < PV_EPS;
            boolean gpuPv = Math.abs(gpuBus.getV() - targetVpu) < PV_EPS;
            if (cpuPv != gpuPv) {
                switchDiffs++;
                // Is the CPU (PV) bus genuinely AT its Q limit (borderline → path-dependent) or well inside it
                // (q << maxQ → the GPU switched a clearly-PV bus, a real bug)? Read the CPU generation Q vs limits.
                double qMw = -g.getTerminal().getQ();          // generation Q (MVAr); sign: injected
                double minQ = g.getReactiveLimits().getMinQ(g.getTargetP());
                double maxQ = g.getReactiveLimits().getMaxQ(g.getTargetP());
                double marginToMax = maxQ - qMw;               // >0 means q is INSIDE the max limit
                System.out.printf("  SWITCH-SET DIFF %-12s (bus %s): CPU %s / GPU %s | cpuV=%.6f gpuV=%.6f targetV=%.6f pu%n"
                        + "      CPU genQ=%.3f MVAr in [%.1f, %.1f], margin to maxQ=%.4f MVAr (%.2e pu)%n",
                        g.getId(), cpuBus.getId(), cpuPv ? "PV" : "PQ", gpuPv ? "PV" : "PQ",
                        cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV(), gpuBus.getV(), targetVpu,
                        qMw, minQ, maxQ, marginToMax, marginToMax / 100.0);
                if (switchDiffs >= 12) {
                    System.out.println("  ... (more)");
                    break;
                }
            }
        }
        System.out.printf("%nPV/PQ switch-set disagreements: %d%n", switchDiffs);
        // Regression guard for the base-PQ-controller fix (GpuAcDataExtractor + initRlLimitTypeBatch): the device
        // must re-derive the SAME PV/PQ set as OLF for the contingency, incl. restoring a base-PQ controller to PV.
        assertTrue(switchDiffs == 0, "the GPU and CPU must agree on the PV/PQ switch set for " + CONT
                + " (disagreements = " + switchDiffs + ")");
    }
}
