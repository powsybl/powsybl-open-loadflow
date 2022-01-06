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

    void onDiscretePhaseControlModeChange(DiscretePhaseControl phaseControl, DiscretePhaseControl.Mode oldMode, DiscretePhaseControl.Mode newMode);

    void onDiscreteVoltageControlModeChange(DiscreteVoltageControl voltageControl, DiscreteVoltageControl.Mode newMode);

    void onLoadActivePowerTargetChange(LfBus bus, double oldLoadTargetP, double newLoadTargetP);

    void onLoadReactivePowerTargetChange(LfBus bus, double oldLoadTargetQ, double newLoadTargetQ);

    void onGenerationActivePowerTargetChange(LfGenerator generator, double oldGenerationTargetP, double newGenerationTargetP);

    void onGenerationReactivePowerTargetChange(LfBus bus, double oldGenerationTargetQ, double newGenerationTargetQ);

    void onDiscretePhaseControlTapPositionChange(PiModel piModel, int oldPosition, int newPosition);

    void onDisableChange(LfElement element, boolean disabled);
}
