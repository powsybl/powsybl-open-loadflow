/*
 * Copyright (c) 2023-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop.config;

import com.google.common.base.Suppliers;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.LoadFlowParametersOverride;
import com.powsybl.openloadflow.ac.outerloop.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractAcOuterLoopConfig implements AcOuterLoopConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAcOuterLoopConfig.class);

    private static final Supplier<Optional<AcOuterLoopConfig>> CONFIG_SUPPLIER = Suppliers.memoize(AcOuterLoopConfig::findOuterLoopConfig);

    protected AbstractAcOuterLoopConfig() {
    }

    public static Optional<AcOuterLoopConfig> getOuterLoopConfig() {
        return CONFIG_SUPPLIER.get();
    }

    protected static Optional<AcOuterLoop> createDistributedSlackOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, LoadFlowParametersOverride loadFlowParametersOverride) {
        if (loadFlowParametersOverride.isDistributedSlack(parameters)) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(loadFlowParametersOverride.getBalanceType(parameters), parametersExt.isLoadPowerFactorConstant(), parametersExt.isUseActiveLimits());
            return Optional.of(new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.getSlackBusPMaxMismatch()));
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createFreezingHvdcACEmulationOuterLoop(OpenLoadFlowParameters parametersExt) {
        if (parametersExt.isStartWithFrozenACEmulation()) {
            return Optional.of(new FreezingHvdcACEmulationOuterloop());
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createAcHvdcAcEmulationLimitsOuterLoop(LoadFlowParameters parameters) {
        if (parameters.isHvdcAcEmulation()) {
            return Optional.of(new AcHvdcAcEmulationLimitsOuterLoop());
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createAreaInterchangeControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, LoadFlowParametersOverride loadFlowParametersOverride) {
        if (loadFlowParametersOverride.isAreaInterchangeControl(parametersExt)) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(loadFlowParametersOverride.getBalanceType(parameters), parametersExt.isLoadPowerFactorConstant(), parametersExt.isUseActiveLimits());
            return Optional.of(new AcAreaInterchangeControlOuterLoop(activePowerDistribution, parametersExt.getSlackBusPMaxMismatch(), parametersExt.getAreaInterchangePMaxMismatch()));
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createReactiveLimitsOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        if (parameters.isUseReactiveLimits()) {
            double effectiveMaxReactivePowerMismatch = switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
                case UNIFORM_CRITERIA -> parametersExt.getNewtonRaphsonConvEpsPerEq();
                case PER_EQUATION_TYPE_CRITERIA -> parametersExt.getMaxReactivePowerMismatch() / PerUnit.SB;
            };
            return Optional.of(new ReactiveLimitsOuterLoop(parametersExt.getReactiveLimitsMaxPqPvSwitch(),
                                                           effectiveMaxReactivePowerMismatch,
                                                           parametersExt.isVoltageRemoteControlRobustMode(),
                                                           parametersExt.getMinRealisticVoltage(),
                                                           parametersExt.getMaxRealisticVoltage()));
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createSecondaryVoltageControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        if (parametersExt.isSecondaryVoltageControl()) {
            return Optional.of(new SecondaryVoltageControlOuterLoop(parametersExt.getMinPlausibleTargetVoltage(), parametersExt.getMaxPlausibleTargetVoltage()));
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createMonitoringVoltageOuterLoop(OpenLoadFlowParameters parametersExt) {
        if (parametersExt.isSvcVoltageMonitoring()) {
            return Optional.of(new MonitoringVoltageOuterLoop());
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createTransformerVoltageControlOuterLoop(LoadFlowParameters parameters,
                                                                                    boolean useInitialTapPosition,
                                                                                    OpenLoadFlowParameters.TransformerVoltageControlMode controlMode,
                                                                                    int incrementalTransformerVoltageControlOuterLoopMaxTapShift,
                                                                                    double generatorVoltageControlMinNominalVoltage) {
        if (parameters.isTransformerVoltageControlOn()) {
            AcOuterLoop outerLoop = switch (controlMode) {
                case WITH_GENERATOR_VOLTAGE_CONTROL -> new SimpleTransformerVoltageControlOuterLoop();
                case AFTER_GENERATOR_VOLTAGE_CONTROL -> new TransformerVoltageControlOuterLoop(useInitialTapPosition, generatorVoltageControlMinNominalVoltage);
                case INCREMENTAL_VOLTAGE_CONTROL -> new IncrementalTransformerVoltageControlOuterLoop(incrementalTransformerVoltageControlOuterLoopMaxTapShift);
            };
            return Optional.of(outerLoop);
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createTransformerVoltageControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return createTransformerVoltageControlOuterLoop(parameters,
                                                        parametersExt.isTransformerVoltageControlUseInitialTapPosition(),
                                                        parametersExt.getTransformerVoltageControlMode(),
                                                        parametersExt.getIncrementalTransformerRatioTapControlOuterLoopMaxTapShift(),
                                                        parametersExt.getGeneratorVoltageControlMinNominalVoltage());
    }

    protected static Optional<AcOuterLoop> createTransformerReactivePowerControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        if (parametersExt.isTransformerReactivePowerControl()) {
            return Optional.of(new IncrementalTransformerReactivePowerControlOuterLoop(parametersExt.getIncrementalTransformerRatioTapControlOuterLoopMaxTapShift()));
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createShuntVoltageControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters.ShuntVoltageControlMode controlMode, int maxSectionShift) {
        if (parameters.isShuntCompensatorVoltageControlOn()) {
            AcOuterLoop outerLoop = switch (controlMode) {
                case WITH_GENERATOR_VOLTAGE_CONTROL -> new ShuntVoltageControlOuterLoop();
                case INCREMENTAL_VOLTAGE_CONTROL -> new IncrementalShuntVoltageControlOuterLoop(maxSectionShift);
            };
            return Optional.of(outerLoop);
        }
        return Optional.empty();
    }

    protected static Optional<AcOuterLoop> createShuntVoltageControlOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return createShuntVoltageControlOuterLoop(parameters, parametersExt.getShuntVoltageControlMode(), parametersExt.getIncrementalShuntControlOuterLoopMaxSectionShift());
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

    protected static Optional<AcOuterLoop> createAutomationSystemOuterLoop(OpenLoadFlowParameters parametersExt) {
        if (parametersExt.isSimulateAutomationSystems()) {
            return Optional.of(new AutomationSystemOuterLoop());
        }
        return Optional.empty();
    }

    static List<AcOuterLoop> filterInconsistentOuterLoops(List<AcOuterLoop> outerLoops) {
        if (outerLoops.stream().anyMatch(AcAreaInterchangeControlOuterLoop.class::isInstance)) {
            return outerLoops.stream().filter(o -> {
                if (o instanceof DistributedSlackOuterLoop) {
                    LOGGER.warn("Distributed slack and area interchange control are both enabled. " +
                            "Distributed slack outer loop will be disabled, slack will be distributed by the area interchange control.");
                    return false;
                }
                return true;
            }).toList();
        }
        return outerLoops;
    }
}
