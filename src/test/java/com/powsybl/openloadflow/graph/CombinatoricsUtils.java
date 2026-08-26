/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.apache.commons.math3.util.Combinations;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings("NewClassNamingConvention")
public class CombinatoricsUtils {

    public static BigInteger rank(int[] indices) {
        BigInteger rank = BigInteger.ZERO;

        for (int i = 0; i < indices.length; i++) {
            rank = rank.add(binomial(indices[i], i + 1));
        }

        return rank;
    }

    public static <T> List<T> unrank2(List<T> list, BigInteger id, int size) {
        // https://en.wikipedia.org/wiki/Combinatorial_number_system
        // n = (i_k) + (i_{k-1}) + ... + (c_1)
        //     (  k)   (    k-1)         (  1)
        List<T> subset = new ArrayList<>();

        BigInteger index = id;
        int n = list.size() - 1;
        BigInteger nCk = binomial(n, size);

        for (int k = size; k > 0; k--) {
            // find biggest n such that (n k) <= index
            while (nCk.compareTo(index) > 0) {
                // calculate n-1Ck with nCk
                nCk = nCk.multiply(BigInteger.valueOf(n - k))
                        .divide(BigInteger.valueOf(n));
                n--;
            }
            index = index.subtract(nCk);
            subset.add(list.get(n));

            // update nCk for next iteration
            if (n > 0) {
                nCk = nCk.multiply(BigInteger.valueOf(k))
                        .divide(BigInteger.valueOf(n));
            }
            n -= 1;
        }

        return subset;
    }

    public static <T> List<T> unrank(List<T> list, BigInteger id, int size) {
        // https://en.wikipedia.org/wiki/Combinatorial_number_system
        // n = (i_k) + (i_{k-1}) + ... + (c_1)
        //     (  k)   (    k-1)         (  1)

        if (id.signum() < 0 || size <= 0 || size > list.size()) {
            throw new IllegalArgumentException("id: '" + id + "' or size: '" + size + "' is invalid");
        }

        List<T> subset = new ArrayList<>();

        BigInteger index = id;
        int n = list.size() - 1;
        for (int i = 0, k = size; i < size; i++, k--) {
            while (binomial(n, k).compareTo(index) > 0) {
                n--;
            }
            subset.add(list.get(n));
            index = index.subtract(binomial(n, k));
            n -= 1;
        }

        return subset;
    }

    public static BigInteger binomial(int n, int k) {
        if (k > n) {
            return BigInteger.ZERO;
        }

        // C_n,k = n! / k! / (n - k)!
        //       = n (n-1) (n-2) ... (n-k+1) / k (k-1) (k-2)...1
        int lim = Math.min(k, n - k);

        BigInteger binomial = BigInteger.ONE;
        for (int i = 1; i <= lim; i++) {
            binomial = binomial.multiply(BigInteger.valueOf(n + 1 - i))
                    .divide(BigInteger.valueOf(i));
        }

        return binomial;
    }

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
