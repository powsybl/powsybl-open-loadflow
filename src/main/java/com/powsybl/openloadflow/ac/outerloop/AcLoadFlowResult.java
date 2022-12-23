/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.lf.AbstractLoadFlowResult;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowResult extends AbstractLoadFlowResult {

    public static AcLoadFlowResult createNoCalculationResult(LfNetwork network) {
        return new AcLoadFlowResult(network, 0, 0, NewtonRaphsonStatus.NO_CALCULATION, Double.NaN, Double.NaN);
    }

    private final int outerLoopIterations;

    private final int newtonRaphsonIterations;

    private final NewtonRaphsonStatus newtonRaphsonStatus;

    private final double distributedActivePower;

    public AcLoadFlowResult(LfNetwork network, int outerLoopIterations, int newtonRaphsonIterations, NewtonRaphsonStatus newtonRaphsonStatus,
                            double slackBusActivePowerMismatch, double distributedActivePower) {
        super(network, slackBusActivePowerMismatch);
        this.outerLoopIterations = outerLoopIterations;
        this.newtonRaphsonIterations = newtonRaphsonIterations;
        this.newtonRaphsonStatus = newtonRaphsonStatus;
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
