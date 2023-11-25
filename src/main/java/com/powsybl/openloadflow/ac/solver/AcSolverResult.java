/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.solver;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcSolverResult {

    private final int iterations;

    private final AcSolverStatus status;

    private final double slackBusActivePowerMismatch;

    public AcSolverResult(AcSolverStatus status, int iterations, double slackBusActivePowerMismatch) {
        if (iterations < 0) {
            throw new IllegalArgumentException("Invalid iteration value: " + iterations);
        }
        this.status = Objects.requireNonNull(status);
        this.iterations = iterations;
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
    }

    public AcSolverStatus getStatus() {
        return status;
    }

    public int getIterations() {
        return iterations;
    }

    public double getSlackBusActivePowerMismatch() {
        return slackBusActivePowerMismatch;
    }
}
