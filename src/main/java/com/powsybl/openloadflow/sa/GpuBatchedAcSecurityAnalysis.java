/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.google.common.base.Stopwatch;
import com.powsybl.action.Action;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcDataExtractor;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcNewtonSolver;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.DistributedSlackOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.ReactiveLimitsOuterLoop;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchContingency;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchScenarioResult;
import com.powsybl.openloadflow.ac.solver.GpuNewtonRaphsonFactory;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonParameters;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.DisabledBranchStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.limitreduction.LimitReduction;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.ConnectivityResult;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.PostContingencyResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prototype integration of the batched GPU N-1 ({@link GpuBatchedSecurityAnalysisSolver})
 * into OLF's AC security-analysis engine. Single-branch contingencies whose post-contingency
 * states the GPU batch can solve (qualified by {@link com.powsybl.openloadflow.ac.solver.GpuBatchContingencyValidator})
 * are solved ALL AT ONCE, once, right after the pre-contingency load flow; the engine's
 * per-contingency loop then INJECTS each precomputed state instead of running its own
 * Newton-Raphson, and reuses the existing violation-detection and result-building machinery
 * unchanged.
 *
 * <p>This subclass intercepts at three seams: {@link #runSimulations} (capture the network +
 * contingency list), {@link #afterPreContingencySimulation} (precompute the batch from the
 * converged base state), and {@link #runPostContingencySimulation} (inject a precomputed
 * state, or delegate to the standard CPU path). Everything it cannot batch — multi-element
 * contingencies, outages that island buses, networks the GPU extractor rejects (zero
 * impedance / remote control), or scenarios that did not converge on the device — falls
 * through to {@code super}, so results are never wrong, only un-accelerated.
 *
 * <p>SCOPE (prototype): plain post-contingency power flow (no device-side outer loops —
 * distributed slack / reactive limits are not re-run on the GPU, so distributed active
 * power is reported from the pre-distribution only). Operator strategies still run on the
 * CPU from the injected post-contingency state. Single-threaded (the device context is not
 * shared across threads). When the GPU is unavailable the class behaves exactly like
 * {@link AcSecurityAnalysis}.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class GpuBatchedAcSecurityAnalysis extends AcSecurityAnalysis {

    /** How many contingencies the LAST {@link #precomputeBatch()} solved on the GPU device: {@code -1} when
     *  the batch was not attempted (no device, nothing to batch), {@code 0+} otherwise. Test-visibility hook
     *  to assert the GPU actually engaged on a device-equipped machine (vs a silent CPU fallback). */
    static final java.util.concurrent.atomic.AtomicInteger LAST_GPU_PRECOMPUTED_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(-1);

    private List<PropagatedContingency> currentContingencies = List.of();
    private LfNetwork currentLfNetwork;
    private AcLoadFlowContext currentContext;
    private EquationSystem<AcVariableType, AcEquationType> batchEquationSystem;
    private final Map<String, double[]> precomputedStates = new HashMap<>();
    // The run's actual outer loops, captured in runSimulations: the device must run the distributed-slack /
    // reactive-limits passes ONLY when OLF would (i.e. when the outer loop is in the configured list), never
    // unconditionally from network capability. Default to OLF's defaults until runSimulations sets them.
    private boolean batchDistributedSlack = true;
    private boolean batchReactiveLimits = true;
    // The run's outer-loop convergence tolerances (per-unit), captured from the loadflow parameters so the
    // device aligns to the CPU loadflow (GPU follows CPU, not the reverse): slackBusPMaxMismatch /
    // maxReactivePowerMismatch divided by the 100 MVA base. Default to OLF's defaults until runSimulations sets.
    private double batchSlackDistTol = GpuBatchedSecurityAnalysisSolver.DEFAULT_SLACK_DIST_TOL;
    private double batchReactiveLimitTol = GpuBatchedSecurityAnalysisSolver.DEFAULT_REACTIVE_LIMIT_TOL;
    // The device inner-Newton stopping criterion, matched to OLF's: for UNIFORM_CRITERIA (default) the device
    // stops on OLF's exact ‖F‖₂ < sqrt(convEpsPerEq²·n) so the reactive-limit switch DECISIONS (whose tolerance
    // is convEpsPerEq for UNIFORM) are made on a state converged to the SAME depth as the CPU — else the device
    // switched on a 1e-8-converged state while the RL threshold was convEpsPerEq (1e-12), flipping borderline
    // buses vs the CPU. 0 = the device infinity-norm floor (kept for PER_EQUATION_TYPE, whose per-eq criteria
    // are not ported to the device).
    private double batchConvEpsPerEq;

    protected GpuBatchedAcSecurityAnalysis(Network network, MatrixFactory matrixFactory,
                                           GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                           List<StateMonitor> stateMonitors, ReportNode reportNode) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reportNode);
    }

    @Override
    protected SecurityAnalysisResult runSimulations(LfNetwork lfNetwork, List<PropagatedContingency> propagatedContingencies,
                                                    AcLoadFlowParameters acParameters, SecurityAnalysisParameters securityAnalysisParameters,
                                                    List<OperatorStrategy> operatorStrategies, List<Action> actions,
                                                    List<LimitReduction> limitReductions,
                                                    ContingencyActivePowerLossDistribution contingencyActivePowerLossDistribution) {
        // Capture this component's network + contingencies; the batch is precomputed once the
        // pre-contingency load flow has converged (afterPreContingencySimulation hook).
        this.currentLfNetwork = lfNetwork;
        this.currentContingencies = propagatedContingencies;
        this.precomputedStates.clear();
        this.batchEquationSystem = null;
        // Capture which outer loops this run actually applies, so the batched device solver runs the
        // distributed-slack / reactive-limits passes only when OLF does (presence in the resolved outer-loop
        // list is the definitive signal — gens may report participation / buses may be PV regardless).
        this.batchDistributedSlack = acParameters.getOuterLoops().stream()
                .anyMatch(ol -> DistributedSlackOuterLoop.NAME.equals(ol.getName()));
        this.batchReactiveLimits = acParameters.getOuterLoops().stream()
                .anyMatch(ol -> ReactiveLimitsOuterLoop.NAME.equals(ol.getName()));
        // Align the device outer-loop tolerances to the CPU loadflow's (per-unit on the 100 MVA base). The
        // reactive-limits PV→PQ switch threshold is NOT always maxReactivePowerMismatch: OLF derives it from the
        // stopping-criteria type exactly as AbstractAcOuterLoopConfig.createReactiveLimitsOuterLoop does —
        // UNIFORM_CRITERIA (the default) ties it to convEpsPerEq, PER_EQUATION_TYPE_CRITERIA to
        // maxReactivePowerMismatch/SB. Deriving it the same way keeps the device switch DECISIONS identical to
        // OLF when a user changes convEpsPerEq (with defaults both are 1e-4, which masked the divergence).
        OpenLoadFlowParameters olfParams = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        this.batchSlackDistTol = olfParams.getSlackBusPMaxMismatch() / PerUnit.SB;
        this.batchReactiveLimitTol = switch (olfParams.getNewtonRaphsonStoppingCriteriaType()) {
            case UNIFORM_CRITERIA -> olfParams.getNewtonRaphsonConvEpsPerEq();
            case PER_EQUATION_TYPE_CRITERIA -> olfParams.getMaxReactivePowerMismatch() / PerUnit.SB;
        };
        // Device inner Newton uses OLF's exact criterion for UNIFORM_CRITERIA (so switch decisions are made at
        // the same convergence depth as the CPU); the device infinity-norm floor otherwise.
        this.batchConvEpsPerEq = switch (olfParams.getNewtonRaphsonStoppingCriteriaType()) {
            case UNIFORM_CRITERIA -> olfParams.getNewtonRaphsonConvEpsPerEq();
            case PER_EQUATION_TYPE_CRITERIA -> 0.0;
        };
        // Run the engine's load flows — the pre-contingency BASE and any contingency that falls through to
        // the sequential path — on the FULL GPU Newton-Raphson when the device + network support it. The base
        // then solves on-device and PRE-WARMS CUDA for the batched contingency pass (the one-time device init
        // is absorbed into the base phase, so the cold batch becomes warm — the base-case warm-up
        // strategy). Guarded by a GPU-support probe so an unsupported network degrades gracefully to the CPU
        // solver. Toggle with -Dolf.gpu.sa.gpuBaseLoadFlow=false.
        if (gpuBaseLoadFlowEnabled() && GpuAcNewtonSolver.isAvailable() && gpuSupports(lfNetwork, acParameters)) {
            acParameters.setSolverFactory(new GpuNewtonRaphsonFactory(), securityAnalysisParameters.getLoadFlowParameters());
            LOGGER.info("GPU batched SA: base + fall-through load flows run on the GPU Newton-Raphson (CUDA pre-warm)");
        }
        return super.runSimulations(lfNetwork, propagatedContingencies, acParameters, securityAnalysisParameters,
                operatorStrategies, actions, limitReductions, contingencyActivePowerLossDistribution);
    }

    /** System property toggle for the GPU base/fall-through load flow (default on). */
    public static final String GPU_BASE_LOADFLOW_PROPERTY = "olf.gpu.sa.gpuBaseLoadFlow";

    private static boolean gpuBaseLoadFlowEnabled() {
        return Boolean.parseBoolean(System.getProperty(GPU_BASE_LOADFLOW_PROPERTY, "true"));
    }

    /** True if the GPU full Newton-Raphson can solve this network — a throwaway extract (which VALIDATES and
     *  throws on anything uncovered) is the same gate the batch uses, so the base never runs on the GPU for a
     *  network whose contingencies would fall back to the CPU anyway. */
    private static boolean gpuSupports(LfNetwork lfNetwork, AcLoadFlowParameters acParameters) {
        try {
            EquationSystem<AcVariableType, AcEquationType> es =
                    new AcEquationSystemCreator(lfNetwork, acParameters.getEquationSystemCreationParameters()).create();
            GpuAcDataExtractor.extract(lfNetwork, es);
            return true;
        } catch (RuntimeException e) {
            LOGGER.debug("GPU batched SA: base load flow stays on CPU (network not GPU-supported: {})", e.toString());
            return false;
        }
    }

    @Override
    protected AcLoadFlowContext createLoadFlowContext(LfNetwork lfNetwork, AcLoadFlowParameters parameters) {
        // Capture the engine's load-flow context so precomputeBatch can REUSE its equation system instead of
        // creating a second one. Creating a second AcEquationSystem re-points every bus/branch evaluable
        // (bus.setP/setQ, branch.setP1/P2, …) off the engine's system, so the CPU solver of a fall-through
        // contingency would then evaluate the batch system's stale-indexed equations (an index -1 crash, or a
        // mis-converged generator/N-2 outage). The context is created per component, just before the base
        // load flow and afterPreContingencySimulation, and lives through the whole contingency loop.
        currentContext = super.createLoadFlowContext(lfNetwork, parameters);
        return currentContext;
    }

    @Override
    protected void afterPreContingencySimulation(AcLoadFlowParameters parameters) {
        super.afterPreContingencySimulation(parameters);
        precomputeBatch();
    }

    /**
     * Solve the qualified single-branch contingencies in one GPU batch, from the converged
     * base state. Failures (no GPU, an out-of-scope network the extractor rejects, a device
     * error) leave the cache empty so every contingency falls back to the CPU path.
     */
    private void precomputeBatch() {
        precomputedStates.clear();
        batchEquationSystem = null;
        LAST_GPU_PRECOMPUTED_COUNT.set(-1);                          // not attempted unless we reach the solve
        if (!GpuAcNewtonSolver.isAvailable() || currentLfNetwork == null || currentContext == null
                || currentContingencies.isEmpty()) {
            return;
        }
        try {
            LfNetwork net = currentLfNetwork;
            // REUSE the engine's equation system + target vector (do NOT create a second system — that would
            // re-point the network's evaluables off the engine's system, breaking fall-through CPU solves).
            // After the converged base load flow the engine's state vector holds the base operating point.
            EquationSystem<AcVariableType, AcEquationType> es = currentContext.getEquationSystem();
            double[] baseState = es.getStateVector().get().clone();
            var target = currentContext.getTargetVector();

            List<BatchContingency> batch = new ArrayList<>();
            for (PropagatedContingency pc : currentContingencies) {
                List<LfBranch> branches = closedBranchOutages(net, pc);
                if (branches != null) {
                    batch.add(new BatchContingency(pc.getContingency().getId(), branches));
                    continue;
                }
                LfGenerator gen = singleGeneratorOutage(net, pc);
                if (gen != null) {
                    batch.add(BatchContingency.generator(pc.getContingency().getId(), gen));
                    continue;
                }
                LfShunt shunt = singleShuntOutage(net, pc);
                if (shunt != null) {
                    batch.add(BatchContingency.shunt(pc.getContingency().getId(), shunt));
                }
            }
            if (batch.isEmpty()) {
                return;
            }

            List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(
                    net, baseState, batch, es, target, new NewtonRaphsonParameters(),
                    batchDistributedSlack, batchReactiveLimits,
                    batchReactiveLimitTol, batchSlackDistTol, batchConvEpsPerEq, reportNode);
            for (BatchScenarioResult r : results) {
                if (r.hasConverged()) {
                    precomputedStates.put(r.contingencyId(), r.state());
                }
            }
            batchEquationSystem = es;
            LAST_GPU_PRECOMPUTED_COUNT.set(precomputedStates.size());
            LOGGER.info("GPU batched SA: precomputed {} of {} branch (N-1/N-2) contingencies on the device",
                    precomputedStates.size(), batch.size());
        } catch (Exception e) {
            LOGGER.warn("GPU batched SA precompute failed ({}); falling back to per-contingency CPU solves", e.toString());
            precomputedStates.clear();
            batchEquationSystem = null;
        }
    }

    /**
     * The 1–2 closed branches a contingency outages (N-1 or N-2), or null if it is not a clean
     * branch-only (both sides) outage the GPU mask can represent (≥3 branches, partial open, or a
     * bus/generator loss). Each branch is disabled in the per-scenario element mask.
     */
    /** The single generator a contingency loses (and that the GPU batch can represent via a per-scenario
     *  target override), or null if it is not a clean single-generator outage (multi-element, bus loss, or a
     *  generator absent from the LF network). */
    private static LfGenerator singleGeneratorOutage(LfNetwork net, PropagatedContingency pc) {
        if (pc.getGeneratorIdsToLose().size() != 1 || !pc.getBranchIdsToOpen().isEmpty()
                || !pc.getBusIdsToLose().isEmpty()) {
            return null;
        }
        LfGenerator gen = net.getGeneratorById(pc.getGeneratorIdsToLose().iterator().next());
        return gen != null && gen.getBus() != null && !gen.getBus().isDisabled() ? gen : null;
    }

    /** The single fixed shunt a contingency disconnects (which the GPU batch can mask off like a branch), or
     *  null if it is not a clean full disconnection of a packed fixed shunt — a multi-element contingency, a
     *  voltage-controlling/SVC shunt the GPU does not pack, or a multi-compensator aggregate the mask cannot
     *  partially remove (those fall through to the CPU). */
    private static LfShunt singleShuntOutage(LfNetwork net, PropagatedContingency pc) {
        if (pc.getShuntIdsToShift().size() != 1 || !pc.getBranchIdsToOpen().isEmpty()
                || !pc.getGeneratorIdsToLose().isEmpty() || !pc.getBusIdsToLose().isEmpty()
                || !pc.getLoadIdsToLose().isEmpty()) {
            return null;
        }
        LfShunt shunt = net.getShuntById(pc.getShuntIdsToShift().keySet().iterator().next());
        // A sole-compensator LfShunt: masking its element removes exactly the contingency's full admittance.
        return shunt != null && !shunt.isDisabled() && shunt.getOriginalIds().size() == 1 ? shunt : null;
    }

    private static List<LfBranch> closedBranchOutages(LfNetwork net, PropagatedContingency pc) {
        int n = pc.getBranchIdsToOpen().size();
        if (n < 1 || n > 2 || !pc.getBusIdsToLose().isEmpty() || !pc.getGeneratorIdsToLose().isEmpty()) {
            return null;
        }
        List<LfBranch> branches = new ArrayList<>(n);
        for (Map.Entry<String, DisabledBranchStatus> e : pc.getBranchIdsToOpen().entrySet()) {
            if (e.getValue() != DisabledBranchStatus.BOTH_SIDES) {
                return null;
            }
            LfBranch branch = net.getBranchById(e.getKey());
            if (branch == null || branch.getBus1() == null || branch.getBus2() == null) {
                return null;
            }
            branches.add(branch);
        }
        return branches;
    }

    @Override
    protected PostContingencyResult runPostContingencySimulation(LfNetwork network, AcLoadFlowContext context, Contingency contingency,
                                                                 LfContingency lfContingency, LimitViolationManager preContingencyLimitViolationManager,
                                                                 SecurityAnalysisParameters securityAnalysisParameters,
                                                                 PreContingencyNetworkResult preContingencyNetworkResult, boolean createResultExtension,
                                                                 List<LimitReduction> limitReductions, double preDistributedActivePower) {
        double[] state = precomputedStates.get(contingency.getId());
        if (state == null || batchEquationSystem == null) {
            // not batched (multi-element / islanding / out of scope / diverged) — standard CPU path
            return super.runPostContingencySimulation(network, context, contingency, lfContingency, preContingencyLimitViolationManager,
                    securityAnalysisParameters, preContingencyNetworkResult, createResultExtension, limitReductions, preDistributedActivePower);
        }

        // Inject the precomputed post-contingency state (the branch is already disabled by
        // lfContingency.apply()), then reuse the engine's result-building unchanged.
        logPostContingencyStart(network, lfContingency);
        Stopwatch stopwatch = Stopwatch.createStarted();

        batchEquationSystem.getStateVector().set(state.clone());
        AcSolverUtil.updateNetwork(network, batchEquationSystem);

        PostContingencyComputationStatus status = PostContingencyComputationStatus.CONVERGED;
        var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, limitReductions,
                securityAnalysisParameters.getIncreasedViolationsParameters());
        LoadFlowModel loadFlowModel = securityAnalysisParameters.getLoadFlowParameters().isDc() ? LoadFlowModel.DC : LoadFlowModel.AC;
        var postContingencyNetworkResult = new PostContingencyNetworkResult(network,
                new AbstractNetworkResult.StateMonitorIndexes(monitorIndex, zeroImpedanceMonitoredIndex), createResultExtension,
                preContingencyNetworkResult, contingency, loadFlowModel, securityAnalysisParameters.getLoadFlowParameters().getDcPowerFactor());
        postContingencyNetworkResult.update();
        postContingencyLimitViolationManager.detectViolations(network);

        stopwatch.stop();
        logPostContingencyEnd(network, lfContingency, stopwatch);

        var connectivityResult = new ConnectivityResult(lfContingency.getCreatedSynchronousComponentsCount(), 0,
                lfContingency.getDisconnectedLoadActivePower() * PerUnit.SB,
                lfContingency.getDisconnectedGenerationActivePower() * PerUnit.SB,
                lfContingency.getDisconnectedElementIds());

        // No device-side slack distribution in the prototype: only the pre-distribution counts.
        return new PostContingencyResult(contingency, status,
                new LimitViolationsResult(postContingencyLimitViolationManager.getLimitViolations()),
                new NetworkResult(postContingencyNetworkResult.getBranchResults(), postContingencyNetworkResult.getBusResults(),
                        postContingencyNetworkResult.getThreeWindingsTransformerResults()),
                connectivityResult, preDistributedActivePower * PerUnit.SB);
    }
}
