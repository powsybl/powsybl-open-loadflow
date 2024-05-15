/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;

import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractSlackBusSelector implements SlackBusSelector {

    private final Set<Country> countries;

    protected AbstractSlackBusSelector(Set<Country> countries) {
        this.countries = Objects.requireNonNull(countries);
    }

    protected boolean filterByCountry(LfBus bus) {
        return countries.isEmpty()
                || bus.getCountry().map(countries::contains).orElse(false);
    }

}
