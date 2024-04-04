package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * This outerloop expects as input
 *     An LFNetwork with basic initialisation
 * And outputs in case of success
 *     An Lf Network with power compensation and primary voltage control performed
 */
public class PrimaryVoltageOuterLoopGroup extends ACOuterLoopGroup {

    public PrimaryVoltageOuterLoopGroup(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        super(getPrimaryVoltageOuterLoopGroup(parameters, parametersExt));
    }

    private static List<AcOuterLoop> getPrimaryVoltageOuterLoopGroup(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {

        List<AcOuterLoop> result = new ArrayList<>();

        ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), parametersExt.isLoadPowerFactorConstant(), parametersExt.isUseActiveLimits());
        DistributedSlackOuterLoop distributedSlackOuterLoop = new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.getSlackBusPMaxMismatch());
        result.add(distributedSlackOuterLoop);

        MonitoringVoltageOuterLoop monitoringVoltageOuterLoop = new MonitoringVoltageOuterLoop();
        result.add(monitoringVoltageOuterLoop);

        if (parameters.isUseReactiveLimits()) {
            double effectiveMaxReactivePowerMismatch = switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
                case UNIFORM_CRITERIA -> parametersExt.getNewtonRaphsonConvEpsPerEq();
                case PER_EQUATION_TYPE_CRITERIA -> parametersExt.getMaxReactivePowerMismatch() / PerUnit.SB;
            };
            result.add(new ReactiveLimitsOuterLoop(parametersExt.getReactiveLimitsMaxPqPvSwitch(), effectiveMaxReactivePowerMismatch));
        }

        return result;
    }

}
