/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark;

import org.apache.commons.math3.util.Combinations;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class RandomUtils {

    private RandomUtils() { }

    /**
     * Generate a list of {@code subsetsCount} sublist each containing between {@code minSize}
     * and {@code maxSize} (inclusive) elements such that each list are distinct up to
     * a permutation.
     */
    public static <T> List<List<T>> distinctSubsets(Random random, List<T> list, int subsetsCount, int minSize, int maxSize) {
        List<BigInteger> sizeCumulative = cumulativeSubsetSizes(list.size(), minSize, maxSize);
        BigInteger count = sizeCumulative.getLast();
        System.out.println("Number of subsets: " + count);

        if (subsetsCount < 0 || count.compareTo(BigInteger.valueOf(subsetsCount)) <= 0) {
            // count < subsetsCount
            return allSubsets(random, list, subsetsCount, minSize, maxSize);
        } else {
            Set<List<T>> subsets = new HashSet<>();

            while (subsets.size() < subsetsCount) {
                int size = random.nextInt(minSize, maxSize + 1);

                Set<Integer> indices = new HashSet<>(size);
                while (indices.size() < size) {
                    indices.add(random.nextInt(list.size()));
                }

                subsets.add(indices.stream().sorted().map(list::get).toList());
            }
            return new ArrayList<>(subsets);
        }
    }

    /**
     * Generate a list of {@code subsetsCount} sublist each containing between {@code minSize}
     * and {@code maxSize} (inclusive) elements such that each list are distinct up to
     * a permutation. Subsets are guaranteed to be uniformly distributed over all possible
     * subsets.
     */
    public static <T> List<List<T>> distinctSubsetsUniformlyDistributed(Random random, List<T> list, int subsetsCount, int minSize, int maxSize) {
        // general idea:
        // - compute the number of subsets P_i of size minSize+i
        // - Let P = sum P_i and PC_i = sum(j<=i) P_j
        // - each integer i between 0 and P is mapped to a distinct subsets using the following process
        //   - if i is between PC_n and PC_(n+1) then the associated subset is of size minSize+n
        //   - let j = i - PC_n.
        //   - j is between 0 and P_n
        //   - A subset of size minSize+n is generated as follows
        //     - https://en.wikipedia.org/wiki/Combinatorial_number_system

        List<BigInteger> sizeCumulative = cumulativeSubsetSizes(list.size(), minSize, maxSize);
        BigInteger count = sizeCumulative.getLast();
        System.out.println("Number of subsets: " + count);

        if (subsetsCount < 0 || count.compareTo(BigInteger.valueOf(subsetsCount)) <= 0) {
            // count < subsetsCount
            return allSubsets(random, list, subsetsCount, minSize, maxSize);
        } else {
            List<List<T>> subsets = new ArrayList<>();
            Set<BigInteger> set = new HashSet<>();

            while (subsets.size() < subsetsCount) {
                BigInteger next = randomBigInt(random, count);
                if (set.add(next)) {
                    int j = indexOf(next, sizeCumulative);
                    int subsetSize = minSize + j;

                    BigInteger rank = j > 0 ? next.subtract(sizeCumulative.get(j - 1)) : next;

                    subsets.add(CombinatoricsUtils.unrank2(list, rank, subsetSize));
                }
            }
            return subsets;
        }
    }

    public static <T> List<List<T>> allSubsets(Random random, List<T> list, int subsetsCount, int minSize, int maxSize) {
        List<List<T>> subsets = new ArrayList<>();

        for (int subsetSize = minSize; subsetSize <= maxSize; subsetSize++) {
            for (int[] combination : new Combinations(list.size(), subsetSize)) {
                List<T> subset = new ArrayList<>();

                for (int index : combination) {
                    subset.add(list.get(index));
                }

                subsets.add(subset);
            }
        }

        return subsets;
    }

    private static List<BigInteger> cumulativeSubsetSizes(int setSize, int minSize, int maxSize) {
        List<BigInteger> sizeCumulative = new ArrayList<>();
        for (int k = minSize; k <= maxSize; k++) {
            BigInteger levelKCount = CombinatoricsUtils.binomial(setSize, k);

            if (sizeCumulative.isEmpty()) {
                sizeCumulative.add(levelKCount);
            } else {
                sizeCumulative.add(sizeCumulative.getLast().add(levelKCount));
            }
        }

        return sizeCumulative;
    }

    static int indexOf(BigInteger id, List<BigInteger> sizeCumulative) {
        int i = 0;
        for (; i < sizeCumulative.size(); i++) {
            BigInteger next = sizeCumulative.get(i);
            if (id.compareTo(next) < 0) {
                break;
            }
        }
        return i;
    }

    private static BigInteger randomBigInt(Random random, BigInteger max) {
        BigInteger randomNumber;
        do {
            randomNumber = new BigInteger(max.bitLength(), random);
        } while (randomNumber.compareTo(max) >= 0);
        return randomNumber;
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
