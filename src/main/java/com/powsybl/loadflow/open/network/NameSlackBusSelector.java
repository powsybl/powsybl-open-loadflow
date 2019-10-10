/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open.network;

import com.powsybl.commons.PowsyblException;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NameSlackBusSelector implements SlackBusSelector {

    private final String busId;

    public NameSlackBusSelector(String busId) {
        this.busId = Objects.requireNonNull(busId);
    }

    @Override
    public LfBus select(List<LfBus> buses) {
        return buses.stream()
                .filter(bus -> bus.getId().equals(busId))
                .findFirst().orElseThrow(() -> new PowsyblException("Slack bus '" + busId + "' not found"));
    }
}
