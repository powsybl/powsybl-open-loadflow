/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import java.util.Comparator;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MostMeshedSlackBusSelector implements SlackBusSelector {

    @Override
    public LfBus select(List<LfBus> buses) {
        double maxNominalV = buses.stream()
                .map(LfBus::getNominalV)
                .mapToDouble(Double::valueOf)
                .max()
                .orElseThrow(AssertionError::new);

        // select most meshed bus among buses with highest nominal voltage
        return buses.stream()
                .filter(bus -> bus.getNominalV() == maxNominalV)
                .max(Comparator.comparingInt(LfBus::getNeighbors))
                .orElseThrow(AssertionError::new);
    }
}
