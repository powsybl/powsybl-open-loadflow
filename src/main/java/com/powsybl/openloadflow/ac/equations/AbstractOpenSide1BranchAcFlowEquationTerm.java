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
import net.jafama.FastMath;

import java.util.List;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
abstract class AbstractOpenSide1BranchAcFlowEquationTerm extends AbstractAcBranchEquationTerm {

    protected final List<Variable<AcVariableType>> variables;

    protected AbstractOpenSide1BranchAcFlowEquationTerm(VectorizedBranches branches, int num, AcVariableType variableType,
                                                        LfBus bus, VariableSet<AcVariableType> variableSet,
                                                        boolean deriveA1, boolean deriveR1) {
        super(branches, num);
        variables = List.of(variableSet.getVariable(bus.getNum(), variableType));
        if (deriveA1 || deriveR1) {
            throw new IllegalArgumentException("Variable A1 or R1 on open branch not supported: " + branches.get(num).getId());
        }
    }

    protected double shunt() {
        double sinKsi = FastMath.sin(branches.ksi(num));
        double cosKsi = FastMath.cos(branches.ksi(num));
        return (branches.g1(num) + branches.y(num) * sinKsi) * (branches.g1(num) + branches.y(num) * sinKsi)
                + (-branches.b1(num) + branches.y(num) * cosKsi) * (-branches.b1(num) + branches.y(num) * cosKsi);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
