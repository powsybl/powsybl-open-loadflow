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
public interface LfNetworkListener {

    void onVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled);

    void onVoltageControlTargetChange(VoltageControl control, double newTargetVoltage);

    void onTransformerPhaseControlChange(LfBranch branch, boolean phaseControlEnabled);

    void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled);

    void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled);

    void onLoadActivePowerTargetChange(LfBus bus, double oldLoadTargetP, double newLoadTargetP);

    void onLoadReactivePowerTargetChange(LfBus bus, double oldLoadTargetQ, double newLoadTargetQ);

    void onGenerationActivePowerTargetChange(LfGenerator generator, double oldGenerationTargetP, double newGenerationTargetP);

    void onGenerationReactivePowerTargetChange(LfBus bus, double oldGenerationTargetQ, double newGenerationTargetQ);

    void onDisableChange(LfElement element, boolean disabled);

    void onTapPositionChange(LfBranch branch, int oldPosition, int newPosition);

    void onShuntSusceptanceChange(LfShunt shunt, double b);

    void onZeroImpedanceNetworkSpanningTreeChange(LfBranch branch, boolean dc, boolean spanningTree);
}
