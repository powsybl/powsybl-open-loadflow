/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import net.jafama.FastMath;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ExponentialLoadModelEquationTerm extends AbstractElementEquationTerm<LfBus, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> vVar;

    private final List<Variable<AcVariableType>> variables;

    private final double exponent;

    public ExponentialLoadModelEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, double exponent) {
        super(bus);
        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        variables = List.of(vVar);
        this.exponent = exponent;
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    protected double v() {
        return sv.get(vVar.getRow());
    }

    public static double c(double v, double exponent) {
        return FastMath.pow(v, exponent);
    }

    public static double dcdv(double v, double exponent) {
        return exponent * FastMath.pow(v, exponent - 1);
    }

    @Override
    public double eval() {
        return c(v(), exponent);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dcdv(v(), exponent);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_exp_load";
    }
}
