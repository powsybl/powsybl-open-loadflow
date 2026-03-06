/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.dcnetwork;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractConverterDcCurrentEquationTerm extends AbstractElementEquationTerm<LfVoltageSourceConverter, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> pAcVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected final double idleLoss;

    protected final double switchingLoss;

    protected final double resistiveLoss;

    protected List<Double> lossFactors;

    protected double dcNominalV;

    protected LfDcBus dcBus1;

    protected LfDcBus dcBus2;

    protected AbstractConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcBus dcBus1, LfDcBus dcBus2, double nominalV, VariableSet<AcVariableType> variableSet) {
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
        lossFactors = converter.getLossFactors();
        dcNominalV = nominalV;
        this.dcBus1 = dcBus1;
        this.dcBus2 = dcBus2;
        this.idleLoss = lossFactors.get(0) / PerUnit.SB;
        this.switchingLoss = lossFactors.get(1) * 1000d / nominalV;
        this.resistiveLoss = lossFactors.get(2) / PerUnit.zb(nominalV);
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
