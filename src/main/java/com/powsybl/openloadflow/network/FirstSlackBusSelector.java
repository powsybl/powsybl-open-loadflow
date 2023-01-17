/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class FirstSlackBusSelector implements SlackBusSelector {

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        return new SelectedSlackBus(buses.stream().limit(limit).collect(Collectors.toList()), "First bus");
    }
}
