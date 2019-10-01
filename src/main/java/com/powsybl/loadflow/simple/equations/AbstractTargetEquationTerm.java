/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import java.util.Collections;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractTargetEquationTerm implements EquationTerm {

    private final List<Variable> variables;

    private double target;

    protected AbstractTargetEquationTerm(int num, VariableType variableType, VariableSet variableSet) {
        variables = Collections.singletonList(variableSet.getVariable(num, variableType));
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        target = x[variables.get(0).getColumn()];
    }

    @Override
    public double eval() {
        return target;
    }

    @Override
    public double der(Variable variable) {
        return 1;
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public double rhs(Variable variable) {
        return 0;
    }
}
