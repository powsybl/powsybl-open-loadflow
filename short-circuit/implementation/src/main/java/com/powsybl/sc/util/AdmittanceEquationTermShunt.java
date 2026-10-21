/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;

import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AdmittanceEquationTermShunt extends AbstractElementEquationTerm<LfBus, VariableType, EquationType> implements LinearEquationTerm {

    protected final Variable<VariableType> v1rVar;

    protected final Variable<VariableType> v1iVar;

    protected final List<Variable<VariableType>> variables;

    protected double g;

    protected double b;

    protected boolean isReal;
    //          v1r   v1i
    //           |     |
    //Eq1r - [ y1r1r y1r1i ]   [ g  -b ]
    //Eq1i - [ y1i1r y1i1i ] = [ b   g ] =

    public AdmittanceEquationTermShunt(double g, double b, LfBus bus, VariableSet<VariableType> variableSet, boolean isReal) {
        super(bus);
        Objects.requireNonNull(variableSet);

        v1rVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_VR);
        v1iVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_VI);

        variables = List.of(v1rVar, v1iVar);

        this.g = g;
        this.b = b;
        this.isReal = isReal;

    }

    @Override
    public ElementType getElementType() {
        return ElementType.BUS;
    }

    @Override
    public int getElementNum() {
        return element.getNum();
    }

    public List<Variable<VariableType>> getVariables() {
        return variables;
    }

    @Override
    public double eval() {
        throw new UnsupportedOperationException("Not needed");
    }

    @Override
    public double der(Variable<VariableType> variable) {
        throw new UnsupportedOperationException("Not needed");
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
    public double getCoefficient(Variable<VariableType> variable) {
        if (variable.equals(v1rVar)) {
            if (isReal) {
                return g;
            } else {
                return b;
            }
        } else if (variable.equals(v1iVar)) {
            if (isReal) {
                return -b;
            } else {
                return g;
            }
        } else {
            throw new IllegalArgumentException("Unknown variable " + variable);
        }
    }

    @Override
    protected String getName() {
        return "yshunt";
    }
}
