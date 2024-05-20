/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;
//import com.powsybl.openloadflow.ac.solver.DefaultNewtonRaphsonStoppingCriteria;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class KnitroSolverParameters {

    public static final int GRADIENT_COMPUTATION_MODE_DEFAULT = 2; // Knitro computes gradients by forward finite differences
    public static final double DEFAULT_CONV_EPS_PER_EQ = NewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ;

    private int gradientComputationMode = GRADIENT_COMPUTATION_MODE_DEFAULT;

    private double convEpsPerEq = DEFAULT_CONV_EPS_PER_EQ;

    public KnitroSolverParameters() {
    }

    public int getGradientComputationMode() {
        return gradientComputationMode;
    }

    public double getConvEpsPerEq() {
        return convEpsPerEq;
    }

    public void setGradientComputationMode(int gradientComputationMode) {
        if (gradientComputationMode < 1 || gradientComputationMode > 3) {
            throw new IllegalArgumentException("Knitro gradient computation mode must be between 1 and 3");
        }
        this.gradientComputationMode = gradientComputationMode;
    }

    public void setConvEpsPerEq(double convEpsPerEq) {
        if (convEpsPerEq<=0) {
            throw new IllegalArgumentException("Knitro final relative stopping tolerance for the feasibility error must be strictly greater than 0");
        }
        this.convEpsPerEq = convEpsPerEq;
    }

    @Override
    public String toString() {
        return "KnitroSolverParameters(" +
                "gradientComputationMode=" + gradientComputationMode +
                "; " + "convEpsPerEq=" + convEpsPerEq +
                ')';
    }
}
