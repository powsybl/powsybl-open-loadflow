/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MostMeshedSlackBusSelector implements SlackBusSelector {

    @Override
    public LfBus select(List<LfBus> buses) {
        double[] nominalVoltages = buses.stream()
                .filter(bus -> !bus.isFictitious())
                .map(LfBus::getNominalV).mapToDouble(Double::valueOf).toArray();
        double maxNominalV = new Percentile()
                .withEstimationType(Percentile.EstimationType.R_3)
                .evaluate(nominalVoltages, 90);

        // select non fictitious and most meshed bus among buses with highest nominal voltage
        return buses.stream()
                .filter(bus -> !bus.isFictitious() && bus.getNominalV() == maxNominalV)
                .max(Comparator.comparingInt(bus -> bus.getBranches().size()))
                .orElseThrow(AssertionError::new);
    }
}
