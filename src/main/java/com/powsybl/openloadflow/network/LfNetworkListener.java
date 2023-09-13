/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfNetworkListener {

    void onGeneratorVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled);

    void onGeneratorVoltageControlTargetChange(GeneratorVoltageControl control, double newTargetVoltage);

    void onTransformerPhaseControlChange(LfBranch controllerBranch, boolean newPhaseControlEnabled);

    void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled);

    void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled);

    void onLoadActivePowerTargetChange(LfLoad load, double oldLoadTargetP, double newLoadTargetP);

    void onLoadReactivePowerTargetChange(LfLoad load, double oldLoadTargetQ, double newLoadTargetQ);

    void onGenerationActivePowerTargetChange(LfGenerator generator, double oldGenerationTargetP, double newGenerationTargetP);

    void onGenerationReactivePowerTargetChange(LfBus bus, double oldGenerationTargetQ, double newGenerationTargetQ);

    void onDisableChange(LfElement element, boolean disabled);

    void onTapPositionChange(LfBranch branch, int oldPosition, int newPosition);

    void onShuntSusceptanceChange(LfShunt shunt, double b);

    void onZeroImpedanceNetworkSpanningTreeChange(LfBranch branch, LoadFlowModel loadFlowModel, boolean spanningTree);

    void onZeroImpedanceNetworkSplit(LfZeroImpedanceNetwork initialNetwork, List<LfZeroImpedanceNetwork> splitNetworks, LoadFlowModel loadFlowModel);

    void onZeroImpedanceNetworkMerge(LfZeroImpedanceNetwork network1, LfZeroImpedanceNetwork network2, LfZeroImpedanceNetwork mergedNetwork, LoadFlowModel loadFlowModel);
}
