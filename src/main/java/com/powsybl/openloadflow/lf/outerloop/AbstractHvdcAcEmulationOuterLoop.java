/**
 * Copyright (c) 2023-2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractHvdcAcEmulationOuterLoop<V extends Enum<V> & Quantity,
        E extends Enum<E> & Quantity,
        P extends AbstractLoadFlowParameters<P>,
        C extends LoadFlowContext<V, E, P>,
        O extends OuterLoopContext<V, E, P, C>> implements OuterLoop<V, E, P, C, O> {

    protected static final int MAX_MODE_SWITCH = 10;

    protected static final class ContextData {
        private final Map<String, MutableInt> modeSwitchCount = new HashMap<>();

        public void incrementModeSwitchCount(String hvdcId) {
            modeSwitchCount.computeIfAbsent(hvdcId, k -> new MutableInt(0))
                    .increment();
        }

        public int getModeSwitchCount(String hvdcId) {
            MutableInt counter = modeSwitchCount.get(hvdcId);
            if (counter == null) {
                return 0;
            }
            return counter.intValue();
        }
    }

    public void initialize(OuterLoopContext context) {
        context.setData(new ContextData());
    }

}
