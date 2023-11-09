/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.lf.AbstractLoadFlowResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcLoadFlowResult extends AbstractLoadFlowResult {

    public static AcLoadFlowResult createNoCalculationResult(LfNetwork network) {
        return new AcLoadFlowResult(network, 0, 0, NewtonRaphsonStatus.NO_CALCULATION, OuterLoopStatus.STABLE, Double.NaN, Double.NaN);
    }

    private final int outerLoopIterations;

    private final int newtonRaphsonIterations;

    private final NewtonRaphsonStatus newtonRaphsonStatus;

    private final OuterLoopStatus outerLoopStatus;

    private final double distributedActivePower;

    public AcLoadFlowResult(LfNetwork network, int outerLoopIterations, int newtonRaphsonIterations,
                            NewtonRaphsonStatus newtonRaphsonStatus, OuterLoopStatus outerLoopStatus,
                            double slackBusActivePowerMismatch, double distributedActivePower) {
        super(network, slackBusActivePowerMismatch);
        this.outerLoopIterations = outerLoopIterations;
        this.newtonRaphsonIterations = newtonRaphsonIterations;
        this.newtonRaphsonStatus = Objects.requireNonNull(newtonRaphsonStatus);
        this.outerLoopStatus = Objects.requireNonNull(outerLoopStatus);
        this.distributedActivePower = distributedActivePower;
    }

    public int getOuterLoopIterations() {
        return outerLoopIterations;
    }

    public int getNewtonRaphsonIterations() {
        return newtonRaphsonIterations;
    }

    public NewtonRaphsonStatus getNewtonRaphsonStatus() {
        return newtonRaphsonStatus;
    }

    public OuterLoopStatus getOuterLoopStatus() {
        return outerLoopStatus;
    }

    public double getDistributedActivePower() {
        return distributedActivePower;
    }

    public boolean isOk() {
        return newtonRaphsonStatus == NewtonRaphsonStatus.CONVERGED && outerLoopStatus == OuterLoopStatus.STABLE;
    }

    public boolean isWithNetworkUpdate() {
        // do not reset state in case all results are ok and no NR iterations because it means that the network was
        // not changed and no calculation update was needed.
        return isOk() && newtonRaphsonIterations > 0;
    }

    public LoadFlowResult.ComponentResult.Status toComponentResultStatus() {
        if (getOuterLoopStatus() == OuterLoopStatus.UNSTABLE) {
            return LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
        } else {
            return switch (getNewtonRaphsonStatus()) {
                case CONVERGED -> LoadFlowResult.ComponentResult.Status.CONVERGED;
                case MAX_ITERATION_REACHED -> LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
                case SOLVER_FAILED -> LoadFlowResult.ComponentResult.Status.SOLVER_FAILED;
                default -> LoadFlowResult.ComponentResult.Status.FAILED;
            };
        }
    }

    @Override
    public String toString() {
        return "AcLoadFlowResult(outerLoopIterations=" + outerLoopIterations
                + ", newtonRaphsonIterations=" + newtonRaphsonIterations
                + ", newtonRaphsonStatus=" + newtonRaphsonStatus
                + ", outerLoopStatus=" + outerLoopStatus
                + ", slackBusActivePowerMismatch=" + slackBusActivePowerMismatch * PerUnit.SB
                + ", distributedActivePower=" + distributedActivePower * PerUnit.SB
                + ")";
    }
}
