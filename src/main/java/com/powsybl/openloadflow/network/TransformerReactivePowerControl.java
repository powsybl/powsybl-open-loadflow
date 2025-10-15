/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.TwoSides;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class TransformerReactivePowerControl extends ReactivePowerControl {

    private final LfBranch controllerBranch;
    private final Double targetDeadband;

    public TransformerReactivePowerControl(LfBranch controlledBranch, TwoSides controlledSide, LfBranch controllerBranch, double targetValue, double targetDeadband) {
        super(controlledBranch, controlledSide, targetValue);
        this.targetDeadband = Objects.requireNonNull(targetDeadband);
        this.controllerBranch = Objects.requireNonNull(controllerBranch);
    }

    public Optional<Double> getTargetDeadband() {
        return Optional.ofNullable(targetDeadband);
    }

    public LfBranch getControllerBranch() {
        return controllerBranch;
    }
}
