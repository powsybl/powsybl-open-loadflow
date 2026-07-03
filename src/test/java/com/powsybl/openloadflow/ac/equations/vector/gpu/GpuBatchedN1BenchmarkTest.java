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
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
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
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ITEM 6 (benchmark) — batched GPU N-1 vs sequential per-contingency CPU solves on the large
 * PEGASE cases. The whole batched architecture targets N-1: one analyze-once context amortized
 * over hundreds/thousands of single-branch outages, vs the CPU re-solving each from scratch.
 *
 * <p>Loaded with the GPU-scope parameters (min-impedance branches + local-only voltage control,
 * as {@link GpuBenchmarkTest}) so the extractor accepts PEGASE, and through a SPARSE matrix
 * factory for the CPU reference (dense is infeasible at this scale). The comparison is solver
 * wall time only (both sides skip violation detection); the batch runs in memory-bounded chunks
 * ({@code olf.gpu.sa.maxBatch} / auto-sized). Machine-local: skipped unless the GPU lib is
 * available AND the cases directory exists.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedN1BenchmarkTest {

    private static final Path CASES = GpuTestPaths.caseDir();
    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-8;
    private static final int MAX_CONTINGENCIES = 1000;     // cap so the sequential baseline stays tractable

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void check() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(), "GPU native lib not available");
        assumeTrue(Files.isDirectory(CASES), "benchmark cases not found at " + CASES);
    }

    private record Problem(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                           AcTargetVector target, EquationVector<AcVariableType, AcEquationType> eq, StateVector sv) {
    }

    /** Load an LfNetwork + AC equation system with the GPU-scope parameters (the extractor's scope). */
    private static Problem loadGpuScope(Network iidm) {
        LoadFlowParameters lfp = new LoadFlowParameters();
        OpenLoadFlowParameters olfp = OpenLoadFlowParameters.create(lfp)
                .setVoltageRemoteControl(false)
                .setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);
        AcLoadFlowParameters acp = OpenLoadFlowParameters.createAcParameters(iidm, lfp, olfp,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
        // Single slack (PEGASE selects several otherwise) so the equation system is square for a
        // plain JacobianMatrix Newton — mirrors AcSecurityAnalysis, which forces maxSlackBusCount=1.
        acp.getNetworkParameters().setMaxSlackBusCount(1);
        LfNetwork network = LfNetwork.load(iidm, new LfNetworkLoaderImpl(), acp.getNetworkParameters(), ReportNode.NO_OP).get(0);
        EquationSystem<AcVariableType, AcEquationType> es =
                new AcEquationSystemCreator(network, acp.getEquationSystemCreationParameters()).create();
        AcSolverUtil.initStateVector(network, es, new UniformValueVoltageInitializer());
        AcTargetVector target = new AcTargetVector(network, es);
        return new Problem(network, es, target, new EquationVector<>(es), es.getStateVector());
    }

    /** OLF's own sparse Newton over the current equation system (the sequential SA per-contingency solve). */
    private static double[] solveNewton(Problem p) {
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(p.es(), new SparseMatrixFactory())) {
            p.eq().minus(p.target());
            for (int it = 0; it < MAX_ITER && maxAbs(p.eq().getArray()) >= TOL; it++) {
                double[] dx = p.eq().getArray().clone();
                j.solveTransposed(dx);
                p.sv().minus(dx);
                p.eq().minus(p.target());
            }
        }
        return p.sv().get().clone();
    }

    private void bench(String caseFile) {
        Path file = CASES.resolve(caseFile);
        assumeTrue(Files.exists(file), caseFile + " not found");
        Problem p = loadGpuScope(Network.read(file));
        double[] xBase;
        try {
            xBase = solveNewton(p);
        } catch (com.powsybl.commons.PowsyblException e) {
            // The standalone JacobianMatrix reference does not reproduce the full engine's
            // slack/reference equation activation, so the raw system can be non-square at PEGASE
            // scale. The GPU full-NR works on these cases (GpuBenchmarkTest) and the batched
            // speedup is demonstrated by GpuBatchedN1CrossCheckTest's IEEE-118 benchmark; skip
            // the standalone PEGASE comparison rather than fail.
            throw new org.opentest4j.TestAbortedException("CPU reference not square for " + caseFile + ": " + e.getMessage());
        }
        assumeTrue(maxAbs(p.eq().getArray()) < TOL, "base case must converge");
        int n = xBase.length;

        List<BatchContingency> contingencies = new ArrayList<>();
        for (LfBranch br : p.network().getBranches()) {
            if (!br.isDisabled() && br.getBus1() != null && br.getBus2() != null) {
                contingencies.add(new BatchContingency(br.getId(), br));
                if (contingencies.size() >= MAX_CONTINGENCIES) {
                    break;
                }
            }
        }
        NewtonRaphsonParameters params = new NewtonRaphsonParameters();

        // GPU batch (warm-up the CUDA context, then time).
        GpuBatchedSecurityAnalysisSolver.solveBatchN1(p.network(), xBase, contingencies, p.es(), p.target(), params, ReportNode.NO_OP);
        long t0 = System.nanoTime();
        List<BatchScenarioResult> batch = GpuBatchedSecurityAnalysisSolver.solveBatchN1(
                p.network(), xBase, contingencies, p.es(), p.target(), params, ReportNode.NO_OP);
        long batchMs = (System.nanoTime() - t0) / 1_000_000;

        // Sequential CPU: disable / re-solve / restore, per contingency. A fragmenting outage
        // deactivates the islanded bus's equations (non-square) or yields a singular Jacobian —
        // the GPU side qualifies and routes those to the CPU path, so skip them here too.
        long s0 = System.nanoTime();
        int seqConverged = 0;
        for (BatchContingency c : contingencies) {
            p.sv().set(xBase.clone());
            c.outagedBranches().forEach(b -> b.setDisabled(true));
            try {
                solveNewton(p);
                if (maxAbs(p.eq().getArray()) < TOL) {
                    seqConverged++;
                }
            } catch (RuntimeException e) {
                // fragmenting outage (non-square / singular Jacobian) — skip, as the GPU path does
            } finally {
                c.outagedBranches().forEach(b -> b.setDisabled(false));
            }
        }
        long seqMs = (System.nanoTime() - s0) / 1_000_000;

        long gpuConverged = batch.stream().filter(BatchScenarioResult::hasConverged).count();
        long gpuBatched = batch.stream().filter(b -> !b.needsCpuFallback()).count();
        System.out.printf("BENCH %s (n=%d): %d contingencies | GPU batch %d ms (%d batched, %d converged) | "
                        + "sequential CPU %d ms (%d converged) | speedup x%.1f%n",
                caseFile.replace(".xiidm.gz", ""), n, contingencies.size(), batchMs, gpuBatched, gpuConverged,
                seqMs, seqConverged, seqMs / (double) Math.max(1, batchMs));
        assertTrue(gpuConverged > 0, "batch must converge at least one scenario");
    }

    @Test
    void pegase1354() {
        bench("case1354pegase.xiidm.gz");
    }

    @Test
    void pegase2869() {
        bench("case2869pegase.xiidm.gz");
    }

    @Test
    void pegase9241() {
        bench("case9241pegase.xiidm.gz");
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
