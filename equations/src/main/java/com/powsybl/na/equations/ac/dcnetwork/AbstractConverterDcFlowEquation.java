/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.equations.ac.dcnetwork;

import com.powsybl.na.equations.AbstractElementEquationTerm;
import com.powsybl.na.equations.Variable;
import com.powsybl.na.equations.VariableSet;
import com.powsybl.na.equations.ac.AcEquationType;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.network.LfDcBus;
import com.powsybl.na.network.LfVoltageSourceConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractConverterDcFlowEquation extends AbstractElementEquationTerm<LfVoltageSourceConverter, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> pAcVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected double dcNominalV;

    protected LfDcBus dcBus1;

    protected LfDcBus dcBus2;

    protected AbstractConverterDcFlowEquation(LfVoltageSourceConverter converter, LfDcBus dcBus1, LfDcBus dcBus2, double nominalV, VariableSet<AcVariableType> variableSet) {
        super(converter);
        Objects.requireNonNull(converter);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_BUS_V;
        AcVariableType pType = AcVariableType.CONV_P_AC;
        v1Var = variableSet.getVariable(dcBus1.getNum(), vType);
        v2Var = variableSet.getVariable(dcBus2.getNum(), vType);
        pAcVar = variableSet.getVariable(converter.getNum(), pType);
        variables.add(v1Var);
        variables.add(v2Var);
        variables.add(pAcVar);
        dcNominalV = nominalV;
        this.dcBus1 = dcBus1;
        this.dcBus2 = dcBus2;
    }

    protected double v1() {
        return sv.get(v1Var.getRow()) * dcBus1.getNominalV() / dcNominalV;
    }

    protected double v2() {
        return sv.get(v2Var.getRow()) * dcBus2.getNominalV() / dcNominalV;
    }

    protected double pAc() {
        return sv.get(pAcVar.getRow());
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
