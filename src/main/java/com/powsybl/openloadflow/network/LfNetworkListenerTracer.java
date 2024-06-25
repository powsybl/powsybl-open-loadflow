/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.TwoSides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
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
    public void onGeneratorReactivePowerControlChange(LfBus controllerBus, boolean newReactiveControllerEnabled) {
        LOGGER.trace("onGeneratorReactivePowerControlChange(controllerBusId='{}', newReactiveControllerEnabled={})",
                controllerBus.getId(), newReactiveControllerEnabled);
        delegate.onGeneratorReactivePowerControlChange(controllerBus, newReactiveControllerEnabled);
    }

    @Override
    public void onTransformerPhaseControlChange(LfBranch controllerBranch, boolean newPhaseControlEnabled) {
        LOGGER.trace("onTransformerPhaseControlChange(controllerBranchId='{}', newPhaseControlEnabled={})",
                controllerBranch.getId(), newPhaseControlEnabled);
        delegate.onTransformerPhaseControlChange(controllerBranch, newPhaseControlEnabled);
    }

    @Override
    public void onTransformerVoltageControlTargetChange(TransformerVoltageControl transformerVoltageControl, double newTargetVoltage) {
        LOGGER.trace("onTransformerVoltageControlTargetChange(controlledBusId='{}', newTargetVoltage={})",
                transformerVoltageControl.getControlledBus().getId(), newTargetVoltage);
        delegate.onTransformerVoltageControlTargetChange(transformerVoltageControl, newTargetVoltage);
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
    public void onLoadActivePowerTargetChange(LfLoad load, double oldTargetP, double newTargetP) {
        LOGGER.trace("onLoadActivePowerTargetChange(loadId='{}', oldTargetP={}, newTargetP={})",
                load.getId(), oldTargetP, newTargetP);
        delegate.onLoadActivePowerTargetChange(load, oldTargetP, newTargetP);
    }

    @Override
    public void onLoadReactivePowerTargetChange(LfLoad load, double oldTargetQ, double newTargetQ) {
        LOGGER.trace("onLoadReactivePowerTargetChange(busId='{}', oldTargetQ={}, newTargetQ={})",
                load.getId(), oldTargetQ, newTargetQ);
        delegate.onLoadReactivePowerTargetChange(load, oldTargetQ, newTargetQ);
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

    @Override
    public void onBranchConnectionStatusChange(LfBranch branch, TwoSides side, boolean connected) {
        LOGGER.trace("onBranchConnectionStatusChange(branch={}, side={}, connected={})", branch, side, connected);
        delegate.onBranchConnectionStatusChange(branch, side, connected);
    }

    @Override
    public void onSlackBusChange(LfBus bus, boolean slack) {
        LOGGER.trace("onSlackBusChange(bus={}, slack={})", bus, slack);
        delegate.onSlackBusChange(bus, slack);
    }

    @Override
    public void onReferenceBusChange(LfBus bus, boolean reference) {
        LOGGER.trace("onReferenceBusChange(bus={}, reference={})", bus, reference);
        delegate.onReferenceBusChange(bus, reference);
    }

    @Override
    public void onHvdcAcEmulationStatusChange(LfHvdc hvdc, LfHvdc.AcEmulationControl.AcEmulationStatus acEmulationStatus) {
        LOGGER.trace("onHvdcAcEmulationStatusChange(hvdc={}, status={})", hvdc, acEmulationStatus);
        delegate.onHvdcAcEmulationStatusChange(hvdc, acEmulationStatus);
    }

    @Override
    public void onHvdcAcEmulationFeedingSideChange(LfHvdc hvdc, TwoSides side) {
        LOGGER.trace("onHvdcAcEmulationFeedingSideChange(hvdc={}, side={})", hvdc, side);
        delegate.onHvdcAcEmulationFeedingSideChange(hvdc, side);
    }
}
