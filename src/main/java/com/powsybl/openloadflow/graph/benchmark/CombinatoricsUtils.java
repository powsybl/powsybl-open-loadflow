/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class CombinatoricsUtils {

    private CombinatoricsUtils() { }

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
}
