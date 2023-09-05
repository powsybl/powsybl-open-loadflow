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

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ExplicitAcOuterLoopConfig extends AbstractAcOuterLoopConfig {

    public static final List<String> TYPES = List.of(AcIncrementalPhaseControlOuterLoop.TYPE,
                                                     DistributedSlackOuterLoop.TYPE,
                                                     IncrementalShuntVoltageControlOuterLoop.TYPE,
                                                     IncrementalTransformerVoltageControlOuterLoop.TYPE,
                                                     MonitoringVoltageOuterLoop.TYPE,
                                                     PhaseControlOuterLoop.TYPE,
                                                     ReactiveLimitsOuterLoop.TYPE,
                                                     SecondaryVoltageControlOuterLoop.TYPE,
                                                     ShuntVoltageControlOuterLoop.TYPE,
                                                     SimpleTransformerVoltageControlOuterLoop.TYPE,
                                                     TransformerVoltageControlOuterLoop.TYPE);

    private static AcOuterLoop createOuterLoop(String type, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return switch (type) {
            case AcIncrementalPhaseControlOuterLoop.TYPE -> new AcIncrementalPhaseControlOuterLoop();
            case DistributedSlackOuterLoop.TYPE -> createDistributedSlackOuterLoop(parameters, parametersExt);
            case IncrementalShuntVoltageControlOuterLoop.TYPE -> new IncrementalShuntVoltageControlOuterLoop();
            case IncrementalTransformerVoltageControlOuterLoop.TYPE -> createIncrementalTransformerVoltageControlOuterLoop(parametersExt);
            case MonitoringVoltageOuterLoop.TYPE -> new MonitoringVoltageOuterLoop();
            case PhaseControlOuterLoop.TYPE -> new PhaseControlOuterLoop();
            case ReactiveLimitsOuterLoop.TYPE -> createReactiveLimitsOuterLoop(parametersExt);
            case SecondaryVoltageControlOuterLoop.TYPE -> createSecondaryVoltageControlOuterLoop(parametersExt);
            case ShuntVoltageControlOuterLoop.TYPE -> new ShuntVoltageControlOuterLoop();
            case SimpleTransformerVoltageControlOuterLoop.TYPE -> new SimpleTransformerVoltageControlOuterLoop();
            case TransformerVoltageControlOuterLoop.TYPE -> new TransformerVoltageControlOuterLoop();
            default -> throw new PowsyblException("Unknown outer loop '" + type + "'");
        };
    }

    @Override
    public List<AcOuterLoop> configure(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return Objects.requireNonNull(parametersExt.getOuterLoopTypes()).stream()
                .map(type -> createOuterLoop(type, parameters, parametersExt))
                .toList();
    }
}
