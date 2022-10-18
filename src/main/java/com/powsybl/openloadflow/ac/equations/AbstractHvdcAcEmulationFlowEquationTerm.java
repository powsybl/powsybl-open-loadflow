/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
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
import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public abstract class AbstractHvdcAcEmulationFlowEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    protected final LfHvdc hvdc;

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    protected final List<Variable<AcVariableType>> variables;

    protected final double k;

    protected final double p0;

    protected final double lossFactor1;

    protected final double lossFactor2;

    protected AbstractHvdcAcEmulationFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(!Objects.requireNonNull(hvdc).isDisabled());
        this.hvdc = hvdc;
        ph1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI);
        variables = List.of(ph1Var, ph2Var);
        k = hvdc.getDroop() * 180 / Math.PI;
        p0 = hvdc.getP0();
        lossFactor1 = hvdc.getConverterStation1().getLossFactor() / 100;
        lossFactor2 = hvdc.getConverterStation2().getLossFactor() / 100;
    }

    protected double ph1() {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return sv.get(ph2Var.getRow());
    }

    protected static double getLossMultiplier(double lossFactor1, double lossFactor2) {
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
