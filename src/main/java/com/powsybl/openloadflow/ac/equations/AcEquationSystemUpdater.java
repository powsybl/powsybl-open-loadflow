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

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemUpdater extends AbstractEquationSystemUpdater<AcVariableType, AcEquationType> {

    private final AcEquationSystemCreationParameters parameters;

    public AcEquationSystemUpdater(EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                   AcEquationSystemCreationParameters parameters) {
        super(equationSystem, LoadFlowModel.AC);
        this.parameters = Objects.requireNonNull(parameters);
    }

    private void updateVoltageControls(LfBus bus) {
        LfZeroImpedanceNetwork zn = bus.getZeroImpedanceNetwork(loadFlowModel);
        if (zn == null) {
            bus.getGeneratorVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateGeneratorVoltageControl(voltageControl.getMainVoltageControl(), equationSystem));
            bus.getTransformerVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateTransformerVoltageControlEquations(voltageControl.getMainVoltageControl(), equationSystem));
            bus.getShuntVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateShuntVoltageControlEquations(voltageControl.getMainVoltageControl(), equationSystem));
        } else {
            for (LfBus zb : zn.getGraph().vertexSet()) {
                zb.getGeneratorVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateGeneratorVoltageControl(voltageControl.getMainVoltageControl(), equationSystem));
                zb.getTransformerVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateTransformerVoltageControlEquations(voltageControl.getMainVoltageControl(), equationSystem));
                zb.getShuntVoltageControl().ifPresent(voltageControl -> AcEquationSystemCreator.updateShuntVoltageControlEquations(voltageControl.getMainVoltageControl(), equationSystem));
            }
        }
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
                bus.getGeneratorVoltageControl().ifPresent(vc -> updateVoltageControls(vc.getControlledBus()));
                bus.getTransformerVoltageControl().ifPresent(vc -> updateVoltageControls(vc.getControlledBus()));
                bus.getShuntVoltageControl().ifPresent(vc -> updateVoltageControls(vc.getControlledBus()));
                bus.getReactivePowerControl().ifPresent(reactivePowerControl -> AcEquationSystemCreator.updateReactivePowerControlBranchEquations(reactivePowerControl, equationSystem));
                break;
            case BRANCH:
                LfBranch branch = (LfBranch) element;
                branch.getVoltageControl().ifPresent(vc -> updateVoltageControls(vc.getControlledBus()));
                branch.getPhaseControl().ifPresent(phaseControl -> AcEquationSystemCreator.updateTransformerPhaseControlEquations(phaseControl, equationSystem));
                branch.getReactivePowerControl().ifPresent(reactivePowerControl -> AcEquationSystemCreator.updateReactivePowerControlBranchEquations(reactivePowerControl, equationSystem));
                break;
            case SHUNT_COMPENSATOR:
                LfShunt shunt = (LfShunt) element;
                shunt.getVoltageControl().ifPresent(vc -> updateVoltageControls(vc.getControlledBus()));
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
                    .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .ifPresent(voltageControl -> AcEquationSystemCreator.recreateReactivePowerDistributionEquations(voltageControl, equationSystem, parameters));
            bus.getTransformerVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .ifPresent(voltageControl -> AcEquationSystemCreator.recreateR1DistributionEquations(voltageControl, equationSystem));
            bus.getShuntVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .ifPresent(voltageControl -> AcEquationSystemCreator.recreateShuntSusceptanceDistributionEquations(voltageControl, equationSystem));
        }
    }

    @Override
    public void onZeroImpedanceNetworkSplit(LfZeroImpedanceNetwork initialNetwork, List<LfZeroImpedanceNetwork> splitNetworks, LoadFlowModel loadFlowModel) {
        if (loadFlowModel == LoadFlowModel.AC) {
            // TODO
            // only recreate distribution equations if controllers buses are redistributed on the different
            // split networks (should be a rare case) and not only ate the end on only one of the split network
            for (LfZeroImpedanceNetwork splitNetwork : splitNetworks) {
                recreateDistributionEquations(splitNetwork);
            }
        }
    }

    @Override
    public void onZeroImpedanceNetworkMerge(LfZeroImpedanceNetwork network1, LfZeroImpedanceNetwork network2, LfZeroImpedanceNetwork mergedNetwork, LoadFlowModel loadFlowModel) {
        if (loadFlowModel == LoadFlowModel.AC) {
            // TODO
            // only recreate distribution equations if controllers buses are merged (should be a rare case)
            // so we have to check here that controllers were spread over network1 and network2 and were not
            // already only on network1 or network2
            recreateDistributionEquations(mergedNetwork);
        }
    }
}
