/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open;

import com.powsybl.loadflow.open.network.FirstSlackBusSelector;
import com.powsybl.loadflow.open.network.MostMeshedSlackBusSelector;
import com.powsybl.loadflow.open.network.SlackBusSelector;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum SlackBusSelectionMode {
    FIRST(new FirstSlackBusSelector()),
    MOST_MESHED(new MostMeshedSlackBusSelector());

    private final SlackBusSelector selector;

    SlackBusSelectionMode(SlackBusSelector selector) {
        this.selector = Objects.requireNonNull(selector);
    }

    public SlackBusSelector getSelector() {
        return selector;
    }
}
