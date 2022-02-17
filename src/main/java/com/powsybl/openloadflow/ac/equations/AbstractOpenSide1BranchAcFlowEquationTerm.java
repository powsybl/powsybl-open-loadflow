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

    protected AbstractOpenSide1BranchAcFlowEquationTerm(BranchVector branchVec, int num, AcVariableType variableType,
                                                        LfBus bus, VariableSet<AcVariableType> variableSet,
                                                        boolean deriveA1, boolean deriveR1) {
        super(branchVec, num);
        variables = List.of(variableSet.getVariable(bus.getNum(), variableType));
        if (deriveA1 || deriveR1) {
            throw new IllegalArgumentException("Variable A1 or R1 on open branch not supported: " + branchVec.get(num).getId());
        }
    }

    protected double shunt() {
        double sinKsi = FastMath.sin(branchVec.ksi(num));
        double cosKsi = FastMath.cos(branchVec.ksi(num));
        return (branchVec.g1(num) + branchVec.y(num) * sinKsi) * (branchVec.g1(num) + branchVec.y(num) * sinKsi)
                + (-branchVec.b1(num) + branchVec.y(num) * cosKsi) * (-branchVec.b1(num) + branchVec.y(num) * cosKsi);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
