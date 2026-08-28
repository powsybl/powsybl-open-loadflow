/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark;

import org.apache.commons.math3.util.Combinations;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.powsybl.openloadflow.graph.benchmark.CombinatoricsUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class CombinatoricsUtilsTest {

    @Test
    void binomialTest() {
        int height = 10;
        BigInteger[][] pascalTriangle = new BigInteger[height][];
        for (int n = 0; n < height; n++) {
            pascalTriangle[n] = new BigInteger[n + 1];
            for (int k = 0; k <= n; k++) {
                BigInteger cur = binomial(n, k);
                if (n == 0 || k == 0 || k == n) {
                    assertEquals(BigInteger.ONE, cur, n + "-" + k);
                } else {
                    assertEquals(pascalTriangle[n - 1][k - 1].add(pascalTriangle[n - 1][k]), cur, n + "-" + k);
                }
                pascalTriangle[n][k] = cur;
            }
        }

        assertEquals(BigInteger.ZERO, binomial(0, 1));
    }

    @Test
    void testUnrank() {
        List<Integer> list = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);

        for (int subsetSize = 1; subsetSize <= list.size(); subsetSize++) {
            Combinations combinations = new Combinations(list.size(), subsetSize);

            for (int[] indices : combinations) {
                BigInteger rank = rank(indices);

                List<Integer> elements = unrank2(list, rank, subsetSize).reversed();

                assertEquals(subsetSize, elements.size());
                for (int i = 0; i < subsetSize; i++) {
                    assertEquals(list.get(indices[i]), elements.get(i));
                }
            }
        }

        List<Integer> subset = unrank2(list, binomial(5, 3).add(binomial(3, 2)).add(binomial(2, 1)), 3);
        assertEquals(List.of(5, 3, 2), subset);

        subset = unrank2(list, binomial(8, 1), 1);
        assertEquals(List.of(8), subset);

        subset = unrank2(list, binomial(5, 5).add(binomial(4, 4)).add(binomial(3, 3)).add(binomial(2, 2)).add(binomial(1, 1)), 5);
        assertEquals(List.of(5, 4, 3, 2, 1), subset);
    }
}
