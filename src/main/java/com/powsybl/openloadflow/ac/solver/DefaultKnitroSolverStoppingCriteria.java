/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class DefaultKnitroSolverStoppingCriteria implements KnitroSolverStoppingCriteria {

    public final double convEpsPerEq;

    public DefaultKnitroSolverStoppingCriteria() {
        this(KnitroSolverStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ);
    }

    public DefaultKnitroSolverStoppingCriteria(double convEpsPerEq) {
        this.convEpsPerEq = convEpsPerEq;
    }

    public double getConvEpsPerEq() {
        return convEpsPerEq;
    }

//    @Override
//    public TestResult test(double[] fx, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
//        // calculate norm L2 of equations mismatch vector
//        double norm = Vectors.norm2(fx);
//        boolean stop = norm < FastMath.sqrt(convEpsPerEq * convEpsPerEq * fx.length);
//        return new TestResult(stop, norm);
//    }
}
