/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.List;
import java.util.Objects;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
public abstract class AbstractHvdcAcEmulationDcFlowEquationTerm extends AbstractElementEquationTerm<LfHvdc, DcVariableType, DcEquationType> {

    protected final Variable<DcVariableType> ph1Var;
    protected final Variable<DcVariableType> ph2Var;
    protected final List<Variable<DcVariableType>> variables;
    protected final LfHvdc hvdc;
    protected final double k;

    protected AbstractHvdcAcEmulationDcFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet) {
        super(hvdc);
        ph1Var = variableSet.getVariable(bus1.getNum(), DcVariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), DcVariableType.BUS_PHI);
        variables = List.of(ph1Var, ph2Var);
        this.hvdc = hvdc;
        k = this.hvdc.getAcEmulationControl().getDroop() * 180 / Math.PI;
    }

    @Override
    public List<Variable<DcVariableType>> getVariables() {
        return variables;
    }

    protected double ph1() {
        return ph1(sv);
    }

    protected double ph1(StateVector sv) {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return ph2(sv);
    }

    protected double ph2(StateVector sv) {
        return sv.get(ph2Var.getRow());
    }

    public Variable<DcVariableType> getPh1Var() {
        return ph1Var;
    }

    public Variable<DcVariableType> getPh2Var() {
        return ph2Var;
    }

    /**
     * Droop coefficient of the AC emulation control, in per unit per radian: the linear mode couples the two buses
     * exactly like a DC branch of power {@code k}.
     */
    public double getK() {
        return k;
    }

    @Override
    public double calculateSensi(DenseMatrix dx, int column) {
        Objects.requireNonNull(dx);
        double dph1 = dx.get(ph1Var.getRow(), column);
        double dph2 = dx.get(ph2Var.getRow(), column);
        return k * (dph1 - dph2);
    }
}
