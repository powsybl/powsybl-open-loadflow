/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
abstract class AbstractAcOuterLoopConfig implements AcOuterLoopConfig {

    protected AbstractAcOuterLoopConfig() {
    }

    protected static Optional<AcOuterLoop> createDistributedSlackOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        if (parameters.isDistributedSlack()) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), parametersExt.isLoadPowerFactorConstant(), parametersExt.isUseActiveLimits());
            return Optional.of(new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.getSlackDistributionFailureBehavior(), parametersExt.getSlackBusPMaxMismatch()));
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createReactiveLimitsOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        if (parameters.isUseReactiveLimits()) {
            double effectiveMaxReactivePowerMismatch = switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
                case UNIFORM_CRITERIA -> parametersExt.getNewtonRaphsonConvEpsPerEq();
                case PER_EQUATION_TYPE_CRITERIA -> parametersExt.getMaxReactivePowerMismatch() / PerUnit.SB;
            };
            return Optional.of(new ReactiveLimitsOuterLoop(parametersExt.getReactiveLimitsMaxPqPvSwitch(), effectiveMaxReactivePowerMismatch));
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createSecondaryVoltageControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        if (parametersExt.isSecondaryVoltageControl()) {
            return Optional.of(new SecondaryVoltageControlOuterLoop());
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createMonitoringVoltageOuterLoop(OpenLoadFlowParameters parametersExt) {
        if (parametersExt.isSvcVoltageMonitoring()) {
            return Optional.of(new MonitoringVoltageOuterLoop());
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createTransformerVoltageControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters.TransformerVoltageControlMode controlMode,
                                                                                    int incrementalTransformerVoltageControlOuterLoopMaxTapShift) {
        if (parameters.isTransformerVoltageControlOn()) {
            AcOuterLoop outerLoop = switch (controlMode) {
                case WITH_GENERATOR_VOLTAGE_CONTROL -> new SimpleTransformerVoltageControlOuterLoop();
                case AFTER_GENERATOR_VOLTAGE_CONTROL -> new TransformerVoltageControlOuterLoop();
                case INCREMENTAL_VOLTAGE_CONTROL -> new IncrementalTransformerVoltageControlOuterLoop(incrementalTransformerVoltageControlOuterLoopMaxTapShift);
            };
            return Optional.of(outerLoop);
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createTransformerVoltageControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return createTransformerVoltageControlOuterLoop(parameters,
                                                        parametersExt.getTransformerVoltageControlMode(),
                                                        parametersExt.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift());
    }

    protected static Optional<AcOuterLoop> createShuntVoltageControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters.ShuntVoltageControlMode controlMode) {
        if (parameters.isShuntCompensatorVoltageControlOn()) {
            AcOuterLoop outerLoop = switch (controlMode) {
                case WITH_GENERATOR_VOLTAGE_CONTROL -> new ShuntVoltageControlOuterLoop();
                case INCREMENTAL_VOLTAGE_CONTROL -> new IncrementalShuntVoltageControlOuterLoop();
            };
            return Optional.of(outerLoop);
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createShuntVoltageControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return createShuntVoltageControlOuterLoop(parameters, parametersExt.getShuntVoltageControlMode());
    }

    protected static Optional<AcOuterLoop> createPhaseControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters.PhaseShifterControlMode controlMode) {
        if (parameters.isPhaseShifterRegulationOn()) {
            AcOuterLoop outerLoop = switch (controlMode) {
                case CONTINUOUS_WITH_DISCRETISATION -> new PhaseControlOuterLoop();
                case INCREMENTAL -> new AcIncrementalPhaseControlOuterLoop();
            };
            return Optional.of(outerLoop);
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createPhaseControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return createPhaseControlOuterLoop(parameters, parametersExt.getPhaseShifterControlMode());
    }
}
