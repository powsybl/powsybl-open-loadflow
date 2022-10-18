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

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DisymAcEquationSystemUpdater extends AbstractLfNetworkListener {

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    public DisymAcEquationSystemUpdater(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
    }

    @Override
    public void onVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        controllerBus.getVoltageControl().ifPresent(voltageControl -> DisymAcEquationSystem.updateGeneratorVoltageControl(voltageControl, equationSystem));
        controllerBus.getReactivePowerControl().ifPresent(reactivePowerControl -> DisymAcEquationSystem.updateReactivePowerControlBranchEquations(reactivePowerControl, equationSystem));
    }

    @Override
    public void onTransformerPhaseControlChange(LfBranch branch, boolean phaseControlEnabled) {
        DisymAcEquationSystem.updateTransformerPhaseControlEquations(branch.getDiscretePhaseControl().orElseThrow(), equationSystem);
    }

    @Override
    public void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled) {
        DisymAcEquationSystem.updateTransformerVoltageControlEquations(controllerBranch.getVoltageControl().orElseThrow(), equationSystem);
    }

    @Override
    public void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled) {
        DisymAcEquationSystem.updateShuntVoltageControlEquations(controllerShunt.getVoltageControl().orElseThrow(), equationSystem);
    }

    private void updateElementEquations(LfElement element, boolean enable) {
        if (element instanceof LfBranch && ((LfBranch) element).isZeroImpedanceBranch(false)) {
            LfBranch branch = (LfBranch) element;
            if (branch.isSpanningTreeEdge()) {
                // depending on the switch status, we activate either v1 = v2, ph1 = ph2 equations
                // or equations that set dummy p and q variable to zero
                equationSystem.getEquation(element.getNum(), AcEquationType.ZERO_PHI)
                        .orElseThrow()
                        .setActive(enable);
                equationSystem.getEquation(element.getNum(), AcEquationType.DUMMY_TARGET_P)
                        .orElseThrow()
                        .setActive(!enable);

                equationSystem.getEquation(element.getNum(), AcEquationType.ZERO_V)
                        .orElseThrow()
                        .setActive(enable);
                equationSystem.getEquation(element.getNum(), AcEquationType.DUMMY_TARGET_Q)
                        .orElseThrow()
                        .setActive(!enable);
            }
        } else {
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
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        updateElementEquations(element, !disabled);
        switch (element.getType()) {
            case BUS:
                LfBus bus = (LfBus) element;
                if (disabled && bus.isSlack()) {
                    throw new PowsyblException("Slack bus '" + bus.getId() + "' disabling is not supported");
                }
                equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_PHI)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled()));
                equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_P)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled() && !bus.isSlack()));
                // set voltage target equation inactive, various voltage control will set next to the correct value
                equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_V)
                        .orElseThrow()
                        .setActive(false);
                bus.getVoltageControl().ifPresent(voltageControl -> DisymAcEquationSystem.updateGeneratorVoltageControl(voltageControl, equationSystem));
                bus.getTransformerVoltageControl().ifPresent(voltageControl -> DisymAcEquationSystem.updateTransformerVoltageControlEquations(voltageControl, equationSystem));
                bus.getShuntVoltageControl().ifPresent(voltageControl -> DisymAcEquationSystem.updateShuntVoltageControlEquations(voltageControl, equationSystem));
                bus.getReactivePowerControl().ifPresent(reactivePowerControl -> DisymAcEquationSystem.updateReactivePowerControlBranchEquations(reactivePowerControl, equationSystem));
                break;
            case BRANCH:
                LfBranch branch = (LfBranch) element;
                branch.getVoltageControl().ifPresent(voltageControl -> DisymAcEquationSystem.updateTransformerVoltageControlEquations(voltageControl, equationSystem));
                branch.getDiscretePhaseControl().ifPresent(phaseControl -> DisymAcEquationSystem.updateTransformerPhaseControlEquations(phaseControl, equationSystem));
                branch.getReactivePowerControl().ifPresent(reactivePowerControl -> DisymAcEquationSystem.updateReactivePowerControlBranchEquations(reactivePowerControl, equationSystem));
                break;
            case SHUNT_COMPENSATOR:
                LfShunt shunt = (LfShunt) element;
                shunt.getVoltageControl().ifPresent(voltageControl -> DisymAcEquationSystem.updateShuntVoltageControlEquations(voltageControl, equationSystem));
                break;
            case HVDC:
                // nothing to do
                break;
            default:
                throw new IllegalStateException("Unknown element type: " + element.getType());
        }
    }
}
