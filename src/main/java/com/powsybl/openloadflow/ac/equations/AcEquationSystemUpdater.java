/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Equation;
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
        // ensure reactive keys are up-to-date
        voltageControl.updateReactiveKeys();

        LfBus controlledBus = voltageControl.getControlledBus();
        Set<LfBus> controllerBuses = voltageControl.getControllerBuses();

        LfBus firstControllerBus = controllerBuses.iterator().next();
        if (firstControllerBus.hasGeneratorsWithSlope()) {
            // we only support one controlling static var compensator without any other controlling generators
            // we don't support controller bus that wants to control back voltage with slope.
            if (!firstControllerBus.isVoltageControllerEnabled()) {
                equationSystem.createEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V_WITH_SLOPE).setActive(false);
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
        Equation<AcVariableType, AcEquationType> qEq = equationSystem.createEquation(controllerBus.getNum(), AcEquationType.BUS_TARGET_Q);
        qEq.setActive(!newVoltageControllerEnabled);
        updateVoltageControl(controllerBus.getVoltageControl().orElseThrow());
    }

    @Override
    public void onVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        updateVoltageControl(controllerBus, newVoltageControllerEnabled);
    }

    private void updateDiscretePhaseControl(DiscretePhaseControl phaseControl, DiscretePhaseControl.Mode newMode) {
        boolean on = newMode != DiscretePhaseControl.Mode.OFF;

        // activate/de-activate phase control equation
        equationSystem.createEquation(phaseControl.getControlled().getNum(), AcEquationType.BRANCH_TARGET_P)
                .setActive(on);

        // de-activate/activate constant A1 equation
        equationSystem.createEquation(phaseControl.getController().getNum(), AcEquationType.BRANCH_TARGET_ALPHA1)
                .setActive(!on);
    }

    @Override
    public void onDiscretePhaseControlModeChange(DiscretePhaseControl phaseControl, DiscretePhaseControl.Mode oldMode, DiscretePhaseControl.Mode newMode) {
        updateDiscretePhaseControl(phaseControl, newMode);
    }

    private void updateDiscreteVoltageControl(DiscreteVoltageControl voltageControl, DiscreteVoltageControl.Mode newMode) {
        LfBus controlledBus = voltageControl.getControlled();
        if (newMode == DiscreteVoltageControl.Mode.OFF) {

            // de-activate transformer voltage control equation
            equationSystem.createEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                    .setActive(false);

            for (LfBranch controllerBranch : voltageControl.getControllers()) {
                // activate constant R1 equation
                equationSystem.createEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_RHO1)
                        .setActive(true);

                // clean transformer distribution equations
                equationSystem.removeEquation(controllerBranch.getNum(), AcEquationType.DISTR_RHO);
            }
        } else { // newMode == DiscreteVoltageControl.Mode.VOLTAGE

            // activate transformer voltage control equation
            equationSystem.createEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                    .setActive(true);

            // add transformer distribution equations
            AcEquationSystem.createR1DistributionEquations(voltageControl.getControllers(), equationSystem);

            for (LfBranch controllerBranch : voltageControl.getControllers()) {
                // de-activate constant R1 equation
                equationSystem.createEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_RHO1)
                        .setActive(false);
            }
        }
    }

    @Override
    public void onDiscreteVoltageControlModeChange(DiscreteVoltageControl voltageControl, DiscreteVoltageControl.Mode oldMode, DiscreteVoltageControl.Mode newMode) {
        updateDiscreteVoltageControl(voltageControl, newMode);
    }
}
