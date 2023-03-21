/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.lf.AbstractEquationSystemUpdater;
import com.powsybl.openloadflow.network.*;

import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator.updateVoltageControlsMergeStatus;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemUpdater extends AbstractEquationSystemUpdater<AcVariableType, AcEquationType> {

    private final AcEquationSystemCreationParameters parameters;

    public AcEquationSystemUpdater(EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                   AcEquationSystemCreationParameters parameters) {
        super(equationSystem, false);
        this.parameters = Objects.requireNonNull(parameters);
    }

    @Override
    public void onGeneratorVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        updateVoltageControls(controllerBus.getGeneratorVoltageControl().orElseThrow().getControlledBus());
        controllerBus.getReactivePowerControl().ifPresent(reactivePowerControl -> AcEquationSystemCreator.updateReactivePowerControlBranchEquations(reactivePowerControl, equationSystem));
    }

    @Override
    public void onTransformerPhaseControlChange(LfBranch controllerBranch, boolean newPhaseControlEnabled) {
        AcEquationSystemCreator.updateTransformerPhaseControlEquations(controllerBranch.getPhaseControl().orElseThrow(), equationSystem);
    }

    @Override
    public void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled) {
        updateVoltageControls(controllerBranch.getVoltageControl().orElseThrow().getControlledBus());
    }

    @Override
    public void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled) {
        updateVoltageControls(controllerShunt.getVoltageControl().orElseThrow().getControlledBus());
    }

    @Override
    protected void updateNonImpedantBranchEquations(LfBranch branch, boolean enable) {
        // depending on the switch status, we activate either v1 = v2, ph1 = ph2 equations
        // or equations that set dummy p and q variable to zero
        equationSystem.getEquation(branch.getNum(), AcEquationType.ZERO_PHI)
                .orElseThrow()
                .setActive(enable);
        equationSystem.getEquation(branch.getNum(), AcEquationType.DUMMY_TARGET_P)
                .orElseThrow()
                .setActive(!enable);

        equationSystem.getEquation(branch.getNum(), AcEquationType.ZERO_V)
                .orElseThrow()
                .setActive(enable);
        equationSystem.getEquation(branch.getNum(), AcEquationType.DUMMY_TARGET_Q)
                .orElseThrow()
                .setActive(!enable);
    }

    private void updateVoltageControls(LfBus bus) {
        bus.getGeneratorVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateGeneratorVoltageControl(voltageControl, equationSystem));
        bus.getTransformerVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateTransformerVoltageControlEquations(voltageControl, equationSystem));
        bus.getShuntVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateShuntVoltageControlEquations(voltageControl, equationSystem));
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        updateElementEquations(element, !disabled);
        switch (element.getType()) {
            case BUS:
                LfBus bus = (LfBus) element;
                checkSlackBus(bus, disabled);
                equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_PHI)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled()));
                equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_P)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled() && !bus.isSlack()));
                // set voltage target equation inactive, various voltage control will set next to the correct value
                equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_V)
                        .orElseThrow()
                        .setActive(false);
                updateVoltageControls(bus);
                bus.getReactivePowerControl().ifPresent(reactivePowerControl -> AcEquationSystemCreator.updateReactivePowerControlBranchEquations(reactivePowerControl, equationSystem));
                break;
            case BRANCH:
                LfBranch branch = (LfBranch) element;
                branch.getVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateTransformerVoltageControlEquations(voltageControl, equationSystem));
                branch.getPhaseControl().ifPresent(phaseControl -> AcEquationSystemCreator.updateTransformerPhaseControlEquations(phaseControl, equationSystem));
                branch.getReactivePowerControl().ifPresent(reactivePowerControl -> AcEquationSystemCreator.updateReactivePowerControlBranchEquations(reactivePowerControl, equationSystem));
                break;
            case SHUNT_COMPENSATOR:
                LfShunt shunt = (LfShunt) element;
                shunt.getVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateShuntVoltageControlEquations(voltageControl, equationSystem));
                break;
            case HVDC:
                // nothing to do
                break;
            default:
                throw new IllegalStateException("Unknown element type: " + element.getType());
        }
    }

    private void recreateDistributionEquations(LfZeroImpedanceNetwork network) {
        for (LfBus bus : network.getGraph().vertexSet()) {
            bus.getGeneratorVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() != VoltageControl.MergeStatus.MERGED_DEPENDENT)
                    .ifPresent(voltageControl -> AcEquationSystemCreator.recreateReactivePowerDistributionEquations(voltageControl, equationSystem, parameters));
            bus.getTransformerVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() != VoltageControl.MergeStatus.MERGED_DEPENDENT)
                    .ifPresent(voltageControl -> {
                        throw new UnsupportedOperationException("TODO");
                    });
            bus.getShuntVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() != VoltageControl.MergeStatus.MERGED_DEPENDENT)
                    .ifPresent(voltageControl -> {
                        throw new UnsupportedOperationException("TODO");
                    });
        }
    }

    @Override
    public void onZeroImpedanceNetworkSplit(LfZeroImpedanceNetwork initialNetwork, List<LfZeroImpedanceNetwork> splitNetworks) {
        for (LfZeroImpedanceNetwork splitNetwork : splitNetworks) {
            updateVoltageControlsMergeStatus(splitNetwork);
        }
        for (LfZeroImpedanceNetwork splitNetwork : splitNetworks) {
            recreateDistributionEquations(splitNetwork);
        }
    }

    @Override
    public void onZeroImpedanceNetworkMerge(LfZeroImpedanceNetwork network1, LfZeroImpedanceNetwork network2, LfZeroImpedanceNetwork mergedNetwork) {
        updateVoltageControlsMergeStatus(mergedNetwork);
        recreateDistributionEquations(mergedNetwork);
    }
}
