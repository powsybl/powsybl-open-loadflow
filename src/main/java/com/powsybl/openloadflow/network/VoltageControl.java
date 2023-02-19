/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageControl extends Control {

    protected final LfBus controlledBus;

    protected VoltageControl(double targetValue, LfBus controlledBus) {
        super(targetValue);
        this.controlledBus = Objects.requireNonNull(controlledBus);
    }

    public LfBus getControlledBus() {
        return controlledBus;
    }
}
