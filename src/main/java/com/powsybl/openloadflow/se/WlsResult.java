/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.openloadflow.ac.equations.AcEquationType;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of estimating one {@link com.powsybl.openloadflow.network.LfNetwork}.
 *
 * <p>One {@code LfNetwork} is one connected component, which a DC link can split into several
 * synchronous components, each with its own angle reference.</p>
 *
 * <p>Identifiers are IIDM identifiers and quantities are in the units IIDM uses, not the per unit
 * and radians the solver works in, so that a caller publishing these results has nothing to
 * convert. Normalized residuals and redundancies are ratios and carry no unit.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public final class WlsResult {

    /** Mirrors {@code StateEstimationResult.ComponentResult.Status}. */
    public enum Status {
        /** The measurements determined the state and the iteration met the tolerance. */
        ESTIMATED,
        /** The gain matrix is singular: the measurements leave the state undetermined. */
        UNOBSERVABLE,
        /** The iteration reached its cap with the fit still improving. */
        DID_NOT_CONVERGE,
        /** The estimation could not be carried out, for instance because the iteration diverged. */
        FAILED
    }

    private final Status status;

    private final String statusText;

    private final int iterationCount;

    private final List<String> referenceBusIds;

    private final double objectiveFunctionValue;

    private final int degreesOfFreedom;

    private final List<BusResult> busResults;

    private final List<BranchResult> branchResults;

    private final List<MeasurementResult> measurementResults;

    /**
     * @param status whether the iteration converged
     * @param statusText text explaining the status, empty when there is nothing to add
     * @param iterationCount how many iterations were run
     * @param referenceBusIds the buses whose angles were held fixed, one per synchronous component
     * @param objectiveFunctionValue the weighted sum of squared residuals at the solution
     * @param degreesOfFreedom the readings in excess of the unknowns
     * @param busResults the estimated state, one entry per bus
     * @param branchResults the flows the state implies, one entry per branch connected at both ends
     * @param measurementResults one entry per reading, in the order the readings were given
     */
    public WlsResult(Status status, String statusText, int iterationCount, List<String> referenceBusIds,
                     double objectiveFunctionValue, int degreesOfFreedom, List<BusResult> busResults,
                     List<BranchResult> branchResults, List<MeasurementResult> measurementResults) {
        this.status = Objects.requireNonNull(status);
        this.statusText = Objects.requireNonNull(statusText);
        this.iterationCount = iterationCount;
        this.referenceBusIds = List.copyOf(Objects.requireNonNull(referenceBusIds));
        this.objectiveFunctionValue = objectiveFunctionValue;
        this.degreesOfFreedom = degreesOfFreedom;
        this.busResults = List.copyOf(Objects.requireNonNull(busResults));
        this.branchResults = List.copyOf(Objects.requireNonNull(branchResults));
        this.measurementResults = List.copyOf(Objects.requireNonNull(measurementResults));
    }

    public Status getStatus() {
        return status;
    }

    /** Why the estimation did not succeed; empty when there is nothing to add. */
    public String getStatusText() {
        return statusText;
    }

    public boolean isConverged() {
        return status == Status.ESTIMATED;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    /**
     * The buses whose voltage angles were held fixed, one per synchronous component. Only angle
     * differences are physical, and a DC link leaves two sides with no shared angle origin.
     */
    public List<String> getReferenceBusIds() {
        return referenceBusIds;
    }

    /** The weighted sum of squared residuals at the final state, the input to a chi-square test. */
    public double getObjectiveFunctionValue() {
        return objectiveFunctionValue;
    }

    /** Readings minus unknowns, for the chi-square test. Zero or less leaves nothing to test. */
    public int getDegreesOfFreedom() {
        return degreesOfFreedom;
    }

    /** The estimated state, one entry per bus. */
    public List<BusResult> getBusResults() {
        return busResults;
    }

    /** The flows the estimated state implies, one entry per branch connected at both ends. */
    public List<BranchResult> getBranchResults() {
        return branchResults;
    }

    /**
     * What each meter read against what the estimate says it should, one entry per reading, in the
     * order the readings were given. This is what a bad data detection is run on.
     */
    public List<MeasurementResult> getMeasurementResults() {
        return measurementResults;
    }

    /**
     * The estimated state at one bus, in the units IIDM uses.
     *
     * <p>A standard deviation is {@code NaN} where the quantity is not an unknown: the angle of a
     * reference bus is held fixed, and a bus no reading reaches has no state to estimate.</p>
     *
     * @param busId the identifier of the bus
     * @param voltageLevelId the identifier of the voltage level holding the bus
     * @param v the voltage magnitude, in kV
     * @param angle the voltage angle, in degrees
     * @param vStandardDeviation the standard deviation of the voltage magnitude, in kV
     * @param angleStandardDeviation the standard deviation of the voltage angle, in degrees
     */
    public record BusResult(String busId, String voltageLevelId, double v, double angle,
                            double vStandardDeviation, double angleStandardDeviation) {

        public BusResult {
            Objects.requireNonNull(busId);
            Objects.requireNonNull(voltageLevelId);
        }
    }

    /**
     * The flows at the two ends of one branch, each positive when flowing from the bus into the
     * branch at that side. Derived from the estimated state rather than estimated.
     *
     * @param branchId the identifier of the branch
     * @param p1 the active power at side 1, in MW
     * @param q1 the reactive power at side 1, in MVar
     * @param p2 the active power at side 2, in MW
     * @param q2 the reactive power at side 2, in MVar
     */
    public record BranchResult(String branchId, double p1, double q1, double p2, double q2) {

        public BranchResult {
            Objects.requireNonNull(branchId);
        }
    }

    /**
     * What one meter read, what the estimate says it should have read, and how far apart those two
     * are allowed to be.
     *
     * <p>The gap itself says little, because an estimate pulled by a bad reading sits closer to it
     * than it should. {@code normalizedResidual} divides the gap by the standard deviation the
     * residual itself has, which accounts for that pull and makes readings of different quantities
     * and precisions comparable. {@code redundancy} is what fraction of the gap survived: it runs
     * from 1, where nothing else measures this quantity's neighborhood and the full error shows
     * up, down to 0, where the estimate reproduces the reading exactly whatever it says.</p>
     *
     * @param elementId the identifier of the bus or branch the meter sits on
     * @param type what the meter reads, and at which side for a branch quantity
     * @param sourceMeasurementIds see {@link LfMeasurement#sourceMeasurementIds()}
     * @param value the reading, in the unit IIDM uses for the quantity
     * @param estimatedValue what the estimate says this meter should read, in the same unit
     * @param residual the reading less the estimated value, in the same unit
     * @param standardDeviation the standard deviation of the reading, in the same unit
     * @param normalizedResidual the residual divided by its own standard deviation, or {@code NaN}
     *                           where the measurement is critical
     * @param redundancy the share of the reading's variance the residual kept, between 0 and 1,
     *                   reported as exactly 0 below
     *                   {@link WlsStateEstimator#MINIMUM_TESTABLE_REDUNDANCY}, or {@code NaN}
     *                   where the gain matrix could not be decomposed
     */
    public record MeasurementResult(String elementId, AcEquationType type,
                                    List<String> sourceMeasurementIds, double value,
                                    double estimatedValue, double residual, double standardDeviation,
                                    double normalizedResidual, double redundancy) {

        public MeasurementResult {
            Objects.requireNonNull(elementId);
            Objects.requireNonNull(type);
            sourceMeasurementIds = List.copyOf(Objects.requireNonNull(sourceMeasurementIds));
        }

        /**
         * Whether the estimate reproduces this reading whatever it says, which is the case when
         * nothing else determines the quantity it reads. Removing it would leave the network
         * unobservable, and no error in it can be detected. The residual of such a reading is
         * zero by construction, so {@link #normalizedResidual()} is {@code NaN}.
         */
        public boolean isCritical() {
            return redundancy == 0;
        }

        /** What this reading contributes to the objective function. */
        public double weightedSquaredResidual() {
            double normalized = residual / standardDeviation;
            return normalized * normalized;
        }
    }
}
