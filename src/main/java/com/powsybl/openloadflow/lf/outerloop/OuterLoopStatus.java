/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public enum OuterLoopStatus {
    STABLE,
    UNSTABLE,
    FAILED,
    FULL_STABLE; // No need to restart the loops even if NR has run.

    public boolean isStable() {
        return this == STABLE || this == FULL_STABLE;
    }
}
