/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractNamedEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.List;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public abstract class AbstractHvdcAcEmulationFlowEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final List<Variable<AcVariableType>> variables;

    protected final double k;

    protected final double p0;

    protected LfHvdc hvdc;

    protected final double lossFactor1;

    protected final double lossFactor2;

    protected AbstractHvdcAcEmulationFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        ph1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI);
        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
        v2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
        variables = List.of(ph1Var, ph2Var);
        this.hvdc = hvdc;
        k = hvdc.getDroop() * 180 / Math.PI;
        p0 = hvdc.getP0();
        lossFactor1 = hvdc.getConverterStation1().getLossFactor() / 100;
        lossFactor2 = hvdc.getConverterStation2().getLossFactor() / 100;
    }

    protected double ph1() {
        return stateVector.get(ph1Var.getRow());
    }

    protected double ph2() {
        return stateVector.get(ph2Var.getRow());
    }

    protected double v1() {
        return stateVector.get(v1Var.getRow());
    }

    protected double v2() {
        return stateVector.get(v2Var.getRow());
    }

    protected double getLossMultiplier() {
        return (1 - lossFactor1) * (1 - lossFactor2);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public ElementType getElementType() {
        return ElementType.HVDC;
    }

    @Override
    public int getElementNum() {
        return hvdc.getNum();
    }
}
