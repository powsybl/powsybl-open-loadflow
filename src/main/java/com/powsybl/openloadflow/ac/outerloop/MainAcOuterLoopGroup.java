package com.powsybl.openloadflow.ac.outerloop;

import com.google.common.collect.Lists;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.AcOuterLoopGroupContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class MainAcOuterLoopGroup extends AbstractACOuterLoopGroup {

    public MainAcOuterLoopGroup(List<AcOuterLoop> loops) {
        super(loops, "AC loadflow");
    }

    @Override
    protected void prepareSolverAndModel(AcOuterLoopGroupContext context, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts) {

        if (!outerLoopsAndContexts.isEmpty() && outerLoopsAndContexts.get(0).getLeft() instanceof ACOuterLoopGroup) {
            // OuterLoop groups should initialize themselves and should control how the netork equations are changed so independant loops are not "initialized"
            return;
        }

        // backward compatility
        // "initialize" all independant outerloops - this may give then an opportunity to modify the active equations
        for (var outerLoopAndContext : outerLoopsAndContexts) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            outerLoop.initialize(outerLoopContext);
        }

        VoltageInitializer voltageInitializer = context.getLoadFlowContext().getParameters().getVoltageInitializer();
        // in case of a DC voltage initializer, an DC equation system in created and equations are attached
        // to the network. It is important that DC init is done before AC equation system is created by
        // calling ACLoadContext.getEquationSystem to avoid DC equations overwrite AC ones in the network.
        voltageInitializer.prepare(context.getNetwork());
        context.runSolver(voltageInitializer, nrReportNode);
    }

    @Override
    protected void cleanModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts) {
        if (!outerLoopsAndContexts.isEmpty() && outerLoopsAndContexts.get(0).getLeft() instanceof ACOuterLoopGroup) {
            // OuterLoop groups have control of model cleaning
            return;
        }

        // Compatibility mode
        // outer loops finalization (in reverse order to allow correct cleanup)
        for (var outerLoopAndContext : Lists.reverse(outerLoopsAndContexts)) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            outerLoop.cleanup(outerLoopContext);
        }
    }

    @Override
    protected OuterLoopStatus getStableStatus() {
        return OuterLoopStatus.STABLE;
    }
}
