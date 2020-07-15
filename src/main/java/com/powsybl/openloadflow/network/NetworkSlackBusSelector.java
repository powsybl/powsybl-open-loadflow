/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.SlackTerminal;

import java.util.List;
import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class NetworkSlackBusSelector implements SlackBusSelector {

    private final Network network;

    private Bus slackBus = null;

    public NetworkSlackBusSelector(Network network) {
        this.network = Objects.requireNonNull(network);
        for (VoltageLevel vl : network.getVoltageLevels()) {
            if (vl.getExtension(SlackTerminal.class) != null) {
                slackBus = vl.getExtension(SlackTerminal.class).getTerminal().getBusView().getBus();
            } // FIXME could have several extensions in the same network.
        }
    }

    @Override
    public LfBus select(List<LfBus> buses) {
        for (LfBus bus : buses) {
            if (bus.getBus().equals(slackBus)) {
                return bus;
            }
        }
        return null;
    }
}
