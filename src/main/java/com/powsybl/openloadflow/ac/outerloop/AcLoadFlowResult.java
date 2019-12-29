/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowResult {

    private final List<LfNetwork> networks;

    private final int outerLoopIterations;

    private final int newtonRaphsonIterations;

    private final NewtonRaphsonStatus newtonRaphsonStatus;

    private final double slackBusActivePowerMismatch;

    public AcLoadFlowResult(List<LfNetwork> networks, int outerLoopIterations, int newtonRaphsonIterations, NewtonRaphsonStatus newtonRaphsonStatus,
                            double slackBusActivePowerMismatch) {
        this.networks = Objects.requireNonNull(networks);
        this.outerLoopIterations = outerLoopIterations;
        this.newtonRaphsonIterations = newtonRaphsonIterations;
        this.newtonRaphsonStatus = newtonRaphsonStatus;
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
    }

    public List<LfNetwork> getNetworks() {
        return networks;
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
