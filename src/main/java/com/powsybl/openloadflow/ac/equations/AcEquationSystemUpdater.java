/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
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
public class AcEquationSystemUpdater extends AbstractLfNetworkListener {

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private final VariableSet<AcVariableType> variableSet;

    private final AcEquationSystemCreationParameters creationParameters;

    private final LfNetworkParameters networkParameters;

    public AcEquationSystemUpdater(EquationSystem<AcVariableType, AcEquationType> equationSystem, VariableSet<AcVariableType> variableSet,
                                   AcEquationSystemCreationParameters creationParameters, LfNetworkParameters networkParameters) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.variableSet = Objects.requireNonNull(variableSet);
        this.creationParameters = Objects.requireNonNull(creationParameters);
        this.networkParameters = Objects.requireNonNull(networkParameters);
    }

    private void updateControlledBus(VoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem, VariableSet<AcVariableType> variableSet) {

        LfBus controlledBus = voltageControl.getControlledBus();
        Set<LfBus> controllerBuses = voltageControl.getControllerBuses();

        LfBus firstControllerBus = controllerBuses.iterator().next();
        if (firstControllerBus.hasGeneratorsWithSlope()) {
            // we only support one controlling static var compensator without any other controlling generators
            // we don't support controller bus that wants to control back voltage with slope.
            if (!firstControllerBus.isVoltageControllerEnabled()) {
                equationSystem.createEquation(controlledBus.getNum(), AcEquationType.BUS_V_SLOPE).setActive(false);
            }
        } else {
            // controlled bus has a voltage equation only if one of the controller bus has voltage control on
            List<LfBus> controllerBusesWithVoltageControlOn = controllerBuses.stream()
                    .filter(LfBus::isVoltageControllerEnabled)
                    .collect(Collectors.toList());
            // clean reactive power distribution equations
            controllerBuses.forEach(b -> equationSystem.removeEquation(b.getNum(), AcEquationType.ZERO_Q));
            // is there one static var compensator with a non-zero slope
            equationSystem.createEquation(controlledBus.getNum(), AcEquationType.BUS_V).setActive(!controllerBusesWithVoltageControlOn.isEmpty());
            // create reactive power equations on controller buses that have voltage control on
            if (!controllerBusesWithVoltageControlOn.isEmpty()) {
                AcEquationSystem.createReactivePowerDistributionEquations(controllerBusesWithVoltageControlOn, networkParameters, equationSystem, variableSet, creationParameters);
            }
        }
    }

    @Override
    public void onVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        if (newVoltageControllerEnabled) { // switch PQ/PV
            Equation<AcVariableType, AcEquationType> qEq = equationSystem.createEquation(controllerBus.getNum(), AcEquationType.BUS_Q);
            qEq.setActive(false);

            Optional<VoltageControl> vc = controllerBus.getVoltageControl();
            if (vc.isPresent() && controllerBus.isVoltageControllerEnabled()) {
                updateControlledBus(vc.get(), equationSystem, variableSet);
            }
        } else { // switch PV/PQ
            Equation<AcVariableType, AcEquationType> qEq = equationSystem.createEquation(controllerBus.getNum(), AcEquationType.BUS_Q);
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
        equationSystem.createEquation(phaseControl.getControlled().getNum(), AcEquationType.BRANCH_P)
                .setActive(on);

        // de-activate/activate constant A1 equation
        equationSystem.createEquation(phaseControl.getController().getNum(), AcEquationType.BRANCH_ALPHA1)
                .setActive(!on);
    }

    @Override
    public void onVoltageControlModeChange(DiscreteVoltageControl voltageControl, DiscreteVoltageControl.Mode oldMode, DiscreteVoltageControl.Mode newMode) {
        LfBus bus = voltageControl.getControlled();
        if (newMode == DiscreteVoltageControl.Mode.OFF) {

            // de-activate transformer voltage control equation
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_V)
                    .setActive(false);

            for (LfBranch controllerBranch : voltageControl.getControllers()) {
                // activate constant R1 equation
                equationSystem.createEquation(controllerBranch.getNum(), AcEquationType.BRANCH_RHO1)
                        .setActive(true);

                // clean transformer distribution equations
                equationSystem.removeEquation(controllerBranch.getNum(), AcEquationType.ZERO_RHO1);
            }
        } else { // newMode == DiscreteVoltageControl.Mode.VOLTAGE

            // activate transformer voltage control equation
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_V)
                    .setActive(true);

            // add transformer distribution equations
            AcEquationSystem.createR1DistributionEquations(voltageControl.getControllers(), equationSystem, variableSet);

            for (LfBranch controllerBranch : voltageControl.getControllers()) {
                // de-activate constant R1 equation
                equationSystem.createEquation(controllerBranch.getNum(), AcEquationType.BRANCH_RHO1)
                        .setActive(false);
            }
        }
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        switch (element.getType()) {
            case BUS:
                // TODO
                break;
            case BRANCH:
                // TODO
                break;
            case SHUNT_COMPENSATOR:
                throw new IllegalStateException("Shunt compensator disabling is not implemented");
        }
    }
}
