/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Branch;

import java.util.*;

/**
 * @author Bertrand Rix <bertrand.rix at artelys.com>
 */
public class ReactivePowerControl {

    private final LfBranch controlledBranch;

    private final Branch.Side controlledSide;

    private final LfBus controller;

    private final double targetValue;

    public ReactivePowerControl(LfBranch controlledBranch, Branch.Side controlledSide, LfBus controller, double targetValue) {
        this.controlledBranch = controlledBranch;
        this.targetValue = targetValue;
        this.controlledSide = controlledSide;
        this.controller = controller;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public LfBranch getControlledBranch() {
        return controlledBranch;
    }

    public Branch.Side getControlledSide() {
        return controlledSide;
    }

    public LfBus getControllerBus() {
        return controller;
    }
}
