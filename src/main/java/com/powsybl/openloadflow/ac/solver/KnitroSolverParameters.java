/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class KnitroSolverParameters {

    private static final int GRADIENT_COMPUTATION_MODE_DEFAULT = 2; // Knitro computes gradients by forward finite differences

    private int gradientComputationMode = GRADIENT_COMPUTATION_MODE_DEFAULT;

    public KnitroSolverParameters() {
    }

    public int getGradientComputationMode() {
        return gradientComputationMode;
    }

    public void setGradientComputationMode(int gradientComputationMode) {
        if (gradientComputationMode < 1 || gradientComputationMode > 3) {
            throw new IllegalArgumentException("Knitro gradient computation mode must be between 1 and 3");
        }
        this.gradientComputationMode = gradientComputationMode;
    }

    @Override
    public String toString() {
        return "KnitroSolverParameters(" +
                "gradientComputationMode=" + gradientComputationMode +
                ')';
    }
}
