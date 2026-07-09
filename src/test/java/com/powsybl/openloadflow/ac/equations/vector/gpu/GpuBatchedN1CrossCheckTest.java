/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrixFactory;
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
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ITEM 6 — VALIDATION: the batched GPU N-1 ({@link GpuBatchedSecurityAnalysisSolver})
 * must produce, for every qualified single-branch contingency, the SAME post-contingency
 * state as OLF's own Newton-Raphson on the equation system with that branch disabled. The
 * reference and the batch share semantics: plain NR, base-case targets/activation, the
 * branch's contribution removed (OLF deactivates the branch's flow terms when
 * {@code setDisabled(true)} fires {@code onDisableChange}; the GPU zeroes the masked
 * element). A second part benchmarks the batch wall time against the sequential
 * per-contingency solves on a larger meshed case.
 *
 * <p>Self-contained meshed networks (IEEE CDF) so no external case files are needed;
 * skipped unless the GPU native lib is available.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedN1CrossCheckTest {

    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-8;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU batched native lib not available (build it with native/build-gpu.sh + cuDSS)");
        // Cross-check any batched solve mode (0/1/2) against OLF: OLF_GPU_BATCHMODE=<n> (env,
        // inherited by the surefire fork). Default 0.
        if (System.getProperty("olf.gpu.batchMode") == null) {
            System.setProperty("olf.gpu.batchMode", System.getenv().getOrDefault("OLF_GPU_BATCHMODE", "0"));
        }
    }

    /** A self-contained AC problem: the LfNetwork, its equation system, targets and state. */
    private record Problem(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                           AcTargetVector target, EquationVector<AcVariableType, AcEquationType> eq, StateVector sv) {
    }

    private static Problem load(Network iidm) {
        LfNetwork network = Networks.load(iidm, new FirstSlackBusSelector()).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(network).create();
        AcSolverUtil.initStateVector(network, es, new UniformValueVoltageInitializer());
        AcTargetVector target = new AcTargetVector(network, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        return new Problem(network, es, target, eq, es.getStateVector());
    }

    /** OLF's own Newton over the current (possibly post-contingency) equation system. */
    private static double[] solveNewton(Problem p) {
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(p.es(), new DenseMatrixFactory())) {
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

    private static List<BatchContingency> closedBranchContingencies(LfNetwork network) {
        List<BatchContingency> contingencies = new ArrayList<>();
        for (LfBranch branch : network.getBranches()) {
            if (!branch.isDisabled() && branch.getBus1() != null && branch.getBus2() != null) {
                contingencies.add(new BatchContingency(branch.getId(), branch));
            }
        }
        return contingencies;
    }

    @Test
    void ieee14MatchesSequentialSolves() {
        // This test cross-checks the batched MACHINERY (per-scenario arrays, fill, retirement) against OLF's
        // sequential Newton, which has no outer loops. The batched full-loadflow path now runs the
        // reactive-limits (PV→PQ) outer loop, so widen the generator Q limits here to keep that outer loop
        // from firing and confounding the plain-NR comparison; reactive limits are verified end-to-end vs a
        // CPU LoadFlow.run in GpuBatchedReactiveLimitsTest.
        Network iidm = IeeeCdfNetworkFactory.create14();
        for (Generator g : iidm.getGenerators()) {
            g.newMinMaxReactiveLimits().setMinQ(-1e4).setMaxQ(1e4).add();
        }
        Problem p = load(iidm);
        // The batched full-loadflow path runs reactive-limits AND distributed-slack outer loops; both are
        // verified end-to-end vs a CPU LoadFlow.run elsewhere (GpuBatchedReactiveLimitsTest /
        // GpuBatchedDistributedSlackTest). Here we cross-check the plain batched MACHINERY against OLF's
        // sequential Newton (no outer loops), so disable participation so neither outer loop fires.
        for (LfBus bus : p.network().getBuses()) {
            bus.getGenerators().forEach(g -> g.setParticipating(false));
        }
        double[] xBase = solveNewton(p);                         // converged base case
        assumeTrue(maxAbs(p.eq().getArray()) < TOL, "base case must converge");

        List<BatchContingency> contingencies = closedBranchContingencies(p.network());
        NewtonRaphsonParameters params = new NewtonRaphsonParameters();

        // GPU batch on the pristine network + base state (no branch disabled yet).
        List<BatchScenarioResult> batch = GpuBatchedSecurityAnalysisSolver.solveBatchN1(
                p.network(), xBase, contingencies, p.es(), p.target(), params, ReportNode.NO_OP);
        assertTrue(batch.size() == contingencies.size(), "one result per contingency");

        int n = xBase.length;
        int compared = 0;
        int rejected = 0;
        double worst = 0;
        for (int i = 0; i < contingencies.size(); i++) {
            BatchScenarioResult r = batch.get(i);
            if (r.needsCpuFallback()) {
                rejected++;
                continue;                                        // fragmenting outage: not batched
            }
            // OLF reference: disable this branch, re-solve from base, restore.
            LfBranch branch = contingencies.get(i).outagedBranches().get(0);
            p.sv().set(xBase.clone());
            branch.setDisabled(true);
            double[] xRef = null;
            boolean refConverged = false;
            try {
                xRef = solveNewton(p);
                refConverged = maxAbs(p.eq().getArray()) < TOL;
            } catch (PowsyblException e) {
                // A fragmenting outage islands a bus, so the naive disable-branch reference is
                // over-determined (equations != variables). The GPU now SOLVES these by freezing the
                // island (full-loadflow mode); that path is verified by GpuBatchedIslandingTest, so the
                // naive cross-check just skips them here.
                rejected++;
            } finally {
                branch.setDisabled(false);
            }
            if (xRef == null || !refConverged) {
                continue;                                        // islanding / reference did not converge; skip
            }
            assertTrue(r.hasConverged(), "GPU scenario " + contingencies.get(i).contingencyId() + " must converge");
            double diff = 0;
            for (int k = 0; k < n; k++) {
                diff = Math.max(diff, Math.abs(r.state()[k] - xRef[k]));
            }
            worst = Math.max(worst, diff);
            compared++;
            assertTrue(diff < 1e-12, "contingency " + contingencies.get(i).contingencyId()
                    + ": GPU batch state must match OLF (max diff = " + diff + ")");
        }
        System.out.printf("IEEE14 batched N-1: %d contingencies, %d cross-checked vs OLF, %d rejected, "
                + "worst |x_gpu - x_olf| = %.2e%n", contingencies.size(), compared, rejected, worst);
        assertTrue(compared > 0, "must cross-check at least one contingency");
    }

    @Test
    void ieee118BatchVsSequentialBenchmark() {
        Problem p = load(IeeeCdfNetworkFactory.create118());
        double[] xBase = solveNewton(p);
        assumeTrue(maxAbs(p.eq().getArray()) < TOL, "base case must converge");

        List<BatchContingency> contingencies = closedBranchContingencies(p.network());
        NewtonRaphsonParameters params = new NewtonRaphsonParameters();

        // Warm-up (CUDA context + JIT), then timed batch.
        GpuBatchedSecurityAnalysisSolver.solveBatchN1(p.network(), xBase, contingencies, p.es(), p.target(), params, ReportNode.NO_OP);
        long t0 = System.nanoTime();
        List<BatchScenarioResult> batch = GpuBatchedSecurityAnalysisSolver.solveBatchN1(
                p.network(), xBase, contingencies, p.es(), p.target(), params, ReportNode.NO_OP);
        long batchMillis = (System.nanoTime() - t0) / 1_000_000;

        // Sequential reference: OLF Newton per contingency (disable / solve / restore). A
        // fragmenting outage (bridge branch) makes OLF's Jacobian singular — the GPU side
        // qualifies and routes those to the CPU path, so guard the reference the same way
        // (skip the ones that throw) to compare wall time on the solvable set.
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
                // singular Jacobian (network fragmented by the outage) — skip, as the GPU path does
            } finally {
                c.outagedBranches().forEach(b -> b.setDisabled(false));
            }
        }
        long seqMillis = (System.nanoTime() - s0) / 1_000_000;

        long gpuConverged = batch.stream().filter(BatchScenarioResult::hasConverged).count();
        System.out.printf("BENCH IEEE118 N-1: %d contingencies | GPU batch %d ms (%d converged) | "
                + "sequential CPU %d ms (%d converged) | speedup x%.1f%n",
                contingencies.size(), batchMillis, gpuConverged, seqMillis, seqConverged,
                seqMillis / (double) Math.max(1, batchMillis));
        assertTrue(gpuConverged > 0, "batch must converge at least one scenario");
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
