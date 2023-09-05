/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DefaultAcOuterLoopConfig extends AbstractAcOuterLoopConfig {

    private static AcOuterLoop createTransformerVoltageControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        switch (parametersExt.getTransformerVoltageControlMode()) {
            case WITH_GENERATOR_VOLTAGE_CONTROL:
                return new SimpleTransformerVoltageControlOuterLoop();
            case AFTER_GENERATOR_VOLTAGE_CONTROL:
                return new TransformerVoltageControlOuterLoop();
            case INCREMENTAL_VOLTAGE_CONTROL:
                return createIncrementalTransformerVoltageControlOuterLoop(parametersExt);
            default:
                throw new IllegalStateException("Unknown transformer voltage control mode: " + parametersExt.getTransformerVoltageControlMode());
        }
    }

    private static AcOuterLoop createShuntVoltageControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        switch (parametersExt.getShuntVoltageControlMode()) {
            case WITH_GENERATOR_VOLTAGE_CONTROL:
                return new ShuntVoltageControlOuterLoop();
            case INCREMENTAL_VOLTAGE_CONTROL:
                return new IncrementalShuntVoltageControlOuterLoop();
            default:
                throw new IllegalStateException("Unknown shunt voltage control mode: " + parametersExt.getShuntVoltageControlMode());
        }
    }

    private static AcOuterLoop createPhaseShifterControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        switch (parametersExt.getPhaseShifterControlMode()) {
            case CONTINUOUS_WITH_DISCRETISATION:
                return new PhaseControlOuterLoop();
            case INCREMENTAL:
                return new AcIncrementalPhaseControlOuterLoop();
            default:
                throw new IllegalStateException("Unknown phase shifter control mode: " + parametersExt.getPhaseShifterControlMode());
        }
    }

    @Override
    public List<AcOuterLoop> configure(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        List<AcOuterLoop> outerLoops = new ArrayList<>(5);
        // primary frequency control
        if (parameters.isDistributedSlack()) {
            outerLoops.add(createDistributedSlackOuterLoop(parameters, parametersExt));
        }
        // primary voltage control
        if (parametersExt.isSvcVoltageMonitoring()) {
            outerLoops.add(new MonitoringVoltageOuterLoop());
        }
        if (parameters.isUseReactiveLimits()) {
            outerLoops.add(createReactiveLimitsOuterLoop(parametersExt));
        }
        // phase shifter control
        if (parameters.isPhaseShifterRegulationOn()) {
            outerLoops.add(createPhaseShifterControlOuterLoop(parametersExt));
        }
        // transformer voltage control
        if (parameters.isTransformerVoltageControlOn()) {
            outerLoops.add(createTransformerVoltageControlOuterLoop(parametersExt));
        }
        // shunt compensator voltage control
        if (parameters.isShuntCompensatorVoltageControlOn()) {
            outerLoops.add(createShuntVoltageControlOuterLoop(parametersExt));
        }
        // secondary voltage control
        if (parametersExt.isSecondaryVoltageControl()) {
            outerLoops.add(createSecondaryVoltageControlOuterLoop(parametersExt));
        }
        return outerLoops;
    }
}
