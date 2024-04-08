package com.powsybl.openloadflow.ac.outerloop;

import com.google.common.collect.Lists;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.AcOuterLoopGroupContext;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.VoltageControl;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * This outerloop expects as input
 *     An LFNetwork with basic initialisation
 * And outputs in case of success
 *     An Lf Network with power compensation and primary voltage control performed
 */
public class PrimaryVoltageOuterLoopGroup extends AbstractACOuterLoopGroup {

    public static final String NAME = "PrimaryVoltageOuterLoopGroup";

    private final boolean isTransformerVoltageControlOn;

    public PrimaryVoltageOuterLoopGroup(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        super(getPrimaryVoltageOuterLoopGroup(parameters, parametersExt), "Primary Voltage Outer Loop Group");
        isTransformerVoltageControlOn = parameters.isTransformerVoltageControlOn();
    }

    private static List<AcOuterLoop> getPrimaryVoltageOuterLoopGroup(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {

        List<AcOuterLoop> result = new ArrayList<>();

        if (parameters.isDistributedSlack()) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), parametersExt.isLoadPowerFactorConstant(), parametersExt.isUseActiveLimits());
            DistributedSlackOuterLoop distributedSlackOuterLoop = new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.getSlackBusPMaxMismatch());
            result.add(distributedSlackOuterLoop);
        }

        if (parametersExt.isSvcVoltageMonitoring()) {
            MonitoringVoltageOuterLoop monitoringVoltageOuterLoop = new MonitoringVoltageOuterLoop();
            result.add(monitoringVoltageOuterLoop);
        }

        if (parameters.isUseReactiveLimits()) {
            double effectiveMaxReactivePowerMismatch = switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
                case UNIFORM_CRITERIA -> parametersExt.getNewtonRaphsonConvEpsPerEq();
                case PER_EQUATION_TYPE_CRITERIA -> parametersExt.getMaxReactivePowerMismatch() / PerUnit.SB;
            };
            result.add(new ReactiveLimitsOuterLoop(parametersExt.getReactiveLimitsMaxPqPvSwitch(), effectiveMaxReactivePowerMismatch));
        }

        return result;
    }

    @Override
    protected boolean prepareSolverAndModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts) {

        // Deactivate voltage control equations of transformers if theu have been created
        if (isTransformerVoltageControlOn) {
            for (LfBranch controllerBranch : groupContext.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
                controllerBranch.setVoltageControlEnabled(false);
            }
        }

        for (Pair<AcOuterLoop, AcOuterLoopContext> outerLoopAndContext : outerLoopsAndContexts) {
            AcOuterLoop outerLoop = outerLoopAndContext.getLeft();
            AcOuterLoopContext loopContext = outerLoopAndContext.getRight();
            outerLoop.initialize(loopContext);
        }

        groupContext.runSolver(getVoltageInitializer(groupContext), nrReportNode);

        return true;
    }

    @Override
    protected void cleanModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts) {

        for (var outerLoopAndContext : Lists.reverse(outerLoopsAndContexts)) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            outerLoop.cleanup(outerLoopContext);
        }
    }

    @Override
    protected OuterLoopStatus getStableStatus() {
        return OuterLoopStatus.FULL_STABLE;
    }

    @Override
    public boolean isMultipleUseAllowed() {
        return true;
    }
}
