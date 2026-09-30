/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
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
import com.powsybl.openloadflow.ac.solver.GpuNewtonRaphsonFactory;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonParameters;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
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
 * BARE fixed-iteration batched N-1 throughput on PEGASE-9241 — the standard fixed-iteration
 * batched-solver benchmark methodology, so the numbers are directly comparable to other GPU N-1 tools.
 *
 * <p>A bare fixed-iteration batched solver runs a <em>fixed</em> number of Newton-Raphson iterations
 * per chunk (typically 4, sometimes 10) over the <em>non-islanding</em>
 * contingencies only — exactly the scope our {@link GpuBatchContingencyValidator}
 * already enforces (a contingency whose branch islands buses from the main component is
 * rejected and routed to the CPU path; a connectivity check marks
 * the same outages disconnected and skips them). Such a solve does <em>no</em>
 * convergence retirement: every live scenario runs all K steps in lockstep.
 *
 * <p>This test reproduces that: it qualifies the non-islanding subset and runs a sweep of
 * fixed iteration counts with {@code tol == 0} — which disables convergence-based retirement,
 * so the run is a clean measure of K lockstep Newton iterations over the contingency set. For
 * each K it reports wall time, throughput, and the post-K accuracy (how many scenarios reached
 * a 1e-6 mismatch), which should climb with K. Unlike {@link GpuBatchedN1BenchmarkTest} there
 * is no CPU comparison — this is a pure GPU-throughput measurement, not a speedup.
 *
 * <p>Machine-local: skipped unless the GPU lib is available AND the cases directory exists.
 * Select the native solve mode at runtime with {@code OLF_GPU_BATCHMODE=1} (uniform batched
 * cuDSS) or {@code 0} (per-scenario fallback, default);
 * both are compiled in (cuDSS >= 0.8 required).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedN1FixedIterTest {

    private static final Path CASES = GpuTestPaths.caseDir();
    private static final String CASE = "case9241pegase.xiidm.gz";
    // Typical fixed-iteration counts: 4 (default), 10. With the AC hot-start (converge the base case first,
    // start every contingency from it — as OLF CPU SA does) we converge to
    // 1e-12 at K=4. The sweep still climbs K to show margin.
    // The K-sweep. Default is just K=4 — the regression harness for the fixed-iteration throughput path
    // (run after every batched-path change). Override with OLF_GPU_FIXED_ITERS="4,6,10" to see the margin.
    private static final int[] FIXED_ITERS = parseFixedIters(System.getenv("OLF_GPU_FIXED_ITERS"), new int[] {4});
    private static final double ACCURACY_TOL = 1e-6;            // the reference mismatch tolerance

    private static int[] parseFixedIters(String env, int[] fallback) {
        if (env == null || env.isBlank()) {
            return fallback;
        }
        String[] parts = env.split(",");
        int[] ks = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ks[i] = Integer.parseInt(parts[i].trim());
        }
        return ks;
    }

    private static final int MAX_CONTINGENCIES = 1000;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void check() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(), "GPU native lib not available");
        assumeTrue(Files.isDirectory(CASES), "benchmark cases not found at " + CASES);
        assumeTrue(Files.exists(CASES.resolve(CASE)), CASE + " not found");
        // The uniform batched (mode 1) chunk auto-size over-commits device memory at PEGASE-9241 on
        // a small card (the known batched-mode memory-model follow-up), so cap the chunk here.
        // Overridable from the shell: OLF_GPU_MAXBATCH=<n> (env is inherited by the surefire fork,
        // unlike -D). A conservative default keeps the test green on a 6 GB card.
        if (System.getProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY) == null) {
            String cap = System.getenv().getOrDefault("OLF_GPU_MAXBATCH", "32");
            System.setProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY, cap);
        }
        // Native batched-solve mode (0 per-scenario / 1 uniform batched), runtime-selected.
        // Overridable from the shell: OLF_GPU_BATCHMODE=<0|1> (env is inherited by the fork).
        if (System.getProperty(GpuBatchedSecurityAnalysisSolver.BATCH_MODE_PROPERTY) == null) {
            System.setProperty(GpuBatchedSecurityAnalysisSolver.BATCH_MODE_PROPERTY,
                    System.getenv().getOrDefault("OLF_GPU_BATCHMODE", "0"));
        }
    }

    private record Problem(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                           AcTargetVector target, AcLoadFlowParameters acParameters) {
    }

    /** Load an LfNetwork + AC equation system with the GPU-scope parameters (the extractor's scope). */
    private static Problem loadGpuScope(Network iidm) {
        // DC_VALUES init for the network state vector; the caller then converges the base case to
        // AC before the contingency sweep (the hot start). Same scope as the GPU extractor.
        LoadFlowParameters lfp = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        OpenLoadFlowParameters olfp = OpenLoadFlowParameters.create(lfp)
                .setVoltageRemoteControl(false)
                .setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);
        // OLF_GPU_BASE_ON_GPU: run the base/warm-up load flow on the GPU (the full GpuNewtonRaphson) instead
        // of the CPU. This is the base-case warm-up strategy — it both runs faster (~0.4 vs ~1.5 s) and
        // ABSORBS the one-time CUDA init into the base phase (same .so), so the cold contingency pass is then
        // warm. Moves CUDA init out of the SA phase, closing the cold-E2E gap.
        if (System.getenv("OLF_GPU_BASE_ON_GPU") != null) {
            olfp.setAcSolverType(GpuNewtonRaphsonFactory.NAME);
        }
        AcLoadFlowParameters acp = OpenLoadFlowParameters.createAcParameters(iidm, lfp, olfp,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
        acp.getNetworkParameters().setMaxSlackBusCount(1);
        LfNetwork network = LfNetwork.load(iidm, new LfNetworkLoaderImpl(), acp.getNetworkParameters(), ReportNode.NO_OP).get(0);
        EquationSystem<AcVariableType, AcEquationType> es =
                new AcEquationSystemCreator(network, acp.getEquationSystemCreationParameters()).create();
        AcSolverUtil.initStateVector(network, es, acp.getVoltageInitializer());
        return new Problem(network, es, new AcTargetVector(network, es), acp);
    }

    @Test
    void pegase9241FixedIterations() {
        Problem p = loadGpuScope(Network.read(CASES.resolve(CASE)));
        LfNetwork network = p.network();
        EquationSystem<AcVariableType, AcEquationType> es = p.es();
        AcTargetVector target = p.target();

        // Every contingency hot-starts from the CONVERGED base-case AC state — exactly what OLF's
        // CPU security analysis does (base load flow, then PreviousValue init per contingency) and
        // what the production GPU path does (GpuBatchedAcSecurityAnalysis precomputes from the
        // converged base in afterPreContingencySimulation). A single-branch N-1 is a small
        // perturbation of the base case, so from this start it converges to 1e-12 in K=4. (DC-init
        // would need K=6+ — that gap is the START POINT, not the formulation.) Run a real AC load
        // flow on the base case, then re-init the state vector from the converged buses.
        long baseLfT0 = System.nanoTime();
        try (AcLoadFlowContext context = new AcLoadFlowContext(network, p.acParameters())) {
            new AcloadFlowEngine(context).run();
        }
        long baseLfMs = (System.nanoTime() - baseLfT0) / 1_000_000;
        AcSolverUtil.initStateVector(network, es, new PreviousValueVoltageInitializer());
        double[] baseState = es.getStateVector().get().clone();
        int n = baseState.length;

        // OLF_GPU_CONT_FILE (one IIDM branch id per line) selects an EXPLICIT contingency set in
        // file order — used to run a fixed, reproducible physical set (e.g. the
        // list from codegen/bench/gen_contingencies.py). Unset → the first MAX_CONTINGENCIES branches.
        List<BatchContingency> contingencies = new ArrayList<>();
        String contFile = System.getenv("OLF_GPU_CONT_FILE");
        if (contFile != null) {
            java.util.Map<String, LfBranch> byId = new java.util.HashMap<>();
            for (LfBranch br : network.getBranches()) {
                byId.put(br.getId(), br);
            }
            try {
                for (String id : Files.readAllLines(Path.of(contFile))) {
                    String key = id.strip();
                    LfBranch br = key.isEmpty() ? null : byId.get(key);
                    if (br != null && !br.isDisabled() && br.getBus1() != null && br.getBus2() != null) {
                        contingencies.add(new BatchContingency(key, br));
                    }
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            System.out.printf("  (explicit set from %s: %d branches resolved)%n", contFile, contingencies.size());
        } else {
            for (LfBranch br : network.getBranches()) {
                if (!br.isDisabled() && br.getBus1() != null && br.getBus2() != null) {
                    contingencies.add(new BatchContingency(br.getId(), br));
                    if (contingencies.size() >= MAX_CONTINGENCIES) {
                        break;
                    }
                }
            }
        }

        System.out.printf("BARE-FIXED-ITER %s (n=%d): %d single-branch contingencies (non-islanding subset "
                + "qualified on-GPU; islanding outages routed to CPU)%n",
                CASE.replace(".xiidm.gz", ""), n, contingencies.size());

        // End-to-end (OLF_GPU_E2E): CPU base load flow + ONE COLD GPU SA pass at K (CUDA init +
        // extract + analysis + solve fall into this first GPU call — we have no GPU base-case phase
        // to absorb CUDA init, unlike the base-case warm-up path). The honest single-pass wall time.
        if (System.getenv("OLF_GPU_E2E") != null) {
            int kE2E = Integer.parseInt(System.getenv().getOrDefault("OLF_GPU_E2E_K", "4"));
            NewtonRaphsonParameters params = new NewtonRaphsonParameters();
            params.setMaxIterations(kE2E);
            GpuBatchedSecurityAnalysisSolver.clearContextCache();
            long g0 = System.nanoTime();
            List<BatchScenarioResult> cold = GpuBatchedSecurityAnalysisSolver.solveBatchN1(
                    network, baseState, contingencies, es, target, params, 0.0, ReportNode.NO_OP);
            long gpuMs = (System.nanoTime() - g0) / 1_000_000;
            long cb = cold.stream().filter(b -> !b.needsCpuFallback()).count();
            String baseWhere = System.getenv("OLF_GPU_BASE_ON_GPU") != null ? "GPU" : "CPU";
            System.out.printf("  [E2E K=%d] base %s loadflow %d ms + %s GPU SA %d ms = %d ms total | %d ctg | %.2f ms/ctg%n",
                    kE2E, baseWhere, baseLfMs, baseWhere.equals("GPU") ? "warm" : "cold", gpuMs,
                    baseLfMs + gpuMs, cb, (baseLfMs + gpuMs) / (double) Math.max(1, cb));
        }

        long totalBatched = 0;
        for (int k : FIXED_ITERS) {
            NewtonRaphsonParameters params = new NewtonRaphsonParameters();
            params.setMaxIterations(k);

            // Warm-up (CUDA context + cuDSS analysis) so the timed run measures steady-state throughput.
            GpuBatchedSecurityAnalysisSolver.solveBatchN1(network, baseState, contingencies, es, target, params, 0.0, ReportNode.NO_OP);

            long t0 = System.nanoTime();
            List<BatchScenarioResult> batch = GpuBatchedSecurityAnalysisSolver.solveBatchN1(
                    network, baseState, contingencies, es, target, params, 0.0, ReportNode.NO_OP);
            long ms = (System.nanoTime() - t0) / 1_000_000;

            long batched = batch.stream().filter(b -> !b.needsCpuFallback()).count();
            double[] mismatches = batch.stream()
                    .filter(b -> !b.needsCpuFallback())
                    .mapToDouble(BatchScenarioResult::finalMismatch)
                    .sorted()
                    .toArray();
            long accurate = java.util.Arrays.stream(mismatches).filter(m -> m < ACCURACY_TOL).count();
            double minMismatch = mismatches.length > 0 ? mismatches[0] : Double.NaN;
            double medMismatch = mismatches.length > 0 ? mismatches[mismatches.length / 2] : Double.NaN;
            totalBatched = batched;
            System.out.printf("  K=%-2d : %5d ms | %d batched | %d/%d reached %.0e | min/med ||F||inf %.1e/%.1e "
                            + "| %.1f scenarios/s | %.2f ms/scenario%n",
                    k, ms, batched, accurate, batched, ACCURACY_TOL, minMismatch, medMismatch,
                    batched * 1000.0 / Math.max(1, ms), ms / (double) Math.max(1, batched));
        }
        assertTrue(totalBatched > 0, "at least one non-islanding contingency must be batched on the GPU");
    }
}
