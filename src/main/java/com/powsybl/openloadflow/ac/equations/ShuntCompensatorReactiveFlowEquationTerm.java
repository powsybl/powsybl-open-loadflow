/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfShunt;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ShuntCompensatorReactiveFlowEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    private final LfShunt shunt;

    private final Variable<AcVariableType> vVar;

    private final List<Variable<AcVariableType>> variables;

    private final double b;

    private double q;

    private double dqdv;

    public ShuntCompensatorReactiveFlowEquationTerm(LfShunt shunt, LfBus bus, VariableSet<AcVariableType> variableSet) {
        this.shunt = Objects.requireNonNull(shunt);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        variables = Collections.singletonList(vVar);
        b = shunt.getB();
    }

    @Override
    public ElementType getElementType() {
        return ElementType.SHUNT_COMPENSATOR;
    }

    @Override
    public int getElementNum() {
        return shunt.getNum();
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x, BranchVector<AcVariableType, AcEquationType> vec) {
        double v = x[vVar.getRow()];
        q = -b * v * v;
        dqdv = -2 * b * v;
    }

    @Override
    public double eval() {
        return q;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        if (variable.equals(vVar)) {
            return dqdv;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public double rhs() {
        return 0;
    }

    @Override
    protected String getName() {
        return "ac_q_shunt";
    }
}
