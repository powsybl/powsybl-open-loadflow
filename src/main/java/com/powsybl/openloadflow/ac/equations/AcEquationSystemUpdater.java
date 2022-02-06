/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
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
            equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V_WITH_SLOPE)
                    .orElseThrow()
                    .setActive(firstControllerBus.isVoltageControlEnabled());
        } else {
            if (voltageControl.isVoltageControlLocal()) {
                equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                        .orElseThrow()
                        .setActive(controlledBus.isVoltageControlEnabled());
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

    @Override
    public void onTransformerPhaseControlChange(LfBranch branch, boolean phaseControlEnabled) {
        AcEquationSystem.updateTransformerPhaseControlEquations(branch.getDiscretePhaseControl().orElseThrow(), equationSystem);
    }

    @Override
    public void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled) {
        AcEquationSystem.updateTransformerVoltageControlEquations(controllerBranch.getVoltageControl().orElseThrow(), equationSystem);
    }

    @Override
    public void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled) {
        AcEquationSystem.updateShuntVoltageControlEquations(controllerShunt.getVoltageControl().orElseThrow(), equationSystem);
    }

    private void updateElementEquations(LfElement element, boolean enable) {
        // update all equations related to the element
        for (var equation : equationSystem.getEquations(element.getType(), element.getNum())) {
            if (equation.isActive() != enable) {
                equation.setActive(enable);
            }
        }

        // update all equation terms related to the element
        for (var equationTerm : equationSystem.getEquationTerms(element.getType(), element.getNum())) {
            if (equationTerm.isActive() != enable) {
                equationTerm.setActive(enable);
            }
        }
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        switch (element.getType()) {
            case BUS:
                LfBus bus = (LfBus) element;
                if (disabled && bus.isSlack()) {
                    throw new PowsyblException("Slack bus '" + bus.getId() + "' disabling is not supported");
                }
                bus.getVoltageControl().ifPresent(this::updateVoltageControl);
                bus.getTransformerVoltageControl().ifPresent(voltageControl -> AcEquationSystem.updateTransformerVoltageControlEquations(voltageControl, equationSystem));
                bus.getShuntVoltageControl().ifPresent(voltageControl -> AcEquationSystem.updateShuntVoltageControlEquations(voltageControl, equationSystem));
                break;
            case BRANCH:
                LfBranch branch = (LfBranch) element;
                branch.getVoltageControl().ifPresent(voltageControl -> AcEquationSystem.updateTransformerVoltageControlEquations(voltageControl, equationSystem));
                branch.getDiscretePhaseControl().ifPresent(phaseControl -> AcEquationSystem.updateTransformerPhaseControlEquations(phaseControl, equationSystem));
                break;
            case SHUNT_COMPENSATOR:
                LfShunt shunt = (LfShunt) element;
                shunt.getVoltageControl().ifPresent(voltageControl -> AcEquationSystem.updateShuntVoltageControlEquations(voltageControl, equationSystem));
                break;
            default:
                throw new IllegalStateException("Unknown element type: " + element.getType());
        }
    }
}
