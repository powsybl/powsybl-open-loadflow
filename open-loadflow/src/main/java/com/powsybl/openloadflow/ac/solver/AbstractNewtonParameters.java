/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractNewtonParameters<T extends AbstractNewtonParameters<T>> {

    protected int maxIterations;

    public static int checkMaxIteration(int maxIteration) {
        if (maxIteration < 1) {
            throw new IllegalArgumentException("Invalid max iteration value: " + maxIteration);
        }
        return maxIteration;
    }

    protected AbstractNewtonParameters(int defaultMaxIterations) {
        this.maxIterations = defaultMaxIterations;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public T setMaxIterations(int maxIterations) {
        this.maxIterations = checkMaxIteration(maxIterations);
        return (T) this;
    }
}
