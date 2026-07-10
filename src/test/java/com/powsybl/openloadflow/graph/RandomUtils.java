/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class RandomUtils {

    private RandomUtils() {
    }

    public static <T> Stream<T> sample(Random random, List<T> list, int minElement, int maxElement) {
        Collections.shuffle(list, random);
        return list.stream()
                .limit(random.nextInt(minElement, maxElement + 1));
    }

    public static <T> T select(Random random, SequencedMap<T, Double> cumulativeDistributionFunction) {
        double v = random.nextDouble();

        for (Map.Entry<T, Double> entry : cumulativeDistributionFunction.entrySet()) {
            if (v <= entry.getValue()) {
                return entry.getKey();
            }
        }

        throw new IllegalArgumentException("No probability exists for " + cumulativeDistributionFunction.keySet());
    }
}
