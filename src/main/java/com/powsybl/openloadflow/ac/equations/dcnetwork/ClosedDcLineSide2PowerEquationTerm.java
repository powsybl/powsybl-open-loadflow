/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.dcnetwork;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfDcLine;
import com.powsybl.openloadflow.network.LfDcNode;

import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class ClosedDcLineSide2PowerEquationTerm extends AbstractClosedDcLineFlowEquationTerm {

    public ClosedDcLineSide2PowerEquationTerm(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet);
    }

    public static double p2(double v1, double v2, double r) {
        return -v2 * (v1 - v2) / r;
    }

    public static double dp2dv1(double v2, double r) {
        return -v2 / r;
    }

    public static double dp2dv2(double v1, double v2, double r) {
        return (2 * v2 - v1) / r;
    }

    @Override
    public double eval() {
        return p2(v1(), v2(), r);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp2dv1(v2(), r);
        } else if (variable.equals(v2Var)) {
            return dp2dv2(v1(), v2(), r);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "dc_p_closed_2";
    }
}

