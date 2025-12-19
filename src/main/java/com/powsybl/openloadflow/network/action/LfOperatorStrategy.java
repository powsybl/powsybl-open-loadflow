/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfOperatorStrategy {

    private final int index;

    private final List<LfAction> actions;

    public LfOperatorStrategy(int index, List<LfAction> actions) {
        this.index = index;
        this.actions = Objects.requireNonNull(actions);
    }

    public int getIndex() {
        return index;
    }

    public List<LfAction> getActions() {
        return actions;
    }
}
