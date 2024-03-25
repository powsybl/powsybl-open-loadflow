/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

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

    // TODO see for which other selector this could be used
    // TODO maybe it will be even better to distinct by ZeroImpedanceNetwork
    protected static Predicate<LfBus> distinctByNonImpedantBranch() {
        Set<LfBranch> visitedNonImpedantBranches = new HashSet<>();
        return b -> {
            List<LfBranch> nonImpedantBranches = b.getBranches().stream().filter(branch -> branch.isZeroImpedance(LoadFlowModel.DC)).toList();
            if (visitedNonImpedantBranches.stream().anyMatch(nonImpedantBranches::contains)) {
                return false;
            }
            visitedNonImpedantBranches.addAll(nonImpedantBranches);
            return true;
        };
    }
}
