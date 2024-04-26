package com.powsybl.openloadflow.ac.outerloop;

import com.google.common.collect.Lists;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.AcOuterLoopGroupContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.lf.outerloop.DistributedSlackContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractCompensationAndSolveOuterLoopGroup implements ACOuterLoopGroup {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCompensationAndSolveOuterLoopGroup.class);

    private final AcOuterLoop compensationLoop;
    private final List<AcOuterLoop> modelCheckers;

    protected AbstractCompensationAndSolveOuterLoopGroup(AcOuterLoop compensationLoop, List<AcOuterLoop> modelCheckers) {
        this.compensationLoop = compensationLoop;
        this.modelCheckers = modelCheckers;
    }

    @Override
    public OuterLoopStatus runOuterLoops(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode) {
        LOGGER.info("Starting Load Flow: " + getName());

        AcOuterLoopContext compensationContext = new AcOuterLoopContext(groupContext.getLoadFlowContext().getNetwork());

        List<Pair<AcOuterLoop, AcOuterLoopContext>> compensationAndContext = List.of(Pair.of(compensationLoop, compensationContext));

        compensationLoop.initialize(compensationContext);

        List<Pair<AcOuterLoop, AcOuterLoopContext>> checkersAndContexts = modelCheckers.stream()
                .map(checker -> Pair.of(checker,
                        checker instanceof ACOuterLoopGroup ?
                                new AcOuterLoopGroupContext(groupContext)
                                :
                                new AcOuterLoopContext(groupContext.getLoadFlowContext().getNetwork())))
                .toList();

        // initial checkers and solvers - run if needed by the group
        boolean modelChanged = prepareSolverAndModel(groupContext, nrReportNode, checkersAndContexts);
        if (!modelChanged) { // If init is a NOOP return groups status
            return OuterLoopStatus.FULL_STABLE;
        }

        // continue with outer loops only if solver succeed
        // The solver may not have been run if first loop prefers to run the solver itself
        if (groupContext.getRunningContext().getLastSolverResult() == null || groupContext.getRunningContext().getLastSolverResult().getStatus() == AcSolverStatus.CONVERGED) {
            // re-run all outer loops until solver failed or no more solver iterations are needed
            int oldNrTotalIterations;
            do {
                oldNrTotalIterations = groupContext.getRunningContext().getNrTotalIterationCount();
                if (groupContext.unHealthy()) {
                    break;
                }

                // Run the compensation
                runOuterLoop(groupContext, compensationAndContext, nrReportNode, true);

                runOuterLoop(groupContext, checkersAndContexts, nrReportNode, false);

                if (groupContext.unHealthy()) {
                    break;
                }

            } while (groupContext.getRunningContext().getNrTotalIterationCount() > oldNrTotalIterations
                    && groupContext.getRunningContext().getLastSolverResult().getStatus() == AcSolverStatus.CONVERGED
                    && groupContext.getRunningContext().getLastOuterLoopStatus() != OuterLoopStatus.FAILED
                    && groupContext.getRunningContext().getOuterLoopTotalIterationCount() < groupContext.getLoadFlowContext().getParameters().getMaxOuterLoopIterations());
        }

        // Update distributed active power
        for (var outerLoopAndContext : Lists.reverse(checkersAndContexts)) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            if (outerLoop instanceof DistributedSlackOuterLoop) {
                groupContext.getRunningContext().addDistributedActivePower(((DistributedSlackContextData) outerLoopContext.getData()).getDistributedActivePower());
            }
        }

        compensationLoop.cleanup(compensationContext);
        cleanModel(groupContext, nrReportNode, checkersAndContexts);

        if (groupContext.getRunningContext().getLastOuterLoopStatus() == OuterLoopStatus.FAILED) {
            return OuterLoopStatus.FAILED;
        } else {
            return groupContext.getRunningContext().getOuterLoopTotalIterationCount() < groupContext.getLoadFlowContext().getParameters().getMaxOuterLoopIterations()
                    ? OuterLoopStatus.FULL_STABLE : OuterLoopStatus.FAILED;
        }
    }

    protected VoltageInitializer getVoltageInitializer(AcOuterLoopGroupContext groupContext) {
        if (groupContext.getRunningContext().getLastSolverResult() == null) {
            return groupContext.getRunningContext().getFirstInitializer();
        } else {
            return new PreviousValueVoltageInitializer();
        }
    }

    private OuterLoopStatus runOuterLoop(AcOuterLoopGroupContext groupContext, List<Pair<AcOuterLoop, AcOuterLoopContext>> loopAndContexts, ReportNode currentNode, boolean isCompensation) {
        String outerLoopName = getName() + (isCompensation ? " Compensation" : " Checkers");
        ReportNode olReportNode = Reports.createOuterLoopReporter(groupContext.getNetwork().getReportNode(), outerLoopName);

        // for each outer loop re-run solver until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = groupContext.getRunningContext().getOuterLoopIterationByType().computeIfAbsent(outerLoopName, k -> new MutableInt());

            outerLoopStatus = OuterLoopStatus.STABLE;
            for (var loopAndContext : loopAndContexts) {
                AcOuterLoopContext checkerContext = loopAndContext.getRight();
                AcOuterLoop outerLoop = loopAndContext.getLeft();
                // check outer loop status
                checkerContext.setIteration(outerLoopIteration.getValue());
                checkerContext.setOuterLoopTotalIterations(groupContext.getRunningContext().getOuterLoopTotalIterationCount());
                checkerContext.setLastSolverResult(groupContext.getRunningContext().getLastSolverResult());
                checkerContext.setLoadFlowContext(groupContext.getLoadFlowContext());
                outerLoopStatus = combineStatus(outerLoopStatus, outerLoop.check(checkerContext, olReportNode));
            }

            groupContext.getRunningContext().setLastOuterLoopStatus(outerLoopStatus);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop '{}' iteration {}", outerLoopName, groupContext.getRunningContext().getOuterLoopTotalIterationCount());

                ReportNode nrReportNode = groupContext.getLoadFlowContext().getNetwork().getReportNode();
                if (groupContext.getLoadFlowContext().getParameters().isDetailedReport()) {
                    nrReportNode = Reports.createDetailedSolverReporterOuterLoop(nrReportNode,
                            groupContext.getSolverName(),
                            groupContext.getLoadFlowContext().getNetwork().getNumCC(),
                            groupContext.getLoadFlowContext().getNetwork().getNumSC(),
                            groupContext.getRunningContext().getOuterLoopTotalIterationCount() + 1,
                            outerLoopName);
                }

                // if not yet stable, restart solver
                groupContext.runSolver(new PreviousValueVoltageInitializer(), nrReportNode);
                groupContext.getRunningContext().incrementOuterLoopTotalIterationCount();

                outerLoopIteration.increment();
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE
                && groupContext.getRunningContext().getLastSolverResult().getStatus() == AcSolverStatus.CONVERGED
                && groupContext.getRunningContext().getOuterLoopTotalIterationCount() < groupContext.getLoadFlowContext().getParameters().getMaxOuterLoopIterations());

        if (!outerLoopStatus.isStable()) {
            Reports.reportUnsuccessfulOuterLoop(olReportNode, outerLoopStatus.name());
        }
        return outerLoopStatus;
    }

    private OuterLoopStatus combineStatus(OuterLoopStatus s1, OuterLoopStatus s2) {
        return switch (s1) {
            case FULL_STABLE ->
            switch (s2) {
                case FULL_STABLE -> OuterLoopStatus.FULL_STABLE;
                case STABLE -> OuterLoopStatus.STABLE;
                case UNSTABLE -> OuterLoopStatus.UNSTABLE;
                case FAILED -> OuterLoopStatus.FAILED;
            };
            case STABLE ->
            switch (s2) {
                case FULL_STABLE, STABLE -> OuterLoopStatus.STABLE;
                case UNSTABLE -> OuterLoopStatus.UNSTABLE;
                case FAILED -> OuterLoopStatus.FAILED;
            };
            case UNSTABLE ->
            switch (s2) {
                case FULL_STABLE, STABLE, UNSTABLE -> OuterLoopStatus.UNSTABLE;
                case FAILED -> OuterLoopStatus.FAILED;
            };
            case FAILED -> OuterLoopStatus.FAILED;
        };
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, ReportNode reportNode) {
        AcOuterLoopGroupContext loopGroupContext = (AcOuterLoopGroupContext) context;
        return runOuterLoops(loopGroupContext, reportNode);
    }

    /**
     * Select the active model equations and run a first load flow
     * @return true if a loadflow is run and outerloop needs to run, or false if the init is a NOOP
     */
    protected abstract boolean prepareSolverAndModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> checkersAndContexts);

    protected abstract void cleanModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> checkersAndContexts);

}
