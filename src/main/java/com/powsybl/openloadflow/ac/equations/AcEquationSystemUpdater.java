/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.*;
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

    public AcEquationSystemUpdater(EquationSystem equationSystem, VariableSet variableSet) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.variableSet = Objects.requireNonNull(variableSet);
    }

    public static void updateControlledBus(VoltageControl voltageControl, EquationSystem equationSystem, VariableSet variableSet) {

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
            AcEquationSystem.createReactivePowerDistributionEquations(equationSystem, variableSet, controllerBusesWithVoltageControlOn);
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
        if (newMode == DiscretePhaseControl.Mode.OFF) {
            // de-activate a1 variable
            Variable a1 = variableSet.getVariable(phaseControl.getController().getNum(), VariableType.BRANCH_ALPHA1);
            a1.setActive(false);

            // de-activate phase control equation
            Equation t = equationSystem.createEquation(phaseControl.getControlled().getNum(), EquationType.BRANCH_P);
            t.setActive(false);
        } else {
            throw new UnsupportedOperationException("TODO");
        }
    }

    @Override
    public void onVoltageControlModeChange(DiscreteVoltageControl voltageControl, DiscreteVoltageControl.Mode oldMode, DiscreteVoltageControl.Mode newMode) {
        if (newMode == DiscreteVoltageControl.Mode.OFF) {
            LfBus bus = voltageControl.getControlled();

            // de-activate transformer voltage control equation
            Equation t = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V);
            t.setActive(false);

            for (LfBranch controllerBranch : bus.getDiscreteVoltageControl().getControllers()) {
                // de-activate r1 variable
                Variable r1 = variableSet.getVariable(controllerBranch.getNum(), VariableType.BRANCH_RHO1);
                r1.setActive(false);

                // clean transformer distribution equations
                equationSystem.removeEquation(controllerBranch.getNum(), EquationType.ZERO_RHO1);
            }
        } else {
            throw new UnsupportedOperationException("TODO");
        }
    }
}
