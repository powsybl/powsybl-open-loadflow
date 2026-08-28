/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class RandomUtilsTest {

    @Test
    void testIndexOf() {
        Assertions.assertEquals(0, RandomUtils.indexOf(BigInteger.valueOf(5), List.of(BigInteger.valueOf(10))));
        Assertions.assertEquals(0, RandomUtils.indexOf(BigInteger.valueOf(5), List.of(BigInteger.valueOf(10), BigInteger.valueOf(15))));
        Assertions.assertEquals(1, RandomUtils.indexOf(BigInteger.valueOf(10), List.of(BigInteger.valueOf(10), BigInteger.valueOf(11))));
        Assertions.assertEquals(1, RandomUtils.indexOf(BigInteger.valueOf(10), List.of(BigInteger.valueOf(10), BigInteger.valueOf(11), BigInteger.valueOf(12))));
        Assertions.assertEquals(2, RandomUtils.indexOf(BigInteger.valueOf(11), List.of(BigInteger.valueOf(10), BigInteger.valueOf(11), BigInteger.valueOf(12))));
    }

    @Test
    void distinctSubsetTest() {
        Random random = new Random(0);
        List<Integer> elements = List.of(1, 2, 4, 5, 7, 8, 9);

        List<List<Integer>> subsets = RandomUtils.distinctSubsets(random, elements, 5, 4, 4);
        assertNotNull(subsets);
        assertEquals(5, subsets.size());
        listListIsSameAsSetSet(subsets);

        subsets = RandomUtils.distinctSubsets(random, elements, 126, 1, elements.size());
        assertNotNull(subsets);
        assertEquals(126, subsets.size());
        listListIsSameAsSetSet(subsets);

        subsets = RandomUtils.distinctSubsets(random, elements, Integer.MAX_VALUE, 1, elements.size());
        assertNotNull(subsets);
        assertEquals(127, subsets.size());
        listListIsSameAsSetSet(subsets);
    }

    @Test
    void distinctSubsetUniformlyDistributedTest() {
        Random random = new Random(0);
        List<Integer> elements = List.of(1, 2, 4, 5, 7, 8, 9);

        List<List<Integer>> subsets = RandomUtils.distinctSubsetsUniformlyDistributed(random, elements, 5, 4, 4);
        assertNotNull(subsets);
        assertEquals(5, subsets.size());
        listListIsSameAsSetSet(subsets);

        subsets = RandomUtils.distinctSubsetsUniformlyDistributed(random, elements, 126, 1, elements.size());
        assertNotNull(subsets);
        assertEquals(126, subsets.size());
        listListIsSameAsSetSet(subsets);

        subsets = RandomUtils.distinctSubsetsUniformlyDistributed(random, elements, Integer.MAX_VALUE, 1, elements.size());
        assertNotNull(subsets);
        assertEquals(127, subsets.size());
        listListIsSameAsSetSet(subsets);
    }

    private static <T> void listListIsSameAsSetSet(List<List<T>> lists) {
        Set<Set<T>> sets = new HashSet<>();

        for (List<T> list : lists) {
            Set<T> set = new HashSet<>(list);
            assertEquals(set.size(), list.size(), list.toString());
            assertTrue(sets.add(set), set.toString());
        }
    }
}
