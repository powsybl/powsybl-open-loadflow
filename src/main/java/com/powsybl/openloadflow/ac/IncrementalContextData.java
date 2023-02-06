/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.network.AllowedDirection;
import com.powsybl.openloadflow.network.Direction;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class IncrementalContextData {

    static final class ControllerContext {

        private final int maxDirectionChange;

        ControllerContext(int maxDirectionChange) {
            this.maxDirectionChange = maxDirectionChange;
        }

        private final MutableInt directionChangeCount = new MutableInt();

        private AllowedDirection allowedDirection = AllowedDirection.BOTH;

        MutableInt getDirectionChangeCount() {
            return directionChangeCount;
        }

        AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        void updateAllowedDirection(Direction direction) {
            if (directionChangeCount.getValue() <= maxDirectionChange) {
                if (!allowedDirection.equals(direction.getAllowedDirection())) {
                    // both vs increase or decrease
                    // increase vs decrease
                    // decrease vs increase
                    directionChangeCount.increment();
                }
                allowedDirection = direction.getAllowedDirection();
            }
        }
    }

    private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

    Map<String, ControllerContext> getControllersContexts() {
        return controllersContexts;
    }
}
