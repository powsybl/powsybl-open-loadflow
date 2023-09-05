/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
abstract class AbstractAcOuterLoopConfig implements AcOuterLoopConfig {

    protected AbstractAcOuterLoopConfig() {
    }

    protected static AcOuterLoop createDistributedSlackOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), parametersExt.isLoadPowerFactorConstant());
        return new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.isThrowsExceptionInCaseOfSlackDistributionFailure(), parametersExt.getSlackBusPMaxMismatch());
    }

    protected static IncrementalTransformerVoltageControlOuterLoop createIncrementalTransformerVoltageControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        return new IncrementalTransformerVoltageControlOuterLoop(parametersExt.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift());
    }

    protected static ReactiveLimitsOuterLoop createReactiveLimitsOuterLoop(OpenLoadFlowParameters parametersExt) {
        final double effectiveMaxReactivePowerMismatch;
        switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
            case UNIFORM_CRITERIA:
                effectiveMaxReactivePowerMismatch = parametersExt.getNewtonRaphsonConvEpsPerEq();
                break;
            case PER_EQUATION_TYPE_CRITERIA:
                effectiveMaxReactivePowerMismatch = parametersExt.getMaxReactivePowerMismatch() / PerUnit.SB;
                break;
            default:
                throw new PowsyblException("Unknown Newton Raphson stopping criteria type: " + parametersExt.getNewtonRaphsonStoppingCriteriaType());
        }
        return new ReactiveLimitsOuterLoop(parametersExt.getReactiveLimitsMaxPqPvSwitch(), effectiveMaxReactivePowerMismatch);
    }

    protected static SecondaryVoltageControlOuterLoop createSecondaryVoltageControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        return new SecondaryVoltageControlOuterLoop(parametersExt.getControllerToPilotPointVoltageSensiEpsilon(),
                                                    parametersExt.getMinPlausibleTargetVoltage(),
                                                    parametersExt.getMaxPlausibleTargetVoltage());
    }
}
