/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

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
 * convert.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public final class WlsResult {

    /** Whether this network produced an estimate, and why not when it did not. */
    public enum Status {
        /** The iteration met the tolerance before the cap. */
        ESTIMATED,
        /** The iteration reached the cap without meeting the tolerance. */
        DID_NOT_CONVERGE
    }

    private final Status status;

    private final int iterationCount;

    private final List<String> referenceBusIds;

    private final double objectiveFunctionValue;

    private final List<BusResult> busResults;

    private final List<BranchResult> branchResults;

    /**
     * @param status whether the iteration converged
     * @param iterationCount how many iterations were run
     * @param referenceBusIds the buses whose angles were held fixed, one per synchronous component
     * @param objectiveFunctionValue the weighted sum of squared residuals at the solution
     * @param busResults the estimated state, one entry per bus
     * @param branchResults the flows the state implies, one entry per branch connected at both ends
     */
    public WlsResult(Status status, int iterationCount, List<String> referenceBusIds, double objectiveFunctionValue,
                     List<BusResult> busResults, List<BranchResult> branchResults) {
        this.status = Objects.requireNonNull(status);
        this.iterationCount = iterationCount;
        this.referenceBusIds = List.copyOf(Objects.requireNonNull(referenceBusIds));
        this.objectiveFunctionValue = objectiveFunctionValue;
        this.busResults = List.copyOf(Objects.requireNonNull(busResults));
        this.branchResults = List.copyOf(Objects.requireNonNull(branchResults));
    }

    public Status getStatus() {
        return status;
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

    /**
     * The weighted sum of squared residuals at the solution, which is the quantity a least squares
     * estimator minimizes and what a chi-square test is computed from.
     */
    public double getObjectiveFunctionValue() {
        return objectiveFunctionValue;
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
     * The estimated state at one bus, in the units IIDM uses.
     *
     * @param busId the identifier of the bus
     * @param voltageLevelId the identifier of the voltage level holding the bus
     * @param v the voltage magnitude, in kV
     * @param angle the voltage angle, in degrees
     */
    public record BusResult(String busId, String voltageLevelId, double v, double angle) {

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
}
