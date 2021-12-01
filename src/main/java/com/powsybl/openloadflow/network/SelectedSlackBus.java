/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SelectedSlackBus {

    private final LfBus bus;

    private final String selectionMethod;

    public SelectedSlackBus(LfBus bus, String selectionMethod) {
        this.bus = Objects.requireNonNull(bus);
        this.selectionMethod = Objects.requireNonNull(selectionMethod);
    }

    public LfBus getBus() {
        return bus;
    }

    public String getSelectionMethod() {
        return selectionMethod;
    }
}
