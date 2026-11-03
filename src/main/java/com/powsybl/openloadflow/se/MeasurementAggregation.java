/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import java.util.Objects;

/**
 * Settings that decide what the aggregated measurement set means, as opposed to how it is
 * assembled.
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public class MeasurementAggregation {

    /** How a bus injection is formed when only some of the devices on the bus carry a meter. */
    public enum BusInjectionPolicy {
        /**
         * Form a bus injection only where every device on the bus has a meter for that
         * quantity. An unmetered device carrying power would otherwise be read as zero,
         * which moves the injection by the whole of its output.
         */
        REQUIRE_ALL_METERED,
        /** Sum the metered devices and treat the rest as contributing nothing. */
        SUM_METERED
    }

    /** How a bus known to inject nothing is handled. */
    public enum ZeroInjectionMode {
        /** A bus with no device is treated like any other unmeasured bus. */
        IGNORED,
        /** A zero-valued measurement with a very small standard deviation. */
        HIGH_CONFIDENCE_MEASUREMENT,
        /** Enforced exactly, so no weight is involved. */
        EQUALITY_CONSTRAINT
    }

    /** What to do with two meters reading the same quantity at the same place. */
    public enum DuplicateMeasurementPolicy {
        /** Combine into one reading weighted by the inverse of each variance. */
        MERGE_INVERSE_VARIANCE,
        /** Let every reading contribute its own row. */
        KEEP_ALL,
        /** Refuse to aggregate. */
        THROW
    }

    private BusInjectionPolicy busInjectionPolicy = BusInjectionPolicy.REQUIRE_ALL_METERED;

    private DuplicateMeasurementPolicy duplicateMeasurementPolicy = DuplicateMeasurementPolicy.MERGE_INVERSE_VARIANCE;

    private ZeroInjectionMode zeroInjectionMode = ZeroInjectionMode.HIGH_CONFIDENCE_MEASUREMENT;

    private double zeroInjectionStandardDeviationPu = 0.001;

    private double relativeStandardDeviationFloor = 0.0;

    private boolean breakers = false;

    public BusInjectionPolicy getBusInjectionPolicy() {
        return busInjectionPolicy;
    }

    public MeasurementAggregation setBusInjectionPolicy(BusInjectionPolicy busInjectionPolicy) {
        this.busInjectionPolicy = Objects.requireNonNull(busInjectionPolicy);
        return this;
    }

    public DuplicateMeasurementPolicy getDuplicateMeasurementPolicy() {
        return duplicateMeasurementPolicy;
    }

    public MeasurementAggregation setDuplicateMeasurementPolicy(DuplicateMeasurementPolicy duplicateMeasurementPolicy) {
        this.duplicateMeasurementPolicy = Objects.requireNonNull(duplicateMeasurementPolicy);
        return this;
    }

    public ZeroInjectionMode getZeroInjectionMode() {
        return zeroInjectionMode;
    }

    public MeasurementAggregation setZeroInjectionMode(ZeroInjectionMode zeroInjectionMode) {
        this.zeroInjectionMode = Objects.requireNonNull(zeroInjectionMode);
        return this;
    }

    /**
     * The standard deviation given to a zero injection pseudo-measurement, per unit. It is the
     * only thing bounding how far such a bus can pull the estimate, since the relative floor
     * below is a fraction of the reading and the reading is zero. The default matches
     * pandapower's {@code ZERO_INJECTION_STD_DEV}.
     */
    public double getZeroInjectionStandardDeviationPu() {
        return zeroInjectionStandardDeviationPu;
    }

    public MeasurementAggregation setZeroInjectionStandardDeviationPu(double zeroInjectionStandardDeviationPu) {
        if (zeroInjectionStandardDeviationPu <= 0) {
            throw new IllegalArgumentException("Zero injection standard deviation must be positive, was "
                    + zeroInjectionStandardDeviationPu);
        }
        this.zeroInjectionStandardDeviationPu = zeroInjectionStandardDeviationPu;
        return this;
    }

    /**
     * Lower bound on a reading's standard deviation, as a fraction of the reading itself.
     * Applied to each reading as it is converted, and to a bus injection once its devices are
     * summed, so two readings merged as duplicates can still land below it. Zero, the default,
     * applies no floor.
     */
    public double getRelativeStandardDeviationFloor() {
        return relativeStandardDeviationFloor;
    }

    public MeasurementAggregation setRelativeStandardDeviationFloor(double relativeStandardDeviationFloor) {
        if (relativeStandardDeviationFloor < 0) {
            throw new IllegalArgumentException("Relative standard deviation floor must not be negative: "
                    + relativeStandardDeviationFloor);
        }
        this.relativeStandardDeviationFloor = relativeStandardDeviationFloor;
        return this;
    }

    /**
     * Which topology view a terminal is resolved through. Must match the view the
     * {@code LfNetwork} was built with, or the bus identifiers will not line up.
     */
    public boolean isBreakers() {
        return breakers;
    }

    public MeasurementAggregation setBreakers(boolean breakers) {
        this.breakers = breakers;
        return this;
    }
}
