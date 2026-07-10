/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.utils;

import java.util.concurrent.TimeUnit;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class Aggregator {

    public static long sum(Aggregator[] aggregators) {
        long sum = 0;
        for (Aggregator aggregator : aggregators) {
            sum += aggregator.sum;
        }

        return sum;
    }

    private long sum = 0;
    private int count = 0;

    private double m2 = 0;

    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    public Aggregator() {

    }

    // https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm
    public void add(long value) {
        double oldMean = getMean();
        count++;
        sum += value;
        double newMean = getMean();

        m2 += (value - oldMean) * (value - newMean);

        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    public long getMin() {
        return min;
    }

    public double getMin(TimeUnit unit) {
        return (double) min / unit.toNanos(1);
    }

    public long getMax() {
        return max;
    }

    public double getMax(TimeUnit unit) {
        return (double) max / unit.toNanos(1);
    }

    public double getMean() {
        return count <= 0 ? 0 : (double) sum / count;
    }

    public double getMean(TimeUnit unit) {
        return getMean() / unit.toNanos(1);
    }

    // https://en.wikipedia.org/wiki/Standard_deviation#Sample_standard_deviation
    public double getSampleVariance() {
        return m2 / (count - 1);
    }

    public double getSampleStandardDeviation() {
        return Math.sqrt(getSampleVariance());
    }

    public double getSampleStandardDeviation(TimeUnit unit) {
        return getSampleStandardDeviation() / unit.toNanos(1);
    }

    public int getCount() {
        return count;
    }

    public long getSum() {
        return sum;
    }

    public void merge(Aggregator aggregator) {
        double mean1 = getMean();
        double mean2 = aggregator.getMean();

        sum += aggregator.sum;
        count += aggregator.count;

        double newMean = getMean();

        // https://math.stackexchange.com/a/2971563
        m2 = m2 + Math.pow(mean1 - newMean, 2) + aggregator.m2 + Math.pow(mean2 - newMean, 2);

        min = Math.min(min, aggregator.min);
        max = Math.max(max, aggregator.max);
    }

    @Override
    public String toString() {
        if (count == 0) {
            return "no measure found";
        } else {
            return "count=%d\n   min=%.4f μs, mean=%.4f μs, max=%.4f μs, stdev=%.4f".formatted(count, min / 1e3, getMean() / 1e3, max / 1e3, getSampleStandardDeviation() / 1e3);
        }
    }
}
