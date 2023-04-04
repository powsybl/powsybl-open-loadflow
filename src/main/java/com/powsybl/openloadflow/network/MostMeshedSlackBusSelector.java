/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MostMeshedSlackBusSelector extends AbstractSlackBusSelector {

    public static final double MAX_NOMINAL_VOLTAGE_PERCENTILE_DEFAULT_VALUE = 95;

    private final double maxNominalVoltagePercentile;

    public MostMeshedSlackBusSelector() {
        this(MAX_NOMINAL_VOLTAGE_PERCENTILE_DEFAULT_VALUE, Collections.emptySet());
    }

    public MostMeshedSlackBusSelector(double maxNominalVoltagePercentile, Set<Country> countries) {
        super(countries);
        this.maxNominalVoltagePercentile = maxNominalVoltagePercentile;
    }

    private static int getBranchCountConnectedAtBothSides(LfBus bus) {
        return (int) bus.getBranches().stream().filter(LfBranch::isConnectedAtBothSides).count();
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        double[] nominalVoltages = buses.stream()
                .filter(bus -> !bus.isFictitious())
                .filter(this::filterByCountry)
                .map(LfBus::getNominalV).mapToDouble(Double::valueOf).toArray();
        double maxNominalV = new Percentile()
                .withEstimationType(Percentile.EstimationType.R_3)
                .evaluate(nominalVoltages, maxNominalVoltagePercentile);

        // select non-fictitious and most meshed bus among buses with the highest nominal voltage
        List<LfBus> slackBuses = buses.stream()
                .filter(bus -> !bus.isFictitious() && bus.getNominalV() == maxNominalV)
                .filter(this::filterByCountry)
                .sorted(Comparator.comparingInt(MostMeshedSlackBusSelector::getBranchCountConnectedAtBothSides)
                    .thenComparing(Comparator.comparing(LfBus::getId).reversed()).reversed())
            .limit(limit)
            .collect(Collectors.toList());

        return new SelectedSlackBus(slackBuses, "Most meshed bus");
    }
}
