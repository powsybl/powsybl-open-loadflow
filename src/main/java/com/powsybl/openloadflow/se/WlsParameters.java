/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

/**
 * Settings of the weighted-least-squares iteration.
 *
 * @param maxIterations the iteration cap; the estimate is reported as not converged if reached
 * @param tolerance     convergence threshold on the largest state change of an iteration, per unit
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public record WlsParameters(int maxIterations, double tolerance) {

    public static final int DEFAULT_MAX_ITERATIONS = 30;

    public static final double DEFAULT_TOLERANCE = 1e-6;

    public WlsParameters {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("Maximum iteration count must be at least 1, was " + maxIterations);
        }
        if (tolerance <= 0) {
            throw new IllegalArgumentException("Tolerance must be positive, was " + tolerance);
        }
    }

    public WlsParameters() {
        this(DEFAULT_MAX_ITERATIONS, DEFAULT_TOLERANCE);
    }
}
