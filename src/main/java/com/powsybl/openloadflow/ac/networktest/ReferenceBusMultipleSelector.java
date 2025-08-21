/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.ReferenceBusSelector;

import java.util.List;
import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ReferenceBusMultipleSelector implements ReferenceBusSelector {
    private static final String METHOD_NAME = "All slack";

    @Override
    public SelectedReferenceBuses select(LfNetwork lfNetwork) {
        Objects.requireNonNull(lfNetwork);
        List<LfBus> slackBuses = lfNetwork.getSlackBuses();
        Objects.requireNonNull(slackBuses);
        if (slackBuses.isEmpty()) {
            throw new IllegalStateException("No slack bus for network " + lfNetwork);
        }
        return new SelectedReferenceBuses(slackBuses, METHOD_NAME);
    }
}
