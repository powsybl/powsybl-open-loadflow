/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.*;

import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemUpdater extends AbstractLfNetworkListener {

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    public AcEquationSystemUpdater(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
    }

    private void updateVoltageControl(VoltageControl voltageControl) {
        LfBus controlledBus = voltageControl.getControlledBus();
        Set<LfBus> controllerBuses = voltageControl.getControllerBuses();

        LfBus firstControllerBus = controllerBuses.iterator().next();
        if (firstControllerBus.hasGeneratorsWithSlope()) {
            // we only support one controlling static var compensator without any other controlling generators
            // we don't support controller bus that wants to control back voltage with slope.
            if (!firstControllerBus.isVoltageControllerEnabled()) {
                equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V_WITH_SLOPE)
                        .orElseThrow().setActive(false);
            }
        } else {
            if (voltageControl.isVoltageControlLocal()) {
                equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                        .orElseThrow()
                        .setActive(controlledBus.isVoltageControllerEnabled());
            } else {
                AcEquationSystem.updateRemoteVoltageControlEquations(voltageControl, equationSystem);
            }
        }
    }

    private void updateVoltageControl(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        // active/de-activate bus target reactive power equation to switch bus PV or PQ
        equationSystem.getEquation(controllerBus.getNum(), AcEquationType.BUS_TARGET_Q)
                .orElseThrow()
                .setActive(!newVoltageControllerEnabled);

        updateVoltageControl(controllerBus.getVoltageControl().orElseThrow());
    }

    @Override
    public void onVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        updateVoltageControl(controllerBus, newVoltageControllerEnabled);
    }

    private void updateDiscretePhaseControl(DiscretePhaseControl phaseControl, DiscretePhaseControl.Mode newMode) {
        boolean on = newMode != DiscretePhaseControl.Mode.OFF;

        // activate/de-activate phase control equation
        equationSystem.getEquation(phaseControl.getControlled().getNum(), AcEquationType.BRANCH_TARGET_P)
                .orElseThrow()
                .setActive(on);

        // de-activate/activate constant A1 equation
        equationSystem.getEquation(phaseControl.getController().getNum(), AcEquationType.BRANCH_TARGET_ALPHA1)
                .orElseThrow()
                .setActive(!on);
    }

    @Override
    public void onDiscretePhaseControlModeChange(DiscretePhaseControl phaseControl, DiscretePhaseControl.Mode oldMode, DiscretePhaseControl.Mode newMode) {
        updateDiscretePhaseControl(phaseControl, newMode);
    }

    @Override
    public void onDiscreteVoltageControlModeChange(DiscreteVoltageControl voltageControl, DiscreteVoltageControl.Mode newMode) {
        if (voltageControl instanceof TransformerVoltageControl) {
            AcEquationSystem.updateTransformerVoltageControlEquations((TransformerVoltageControl) voltageControl, equationSystem);
        } else if (voltageControl instanceof ShuntVoltageControl) {
            AcEquationSystem.updateShuntVoltageControlEquations((ShuntVoltageControl) voltageControl, equationSystem);
        }
    }
}
