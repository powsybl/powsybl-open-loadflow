/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetworkListenerTracer implements LfNetworkListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetworkListenerTracer.class);

    private final LfNetworkListener delegate;

    protected LfNetworkListenerTracer(LfNetworkListener delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public static LfNetworkListener trace(LfNetworkListener listener) {
        Objects.requireNonNull(listener);
        if (LOGGER.isTraceEnabled()) {
            return new LfNetworkListenerTracer(listener);
        }
        return listener;
    }

    @Override
    public void onGeneratorVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        LOGGER.trace("onGeneratorVoltageControlChange(controllerBusId='{}', newVoltageControllerEnabled={})",
                controllerBus.getId(), newVoltageControllerEnabled);
        delegate.onGeneratorVoltageControlChange(controllerBus, newVoltageControllerEnabled);
    }

    @Override
    public void onGeneratorVoltageControlTargetChange(GeneratorVoltageControl control, double newTargetVoltage) {
        LOGGER.trace("onGeneratorVoltageControlTargetChange(controlledBusId='{}', newTargetVoltage={})",
                control.getControlledBus(), newTargetVoltage);
        delegate.onGeneratorVoltageControlTargetChange(control, newTargetVoltage);
    }

    @Override
    public void onTransformerPhaseControlChange(LfBranch controllerBranch, boolean newPhaseControlEnabled) {
        LOGGER.trace("onTransformerPhaseControlChange(controllerBranchId='{}', newPhaseControlEnabled={})",
                controllerBranch.getId(), newPhaseControlEnabled);
        delegate.onTransformerPhaseControlChange(controllerBranch, newPhaseControlEnabled);
    }

    @Override
    public void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled) {
        LOGGER.trace("onTransformerVoltageControlChange(controllerBranchId='{}', newVoltageControllerEnabled={})",
                controllerBranch.getId(), newVoltageControllerEnabled);
        delegate.onTransformerVoltageControlChange(controllerBranch, newVoltageControllerEnabled);
    }

    @Override
    public void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled) {
        LOGGER.trace("onShuntVoltageControlChange(controllerShuntId={}, newVoltageControllerEnabled={})",
                controllerShunt.getId(), newVoltageControllerEnabled);
        delegate.onShuntVoltageControlChange(controllerShunt, newVoltageControllerEnabled);
    }

    @Override
    public void onLoadActivePowerTargetChange(LfLoad load, double oldLoadTargetP, double newLoadTargetP) {
        LOGGER.trace("onLoadActivePowerTargetChange(loadId='{}', oldLoadTargetP={}, newLoadTargetP={})",
                load.getId(), oldLoadTargetP, newLoadTargetP);
        delegate.onLoadActivePowerTargetChange(load, oldLoadTargetP, newLoadTargetP);
    }

    @Override
    public void onLoadReactivePowerTargetChange(LfLoad load, double oldLoadTargetQ, double newLoadTargetQ) {
        LOGGER.trace("onLoadReactivePowerTargetChange(busId='{}', oldLoadTargetQ={}, newLoadTargetQ={})",
                load.getId(), oldLoadTargetQ, newLoadTargetQ);
        delegate.onLoadReactivePowerTargetChange(load, oldLoadTargetQ, newLoadTargetQ);
    }

    @Override
    public void onGenerationActivePowerTargetChange(LfGenerator generator, double oldGenerationTargetP, double newGenerationTargetP) {
        LOGGER.trace("onGenerationActivePowerTargetChange(generatorId='{}', oldGenerationTargetP={}, newGenerationTargetP={})",
                generator.getId(), oldGenerationTargetP, newGenerationTargetP);
        delegate.onGenerationActivePowerTargetChange(generator, oldGenerationTargetP, newGenerationTargetP);
    }

    @Override
    public void onGenerationReactivePowerTargetChange(LfBus bus, double oldGenerationTargetQ, double newGenerationTargetQ) {
        LOGGER.trace("onGenerationReactivePowerTargetChange(busId='{}', oldGenerationTargetQ={}, newGenerationTargetQ={})",
                bus.getId(), oldGenerationTargetQ, newGenerationTargetQ);
        delegate.onGenerationReactivePowerTargetChange(bus, oldGenerationTargetQ, newGenerationTargetQ);
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        LOGGER.trace("onDisableChange(elementType={}, elementId='{}', disabled={})", element.getType(), element.getId(), disabled);
        delegate.onDisableChange(element, disabled);
    }

    @Override
    public void onTapPositionChange(LfBranch branch, int oldPosition, int newPosition) {
        LOGGER.trace("onTapPositionChange(branchId='{}', oldPosition={}, newPosition={})",
                branch.getId(), oldPosition, newPosition);
        delegate.onTapPositionChange(branch, oldPosition, newPosition);
    }

    @Override
    public void onShuntSusceptanceChange(LfShunt shunt, double b) {
        LOGGER.trace("onShuntSusceptanceChange(shuntId='{}', b={})", shunt.getId(), b);
        delegate.onShuntSusceptanceChange(shunt, b);
    }

    @Override
    public void onZeroImpedanceNetworkSpanningTreeChange(LfBranch branch, LoadFlowModel loadFlowModel, boolean spanningTree) {
        LOGGER.trace("onZeroImpedanceNetworkSpanningTreeChange(branchId='{}', loadFlowModel={}, spanningTree={})",
                branch, loadFlowModel, spanningTree);
        delegate.onZeroImpedanceNetworkSpanningTreeChange(branch, loadFlowModel, spanningTree);
    }

    @Override
    public void onZeroImpedanceNetworkSplit(LfZeroImpedanceNetwork initialNetwork, List<LfZeroImpedanceNetwork> splitNetworks, LoadFlowModel loadFlowModel) {
        LOGGER.trace("onZeroImpedanceNetworkSplit(initialNetwork={}, splitNetworks={}, loadFlowModel={})", initialNetwork, splitNetworks, loadFlowModel);
        delegate.onZeroImpedanceNetworkSplit(initialNetwork, splitNetworks, loadFlowModel);
    }

    @Override
    public void onZeroImpedanceNetworkMerge(LfZeroImpedanceNetwork network1, LfZeroImpedanceNetwork network2, LfZeroImpedanceNetwork mergedNetwork, LoadFlowModel loadFlowModel) {
        LOGGER.trace("onZeroImpedanceNetworkMerge(network1={}, network2={}, mergedNetwork={}, loadFlowModel={})", network1, network2, mergedNetwork, loadFlowModel);
        delegate.onZeroImpedanceNetworkMerge(network1, network2, mergedNetwork, loadFlowModel);
    }
}
