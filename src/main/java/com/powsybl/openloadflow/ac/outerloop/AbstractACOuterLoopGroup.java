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

public abstract class AbstractACOuterLoopGroup implements ACOuterLoopGroup {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractACOuterLoopGroup.class);

    private final List<AcOuterLoop> loops;
    // TODO: Remove field and make getName abstract ?
    private final String name;

    protected AbstractACOuterLoopGroup(List<AcOuterLoop> loops, String name) {
        this.loops = loops;
        this.name = name;
    }

    @Override
    public OuterLoopStatus runOuterLoops(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode) {

        LOGGER.info("Starting Load Flow: " + getName());

        List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts = loops.stream()
                .map(outerLoop -> Pair.of(outerLoop,
                        outerLoop instanceof ACOuterLoopGroup ?
                                new AcOuterLoopGroupContext(groupContext)
                                :
                                new AcOuterLoopContext(groupContext.getLoadFlowContext().getNetwork())))
                .toList();

        // TODO: Review this protocol
        // initial solver run if needed by the group
        boolean modelChanged = prepareSolverAndModel(groupContext, nrReportNode, outerLoopsAndContexts);
        if (!modelChanged) { // If init is a NOOP return groups status
            return getStableStatus();
        }

        // continue with outer loops only if solver succeed
        // The solver may not have been run if first loop prefers to run the solver itself
        if (groupContext.getRunningContext().getLastSolverResult() == null || groupContext.getRunningContext().getLastSolverResult().getStatus() == AcSolverStatus.CONVERGED) {
            // re-run all outer loops until solver failed or no more solver iterations are needed
            int oldNrTotalIterations;
            do {
                oldNrTotalIterations = groupContext.getRunningContext().getNrTotalIterationCount();

                // outer loops are nested: innermost loop first in the list, outermost loop last
                for (var outerLoopAndContext : outerLoopsAndContexts) {

                    OuterLoopStatus outerLoopStatus = runOuterLoop(groupContext, outerLoopAndContext.getLeft(), outerLoopAndContext.getRight(), nrReportNode);
                    if (outerLoopStatus == OuterLoopStatus.FULL_STABLE) {
                        // Reset NR Totial Iteration counter
                        oldNrTotalIterations = groupContext.getRunningContext().getNrTotalIterationCount();
                    }
                    // continue with next outer loop only if:
                    // - last solver run succeed,
                    // - last OuterLoopStatus is not FAILED
                    // - we have not reached max number of outer loop iteration
                    if (groupContext.getRunningContext().getLastSolverResult().getStatus() != AcSolverStatus.CONVERGED
                            || groupContext.getRunningContext().getLastOuterLoopStatus() == OuterLoopStatus.FAILED
                            || groupContext.getRunningContext().getOuterLoopTotalIterationCount() >= groupContext.getLoadFlowContext().getParameters().getMaxOuterLoopIterations()) {
                        break;
                    }
                }
            } while (groupContext.getRunningContext().getNrTotalIterationCount() > oldNrTotalIterations
                    && groupContext.getRunningContext().getLastSolverResult().getStatus() == AcSolverStatus.CONVERGED
                    && groupContext.getRunningContext().getLastOuterLoopStatus() != OuterLoopStatus.FAILED
                    && groupContext.getRunningContext().getOuterLoopTotalIterationCount() < groupContext.getLoadFlowContext().getParameters().getMaxOuterLoopIterations());
        }

        // Update distributed active power
        for (var outerLoopAndContext : Lists.reverse(outerLoopsAndContexts)) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            if (outerLoop instanceof DistributedSlackOuterLoop) {
                groupContext.getRunningContext().addDistributedActivePower(((DistributedSlackContextData) outerLoopContext.getData()).getDistributedActivePower());
            }
        }

        cleanModel(groupContext, nrReportNode, outerLoopsAndContexts);

        if (groupContext.getRunningContext().getLastOuterLoopStatus() == OuterLoopStatus.FAILED) {
            return OuterLoopStatus.FAILED;
        } else {
            return groupContext.getRunningContext().getOuterLoopTotalIterationCount() < groupContext.getLoadFlowContext().getParameters().getMaxOuterLoopIterations()
                    ? getStableStatus() : OuterLoopStatus.UNSTABLE;
        }

    }

    private OuterLoopStatus runOuterLoop(AcOuterLoopGroupContext groupContext, AcOuterLoop outerLoop, AcOuterLoopContext outerLoopContext, ReportNode currentNode) {
        ReportNode olReportNode =
                outerLoop instanceof ACOuterLoopGroup ? currentNode  // TODO remot this patch to avoit modifying reports during dev
                :
                Reports.createOuterLoopReporter(outerLoopContext.getNetwork().getReportNode(), outerLoop.getName());

        // for each outer loop re-run solver until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = groupContext.getRunningContext().getOuterLoopIterationByType().computeIfAbsent(outerLoop.getName(), k -> new MutableInt());

            // check outer loop status
            outerLoopContext.setIteration(outerLoopIteration.getValue());
            outerLoopContext.setOuterLoopTotalIterations(groupContext.getRunningContext().getOuterLoopTotalIterationCount());
            outerLoopContext.setLastSolverResult(groupContext.getRunningContext().getLastSolverResult());
            outerLoopContext.setLoadFlowContext(groupContext.getLoadFlowContext());
            outerLoopStatus = outerLoop.check(outerLoopContext, olReportNode);
            groupContext.getRunningContext().setLastOuterLoopStatus(outerLoopStatus);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop '{}' iteration {}", outerLoop.getName(), groupContext.getRunningContext().getOuterLoopTotalIterationCount());

                ReportNode nrReportNode = groupContext.getLoadFlowContext().getNetwork().getReportNode();
                if (groupContext.getLoadFlowContext().getParameters().isDetailedReport()) {
                    nrReportNode = Reports.createDetailedSolverReporterOuterLoop(nrReportNode,
                            groupContext.getSolverName(),
                            groupContext.getLoadFlowContext().getNetwork().getNumCC(),
                            groupContext.getLoadFlowContext().getNetwork().getNumSC(),
                            groupContext.getRunningContext().getOuterLoopTotalIterationCount() + 1,
                            outerLoop.getName());
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

    public String getName() {
        return name;
    }

    protected VoltageInitializer getVoltageInitializer(AcOuterLoopGroupContext groupContext) {
        if (groupContext.getRunningContext().getLastSolverResult() == null) {
            return groupContext.getRunningContext().getFirstInitializer();
        } else {
            return new PreviousValueVoltageInitializer();
        }
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, ReportNode reportNode) {
        AcOuterLoopGroupContext loopGroupContext = (AcOuterLoopGroupContext) context;
        return runOuterLoops(loopGroupContext, reportNode);
    }

    /**
     * If needed modifies the model and equations and run a first load flow
     * @return true if a loadflow is run and outerloop needs to run, or false if the init is a NOOP
     */
    protected abstract boolean prepareSolverAndModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts);

    protected abstract void cleanModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts);

    protected abstract OuterLoopStatus getStableStatus();
}
