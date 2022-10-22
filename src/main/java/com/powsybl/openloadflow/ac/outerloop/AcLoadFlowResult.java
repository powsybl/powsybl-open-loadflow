/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowResult {

    public static AcLoadFlowResult createNoCalculationResult(LfNetwork network) {
        return new AcLoadFlowResult(network, 0, 0, NewtonRaphsonStatus.NO_CALCULATION, Double.NaN, Double.NaN);
    }

    private final LfNetwork network;

    private final int outerLoopIterations;

    private final int newtonRaphsonIterations;

    private final NewtonRaphsonStatus newtonRaphsonStatus;

    private final double slackBusActivePowerMismatch;

    private final double distributedActivePower;

    public AcLoadFlowResult(LfNetwork network, int outerLoopIterations, int newtonRaphsonIterations, NewtonRaphsonStatus newtonRaphsonStatus,
                            double slackBusActivePowerMismatch, double distributedActivePower) {
        this.network = Objects.requireNonNull(network);
        this.outerLoopIterations = outerLoopIterations;
        this.newtonRaphsonIterations = newtonRaphsonIterations;
        this.newtonRaphsonStatus = newtonRaphsonStatus;
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
        this.distributedActivePower = distributedActivePower;
    }

    public LfNetwork getNetwork() {
        return network;
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

    public double getSlackBusActivePowerMismatch() {
        return slackBusActivePowerMismatch;
    }

    public double getDistributedActivePower() {
        return distributedActivePower;
    }

    @Override
    public String toString() {
        return "AcLoadFlowResult(outerLoopIterations=" + outerLoopIterations
                + ", newtonRaphsonIterations=" + newtonRaphsonIterations
                + ", newtonRaphsonStatus=" + newtonRaphsonStatus
                + ", slackBusActivePowerMismatch=" + slackBusActivePowerMismatch * PerUnit.SB
                + ", distributedActivePower=" + distributedActivePower * PerUnit.SB
                + ")";
    }
}
