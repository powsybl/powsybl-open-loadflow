/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.network.AllowedDirection;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class IncrementalContextData {

    static final class ControllerContext {

        private final MutableInt directionChangeCount = new MutableInt();

        private AllowedDirection allowedDirection = AllowedDirection.BOTH;

        MutableInt getDirectionChangeCount() {
            return directionChangeCount;
        }

        AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        void setAllowedDirection(AllowedDirection allowedDirection) {
            this.allowedDirection = Objects.requireNonNull(allowedDirection);
        }
    }

    private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

    Map<String, ControllerContext> getControllersContexts() {
        return controllersContexts;
    }
}
