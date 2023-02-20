/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfNetworkListener implements LfNetworkListener {

    @Override
    public void onGeneratorVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
        // empty
    }

    @Override
    public void onGeneratorVoltageControlTargetChange(GeneratorVoltageControl control, double newTargetVoltage) {
        // empty
    }

    @Override
    public void onTransformerPhaseControlChange(LfBranch controllerBranch, boolean newPhaseControlEnabled) {
        // empty
    }

    @Override
    public void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled) {
        // empty
    }

    @Override
    public void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled) {
        // empty
    }

    @Override
    public void onLoadActivePowerTargetChange(LfBus bus, double oldLoadTargetP, double newLoadTargetP) {
        // empty
    }

    @Override
    public void onLoadReactivePowerTargetChange(LfBus bus, double oldLoadTargetQ, double newLoadTargetQ) {
        // empty
    }

    @Override
    public void onGenerationActivePowerTargetChange(LfGenerator generator, double oldGenerationTargetP, double newGenerationTargetP) {
        // empty
    }

    @Override
    public void onGenerationReactivePowerTargetChange(LfBus bus, double oldGenerationTargetQ, double newGenerationTargetQ) {
        // empty
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        // empty
    }

    @Override
    public void onTapPositionChange(LfBranch branch, int oldPosition, int newPosition) {
        // empty
    }

    @Override
    public void onShuntSusceptanceChange(LfShunt shunt, double b) {
        // empty
    }

    @Override
    public void onZeroImpedanceNetworkSpanningTreeChange(LfBranch branch, boolean dc, boolean spanningTree) {
        // empty
    }
}
