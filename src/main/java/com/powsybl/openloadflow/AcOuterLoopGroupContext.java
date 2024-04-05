package com.powsybl.openloadflow;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.AcSolver;
import com.powsybl.openloadflow.ac.solver.AcSolverResult;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

public class AcOuterLoopGroupContext extends AcOuterLoopContext {

    private final AcloadFlowEngine.RunningContext runningContext;
    // We won't give direct access to the solver to ensure consistent usage and update of the running context
    private final AcSolver solver;

    public AcOuterLoopGroupContext(LfNetwork network, AcloadFlowEngine.RunningContext runningContext, AcSolver solver) {
        super(network);
        this.runningContext = runningContext;
        this.solver = solver;
    }

    public AcOuterLoopGroupContext(AcOuterLoopGroupContext parent) {
        super(parent.getNetwork());
        this.runningContext = parent.getRunningContext();
        this.solver = parent.solver;
    }

    public AcloadFlowEngine.RunningContext getRunningContext() {
        return runningContext;
    }

    /**
     * Runs the solver and updates the running context status
     */
    public AcSolverResult runSolver(VoltageInitializer voltageInitializer, ReportNode nrReportNode) {
        // TODO: Can this be moved in the context - and the solver be hidden ?
        runningContext.setLastSolverResult(solver.run(voltageInitializer, nrReportNode));
        runningContext.incNrTotalIterationCount(runningContext.getLastSolverResult().getIterations());
        return runningContext.getLastSolverResult();
    }

    public String getSolverName() {
        return solver.getName();
    }
}
