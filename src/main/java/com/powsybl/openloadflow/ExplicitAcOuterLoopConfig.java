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
import com.powsybl.openloadflow.lf.outerloop.OuterLoop;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ExplicitAcOuterLoopConfig extends AbstractAcOuterLoopConfig {

    public static final List<String> NAMES = List.of(AcIncrementalPhaseControlOuterLoop.NAME,
                                                     DistributedSlackOuterLoop.NAME,
                                                     IncrementalShuntVoltageControlOuterLoop.NAME,
                                                     IncrementalTransformerVoltageControlOuterLoop.NAME,
                                                     MonitoringVoltageOuterLoop.NAME,
                                                     PhaseControlOuterLoop.NAME,
                                                     ReactiveLimitsOuterLoop.NAME,
                                                     SecondaryVoltageControlOuterLoop.NAME,
                                                     ShuntVoltageControlOuterLoop.NAME,
                                                     SimpleTransformerVoltageControlOuterLoop.NAME,
                                                     TransformerVoltageControlOuterLoop.NAME);

    private static AcOuterLoop createOuterLoop(String name, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return switch (name) {
            case AcIncrementalPhaseControlOuterLoop.NAME -> new AcIncrementalPhaseControlOuterLoop();
            case DistributedSlackOuterLoop.NAME -> createDistributedSlackOuterLoop(parameters, parametersExt);
            case IncrementalShuntVoltageControlOuterLoop.NAME -> new IncrementalShuntVoltageControlOuterLoop();
            case IncrementalTransformerVoltageControlOuterLoop.NAME -> createIncrementalTransformerVoltageControlOuterLoop(parametersExt);
            case MonitoringVoltageOuterLoop.NAME -> new MonitoringVoltageOuterLoop();
            case PhaseControlOuterLoop.NAME -> new PhaseControlOuterLoop();
            case ReactiveLimitsOuterLoop.NAME -> createReactiveLimitsOuterLoop(parametersExt);
            case SecondaryVoltageControlOuterLoop.NAME -> createSecondaryVoltageControlOuterLoop(parametersExt);
            case ShuntVoltageControlOuterLoop.NAME -> new ShuntVoltageControlOuterLoop();
            case SimpleTransformerVoltageControlOuterLoop.NAME -> new SimpleTransformerVoltageControlOuterLoop();
            case TransformerVoltageControlOuterLoop.NAME -> new TransformerVoltageControlOuterLoop();
            default -> throw new PowsyblException("Unknown outer loop '" + name + "'");
        };
    }

    @Override
    public List<AcOuterLoop> configure(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        List<AcOuterLoop> outerLoops = Objects.requireNonNull(parametersExt.getOuterLoopNames()).stream()
                .map(name -> createOuterLoop(name, parameters, parametersExt))
                .toList();
        Map<String, Integer> outerLoopTypesCount = outerLoops.stream().collect(Collectors.toMap(OuterLoop::getType, outerLoop -> 1, Integer::sum));
        for (var e : outerLoopTypesCount.entrySet()) {
            int count = e.getValue();
            if (count > 1) {
                String type = e.getKey();
                throw new PowsyblException("Multiple (" + count + ") outer loops with same type: " + type);
            }
        }
        return outerLoops;
    }
}
