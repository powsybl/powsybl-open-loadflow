/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.nr;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NewtonRaphsonResult {

    private int iteration;

    private NewtonRaphsonStatus status;

    private double slackBusActivePowerMismatch;

    public NewtonRaphsonResult(NewtonRaphsonStatus status, int iteration, double slackBusActivePowerMismatch) {
        if (iteration < 0) {
            throw new IllegalArgumentException("Invalid iteration value: " + iteration);
        }
        this.status = Objects.requireNonNull(status);
        this.iteration = iteration;
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
    }

    public NewtonRaphsonStatus getStatus() {
        return status;
    }

    public int getIteration() {
        return iteration;
    }

    public double getSlackBusActivePowerMismatch() {
        return slackBusActivePowerMismatch;
    }
}
