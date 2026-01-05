/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfDcNode;
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

    protected final Variable<AcVariableType> qAcVar;

    protected final Variable<AcVariableType> vAcVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected final double idleLoss;

    protected final double switchingLoss;

    protected final double resistiveLoss;

    protected List<Double> lossFactors;

    protected double dcNominalV;

    protected LfDcNode dcNode1;

    protected LfDcNode dcNode2;

    protected AbstractConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcNode dcNode1, LfDcNode dcNode2, double nominalV, VariableSet<AcVariableType> variableSet) {
        super(converter);
        Objects.requireNonNull(converter);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_NODE_V;
        AcVariableType pType = AcVariableType.CONV_P_AC;
        AcVariableType qType = AcVariableType.CONV_Q_AC;
        LfBus bus = converter.getBus1();
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        v2Var = variableSet.getVariable(dcNode2.getNum(), vType);
        pAcVar = variableSet.getVariable(converter.getNum(), pType);
        qAcVar = variableSet.getVariable(converter.getNum(), qType);
        vAcVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        variables.add(v1Var);
        variables.add(v2Var);
        variables.add(pAcVar);
        variables.add(qAcVar);
        variables.add(vAcVar);
        lossFactors = converter.getLossFactors();
        dcNominalV = nominalV;
        this.dcNode1 = dcNode1;
        this.dcNode2 = dcNode2;
        this.idleLoss = lossFactors.get(0) / PerUnit.SB;
        this.switchingLoss = lossFactors.get(1) * PerUnit.ib(bus.getNominalV()) / PerUnit.SB;
        this.resistiveLoss = lossFactors.get(2) / PerUnit.zb(bus.getNominalV()) / 3;
    }

    protected double v1() {
        return sv.get(v1Var.getRow()) * dcNode1.getNominalV() / dcNominalV;
    }

    protected double v2() {
        return sv.get(v2Var.getRow()) * dcNode2.getNominalV() / dcNominalV;
    }

    protected double pAc() {
        return sv.get(pAcVar.getRow());
    }

    protected double qAc() {
        return sv.get(qAcVar.getRow());
    }

    protected double vAc() {
        return sv.get(vAcVar.getRow());
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
