/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.Country;
import com.powsybl.openloadflow.network.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class MultipleSlackBusSelector extends AbstractSlackBusSelector {

    public MultipleSlackBusSelector() { // for tests only
        this(Collections.emptySet());
    }

    public MultipleSlackBusSelector(Set<Country> countries) {
        super(countries);
    }

    private boolean isGeneratorInvalid(LfGenerator generator) {
        return generator.isFictitious();
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        List<LfBus> slackBuses = buses.stream()
                .filter(bus -> !bus.isFictitious())
                .filter(this::filterByCountry)
                .filter(bus -> !bus.getGenerators().isEmpty() && bus.getGenerators().stream().noneMatch(this::isGeneratorInvalid))
                .limit(limit)
                .collect(Collectors.toList());

        return new SelectedSlackBus(slackBuses, "Multiple generators buses");
    }
}
