/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public enum ControlTargetPriority {
    GENERATOR(0),
    TRANSFORMER(1),
    SHUNT(2);

    private final int priority;

    ControlTargetPriority(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
