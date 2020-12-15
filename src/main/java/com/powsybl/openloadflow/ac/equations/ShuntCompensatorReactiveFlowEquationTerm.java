/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfShunt;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ShuntCompensatorReactiveFlowEquationTerm extends AbstractNamedEquationTerm {

    private final LfShunt shunt;

    private final Variable vVar;

    private Variable bVar = null;

    private final List<Variable> variables;

    private double b;

    private double q;

    private double dqdv;

    private double dqdb;

    public ShuntCompensatorReactiveFlowEquationTerm(LfShunt shunt, LfBus bus, VariableSet variableSet, boolean deriveB) {
        this.shunt = Objects.requireNonNull(shunt);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        vVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_V);
        variables = Collections.singletonList(vVar);
        if (deriveB) {
            bVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_B);
            variables.add(bVar);
        }
        b = shunt.getB();
    }

    @Override
    public SubjectType getSubjectType() {
        return SubjectType.SHUNT_COMPENSATOR;
    }

    @Override
    public int getSubjectNum() {
        return shunt.getNum();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v = x[vVar.getRow()];
        q = -b * v * v;
        dqdv = -2 * b * v;
        dqdb = -v * v;
        b = bVar != null && bVar.isActive() ? x[bVar.getRow()] : shunt.getB();
    }

    @Override
    public double eval() {
        return q;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dqdv;
        }
        if (variable.equals(bVar)) {
            return dqdb;
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
