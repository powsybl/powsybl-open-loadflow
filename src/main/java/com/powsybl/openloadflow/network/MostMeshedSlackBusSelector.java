/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.*;
import java.util.stream.Collectors;

import com.powsybl.iidm.network.Country;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MostMeshedSlackBusSelector implements SlackBusSelector {

    private final Set<Country> countriesForSlackBusSelection;

    public MostMeshedSlackBusSelector(Set<Country> countriesForSlackBusSelection) {
        this.countriesForSlackBusSelection = Objects.requireNonNull(countriesForSlackBusSelection);
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        double[] nominalVoltages = buses.stream()
                .filter(bus -> !bus.isFictitious())
                .filter(bus -> SlackBusSelector.participateToSlackBusSelection(countriesForSlackBusSelection, bus))
                .map(LfBus::getNominalV).mapToDouble(Double::valueOf).toArray();
        double maxNominalV = new Percentile()
                .withEstimationType(Percentile.EstimationType.R_3)
                .evaluate(nominalVoltages, 90);

        // select non-fictitious and most meshed bus among buses with the highest nominal voltage
        List<LfBus> slackBuses = buses.stream()
                .filter(bus -> !bus.isFictitious() && bus.getNominalV() == maxNominalV)
                .filter(bus -> SlackBusSelector.participateToSlackBusSelection(countriesForSlackBusSelection, bus))
                .sorted(Comparator.comparingInt((LfBus bus) -> bus.getBranches().size())
                        .thenComparing(Comparator.comparing(LfBus::getId).reversed()).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        return new SelectedSlackBus(slackBuses, "Most meshed bus");
    }
}
