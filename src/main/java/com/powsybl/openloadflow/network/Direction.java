/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public enum Direction {
    INCREASE(AllowedDirection.INCREASE),
    DECREASE(AllowedDirection.DECREASE);

    private final AllowedDirection allowedDirection;

    Direction(AllowedDirection allowedDirection) {
        this.allowedDirection = allowedDirection;
    }

    public AllowedDirection getAllowedDirection() {
        return allowedDirection;
    }
}
