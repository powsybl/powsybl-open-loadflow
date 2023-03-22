/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import net.jafama.FastMath;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MaxTransmittedPowerSlackBusSelector implements SlackBusSelector {

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        List<Pair<LfBus, Double>> busAndMaxTransmittedPowerList = new ArrayList<>();
        for (LfBus bus : buses) {
            double maxTransmittedPower = 0;
            for (LfBranch branch : bus.getBranches()) {
                PiModel piModel = branch.getPiModel();
                if (piModel.getX() != 0) {
                    maxTransmittedPower += piModel.getR1() / FastMath.abs(piModel.getX());
                }
            }
            busAndMaxTransmittedPowerList.add(Pair.of(bus, maxTransmittedPower));
        }
        List<LfBus> slackBuses = busAndMaxTransmittedPowerList.stream()
                .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()))
                .limit(limit)
                .map(Pair::getLeft)
                .collect(Collectors.toList());
        return new SelectedSlackBus(slackBuses, "Max transmitted power bus");
    }
}
