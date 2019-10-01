/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.outerloop;

import com.powsybl.loadflow.simple.ac.nr.NewtonRaphsonStatus;
import com.powsybl.loadflow.simple.network.PerUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowResult {

    private final int outerLoopIterations;

    private final int newtonRaphsonIterations;

    private final NewtonRaphsonStatus newtonRaphsonStatus;

    private final double slackBusActivePowerMismatch;

    public AcLoadFlowResult(int outerLoopIterations, int newtonRaphsonIterations, NewtonRaphsonStatus newtonRaphsonStatus,
                            double slackBusActivePowerMismatch) {
        this.outerLoopIterations = outerLoopIterations;
        this.newtonRaphsonIterations = newtonRaphsonIterations;
        this.newtonRaphsonStatus = newtonRaphsonStatus;
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
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

    @Override
    public String toString() {
        return "AcLoadFlowResult(outerLoopIterations=" + outerLoopIterations
                + ", newtonRaphsonIterations=" + newtonRaphsonIterations
                + ", newtonRaphsonStatus=" + newtonRaphsonStatus
                + ", slackBusActivePowerMismatch=" + slackBusActivePowerMismatch * PerUnit.SB
                + ")";
    }
}
