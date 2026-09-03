/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcActivation;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcData;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcDataExtractor;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcNewtonSolver;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfShunt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Solver for batched N-1 security analysis on the GPU: runs hundreds to thousands of
 * post-contingency AC power flows in one batched Newton-Raphson, with per-scenario
 * convergence retirement (converged scenarios stop filling/solving early). Expected
 * throughput gain: one-to-two orders of magnitude over sequential per-contingency solves,
 * amortizing the device context creation and cuDSS symbolic analysis over the entire
 * contingency set.
 *
 * <p>Scope: single-branch outages that keep the main synchronous component intact
 * (checked per scenario; contingencies that would island or fragment the network are
 * rejected). Distributed slack, reactive limits, and secondary controls are not yet
 * device-resident (scope: plain N-1 with base-case participation factors).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class GpuBatchedSecurityAnalysisSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpuBatchedSecurityAnalysisSolver.class);

    /** System property: an explicit GPU batch chunk size (scenarios). When unset, the chunk is auto-sized from free device memory. */
    public static final String MAX_BATCH_PROPERTY = "olf.gpu.sa.maxBatch";
    /** Fallback chunk size used when auto-sizing is unavailable (no GPU / memory-query failure). */
    public static final int DEFAULT_MAX_BATCH = 256;
    /**
     * System property: the native batched-solve mode (cuDSS >= 0.8, all compiled in, runtime-selected).
     * 0 = per-scenario refactor+solve loop (memory-light serial fallback; never benchmarked as best);
     * 1 = uniform batched cuDSS API (one batched factorization+solve per iteration — faster per-iteration
     * SOLVE, but its nS-block ANALYSIS costs ~1.6 s cold, the single-pass loser even with the context cache);
     * 2 = block-diagonal single matrix, factorize-once + refactorize-every (the refactor-every strategy;
     * measured ~parity with mode 1 since cuDSS refactorization is not cheaper than factorization);
     * 3 = UBATCH (single per-block matrix + CUDSS_CONFIG_UBATCH_SIZE), the DEFAULT
     * mechanism: a cheap one-block ANALYSIS (~150 ms vs mode 1's ~1.6 s) wins the PRIMARY single-cold-pass
     * use case (8.52 vs 9.23 ms/ctg on cuDSS 0.7.1, K=4 — the cheap analysis beats mode 1's faster solve),
     * which is what we build and ship against. NB cuDSS 0.8 regressed UBATCH factorize/solve ~2x (UBATCH
     * deprecated there) so set {@code -Dolf.gpu.batchMode=1} when running against a 0.8 install.
     * 4 = UBATCH + iter0-only (factor at each chunk's iter 0, SOLVE-only after) and
     * 5 = UBATCH + refactorize-after: EXPERIMENTAL factor-skip probes, kept for retesting on other
     * grids/formulations. Measured non-beneficial on case9241pegase (mode 5 = mode 3 exactly; mode 4 is
     * ~2x faster per iter but needs ~3x more iters to converge, a net wash) — do NOT use in production.
     */
    public static final String BATCH_MODE_PROPERTY = "olf.gpu.batchMode";

    /** Resolve the native batched-solve mode from {@value #BATCH_MODE_PROPERTY} (default 3, UBATCH). */
    public static int resolveBatchMode() {
        return Integer.getInteger(BATCH_MODE_PROPERTY, 3);
    }

    /**
     * Cached batched native context, reused across {@code solveBatchN1} calls whose extracted
     * {@link GpuAcData}, chunk size and mode are identical. The cuDSS symbolic ANALYSIS (and the
     * uploaded per-element static data) depend only on the network pattern + admittances — NOT on
     * the operating point (injections/targets are passed per solve) — so successive SA runs on the
     * same network reuse the analyzed, allocated context instead of rebuilding it (the per-run
     * extraction/build/analysis + device alloc/free was ~25% of a single-run wall time). The native
     * handle is freed when a non-matching context evicts it, by {@link #clearContextCache()}, or at
     * JVM shutdown. NOT thread-safe across different networks (single-entry cache); guarded so the
     * native handle is never corrupted.
     */
    private static final class CachedBatchContext {
        final long handle;
        final GpuAcData data;
        final int chunkSize;
        final int mode;

        CachedBatchContext(long handle, GpuAcData data, int chunkSize, int mode) {
            this.handle = handle;
            this.data = data;
            this.chunkSize = chunkSize;
            this.mode = mode;
        }
    }

    private static CachedBatchContext cachedContext;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(GpuBatchedSecurityAnalysisSolver::clearContextCache,
                "olf-gpu-batch-context-cleanup"));
    }

    /** Acquire a batched native context for this (data, chunkSize, mode), reusing the cache when it matches. */
    private static synchronized long acquireBatchContext(GpuAcData data, int chunkSize, int mode, int n) {
        if (cachedContext != null && cachedContext.chunkSize == chunkSize && cachedContext.mode == mode
                && cachedContext.data.sameAs(data)) {
            LOGGER.debug("Batched N-1: reusing cached GPU context (chunk {}, mode {})", chunkSize, mode);
            return cachedContext.handle;
        }
        if (cachedContext != null) {                         // evict a stale context (different network/chunk/mode)
            GpuAcNewtonSolver.destroyBatchContext(cachedContext.handle);
            cachedContext = null;
        }
        long handle = GpuAcNewtonSolver.createBatchContext(n, chunkSize, mode,
                data.cbIn(), data.cbIdx(), data.obIn(), data.obIdx(),
                data.shIn(), data.shIdx(), data.hvIn(), data.hvIdx(),
                data.rlVrow(), data.rlMinQ(), data.rlMaxQ(), data.rlLoadQ(), data.rlVtarget(),
                data.dsPhiRow(), data.dsFactor(), data.dsSlackPhiRow(), data.dsSlackTargetP(),
                data.dsBaseTargetP(), data.dsMaxDelta(), data.dsMinDelta(),
                data.dqElem(), data.dqSide(), data.dqWeight(), data.dqRow(), data.dqKind(),
                data.drRow(), data.drCol(), data.drCoef(), data.ziIn(), data.ziIdx());
        cachedContext = new CachedBatchContext(handle, data, chunkSize, mode);
        return handle;
    }

    /** Free the cached batched native context, if any. Safe to call repeatedly. */
    public static synchronized void clearContextCache() {
        if (cachedContext != null) {
            GpuAcNewtonSolver.destroyBatchContext(cachedContext.handle);
            cachedContext = null;
        }
    }

    /** A contingency to analyze on the device: exactly one of — 1–2 branch outages (N-1 / N-2, disabled in
     *  the per-scenario element mask), a single generator loss ({@code lostGenerator}, applied as a per-scenario
     *  target override), or one+ fixed-shunt disconnections ({@code outagedShunts}, disabled in the element
     *  mask like branches). */
    public record BatchContingency(String contingencyId, List<LfBranch> outagedBranches, LfGenerator lostGenerator,
                                   List<LfShunt> outagedShunts) {
        public BatchContingency {
            Objects.requireNonNull(contingencyId);
            Objects.requireNonNull(outagedBranches);
            Objects.requireNonNull(outagedShunts);
            boolean isGen = lostGenerator != null;
            boolean isShunt = !outagedShunts.isEmpty();
            boolean isBranch = !outagedBranches.isEmpty();
            int kinds = (isGen ? 1 : 0) + (isShunt ? 1 : 0) + (isBranch ? 1 : 0);
            if (kinds != 1 || outagedBranches.size() > 2) {
                throw new IllegalArgumentException(
                        "a batched contingency is exactly one of: 1-2 branches, one generator, or fixed shunt(s)");
            }
            outagedBranches = List.copyOf(outagedBranches);
            outagedShunts = List.copyOf(outagedShunts);
        }

        /** A branch outage (N-1 or N-2). */
        public BatchContingency(String contingencyId, List<LfBranch> outagedBranches) {
            this(contingencyId, outagedBranches, null, List.of());
        }

        /** Convenience for the common single-branch (N-1) case. */
        public BatchContingency(String contingencyId, LfBranch outagedBranch) {
            this(contingencyId, List.of(Objects.requireNonNull(outagedBranch)), null, List.of());
        }

        /** A generator loss. */
        public static BatchContingency generator(String contingencyId, LfGenerator lostGenerator) {
            return new BatchContingency(contingencyId, List.of(), Objects.requireNonNull(lostGenerator), List.of());
        }

        /** A fixed-shunt disconnection (the shunt element is masked off, like a branch outage). */
        public static BatchContingency shunt(String contingencyId, LfShunt outagedShunt) {
            return new BatchContingency(contingencyId, List.of(), null, List.of(Objects.requireNonNull(outagedShunt)));
        }
    }

    /** The per-scenario target/row-mode changes a generator loss applies on the device: remove the lost
     *  generator's P from its bus's P target ({@code pRow}→{@code pTarget}); if the loss un-controls the bus
     *  ({@code toPq}) flip its BUS_V row to the reactive-power equation and set {@code vRow}→{@code qTarget}.
     *  Mirrors {@code LfContingency.processLostGenerators}.
     *
     *  <p>When the lost generator sits on a distributed-slack participant bus, {@code dsBus} is that bus's
     *  DS-pack index (else -1) and the {@code ds*Delta} fields carry the reweight the device applies: the
     *  bus's participation factor, base target P, headroom and footroom each shrink by the lost gen's share,
     *  and the per-scenario factor sum drops by {@code dsFactorDelta} so the other buses absorb that share.
     *  This is only valid when the slack itself does not participate (otherwise the loss is rejected). */
    public record GenOutage(int pRow, double pTarget, int vRow, double qTarget, boolean toPq,
                            int dsBus, double dsFactorDelta, double dsBaseDelta, double dsMaxDelta, double dsMinDelta) {
    }

    /** Convergence status: the scenario converged within the tolerance. */
    public static final int STATUS_CONVERGED = 0;
    /** Convergence status: the scenario hit the iteration cap without converging. */
    public static final int STATUS_MAX_ITERATION = 1;
    /** Convergence status: the scenario diverged (NaN/Inf mismatch). */
    public static final int STATUS_DIVERGED = -1;
    /**
     * Convergence status: the contingency was not batched on the GPU (it fragments the
     * network or its branch is absent from the GPU model) and must be solved by the
     * sequential CPU security-analysis path. The state is the base case (unchanged).
     */
    public static final int STATUS_REJECTED = -2;

    /**
     * The per-scenario outcome: the converged state, iteration count, final mismatch and
     * the convergence status (see the {@code STATUS_*} constants).
     */
    public record BatchScenarioResult(String contingencyId, double[] state, int iterations,
                                      double finalMismatch, int convergenceStatus) {
        public BatchScenarioResult {
            Objects.requireNonNull(contingencyId);
            Objects.requireNonNull(state);
        }

        public boolean hasConverged() {
            return convergenceStatus == STATUS_CONVERGED;
        }

        /** True if this contingency must fall back to the sequential CPU path. */
        public boolean needsCpuFallback() {
            return convergenceStatus == STATUS_REJECTED;
        }
    }

    /**
     * Solver for batched contingencies: given a base-case state and a list of
     * single-branch contingencies, extract one batched GPU context and run all
     * scenarios in a single Newton-Raphson loop.
     *
     * @param network         the AC network (base case)
     * @param baseState       the converged base-case state vector
     * @param contingencies   list of single-branch outages to analyze
     * @param equationSystem  the base-case equation system
     * @param targetVector    the base-case targets
     * @param parameters      Newton-Raphson stopping criteria
     * @param reportNode      for logging and diagnostics
     * @return per-scenario results (state, iterations, convergence status)
     */
    public static List<BatchScenarioResult> solveBatchN1(LfNetwork network, double[] baseState,
                                                         List<BatchContingency> contingencies,
                                                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                         TargetVector<AcVariableType, AcEquationType> targetVector,
                                                         NewtonRaphsonParameters parameters,
                                                         ReportNode reportNode) {
        return solveBatchN1(network, baseState, contingencies, equationSystem, targetVector, parameters,
                GpuNewtonRaphson.GPU_TOLERANCE, true, true, reportNode);
    }

    /**
     * As {@link #solveBatchN1(LfNetwork, double[], List, EquationSystem, TargetVector, NewtonRaphsonParameters, ReportNode)}
     * but with the run's actual outer loops declared. {@code distributedSlack} / {@code reactiveLimits} must
     * match the outer loops OLF applied to the base case: the device runs the distributed-slack pass only when
     * {@code distributedSlack} and the PV→PQ reactive-limits pass only when {@code reactiveLimits}, so a
     * disabled outer loop is never run on the device. Passing the wrong flags diverges from the CPU under a
     * non-trivial slack mismatch or a reactive-limit violation.
     */
    public static List<BatchScenarioResult> solveBatchN1(LfNetwork network, double[] baseState,
                                                         List<BatchContingency> contingencies,
                                                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                         TargetVector<AcVariableType, AcEquationType> targetVector,
                                                         NewtonRaphsonParameters parameters,
                                                         boolean distributedSlack, boolean reactiveLimits,
                                                         ReportNode reportNode) {
        return solveBatchN1(network, baseState, contingencies, equationSystem, targetVector, parameters,
                GpuNewtonRaphson.GPU_TOLERANCE, distributedSlack, reactiveLimits, reportNode);
    }

    /**
     * As the {@code (distributedSlack, reactiveLimits)} overload but with the device outer-loop tolerances
     * declared explicitly (per-unit) so the device aligns to the CPU loadflow's outer-loop criteria. Used by
     * the production SA path, which reads them from the run's loadflow parameters.
     */
    public static List<BatchScenarioResult> solveBatchN1(LfNetwork network, double[] baseState,
                                                         List<BatchContingency> contingencies,
                                                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                         TargetVector<AcVariableType, AcEquationType> targetVector,
                                                         NewtonRaphsonParameters parameters,
                                                         boolean distributedSlack, boolean reactiveLimits,
                                                         double reactiveLimitTol, double slackDistTol,
                                                         ReportNode reportNode) {
        return solveBatchN1(network, baseState, contingencies, equationSystem, targetVector, parameters,
                GpuNewtonRaphson.GPU_TOLERANCE, distributedSlack, reactiveLimits, reactiveLimitTol, slackDistTol,
                reportNode);
    }

    /**
     * As the {@code (reactiveLimitTol, slackDistTol)} overload but with an explicit {@code convEpsPerEq} for the
     * device inner Newton (device tol defaults to {@link GpuNewtonRaphson#GPU_TOLERANCE} as the fixed-iter
     * sentinel). {@code convEpsPerEq > 0} makes the device stop on OLF's exact {@code ‖F‖₂ < sqrt(convEpsPerEq²·n)}
     * so its reactive-limit switch decisions are taken at the SAME convergence depth as the CPU — used by the
     * production SA path so GPU and CPU agree beyond the outer-loop tolerance band.
     */
    public static List<BatchScenarioResult> solveBatchN1(LfNetwork network, double[] baseState,
                                                         List<BatchContingency> contingencies,
                                                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                         TargetVector<AcVariableType, AcEquationType> targetVector,
                                                         NewtonRaphsonParameters parameters,
                                                         boolean distributedSlack, boolean reactiveLimits,
                                                         double reactiveLimitTol, double slackDistTol,
                                                         double convEpsPerEq, ReportNode reportNode) {
        return solveBatchN1(network, baseState, contingencies, equationSystem, targetVector, parameters,
                GpuNewtonRaphson.GPU_TOLERANCE, distributedSlack, reactiveLimits, reactiveLimitTol, slackDistTol,
                convEpsPerEq, reportNode);
    }

    /**
     * As {@link #solveBatchN1(LfNetwork, double[], List, EquationSystem, TargetVector, NewtonRaphsonParameters, ReportNode)}
     * but with an explicit mismatch tolerance. Passing {@code tol <= 0} selects the native
     * DEVICE-SIDE fixed-iteration mode: every scenario runs exactly {@code parameters.getMaxIterations()}
     * Newton steps entirely on the device, with NO per-iteration mismatch copy back to the host and
     * NO convergence retirement (the mismatch is read back once, at the end, only to report it).
     * This is a fixed-iteration NR loop — it drops the per-iteration PCIe sync, so it is
     * the fastest way to benchmark raw batched throughput. Tradeoff: a diverging scenario is not
     * isolated and can poison a uniform-batched factorization, so use {@code tol > 0} when scenarios
     * may diverge.
     */
    public static List<BatchScenarioResult> solveBatchN1(LfNetwork network, double[] baseState,
                                                         List<BatchContingency> contingencies,
                                                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                         TargetVector<AcVariableType, AcEquationType> targetVector,
                                                         NewtonRaphsonParameters parameters,
                                                         double tol,
                                                         ReportNode reportNode) {
        return solveBatchN1(network, baseState, contingencies, equationSystem, targetVector, parameters,
                tol, true, true, reportNode);
    }

    /** OLF default outer-loop convergence tolerances (per-unit on the 100 MVA base): slackBusPMaxMismatch =
     *  1 MW → 1e-2 pu, maxReactivePowerMismatch = 1 MVAr → 1e-2/… → 1e-4 pu. The device uses these so its
     *  outer-loop fixed point matches the CPU loadflow's; a test wanting machine-precision agreement passes
     *  tight values on BOTH sides (a loose tolerance leaves a redistribution band ~its size). */
    public static final double DEFAULT_REACTIVE_LIMIT_TOL = 1e-4;
    public static final double DEFAULT_SLACK_DIST_TOL = 1e-2;

    /**
     * As {@link #solveBatchN1(LfNetwork, double[], List, EquationSystem, TargetVector, NewtonRaphsonParameters, double, boolean, boolean, ReportNode)}
     * with the device outer-loop tolerances defaulting to OLF's ({@link #DEFAULT_REACTIVE_LIMIT_TOL} /
     * {@link #DEFAULT_SLACK_DIST_TOL}).
     */
    public static List<BatchScenarioResult> solveBatchN1(LfNetwork network, double[] baseState,
                                                         List<BatchContingency> contingencies,
                                                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                         TargetVector<AcVariableType, AcEquationType> targetVector,
                                                         NewtonRaphsonParameters parameters,
                                                         double tol,
                                                         boolean distributedSlack, boolean reactiveLimits,
                                                         ReportNode reportNode) {
        return solveBatchN1(network, baseState, contingencies, equationSystem, targetVector, parameters, tol,
                distributedSlack, reactiveLimits, DEFAULT_REACTIVE_LIMIT_TOL, DEFAULT_SLACK_DIST_TOL, reportNode);
    }

    /**
     * Lowest-level entry point. {@code reactiveLimitTol} / {@code slackDistTol} are the device outer-loop
     * convergence tolerances (pu) — set them to the run's loadflow criteria (maxReactivePowerMismatch /
     * slackBusPMaxMismatch, per-unitized) so the device runs the DS/RL outer loops to the SAME tolerance as
     * the CPU. With matching parameters and the same equations the GPU and CPU outer-loop fixed points agree
     * to the inner-Newton precision.
     */
    public static List<BatchScenarioResult> solveBatchN1(LfNetwork network, double[] baseState,
                                                         List<BatchContingency> contingencies,
                                                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                         TargetVector<AcVariableType, AcEquationType> targetVector,
                                                         NewtonRaphsonParameters parameters,
                                                         double tol,
                                                         boolean distributedSlack, boolean reactiveLimits,
                                                         double reactiveLimitTol, double slackDistTol,
                                                         ReportNode reportNode) {
        return solveBatchN1(network, baseState, contingencies, equationSystem, targetVector, parameters, tol,
                distributedSlack, reactiveLimits, reactiveLimitTol, slackDistTol, 0.0, reportNode);
    }

    /**
     * As the lowest {@code (reactiveLimitTol, slackDistTol)} overload but with an explicit {@code convEpsPerEq}:
     * when {@code > 0} the device inner-Newton stops on OLF's EXACT criterion
     * ({@code DefaultNewtonRaphsonStoppingCriteria}: {@code ‖F‖₂ < sqrt(convEpsPerEq² · n)}) instead of the
     * {@code tol} infinity-norm floor — so a batched SA can be run with the SAME stopping decision as the CPU
     * loadflow for an apples-to-apples GPU-vs-CPU timing comparison. {@code convEpsPerEq <= 0} keeps the
     * infinity-norm floor (the default the correctness/parity tests rely on).
     */
    public static List<BatchScenarioResult> solveBatchN1(LfNetwork network, double[] baseState,
                                                         List<BatchContingency> contingencies,
                                                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                         TargetVector<AcVariableType, AcEquationType> targetVector,
                                                         NewtonRaphsonParameters parameters,
                                                         double tol,
                                                         boolean distributedSlack, boolean reactiveLimits,
                                                         double reactiveLimitTol, double slackDistTol,
                                                         double convEpsPerEq,
                                                         ReportNode reportNode) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(baseState);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(equationSystem);
        Objects.requireNonNull(targetVector);
        Objects.requireNonNull(parameters);

        if (contingencies.isEmpty()) {
            LOGGER.debug("Batched N-1 with empty contingency list");
            return List.of();
        }

        // Extract base-case GPU data once (shared structure, all scenarios use same pattern). The batch path
        // now carries all three family passes: DISTR_Q (remote/distributed generator VC), DISTR_RHO
        // (transformer ratio distribution) and zero-impedance branches (bus couplers). A contingency that
        // outages a DISTR_RHO controller or a zero-impedance branch is routed to the CPU (below / via the
        // element mapper) because the fixed batch pattern cannot represent the resulting structural change.
        GpuAcData gpuData = GpuAcDataExtractor.extract(network, equationSystem, distributedSlack, reactiveLimits);
        GpuAcActivation baseActivation = GpuAcActivation.fromEquationSystem(network, equationSystem);
        GpuBatchElementMapper elementMapper = new GpuBatchElementMapper(network);

        int n = baseState.length;
        int nElements = gpuData.getTotalElementCount();
        double[] baseTargets = baseActivation.applyTargets(targetVector.getArray());

        // Qualify each contingency. Outages that keep the main component intact batch as a value-only
        // mask over the fixed pattern. A FRAGMENTING outage batches too in the full-loadflow mode
        // (tol > 0): its islanded buses are frozen to identity (held at base) in that scenario's row
        // mode, so the islanded sub-block stays non-singular and the main component solves correctly.
        // In the fixed-iter benchmark mode (tol <= 0) islanding routes to the CPU path.
        boolean handleIslanding = tol > 0;
        List<GpuBatchContingencyValidator.Qualification> qualifications =
                GpuBatchContingencyValidator.qualify(network, contingencies, elementMapper, handleIslanding);
        // Outaging a DISTR_RHO controller transformer reforms the ratio-distribution 1/count coefficients,
        // which the fixed batch pattern cannot represent — route those to the CPU (the DISTR_Q pass IS
        // batched, so DISTR_RHO is the only family that needs a contributor-outage rejection).
        Set<LfBranch> distrRhoControllers = gpuData.getDistrRhoContributionCount() > 0
                ? GpuAcDataExtractor.distrRhoControllerBranches(network, equationSystem) : Set.of();
        // Distributed-slack factor denominator (Σ maxP/droop over ALL participating gens, incl. the slack's)
        // and whether the slack itself participates: a generator at a DS-participant bus is batched with a
        // per-scenario reweight (computeGenOutage), but only when the slack does not participate.
        double dsSum = 0;
        if (distributedSlack) {                              // no slack distribution ⇒ no per-scenario DS reweight
            for (LfBus b : network.getBuses()) {
                for (LfGenerator g : b.getGenerators()) {
                    if (g.isParticipating() && g.getDroop() != 0) {
                        dsSum += g.getMaxP() / g.getDroop();
                    }
                }
            }
        }
        boolean slackParticipates = java.util.Arrays.stream(gpuData.dsFactor()).sum() < 1 - 1e-9;
        List<Integer> batchedIndices = new ArrayList<>();
        int[][] frozenRowsByIndex = new int[contingencies.size()][];     // islanded buses' variable rows, per contingency
        double[][] frozenTargetsByIndex = new double[contingencies.size()][];   // the clean value each frozen row holds (V=1, phi=0)
        GenOutage[] genOutageByIndex = new GenOutage[contingencies.size()];   // per generator contingency, else null
        int islandingCount = 0;
        for (int i = 0; i < qualifications.size(); i++) {
            GpuBatchContingencyValidator.Qualification q = qualifications.get(i);
            if (!q.qualified()) {
                continue;
            }
            BatchContingency c = contingencies.get(i);
            if (c.lostGenerator() != null) {                 // generator contingency: per-scenario target override
                GenOutage go = computeGenOutage(equationSystem, gpuData, dsSum, slackParticipates, c.lostGenerator());
                if (go == null) {                            // slack / participating-slack DS / remote gen — route to CPU
                    continue;
                }
                genOutageByIndex[i] = go;
                batchedIndices.add(i);
                frozenRowsByIndex[i] = NO_FROZEN_ROWS;
                frozenTargetsByIndex[i] = NO_FROZEN_TARGETS;
            } else if (!c.outagedShunts().isEmpty()) {       // fixed-shunt disconnection: mask the shunt element
                if (canBatchShuntOutage(c.outagedShunts(), elementMapper, gpuData)) {
                    batchedIndices.add(i);
                    frozenRowsByIndex[i] = NO_FROZEN_ROWS;
                    frozenTargetsByIndex[i] = NO_FROZEN_TARGETS;
                }                                            // else: not a packed fixed shunt / DISTR_Q contributor → CPU
            } else if (c.outagedBranches().stream().noneMatch(distrRhoControllers::contains)) {
                batchedIndices.add(i);
                FrozenIsland fi = islandedFrozen(equationSystem, q.islandedBuses());
                frozenRowsByIndex[i] = fi.rows();
                frozenTargetsByIndex[i] = fi.targets();
                if (frozenRowsByIndex[i].length > 0) {
                    islandingCount++;
                }
            }
        }
        int rejected = contingencies.size() - batchedIndices.size();
        if (rejected > 0 || islandingCount > 0) {
            LOGGER.info("Batched N-1: {} of {} contingencies routed to the CPU path (absent from the GPU "
                    + "model or islanding the slack); {} fragmenting outages solved on-GPU with frozen islands",
                    rejected, contingencies.size(), islandingCount);
        }

        // Solve the qualified scenarios on the GPU, in chunks bounded by the device-memory budget (the
        // uniform batched factorization holds an LU per scenario, so the footprint scales with the chunk).
        double[][] resultsByIndex = new double[contingencies.size()][];
        if (!batchedIndices.isEmpty()) {
            solveQualifiedInChunks(batchedIndices, contingencies, elementMapper, gpuData, baseState,
                    baseTargets, baseActivation.rowMode(), frozenRowsByIndex, frozenTargetsByIndex, genOutageByIndex,
                    n, nElements, parameters, tol, reactiveLimitTol, slackDistTol, convEpsPerEq, resultsByIndex);
        }

        // Assemble results in the original contingency order: batched scenarios carry their
        // GPU outcome; rejected ones carry the base state with STATUS_REJECTED.
        List<BatchScenarioResult> results = new ArrayList<>(contingencies.size());
        for (int i = 0; i < contingencies.size(); i++) {
            String id = contingencies.get(i).contingencyId();
            double[] result = resultsByIndex[i];
            if (result != null) {
                double[] state = new double[n];
                System.arraycopy(result, 0, state, 0, n);
                results.add(new BatchScenarioResult(id, state, (int) result[n], result[n + 1], (int) result[n + 2]));
            } else {
                results.add(new BatchScenarioResult(id, baseState.clone(), 0, Double.NaN, STATUS_REJECTED));
            }
        }
        return results;
    }

    private static final int[] NO_FROZEN_ROWS = new int[0];
    private static final double[] NO_FROZEN_TARGETS = new double[0];

    /** The islanded buses' variable rows to freeze + the clean flat value each holds (phi=0, V=1 pu). */
    private record FrozenIsland(int[] rows, double[] targets) { }

    /**
     * The variable rows (BUS_PHI + BUS_V) of every islanded bus — frozen to identity in a fragmenting
     * scenario's row mode — paired with the clean flat value each is HELD at: {@code phi=0}, {@code V=1} pu.
     * With the outaged branch (the only island↔main link) already masked, freezing these rows makes the
     * islanded sub-block a benign identity block that cannot make the batched factorization singular, while
     * the main component solves correctly. Holding the island at a flat 1∠0 (rather than the base equation
     * target, a P/Q value that produces a nonsensical islanded voltage/angle) gives the disconnected buses a
     * well-defined reported state — no garbage voltage, no spurious limit violations — without touching the main.
     */
    private static FrozenIsland islandedFrozen(EquationSystem<AcVariableType, AcEquationType> es, List<LfBus> islandedBuses) {
        if (islandedBuses.isEmpty()) {
            return new FrozenIsland(NO_FROZEN_ROWS, NO_FROZEN_TARGETS);
        }
        List<Integer> rows = new ArrayList<>(islandedBuses.size() * 2);
        List<Double> targets = new ArrayList<>(islandedBuses.size() * 2);
        for (LfBus bus : islandedBuses) {
            addFrozenVarRow(es, bus.getNum(), AcVariableType.BUS_PHI, 0.0, rows, targets);   // angle 0
            addFrozenVarRow(es, bus.getNum(), AcVariableType.BUS_V, 1.0, rows, targets);     // voltage 1 pu
        }
        int[] outRows = new int[rows.size()];
        double[] outTargets = new double[targets.size()];
        for (int i = 0; i < outRows.length; i++) {
            outRows[i] = rows.get(i);
            outTargets[i] = targets.get(i);
        }
        return new FrozenIsland(outRows, outTargets);
    }

    private static void addFrozenVarRow(EquationSystem<AcVariableType, AcEquationType> es, int busNum,
                                        AcVariableType type, double target, List<Integer> rows, List<Double> targets) {
        Variable<AcVariableType> v = es.getVariableSet().getVariable(busNum, type);
        if (v != null && v.getRow() >= 0) {
            rows.add(v.getRow());
            targets.add(target);
        }
    }

    /**
     * The per-scenario device changes a single generator loss applies (mirrors {@code LfContingency
     * .processLostGenerators}: remove the generator's P, and PV→PQ-flip the bus if it loses its last voltage
     * controller), or {@code null} when the generator is not batchable on the device and must fall through to
     * the CPU — the slack/reference bus, a remote / distributed (DISTR_Q) voltage controller, or a
     * distributed-slack participant bus when the slack ITSELF participates (the on-device retain math only
     * holds for a non-participating slack).
     *
     * <p>A generator at a DS-participant bus (non-participating slack) IS batched: {@code dsBus} carries the
     * bus's DS-pack index and the reweight deltas, applied by {@code distributeSlackBatch}.
     *
     * @param dsSum             Σ(maxP/droop) over ALL participating generators (incl. the slack's) — the
     *                          un-normalized factor denominator, to normalize the lost gen's factor delta
     * @param slackParticipates whether the slack bus itself has participating generators (then DS gens reject)
     */
    private static GenOutage computeGenOutage(EquationSystem<AcVariableType, AcEquationType> es, GpuAcData gpuData,
                                              double dsSum, boolean slackParticipates, LfGenerator gen) {
        LfBus bus = gen.getBus();
        if (bus == null) {
            return null;
        }
        int busNum = bus.getNum();
        if (es.getEquation(busNum, AcEquationType.BUS_TARGET_PHI).isPresent()        // slack/reference: P is free
                || es.getEquation(busNum, AcEquationType.DISTR_Q).isPresent()) {     // remote/distributed VC controller
            return null;
        }
        int pRow = es.getVariableSet().getVariable(busNum, AcVariableType.BUS_PHI).getRow();
        int vRow = es.getVariableSet().getVariable(busNum, AcVariableType.BUS_V).getRow();
        if (pRow < 0 || vRow < 0) {
            return null;
        }
        // Is this bus a distributed-slack participant? (its BUS_PHI row appears in the DS pack)
        int dsBus = -1;
        int[] dsPhiRow = gpuData.dsPhiRow();
        for (int i = 0; i < dsPhiRow.length; i++) {
            if (dsPhiRow[i] == pRow) {
                dsBus = i;
                break;
            }
        }
        if (dsBus >= 0 && slackParticipates) {
            return null;                            // DS reweight + participating slack: retain math doesn't hold
        }
        if (bus.getGeneratorVoltageControl().map(vc -> vc.getControlledBus() != null
                && vc.getControlledBus().getNum() != busNum).orElse(false)) {
            return null;                                                            // remote voltage control
        }
        double pTarget = bus.getTargetP() - gen.getTargetP();
        boolean genControls = gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE;
        boolean otherController = bus.getGenerators().stream()
                .anyMatch(g -> g != gen && g.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE);
        boolean toPq = genControls && !otherController;
        double qTarget = 0;
        if (toPq) {
            double remainingGenQ = bus.getGenerators().stream().filter(g -> g != gen)
                    .mapToDouble(LfGenerator::getTargetQ).sum();
            qTarget = remainingGenQ - bus.getLoadTargetQ();
        }
        // Distributed-slack reweight for the affected bus. The bus's base target P always drops by the lost
        // gen's P. The participation factor / headroom / footroom drop only if the LOST gen itself participated
        // (mirrors the extractor, which sums these over participating gens with a non-zero droop).
        double dsFactorDelta = 0;
        double dsBaseDelta = 0;
        double dsMaxDelta = 0;
        double dsMinDelta = 0;
        if (dsBus >= 0) {
            dsBaseDelta = gen.getTargetP();
            if (gen.isParticipating() && gen.getDroop() != 0) {
                dsFactorDelta = (gen.getMaxP() / gen.getDroop()) / dsSum;
                double minTp = gen.getMinTargetP();
                double maxTp = gen.getMaxTargetP();
                double tp = gen.getTargetP();
                if (tp < 0) {
                    maxTp = Math.min(maxTp, 0);
                } else {
                    minTp = Math.max(minTp, 0);
                }
                dsMaxDelta = maxTp - tp;
                dsMinDelta = minTp - tp;
            }
        }
        return new GenOutage(pRow, pTarget, vRow, qTarget, toPq, dsBus, dsFactorDelta, dsBaseDelta, dsMaxDelta, dsMinDelta);
    }

    /**
     * Whether a fixed-shunt disconnection can be batched as a plain element mask. Requires every outaged shunt
     * to be a packed fixed shunt ({@code getElementIndex >= 0}) and NOT a reactive-distribution (DISTR_Q)
     * contributor — a shunt whose reactive feeds a controlled bus's DISTR_Q sum is read unconditionally by the
     * batched DISTR_Q fill (it ignores the disabled mask), so masking it would leave the sum stale. Such a shunt
     * outage is routed to the CPU instead.
     */
    private static boolean canBatchShuntOutage(List<LfShunt> shunts, GpuBatchElementMapper elementMapper,
                                               GpuAcData gpuData) {
        int shOffset = elementMapper.getClosedBranchCount() + elementMapper.getOpenBranchCount();
        int[] dqElem = gpuData.dqElem();
        int[] dqKind = gpuData.dqKind();
        for (LfShunt shunt : shunts) {
            int elementIndex = elementMapper.getElementIndex(shunt);
            if (elementIndex < 0) {
                return false;                                // not a packed fixed shunt (controller/SVC shunt)
            }
            int packIndex = elementIndex - shOffset;
            for (int i = 0; i < dqKind.length; i++) {
                if (dqKind[i] == 1 && dqElem[i] == packIndex) {
                    return false;                            // shunt feeds a DISTR_Q sum — mask wouldn't update it
                }
            }
        }
        return true;
    }

    /**
     * Solve the qualified scenarios on the GPU in chunks sized by {@link #resolveChunkSize}
     * (auto from device memory, or a manual cap). ONE native context, sized to the chunk, is created and reused across all
     * chunks: cuDSS's symbolic ANALYSIS (on the shared pattern, identical for every chunk) is
     * done once on the first chunk and reused, so chunking costs nothing in amortization — it
     * only bounds the resident per-scenario LU factors. The last (partial) chunk is padded
     * with base-case scenarios (no element disabled): they converge at iteration 0 and their
     * results are discarded.
     */
    private static void solveQualifiedInChunks(List<Integer> batchedIndices, List<BatchContingency> contingencies,
                                               GpuBatchElementMapper elementMapper, GpuAcData gpuData,
                                               double[] baseState, double[] baseTargets, int[] baseRowMode,
                                               int[][] frozenRowsByIndex, double[][] frozenTargetsByIndex,
                                               GenOutage[] genOutageByIndex,
                                               int n, int nElements, NewtonRaphsonParameters parameters,
                                               double tol, double reactiveLimitTol, double slackDistTol,
                                               double convEpsPerEq, double[][] resultsByIndex) {
        int nb = batchedIndices.size();
        int chunkSize = resolveChunkSize(nb, n, gpuData);
        int nChunks = (nb + chunkSize - 1) / chunkSize;

        // Reuse a cached analyzed context when the pattern/admittances/chunk/mode match (across
        // SA runs on the same network) — the context is NOT destroyed here, it stays cached.
        long handle = acquireBatchContext(gpuData, chunkSize, resolveBatchMode(), n);

        // The base state / targets are IDENTICAL for every scenario (a single-branch N-1 starts from the
        // same converged base operating point): passed as single length-n rows and tiled on-device. The
        // disabled mask is per-scenario (each chunk clears the one flag each slot set, sets the new one).
        // The ROW MODE is per-scenario too: a non-fragmenting outage shares the base row (same PV/slack
        // layout); a fragmenting one gets a cloned base row with its islanded buses frozen to identity.
        boolean[][] disabledMask = new boolean[chunkSize][nElements];
        int[][] disabledIdx = new int[chunkSize][];      // element indices each slot currently disables (1-2), null = none
        int[][] rowModePerScenario = new int[chunkSize][];   // per-scenario row mode (re-assigned every chunk)
        // Per-scenario target overrides (generator contingencies): flat (dTargetIndex = slot*n + row, value)
        // lists rebuilt per chunk. Empty when no generator outage is in the chunk.
        List<Integer> ovIndexList = new ArrayList<>();
        List<Double> ovValueList = new ArrayList<>();
        // Per-scenario distributed-slack reweight (a generator loss at a DS-participant bus): one affected DS
        // bus per slot (dsOvBus[s] = its DS-pack index, -1 = none) whose factor/base/headroom/footroom and the
        // factor sum shrink by the lost gen's share. Only allocated when the network has distributed slack.
        boolean hasDs = gpuData.dsPhiRow().length > 0;
        int[] dsOvBus = hasDs ? new int[chunkSize] : new int[0];
        double[] dsOvFactorDelta = hasDs ? new double[chunkSize] : new double[0];
        double[] dsOvBaseDelta = hasDs ? new double[chunkSize] : new double[0];
        double[] dsOvMaxDelta = hasDs ? new double[chunkSize] : new double[0];
        double[] dsOvMinDelta = hasDs ? new double[chunkSize] : new double[0];
        try {
            long startTime = System.currentTimeMillis();
            for (int chunkStart = 0; chunkStart < nb; chunkStart += chunkSize) {
                int realCount = Math.min(chunkSize, nb - chunkStart);
                ovIndexList.clear();
                ovValueList.clear();
                if (hasDs) {
                    java.util.Arrays.fill(dsOvBus, -1);
                    java.util.Arrays.fill(dsOvFactorDelta, 0);
                    java.util.Arrays.fill(dsOvBaseDelta, 0);
                    java.util.Arrays.fill(dsOvMaxDelta, 0);
                    java.util.Arrays.fill(dsOvMinDelta, 0);
                }
                for (int s = 0; s < chunkSize; s++) {
                    if (disabledIdx[s] != null) {        // clear this slot's outage(s) from the previous chunk
                        for (int ei : disabledIdx[s]) {
                            disabledMask[s][ei] = false;
                        }
                        disabledIdx[s] = null;
                    }
                    rowModePerScenario[s] = baseRowMode;     // default: base layout (padding + non-fragmenting)
                    if (s < realCount) {                 // real scenario: disable its outaged branch(es)
                        int ci = batchedIndices.get(chunkStart + s);
                        List<LfBranch> branches = contingencies.get(ci).outagedBranches();
                        List<LfShunt> shunts = contingencies.get(ci).outagedShunts();
                        int[] eis = new int[branches.size() + shunts.size()];
                        int ei = 0;
                        for (LfBranch branch : branches) {
                            int idx = elementMapper.getElementIndex(branch);          // qualification guarantees >= 0
                            disabledMask[s][idx] = true;
                            eis[ei++] = idx;
                        }
                        for (LfShunt shunt : shunts) {                                // fixed-shunt disconnection
                            int idx = elementMapper.getElementIndex(shunt);          // qualification guarantees >= 0
                            disabledMask[s][idx] = true;
                            eis[ei++] = idx;
                        }
                        disabledIdx[s] = eis;
                        int[] frozen = frozenRowsByIndex[ci];
                        if (frozen.length > 0) {         // fragmenting outage: freeze the islanded buses' rows
                            int[] row = baseRowMode.clone();
                            double[] frozenTargets = frozenTargetsByIndex[ci];
                            for (int k = 0; k < frozen.length; k++) {
                                row[frozen[k]] = 0;      // identity row — bus decoupled from the main component
                                // ...held at a CLEAN flat state (V=1, phi=0) via the target override, NOT the base
                                // equation target (a P/Q value that gives a nonsensical islanded voltage/angle).
                                ovIndexList.add(s * n + frozen[k]);
                                ovValueList.add(frozenTargets[k]);
                            }
                            rowModePerScenario[s] = row;
                        }
                        GenOutage go = genOutageByIndex[ci];
                        if (go != null) {                // generator loss: per-scenario P (and PV→PQ Q) target override
                            ovIndexList.add(s * n + go.pRow());
                            ovValueList.add(go.pTarget());
                            if (go.toPq()) {
                                ovIndexList.add(s * n + go.vRow());
                                ovValueList.add(go.qTarget());
                                int[] row = rowModePerScenario[s] == baseRowMode ? baseRowMode.clone() : rowModePerScenario[s];
                                row[go.vRow()] = 1;      // BUS_V row becomes the reactive-power equation (PV→PQ)
                                rowModePerScenario[s] = row;
                            }
                            if (hasDs && go.dsBus() >= 0) {   // lost gen on a DS-participant bus: per-scenario reweight
                                dsOvBus[s] = go.dsBus();
                                dsOvFactorDelta[s] = go.dsFactorDelta();
                                dsOvBaseDelta[s] = go.dsBaseDelta();
                                dsOvMaxDelta[s] = go.dsMaxDelta();
                                dsOvMinDelta[s] = go.dsMinDelta();
                            }
                        }
                    }
                    // else: padding scenario = base case (no element disabled), discarded below
                }
                int[] targetOverrideIndex = ovIndexList.stream().mapToInt(Integer::intValue).toArray();
                double[] targetOverrideValue = ovValueList.stream().mapToDouble(Double::doubleValue).toArray();
                double[][] chunkResults = GpuAcNewtonSolver.solveBatchContext(handle, baseState, baseTargets,
                        rowModePerScenario, baseRowMode, disabledMask, targetOverrideIndex, targetOverrideValue,
                        dsOvBus, dsOvFactorDelta, dsOvBaseDelta, dsOvMaxDelta, dsOvMinDelta,
                        parameters.getMaxIterations(), tol, reactiveLimitTol, slackDistTol, convEpsPerEq);
                for (int s = 0; s < realCount; s++) {
                    resultsByIndex[batchedIndices.get(chunkStart + s)] = chunkResults[s];
                }
            }
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.info("Batched N-1: {} GPU scenarios in {} chunk(s) of <= {} solved in {} ms ({} ms/scenario)",
                    nb, nChunks, chunkSize, elapsed, (double) elapsed / nb);
        } catch (RuntimeException e) {
            clearContextCache();             // a failed solve may leave the context inconsistent — rebuild next time
            throw e;
        }
    }

    /** Absolute upper bound on an auto-sized chunk, so a huge free-memory reading never yields a pathological chunk. */
    static final int AUTO_MAX_CHUNK = 4096;

    /**
     * Resolve the GPU batch chunk size, bounding the resident device memory (the uniform
     * batched factorization holds an LU per scenario, so the footprint scales with the chunk).
     * <ul>
     *   <li>{@value #MAX_BATCH_PROPERTY} set to a positive value — an explicit manual cap.</li>
     *   <li>otherwise — AUTO-SIZED from free device memory via
     *       {@link GpuAcNewtonSolver#recommendBatchSize} (real Jacobian nnz + heuristic LU
     *       estimate + safety margin).</li>
     *   <li>if auto-sizing is unavailable (no GPU / query error) — {@value #DEFAULT_MAX_BATCH}.</li>
     * </ul>
     * The result is always clamped to {@code [1, nb]}.
     */
    static int resolveChunkSize(int nb, int n, GpuAcData gpuData) {
        Integer explicit = Integer.getInteger(MAX_BATCH_PROPERTY);
        if (explicit != null && explicit > 0) {
            return Math.min(nb, explicit);
        }
        try {
            int rec = GpuAcNewtonSolver.recommendBatchSize(n, gpuData.cbIdx(), gpuData.obIdx(),
                    gpuData.shIdx(), gpuData.hvIdx(),
                    gpuData.dqElem(), gpuData.dqSide(), gpuData.dqKind(), gpuData.dqRow(),
                    gpuData.drRow(), gpuData.drCol(), gpuData.ziIdx(), Math.min(nb, AUTO_MAX_CHUNK));
            return Math.min(nb, Math.max(1, rec));
        } catch (Throwable t) {
            LOGGER.debug("GPU batch auto-size unavailable ({}); using default chunk {}", t.toString(), DEFAULT_MAX_BATCH);
            return Math.min(nb, DEFAULT_MAX_BATCH);
        }
    }

    private GpuBatchedSecurityAnalysisSolver() {
    }
}
