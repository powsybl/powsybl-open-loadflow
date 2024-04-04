package com.powsybl.openloadflow.ac.outerloop;

import com.google.common.collect.Lists;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.AcSolver;
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

// TODO: En faire une OuterLoop (mais avec un contexte différent)
public class ACOuterLoopGroup {

    private static final Logger LOGGER = LoggerFactory.getLogger(ACOuterLoopGroup.class);

    private final List<AcOuterLoop> loops;

    public ACOuterLoopGroup(List<AcOuterLoop> loops) {
        this.loops = loops;
    }

    public OuterLoopStatus runOuterLoops(AcLoadFlowContext loadFlowContext,
                                         AcloadFlowEngine.RunningContext runningContext,
                                         AcSolver solver,
                                         ReportNode nrReportNode,
                                         VoltageInitializer voltageInitializer) {

        LOGGER.info("Starting Load Flow: " + getName());

        List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts = loops.stream()
                .map(outerLoop -> Pair.of(outerLoop, new AcOuterLoopContext(loadFlowContext.getNetwork())))
                .toList();

        // outer loops initialization
        for (var outerLoopAndContext : outerLoopsAndContexts) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            outerLoop.initialize(outerLoopContext);
        }

        // initial solver run
        runningContext.setLastSolverResult(solver.run(voltageInitializer, nrReportNode));

        runningContext.incNrTotalIterationCount(runningContext.getLastSolverResult().getIterations());

        // continue with outer loops only if solver succeed
        if (runningContext.getLastSolverResult().getStatus() == AcSolverStatus.CONVERGED) {
            // re-run all outer loops until solver failed or no more solver iterations are needed
            int oldNrTotalIterations;
            do {
                oldNrTotalIterations = runningContext.getNrTotalIterationCount();

                // outer loops are nested: innermost loop first in the list, outermost loop last
                for (var outerLoopAndContext : outerLoopsAndContexts) {
                    runOuterLoop(outerLoopAndContext.getLeft(), outerLoopAndContext.getRight(), solver, loadFlowContext, runningContext);

                    // continue with next outer loop only if:
                    // - last solver run succeed,
                    // - last OuterLoopStatus is not FAILED
                    // - we have not reached max number of outer loop iteration
                    if (runningContext.getLastSolverResult().getStatus() != AcSolverStatus.CONVERGED
                            || runningContext.getLastOuterLoopStatus() == OuterLoopStatus.FAILED
                            || runningContext.getOuterLoopTotalIterationCount() >= loadFlowContext.getParameters().getMaxOuterLoopIterations()) {
                        break;
                    }
                }
            } while (runningContext.getNrTotalIterationCount() > oldNrTotalIterations
                    && runningContext.getLastSolverResult().getStatus() == AcSolverStatus.CONVERGED
                    && runningContext.getLastOuterLoopStatus() != OuterLoopStatus.FAILED
                    && runningContext.getOuterLoopTotalIterationCount() < loadFlowContext.getParameters().getMaxOuterLoopIterations());
        }

        // outer loops finalization (in reverse order to allow correct cleanup)
        for (var outerLoopAndContext : Lists.reverse(outerLoopsAndContexts)) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            if (outerLoop instanceof DistributedSlackOuterLoop) {
                runningContext.addDistributedActivePower(((DistributedSlackContextData) outerLoopContext.getData()).getDistributedActivePower());
            }
            outerLoop.cleanup(outerLoopContext);
        }

        if (runningContext.getLastOuterLoopStatus() == OuterLoopStatus.FAILED) {
            return OuterLoopStatus.FAILED;
        } else {
            return runningContext.getOuterLoopTotalIterationCount() < loadFlowContext.getParameters().getMaxOuterLoopIterations()
                    ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE;
        }

    }

    private void runOuterLoop(AcOuterLoop outerLoop, AcOuterLoopContext outerLoopContext, AcSolver solver, AcLoadFlowContext context, AcloadFlowEngine.RunningContext runningContext) {
        ReportNode olReportNode = Reports.createOuterLoopReporter(outerLoopContext.getNetwork().getReportNode(), outerLoop.getName());

        // for each outer loop re-run solver until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = runningContext.getOuterLoopIterationByType().computeIfAbsent(outerLoop.getName(), k -> new MutableInt());

            // check outer loop status
            outerLoopContext.setIteration(outerLoopIteration.getValue());
            outerLoopContext.setOuterLoopTotalIterations(runningContext.getOuterLoopTotalIterationCount());
            outerLoopContext.setLastSolverResult(runningContext.getLastSolverResult());
            outerLoopContext.setLoadFlowContext(context);
            outerLoopStatus = outerLoop.check(outerLoopContext, olReportNode);
            runningContext.setLastOuterLoopStatus(outerLoopStatus);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop '{}' iteration {}", outerLoop.getName(), runningContext.getOuterLoopTotalIterationCount());

                ReportNode nrReportNode = context.getNetwork().getReportNode();
                if (context.getParameters().isDetailedReport()) {
                    nrReportNode = Reports.createDetailedSolverReporterOuterLoop(nrReportNode,
                            solver.getName(),
                            context.getNetwork().getNumCC(),
                            context.getNetwork().getNumSC(),
                            runningContext.getOuterLoopTotalIterationCount() + 1,
                            outerLoop.getName());
                }

                // if not yet stable, restart solver
                runningContext.setLastSolverResult(solver.run(new PreviousValueVoltageInitializer(), nrReportNode));

                runningContext.incNrTotalIterationCount(runningContext.getLastSolverResult().getIterations());
                runningContext.incrementOuterLoopTotalIterationCount();

                outerLoopIteration.increment();
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE
                && runningContext.getLastSolverResult().getStatus() == AcSolverStatus.CONVERGED
                && runningContext.getOuterLoopTotalIterationCount() < context.getParameters().getMaxOuterLoopIterations());

        if (outerLoopStatus != OuterLoopStatus.STABLE) {
            Reports.reportUnsuccessfulOuterLoop(olReportNode, outerLoopStatus.name());
        }
    }

    public String getName() {
        // TODO: Make abstract
        return "TODO";
    }

}
