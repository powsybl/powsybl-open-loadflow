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
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SCALE validation of the batched GPU FULL loadflow (reactive limits + distributed slack, the outer loops
 * a bare fixed-iteration N-1 batch lacks) on the real PEGASE-9241 N-1 set. Where {@link GpuBatchedFullLoadflowTest} proves the
 * outer-loop machinery on IEEE14, this proves it engages and AGREES with the CPU at production scale.
 *
 * <p>It loads case9241pegase with the GPU-extractor scope (the same {@code loadGpuScope} as the throughput
 * bench: remote voltage control off, low-impedance branches replaced, one slack bus), converges the base
 * case, then runs a sample of single-branch outages through {@link GpuBatchedSecurityAnalysisSolver#solveBatchN1}
 * with {@code tol > 0} — full-loadflow mode, so reactive limits AND distributed slack fire per scenario. Each
 * converged post-contingency state is compared bus-by-bus to a CPU {@code LoadFlow.run} of the same outage
 * with the SAME parameters (reactive limits + distributed slack on). Islanding outages route to the CPU path
 * and are skipped here. The reported agreement is the headline: "CPU and GPU agree" at scale.
 *
 * <p>Machine-local: skipped unless the GPU lib is available AND the cases directory exists. The contingency
 * sample is the first {@code OLF_GPU_SAMPLE} (default 40) of the matched set ({@code OLF_GPU_CONT_FILE}) or,
 * absent that, the first enabled branches.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedPegaseFullLoadflowTest {

    private static final Path CASES = GpuTestPaths.caseDir();
    private static final String CASE = "case9241pegase.xiidm.gz";
    private static final double TOL = 1e-10;                    // device convergence target (full-loadflow mode)
    private static final int MAX_ITER = 30;
    private static final int SAMPLE = Integer.parseInt(System.getenv().getOrDefault("OLF_GPU_SAMPLE", "40"));
    // Per-bus agreement between the GPU full-loadflow state and the CPU LoadFlow.run. CPU and GPU use IDENTICAL
    // parameters AND convergence criteria on ALL levels: inner Newton (OLF's ‖F‖₂ < sqrt(convEpsPerEq²·n) with
    // convEpsPerEq=1e-12 on both — CPU via gpuScopeParameters, device via the convEpsPerEq batch arg), reactive
    // limits (switch tolerance = convEpsPerEq, matched), AND distributed slack (SLACK_TOL_PU on both — see
    // below). Two earlier discrepancies were resolved: (1) a real BUG — base-case-PQ voltage controllers were
    // frozen and could never switch back to PV under a relieving contingency (fixed in GpuAcDataExtractor +
    // initRlLimitTypeBatch; was the ~1e-3 TWT-14-2925 outlier); (2) the loose 1-MW default DS tolerance left a
    // ~1e-4 uniform-angle band, removed by matching a TIGHT DS tolerance on both sides. With all criteria tight
    // and matched the 40-sample agrees to worst ~1.5e-7 / median ~2e-8 — machine precision for this scale.
    // Overridable to inspect outliers.
    private static final double AGREE_TOL = Double.parseDouble(System.getenv().getOrDefault("OLF_GPU_AGREE_TOL", "1e-6"));
    // Distributed-slack tolerance (pu), MATCHED on both sides: device slackDistTol + CPU slackBusPMaxMismatch
    // (= SLACK_TOL_PU * SB). Tight so DS agrees to inner-Newton precision, not the loose 1-MW default band.
    private static final double SLACK_TOL_PU = Double.parseDouble(System.getenv().getOrDefault("OLF_GPU_SLACK_TOL", "1e-8"));

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void check() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(), "GPU native lib not available");
        assumeTrue(Files.isDirectory(CASES), "benchmark cases not found at " + CASES);
        assumeTrue(Files.exists(CASES.resolve(CASE)), CASE + " not found");
        if (System.getProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY) == null) {
            System.setProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY,
                    System.getenv().getOrDefault("OLF_GPU_MAXBATCH", "32"));
        }
        if (System.getProperty(GpuBatchedSecurityAnalysisSolver.BATCH_MODE_PROPERTY) == null) {
            System.setProperty(GpuBatchedSecurityAnalysisSolver.BATCH_MODE_PROPERTY,
                    System.getenv().getOrDefault("OLF_GPU_BATCHMODE", "3"));
        }
    }

    /** The GPU-scope OLF parameters — identical to the extractor's scope and reused for the CPU reference. */
    private static LoadFlowParameters gpuScopeParameters() {
        LoadFlowParameters lfp = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES)
                .setUseReactiveLimits(true)
                .setDistributedSlack(true);
        OpenLoadFlowParameters.create(lfp)
                .setVoltageRemoteControl(false)
                .setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE)
                // TIGHT inner Newton: OLF's default UNIFORM_CRITERIA (convEpsPerEq=1e-4) stops ~e-4 short of the
                // true root, which used to force AGREE_TOL down to a load-flow-tolerance band. Drive the CPU
                // reference to the root so this is a machine-precision comparison (see GpuBatchedL1L2DiagnosticTest).
                .setNewtonRaphsonConvEpsPerEq(1e-12)
                // TIGHT + MATCHED distributed-slack tolerance. OLF's default slackBusPMaxMismatch = 1 MW (1e-2 pu)
                // is the LOOSEST criterion in the solve: the DS outer loop stops early and admits a ~1 MW band, so
                // the device and CPU land at different (both-valid) distribution points → a uniform ~1e-4 angle
                // shift. Tighten it on BOTH sides (device slackDistTol below) so DS agrees to the same precision
                // as the inner Newton and reactive limits.
                .setSlackBusPMaxMismatch(SLACK_TOL_PU * 100.0);
        return lfp;
    }

    @Test
    void pegase9241FullLoadflowMatchesCpu() {
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

        // Confirm the full-loadflow machinery actually has work to do at this scope (else the comparison is
        // hollow — it would degenerate to a plain Newton solve). Also re-runs validate(): no DISTR_Q /
        // zero-impedance / disconnection-allowed throw on this scope.
        GpuAcData gpuData = GpuAcDataExtractor.extract(network, es);
        int nRl = gpuData.getReactiveLimitCount();
        int nDs = gpuData.getDistributedSlackBusCount();
        System.out.printf("PEGASE-9241 GPU full-loadflow scope: %d reactive-limit controllers, %d distributed-slack buses%n",
                nRl, nDs);
        assertTrue(nRl > 0, "expected local PV controllers to exercise reactive limits");

        // Converged base-case AC state (the hot start), exactly as the throughput bench does.
        try (AcLoadFlowContext context = new AcLoadFlowContext(network, acp)) {
            new AcloadFlowEngine(context).run();
        }
        AcSolverUtil.initStateVector(network, es, new PreviousValueVoltageInitializer());
        double[] baseState = es.getStateVector().get().clone();
        int n = baseState.length;

        List<BatchContingency> contingencies = sampleContingencies(network);
        System.out.printf("  driving %d single-branch contingencies through the FULL-loadflow batch (tol=%.0e)%n",
                contingencies.size(), TOL);

        NewtonRaphsonParameters params = new NewtonRaphsonParameters();
        params.setMaxIterations(MAX_ITER);
        // Apples-to-apples: the device inner Newton uses OLF's EXACT stopping criterion (convEpsPerEq=1e-12 →
        // ‖F‖₂ < sqrt(convEpsPerEq²·n)), the SAME one the CPU reference uses (gpuScopeParameters sets it), and
        // the SAME outer-loop tolerances (1e-4 reactive, 1e-2 slack). So CPU and GPU compare under identical
        // parameters and convergence criteria — any residual is the outer-loop orchestration band, not a
        // criterion or parameter mismatch.
        // OLF with the default UNIFORM_CRITERIA ties the reactive-limits PV→PQ switch threshold to
        // convEpsPerEq (AbstractAcOuterLoopConfig.createReactiveLimitsOuterLoop), NOT to maxReactivePowerMismatch.
        // So to match the switch DECISIONS the device reactiveLimitTol must equal convEpsPerEq (1e-12) too, and
        // the slack tolerance stays slackBusPMaxMismatch/SB = 1e-2.
        double convEpsPerEq = 1e-12;
        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(
                network, baseState, contingencies, es, target, params, TOL, true, true,
                convEpsPerEq, SLACK_TOL_PU, convEpsPerEq, ReportNode.NO_OP);

        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new SparseMatrixFactory()));

        double worst = 0;
        int checked = 0;
        int fallback = 0;
        int diverged = 0;
        List<double[]> errs = new ArrayList<>();             // [error] for the median
        for (int i = 0; i < contingencies.size(); i++) {
            String id = contingencies.get(i).contingencyId();
            BatchScenarioResult r = results.get(i);
            if (r.needsCpuFallback()) {
                fallback++;
                continue;                                    // islanding etc. — the CPU path owns these
            }
            if (!r.hasConverged()) {
                diverged++;
                System.out.printf("  %-18s GPU did NOT converge (status=%d, ||F||=%.2e)%n",
                        id, r.convergenceStatus(), r.finalMismatch());
                continue;
            }

            es.getStateVector().set(java.util.Arrays.copyOf(r.state(), n));
            AcSolverUtil.updateNetwork(network, es);

            // CPU reference: the same outage on a fresh network, full LoadFlow.run with the same scope params.
            Network cpu = Network.read(CASES.resolve(CASE));
            Branch<?> br = cpu.getBranch(id);
            if (br == null) {
                continue;                                    // a replaced low-impedance branch has no iidm id
            }
            br.getTerminal1().disconnect();
            br.getTerminal2().disconnect();
            boolean cpuOk = runner.run(cpu, gpuScopeParameters()).isFullyConverged();
            if (!cpuOk) {
                System.out.printf("  %-18s CPU reference did not fully converge — skipped%n", id);
                continue;
            }

            double w = 0;
            for (Bus cpuBus : cpu.getBusView().getBuses()) {
                LfBus gpuBus = network.getBusById(cpuBus.getId());
                if (gpuBus == null || Double.isNaN(cpuBus.getV())) {
                    continue;
                }
                double dv = Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - gpuBus.getV());
                double da = Math.abs(Math.toRadians(cpuBus.getAngle()) - gpuBus.getAngle());
                w = Math.max(w, Math.max(dv, da));
            }
            errs.add(new double[] {w});
            worst = Math.max(worst, w);
            checked++;
            if (w > AGREE_TOL) {
                System.out.printf("  %-18s OUTLIER max|gpu - cpu| = %.2e%n", id, w);
            }
        }

        double median = errs.isEmpty() ? Double.NaN
                : errs.stream().mapToDouble(a -> a[0]).sorted().toArray()[errs.size() / 2];
        System.out.printf("PEGASE-9241 full-loadflow vs CPU: %d checked, %d CPU-fallback (islanding), %d GPU non-converged "
                + "| median %.2e, worst %.2e (agree tol %.0e)%n",
                checked, fallback, diverged, median, worst, AGREE_TOL);
        assertTrue(checked >= 10, "must validate at least 10 contingencies at scale (checked " + checked + ")");
        assertTrue(worst < AGREE_TOL, "every GPU full-loadflow post-contingency state must agree with the CPU LoadFlow "
                + "(worst = " + worst + ")");
    }

    /** First SAMPLE branches of the matched set ({@code OLF_GPU_CONT_FILE}), or the first enabled branches. */
    private static List<BatchContingency> sampleContingencies(LfNetwork network) {
        List<BatchContingency> contingencies = new ArrayList<>();
        java.util.Map<String, LfBranch> byId = new java.util.HashMap<>();
        for (LfBranch br : network.getBranches()) {
            byId.put(br.getId(), br);
        }
        String contFile = System.getenv("OLF_GPU_CONT_FILE");
        if (contFile != null) {
            try {
                for (String line : Files.readAllLines(Path.of(contFile))) {
                    String key = line.strip();
                    LfBranch br = key.isEmpty() ? null : byId.get(key);
                    if (br != null && !br.isDisabled() && br.getBus1() != null && br.getBus2() != null) {
                        contingencies.add(new BatchContingency(key, br));
                        if (contingencies.size() >= SAMPLE) {
                            break;
                        }
                    }
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        } else {
            for (LfBranch br : network.getBranches()) {
                if (!br.isDisabled() && br.getBus1() != null && br.getBus2() != null) {
                    contingencies.add(new BatchContingency(br.getId(), br));
                    if (contingencies.size() >= SAMPLE) {
                        break;
                    }
                }
            }
        }
        return contingencies;
    }
}
