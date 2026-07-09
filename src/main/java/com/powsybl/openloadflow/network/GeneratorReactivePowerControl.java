/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.TwoSides;

import java.util.*;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public class GeneratorReactivePowerControl extends ReactivePowerControl implements LfCopyable<GeneratorReactivePowerControl, LfNetwork> {

    private final List<LfBus> controllerBuses = new ArrayList<>();

    public GeneratorReactivePowerControl(LfBranch controlledBranch, TwoSides controlledSide, double targetValue) {
        super(controlledBranch, controlledSide, targetValue);
    }

    @Override
    public GeneratorReactivePowerControl copy(LfNetwork copyNetwork) {
        LfBranch copiedControlled = copyNetwork.getBranchById(getControlledBranch().getId());
        GeneratorReactivePowerControl copiedRc = new GeneratorReactivePowerControl(copiedControlled, getControlledSide(), getTargetValue());
        for (LfBus controllerBus : getControllerBuses()) {
            copiedRc.addControllerBus(copyNetwork.getBusById(controllerBus.getId()));
        }
        return copiedRc;
    }

    public List<LfBus> getControllerBuses() {
        return controllerBuses;
    }

    public void addControllerBus(LfBus controllerBus) {
        controllerBuses.add(Objects.requireNonNull(controllerBus));
        controllerBus.setGeneratorReactivePowerControl(this);
        controllerBus.setGeneratorReactivePowerControlEnabled(true);
    }

    public void updateReactiveKeys() {
        double[] reactiveKeys = createReactiveKeys(controllerBuses, LfGenerator.GeneratorControlType.REMOTE_REACTIVE_POWER);

        // key is 0 only on disabled controllers
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            if (controllerBus.isDisabled() || !controllerBus.isGeneratorReactivePowerControlEnabled()) {
                reactiveKeys[i] = 0d;
            }
        }

        // update bus reactive keys for remote reactive power control
        double reactiveKeysSum = Arrays.stream(reactiveKeys).sum();
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            controllerBus.setRemoteControlReactivePercent(reactiveKeysSum == 0 ? 0 : reactiveKeys[i] / reactiveKeysSum);
        }
    }
}
