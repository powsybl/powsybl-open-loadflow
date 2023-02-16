/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DebugLfNetworkListener implements LfNetworkListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugLfNetworkListener.class);

    private final LfNetworkListener delegate;

    public DebugLfNetworkListener(LfNetworkListener delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void onVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        delegate.onVoltageControlChange(controllerBus, newVoltageControllerEnabled);
    }

    @Override
    public void onVoltageControlTargetChange(VoltageControl control, double newTargetVoltage) {
        delegate.onVoltageControlTargetChange(control, newTargetVoltage);
    }

    @Override
    public void onTransformerPhaseControlChange(LfBranch branch, boolean phaseControlEnabled) {
        delegate.onTransformerPhaseControlChange(branch, phaseControlEnabled);
    }

    @Override
    public void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled) {
        delegate.onTransformerVoltageControlChange(controllerBranch, newVoltageControllerEnabled);
    }

    @Override
    public void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled) {
        delegate.onShuntVoltageControlChange(controllerShunt, newVoltageControllerEnabled);
    }

    @Override
    public void onLoadActivePowerTargetChange(LfBus bus, double oldLoadTargetP, double newLoadTargetP) {
        delegate.onLoadActivePowerTargetChange(bus, oldLoadTargetP, newLoadTargetP);
    }

    @Override
    public void onLoadReactivePowerTargetChange(LfBus bus, double oldLoadTargetQ, double newLoadTargetQ) {
        delegate.onLoadReactivePowerTargetChange(bus, oldLoadTargetQ, newLoadTargetQ);
    }

    @Override
    public void onGenerationActivePowerTargetChange(LfGenerator generator, double oldGenerationTargetP, double newGenerationTargetP) {
        delegate.onGenerationActivePowerTargetChange(generator, oldGenerationTargetP, newGenerationTargetP);
    }

    @Override
    public void onGenerationReactivePowerTargetChange(LfBus bus, double oldGenerationTargetQ, double newGenerationTargetQ) {
        delegate.onGenerationReactivePowerTargetChange(bus, oldGenerationTargetQ, newGenerationTargetQ);
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        LOGGER.debug("onDisableChange(element={}, disabled={})", element, disabled);
        delegate.onDisableChange(element, disabled);
    }

    @Override
    public void onTapPositionChange(LfBranch branch, int oldPosition, int newPosition) {
        delegate.onTapPositionChange(branch, oldPosition, newPosition);
    }

    @Override
    public void onShuntSusceptanceChange(LfShunt shunt, double b) {
        delegate.onShuntSusceptanceChange(shunt, b);
    }

    @Override
    public void onZeroImpedanceNetworkSpanningTreeChange(LfBranch branch, boolean dc, boolean spanningTree) {
        LOGGER.debug("onZeroImpedanceNetworkSpanningTreeChange(branch={}, dc={}, spanningTree={})",
                branch, dc, spanningTree);
        delegate.onZeroImpedanceNetworkSpanningTreeChange(branch, dc, spanningTree);
    }
}
