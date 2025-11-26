/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop.config;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.LoadFlowParametersOverride;
import com.powsybl.openloadflow.ac.outerloop.*;
import com.powsybl.openloadflow.lf.outerloop.AbstractAreaInterchangeControlOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.AbstractIncrementalPhaseControlOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.OuterLoop;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ExplicitAcOuterLoopConfig extends AbstractAcOuterLoopConfig {

    public static final List<String> NAMES = List.of(AbstractIncrementalPhaseControlOuterLoop.NAME,
                                                     DistributedSlackOuterLoop.NAME,
                                                     IncrementalShuntVoltageControlOuterLoop.NAME,
                                                     IncrementalTransformerVoltageControlOuterLoop.NAME,
                                                     MonitoringVoltageOuterLoop.NAME,
                                                     PhaseControlOuterLoop.NAME,
                                                     ReactiveLimitsOuterLoop.NAME,
                                                     SecondaryVoltageControlOuterLoop.NAME,
                                                     ShuntVoltageControlOuterLoop.NAME,
                                                     SimpleTransformerVoltageControlOuterLoop.NAME,
                                                     TransformerVoltageControlOuterLoop.NAME,
                                                     AutomationSystemOuterLoop.NAME,
                                                     IncrementalTransformerReactivePowerControlOuterLoop.NAME,
                                                     AbstractAreaInterchangeControlOuterLoop.NAME,
                                                     FreezingHvdcACEmulationOuterloop.NAME);

    private static Optional<AcOuterLoop> createOuterLoop(String name, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, LoadFlowParametersOverride loadFlowParametersOverride) {
        return switch (name) {
            case AbstractIncrementalPhaseControlOuterLoop.NAME -> createPhaseControlOuterLoop(parameters,
                                                                                               OpenLoadFlowParameters.PhaseShifterControlMode.INCREMENTAL);
            case DistributedSlackOuterLoop.NAME -> createDistributedSlackOuterLoop(parameters, parametersExt, loadFlowParametersOverride);
            case IncrementalShuntVoltageControlOuterLoop.NAME -> createShuntVoltageControlOuterLoop(parameters,
                                                                                                    OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL,
                                                                                                    parametersExt.getIncrementalShuntControlOuterLoopMaxSectionShift());
            case IncrementalTransformerVoltageControlOuterLoop.NAME -> createTransformerVoltageControlOuterLoop(parameters,
                                                                                                                parametersExt.isTransformerVoltageControlUseInitialTapPosition(),
                                                                                                                OpenLoadFlowParameters.TransformerVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL,
                                                                                                                parametersExt.getIncrementalTransformerRatioTapControlOuterLoopMaxTapShift(),
                                                                                                                parametersExt.getGeneratorVoltageControlMinNominalVoltage());
            case MonitoringVoltageOuterLoop.NAME -> createMonitoringVoltageOuterLoop(parametersExt);
            case PhaseControlOuterLoop.NAME -> createPhaseControlOuterLoop(parameters,
                                                                                  OpenLoadFlowParameters.PhaseShifterControlMode.CONTINUOUS_WITH_DISCRETISATION);
            case ReactiveLimitsOuterLoop.NAME -> createReactiveLimitsOuterLoop(parameters, parametersExt);
            case SecondaryVoltageControlOuterLoop.NAME -> createSecondaryVoltageControlOuterLoop(parametersExt);
            case ShuntVoltageControlOuterLoop.NAME -> createShuntVoltageControlOuterLoop(parameters,
                                                                                         OpenLoadFlowParameters.ShuntVoltageControlMode.WITH_GENERATOR_VOLTAGE_CONTROL,
                                                                                         parametersExt.getIncrementalShuntControlOuterLoopMaxSectionShift());
            case SimpleTransformerVoltageControlOuterLoop.NAME -> createTransformerVoltageControlOuterLoop(parameters,
                                                                                                           parametersExt.isTransformerVoltageControlUseInitialTapPosition(),
                                                                                                           OpenLoadFlowParameters.TransformerVoltageControlMode.WITH_GENERATOR_VOLTAGE_CONTROL,
                                                                                                           parametersExt.getIncrementalTransformerRatioTapControlOuterLoopMaxTapShift(),
                                                                                                           parametersExt.getGeneratorVoltageControlMinNominalVoltage());
            case TransformerVoltageControlOuterLoop.NAME -> createTransformerVoltageControlOuterLoop(parameters,
                                                                                                     parametersExt.isTransformerVoltageControlUseInitialTapPosition(),
                                                                                                     OpenLoadFlowParameters.TransformerVoltageControlMode.AFTER_GENERATOR_VOLTAGE_CONTROL,
                                                                                                     parametersExt.getIncrementalTransformerRatioTapControlOuterLoopMaxTapShift(),
                                                                                                     parametersExt.getGeneratorVoltageControlMinNominalVoltage());
            case AutomationSystemOuterLoop.NAME -> createAutomationSystemOuterLoop(parametersExt);
            case IncrementalTransformerReactivePowerControlOuterLoop.NAME -> createTransformerReactivePowerControlOuterLoop(parametersExt);
            case AbstractAreaInterchangeControlOuterLoop.NAME -> createAreaInterchangeControlOuterLoop(parameters, parametersExt, loadFlowParametersOverride);
            case FreezingHvdcACEmulationOuterloop.NAME -> createFreezingHvdcACEmulationOuterLoop(parametersExt);
            default -> throw new PowsyblException("Unknown outer loop '" + name + "' for AC load flow");
        };
    }

    private static void checkTypeUnicity(List<AcOuterLoop> outerLoops) {
        Map<String, Integer> outerLoopTypesCount = outerLoops.stream().collect(Collectors.toMap(OuterLoop::getType, outerLoop -> 1, Integer::sum));
        for (var e : outerLoopTypesCount.entrySet()) {
            int count = e.getValue();
            if (count > 1) {
                String type = e.getKey();
                throw new PowsyblException("Multiple (" + count + ") outer loops with same type: " + type);
            }
        }
    }

    @Override
    public List<AcOuterLoop> configure(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return configure(parameters, parametersExt, LoadFlowParametersOverride.NO_OVERRIDE);
    }

    @Override
    public List<AcOuterLoop> configure(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, LoadFlowParametersOverride loadFlowParametersOverride) {
        List<AcOuterLoop> outerLoops = Objects.requireNonNull(loadFlowParametersOverride.getOuterLoopNames(parametersExt)).stream()
                .flatMap(name -> createOuterLoop(name, parameters, parametersExt, loadFlowParametersOverride).stream())
                .toList();
        checkTypeUnicity(outerLoops);
        return filterInconsistentOuterLoops(outerLoops);
    }
}
