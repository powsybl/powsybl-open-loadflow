/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class SelectedSlackBus {

    private final List<LfBus> buses;

    private final String selectionMethod;

    public SelectedSlackBus(List<LfBus> buses, String selectionMethod) {
        this.buses = Objects.requireNonNull(buses);
        this.selectionMethod = Objects.requireNonNull(selectionMethod);
    }

    public List<LfBus> getBuses() {
        return buses;
    }

    public String getSelectionMethod() {
        return selectionMethod;
    }
}
