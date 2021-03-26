/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemUpdater implements LfNetworkListener {

    private final EquationSystem equationSystem;

    private final VariableSet variableSet;

    private final AcEquationSystemCreationParameters creationParameters;

    public AcEquationSystemUpdater(EquationSystem equationSystem, VariableSet variableSet, AcEquationSystemCreationParameters creationParameters) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.variableSet = Objects.requireNonNull(variableSet);
        this.creationParameters = Objects.requireNonNull(creationParameters);
    }

    private void updateControlledBus(VoltageControl voltageControl, EquationSystem equationSystem, VariableSet variableSet) {

        LfBus controlledBus = voltageControl.getControlledBus();
        Set<LfBus> controllerBuses = voltageControl.getControllerBuses();

        // clean reactive power distribution equations
        controllerBuses.forEach(b -> equationSystem.removeEquation(b.getNum(), EquationType.ZERO_Q));

        // controlled bus has a voltage equation only if one of the controller bus has voltage control on
        List<LfBus> controllerBusesWithVoltageControlOn = controllerBuses.stream()
            .filter(LfBus::isVoltageControllerEnabled)
            .collect(Collectors.toList());
        equationSystem.createEquation(controlledBus.getNum(), EquationType.BUS_V).setActive(!controllerBusesWithVoltageControlOn.isEmpty());

        // create reactive power equations on controller buses that have voltage control on
        if (!controllerBusesWithVoltageControlOn.isEmpty()) {
            AcEquationSystem.createReactivePowerDistributionEquations(equationSystem, variableSet, creationParameters, controllerBusesWithVoltageControlOn);
        }
    }

    @Override
    public void onVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        if (newVoltageControllerEnabled) { // switch PQ/PV
            Equation qEq = equationSystem.createEquation(controllerBus.getNum(), EquationType.BUS_Q);
            qEq.setActive(false);

            Optional<VoltageControl> vc = controllerBus.getVoltageControl();
            if (vc.isPresent() && controllerBus.isVoltageControllerEnabled()) {
                updateControlledBus(vc.get(), equationSystem, variableSet);
            }
        } else { // switch PV/PQ
            Equation qEq = equationSystem.createEquation(controllerBus.getNum(), EquationType.BUS_Q);
            qEq.setActive(true);

            Optional<VoltageControl> vc = controllerBus.getVoltageControl();
            if (vc.isPresent() && controllerBus.hasVoltageControllerCapability()) {
                updateControlledBus(vc.get(), equationSystem, variableSet);
            }
        }
    }

    @Override
    public void onPhaseControlModeChange(DiscretePhaseControl phaseControl, DiscretePhaseControl.Mode oldMode, DiscretePhaseControl.Mode newMode) {
        boolean on = newMode != DiscretePhaseControl.Mode.OFF;

        // activate/de-activate phase control equation
        equationSystem.createEquation(phaseControl.getControlled().getNum(), EquationType.BRANCH_P)
                .setActive(on);

        // de-activate/activate constant A1 equation
        equationSystem.createEquation(phaseControl.getController().getNum(), EquationType.BRANCH_ALPHA1)
                .setActive(!on);
    }

    @Override
    public void onVoltageControlModeChange(DiscreteVoltageControl voltageControl, DiscreteVoltageControl.Mode oldMode, DiscreteVoltageControl.Mode newMode) {
        if (newMode == DiscreteVoltageControl.Mode.OFF) {
            if (oldMode == DiscreteVoltageControl.Mode.VOLTAGE_TRANSFORMER) {
                LfBus bus = voltageControl.getControlled();

                // de-activate transformer voltage control equation
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_V)
                        .setActive(false);

                for (LfBranch controllerBranch : bus.getDiscreteVoltageControl().getControllerBranches()) {
                    // activate constant R1 equation
                    equationSystem.createEquation(controllerBranch.getNum(), EquationType.BRANCH_RHO1)
                            .setActive(true);

                    // clean transformer distribution equations
                    equationSystem.removeEquation(controllerBranch.getNum(), EquationType.ZERO_RHO1);
                }
            } else if (oldMode == DiscreteVoltageControl.Mode.VOLTAGE_SHUNT) {
                LfBus bus = voltageControl.getControlled();

                // de-activate transformer voltage control equation
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_V)
                        .setActive(false);

                for (LfBus controllerBus : bus.getDiscreteVoltageControl().getControllerBuses()) {
                    List<LfShunt> controllerShunts = controllerBus.getShunts().stream()
                            .filter(LfShunt::hasVoltageControl)
                            .collect(Collectors.toList());
                    LfShunt firstControllerShunt = controllerShunts.get(0);
                    // activate constant B equation
                    equationSystem.createEquation(firstControllerShunt.getNum(), EquationType.SHUNT_B)
                            .setActive(true);
                    for (LfShunt shunt : controllerShunts) {
                        shunt.setVoltageControl(false);
                    }
                    // TODO: clean shunt distribution equations in case of remote control.
                }
            } else {
                throw new UnsupportedOperationException("Not supported mode");
            }
        } else {
            throw new UnsupportedOperationException("TODO");
        }
    }
}
