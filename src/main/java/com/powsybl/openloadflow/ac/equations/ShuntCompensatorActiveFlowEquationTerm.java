/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfShunt;

import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ShuntCompensatorActiveFlowEquationTerm extends AbstractShuntCompensatorEquationTerm {

    private final Set<Variable<AcVariableType>> variables;

    public ShuntCompensatorActiveFlowEquationTerm(LfShunt shunt, LfBus bus, VariableSet<AcVariableType> variableSet) {
        super(shunt, bus, variableSet);
        variables = Set.of(vVar);
    }

    @Override
    public Set<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    private double g() {
        return shunt.getG();
    }

    private static double p(double v, double g) {
        return g * v * v;
    }

    private static double dpdv(double v, double g) {
        return 2 * g * v;
    }

    @Override
    public double eval() {
        return p(v(), g());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dpdv(v(), g());
        }
        return 0;
    }

    @Override
    protected String getName() {
        return "ac_p_shunt";
    }
}
