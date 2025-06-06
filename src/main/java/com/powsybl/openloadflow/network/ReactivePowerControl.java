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

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class ReactivePowerControl extends Control {

    private final TwoSides controlledSide;
    private final LfBranch controlledBranch;

    public ReactivePowerControl(LfBranch controlledBranch, TwoSides controlledSide, double targetValue) {
        super(targetValue);
        this.controlledBranch = Objects.requireNonNull(controlledBranch);
        this.controlledSide = Objects.requireNonNull(controlledSide);
    }

    public LfBranch getControlledBranch() {
        return controlledBranch;
    }

    public TwoSides getControlledSide() {
        return controlledSide;
    }

}
