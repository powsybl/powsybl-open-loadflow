/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.List;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public abstract class AbstractHvdcAcEmulationFlowEquationTerm<T extends Enum<T> & Quantity, U extends Enum<U> & Quantity> extends AbstractElementEquationTerm<LfHvdc, T, U> {

    protected final Variable<T> ph1Var;

    protected final Variable<T> ph2Var;

    protected final List<Variable<T>> variables;

    protected final double k;

    protected final double p0;

    protected final double lossFactor1;

    protected final double lossFactor2;

    protected AbstractHvdcAcEmulationFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<T> variableSet) {
        super(hvdc);
        ph1Var = variableSet.getVariable(bus1.getNum(), getBusPhi());
        ph2Var = variableSet.getVariable(bus2.getNum(), getBusPhi());
        variables = List.of(ph1Var, ph2Var);
        k = hvdc.getDroop() * 180 / Math.PI;
        p0 = hvdc.getP0();
        lossFactor1 = hvdc.getConverterStation1().getLossFactor() / 100;
        lossFactor2 = hvdc.getConverterStation2().getLossFactor() / 100;
    }

    /**
     * @return TODO documentation
     */
    protected abstract T getBusPhi();

    protected double ph1() {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return sv.get(ph2Var.getRow());
    }

    protected static double getLossMultiplier(double lossFactor1, double lossFactor2) {
        return (1 - lossFactor1) * (1 - lossFactor2);
    }

    /**
     * @return TODO documentation
     */
    protected abstract boolean isController(double ph1, double ph2);

    protected double p1(double p0, double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return (isController(ph1, ph2) ? 1 : getLossMultiplier(lossFactor1, lossFactor2)) * (p0 + k * (ph1 - ph2));
    }

    protected double p2(double p0, double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return -(isController(ph1, ph2) ? 1 : getLossMultiplier(lossFactor1, lossFactor2)) * (p0 + k * (ph1 - ph2));
    }

    protected double dp1dph1(double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return (isController(ph1, ph2) ? 1 : getLossMultiplier(lossFactor1, lossFactor2)) * k;
    }

    protected double dp1dph2(double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return -dp1dph1(k, lossFactor1, lossFactor2, ph1, ph2);
    }

    protected double dp2dph1(double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return -(isController(ph1, ph2) ? 1 : getLossMultiplier(lossFactor1, lossFactor2)) * k;
    }

    protected double dp2dph2(double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return -dp2dph1(k, lossFactor1, lossFactor2, ph1, ph2);
    }

    @Override
    public List<Variable<T>> getVariables() {
        return variables;
    }

    @Override
    public boolean hasRhs() {
        return false;
    }
}
