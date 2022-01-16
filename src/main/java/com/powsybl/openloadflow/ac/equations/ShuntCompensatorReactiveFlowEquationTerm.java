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

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ShuntCompensatorReactiveFlowEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    private final LfShunt shunt;

    private final Variable<AcVariableType> vVar;

    private Variable<AcVariableType> bVar = null;

    private final List<Variable<AcVariableType>> variables;

    public ShuntCompensatorReactiveFlowEquationTerm(LfShunt shunt, LfBus bus, VariableSet<AcVariableType> variableSet, boolean deriveB) {
        this.shunt = Objects.requireNonNull(shunt);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        if (deriveB) {
            bVar = variableSet.getVariable(shunt.getNum(), AcVariableType.SHUNT_B);
            variables = List.of(vVar, bVar);
        } else {
            variables = List.of(vVar);
        }
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

    private double v() {
        return stateVector.get(vVar.getRow());
    }

    private double b() {
        return bVar != null ? stateVector.get(bVar.getRow()) : shunt.getB();
    }

    private double q() {
        return  -b() * v() * v();
    }

    private double dqdv() {
        return -2 * b() * v();
    }

    private double dqdb() {
        return -v() * v();
    }

    @Override
    public double eval() {
        return q();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dqdv();
        } else if (variable.equals(bVar)) {
            return dqdb();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_shunt";
    }
}
