/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.equations.ac.dcnetwork;

import com.powsybl.na.commons.PerUnit;
import com.powsybl.na.equations.AbstractElementEquationTerm;
import com.powsybl.na.equations.Variable;
import com.powsybl.na.equations.VariableSet;
import com.powsybl.na.equations.ac.AcEquationType;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.network.LfDcBranch;
import com.powsybl.na.network.LfDcBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractClosedDcBranchFlowEquationTerm extends AbstractElementEquationTerm<LfDcBranch, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected final double r;

    protected AbstractClosedDcBranchFlowEquationTerm(LfDcBranch dcBranch, LfDcBus dcBus1, LfDcBus dcBus2, VariableSet<AcVariableType> variableSet) {
        super(dcBranch);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_BUS_V;
        v1Var = variableSet.getVariable(dcBus1.getNum(), vType);
        v2Var = variableSet.getVariable(dcBus2.getNum(), vType);
        variables.add(v1Var);
        variables.add(v2Var);
        r = dcBranch.getR() / (dcBus1.getNominalV() * dcBus2.getNominalV() / PerUnit.SB);
    }

    protected double v1() {
        return sv.get(v1Var.getRow());
    }

    protected double v2() {
        return sv.get(v2Var.getRow());
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}

