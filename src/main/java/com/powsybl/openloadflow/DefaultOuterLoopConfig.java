/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DefaultOuterLoopConfig implements OuterLoopConfig {

    private static OuterLoop createTransformerVoltageControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        switch (parametersExt.getTransformerVoltageControlMode()) {
            case WITH_GENERATOR_VOLTAGE_CONTROL:
                return new SimpleTransformerVoltageControlOuterLoop();
            case AFTER_GENERATOR_VOLTAGE_CONTROL:
                return new TransformerVoltageControlOuterLoop();
            case INCREMENTAL_VOLTAGE_CONTROL:
                return new IncrementalTransformerVoltageControlOuterLoop(parametersExt.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift());
            default:
                throw new IllegalStateException("Unknown transformer voltage control mode: " + parametersExt.getTransformerVoltageControlMode());
        }
    }

    private static OuterLoop createShuntVoltageControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        switch (parametersExt.getShuntVoltageControlMode()) {
            case WITH_GENERATOR_VOLTAGE_CONTROL:
                return new ShuntVoltageControlOuterLoop();
            case INCREMENTAL_VOLTAGE_CONTROL:
                return new IncrementalShuntVoltageControlOuterLoop();
            default:
                throw new IllegalStateException("Unknown shunt voltage control mode: " + parametersExt.getShuntVoltageControlMode());
        }
    }

    private static OuterLoop createPhaseShifterControlOuterLoop(OpenLoadFlowParameters parametersExt) {
        switch (parametersExt.getPhaseShifterControlMode()) {
            case CONTINUOUS_WITH_DISCRETISATION:
                return new PhaseControlOuterLoop();
            case INCREMENTAL:
                return new IncrementalPhaseControlOuterLoop();
            default:
                throw new IllegalStateException("Unknown phase shifter control mode: " + parametersExt.getPhaseShifterControlMode());
        }
    }

    private static OuterLoop createDistributedSlackOuterLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), parametersExt.isLoadPowerFactorConstant());
        return new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.isThrowsExceptionInCaseOfSlackDistributionFailure(), parametersExt.getSlackBusPMaxMismatch());
    }

    @Override
    public List<OuterLoop> configure(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        List<OuterLoop> outerLoops = new ArrayList<>(5);
        // primary frequency control
        if (parameters.isDistributedSlack()) {
            outerLoops.add(createDistributedSlackOuterLoop(parameters, parametersExt));
        }
        // primary voltage control
        if (parametersExt.isSvcVoltageMonitoring()) {
            outerLoops.add(new MonitoringVoltageOuterLoop());
        }
        if (parameters.isUseReactiveLimits()) {
            outerLoops.add(new ReactiveLimitsOuterLoop(parametersExt.getReactiveLimitsMaxPqPvSwitch()));
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
        // automatons
        if (parametersExt.isSimulateAutomatons()) {
            outerLoops.add(new AutomatonOuterLoop());
        }
        // secondary voltage control
        if (parametersExt.isSecondaryVoltageControl()) {
            outerLoops.add(new SecondaryVoltageControlOuterLoop());
        }
        return outerLoops;
    }
}
