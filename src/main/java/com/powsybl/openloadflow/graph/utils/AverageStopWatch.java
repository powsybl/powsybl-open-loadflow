/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.utils;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.*;

public class AverageStopWatch {

    public static AverageStopWatch merge(AverageStopWatch[] stopWatches) {
        AverageStopWatch total = new AverageStopWatch();
        for (AverageStopWatch sw : stopWatches) {
            total.merge(sw);
        }

        return total;
    }

    public static double averageTotal(AverageStopWatch[] stopWatches) {
        int n = 0;
        long sum = 0;
        for (AverageStopWatch sw : stopWatches) {
            sum += sw.totalElapsed();
            n = Math.max(n, sw.count());
        }

        return (double) sum / n;
    }

    public static long total(AverageStopWatch[] stopWatches) {
        long sum = 0;
        for (AverageStopWatch sw : stopWatches) {
            sum += sw.totalElapsed();
        }

        return sum;
    }

    private long totalElapsedNanos = 0;
    private long startTick;
    private long elapsedNanos;

    private int n = 0;

    public void merge(AverageStopWatch other) {
        // assumes that the two stop watch are stopped
        totalElapsedNanos += other.totalElapsedNanos;
        n += other.n;
    }

    public void start() {
        startTick = System.nanoTime();
    }

    public void stop() {
        elapsedNanos = System.nanoTime() - startTick;
        totalElapsedNanos += elapsedNanos;
        n++;
    }

    public long elapsed() {
        return elapsedNanos;
    }

    public long totalElapsed() {
        return totalElapsedNanos;
    }

    public double averageElapsed() {
        return (double) totalElapsedNanos / n;
    }

    public String averageElapsedString(TimeUnit desiredUnit) {
        if (n == 0) {
            return "Nothing has been measured";
        } else {
            double avg = averageElapsed();
            TimeUnit unit = Objects.requireNonNullElseGet(desiredUnit, () -> chooseUnit((long) avg));
            return prettyPrint(avg, unit) + " (measured " + n + " times)";
        }
    }

    public int count() {
        return n;
    }

    @Override
    public String toString() {
        return averageElapsedString(null);
    }

    public static String prettyPrint(long nanos) {
        return prettyPrint(nanos, chooseUnit(nanos));
    }

    public static TimeUnit chooseUnit(long nanos) {
        if (DAYS.convert(nanos, NANOSECONDS) > 0) {
            return DAYS;
        }
        if (HOURS.convert(nanos, NANOSECONDS) > 0) {
            return HOURS;
        }
        if (MINUTES.convert(nanos, NANOSECONDS) > 0) {
            return MINUTES;
        }
        if (SECONDS.convert(nanos, NANOSECONDS) > 0) {
            return SECONDS;
        }
        if (MILLISECONDS.convert(nanos, NANOSECONDS) > 0) {
            return MILLISECONDS;
        }
        if (MICROSECONDS.convert(nanos, NANOSECONDS) > 0) {
            return MICROSECONDS;
        }
        return NANOSECONDS;
    }

    public static String prettyPrint(double nanos, TimeUnit desiredUnit) {
        double value = nanos / NANOSECONDS.convert(1, desiredUnit);

        return String.format(Locale.ROOT, "%.4f %s", value, abbreviate(desiredUnit));
    }

    public static String abbreviate(TimeUnit unit) {
        return switch (unit) {
            case NANOSECONDS -> "ns";
            case MICROSECONDS -> "μs";
            case MILLISECONDS -> "ms";
            case SECONDS -> "s";
            case MINUTES -> "min";
            case HOURS -> "h";
            case DAYS -> "d";
        };
    }
}
