/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.Objects;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public abstract class AbstractHvdcAcEmulationSide2ActiveFlowEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        extends AbstractHvdcAcEmulationFlowEquationTerm<V, E> {

    protected AbstractHvdcAcEmulationSide2ActiveFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<V> variableSet,
                                                              V busPhiVariableType) {
        super(hvdc, bus1, bus2, variableSet, busPhiVariableType);
    }

    private double p2(double ph1, double ph2) {
        double rawP = rawP(p0, k, ph1, ph2);
        double boundedP = boundedP(rawP);
        return -(isController(rawP) ? 1 : getVscLossMultiplier()) * boundedP;
    }

    private boolean isController(double rawP) {
        return rawP < 0;
    }

    protected boolean isInOperatingRange(double rawP) {
        return rawP < pMaxFromCS2toCS1 && rawP > -pMaxFromCS1toCS2;
    }

    private double dp2dph1(double ph1, double ph2) {
        double rawP = rawP(p0, k, ph1, ph2);
        if (isInOperatingRange(rawP)) {
            return -(isController(rawP) ? 1 : getVscLossMultiplier()) * k;
        } else {
            return 0;
        }
    }

    private double dp2dph2(double ph1, double ph2) {
        return -dp2dph1(ph1, ph2);
    }

    @Override
    public double eval() {
        return p2(ph1(), ph2());
    }

    @Override
    public double der(Variable<V> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(ph1Var)) {
            return dp2dph1(ph1(), ph2());
        } else if (variable.equals(ph2Var)) {
            return dp2dph2(ph1(), ph2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_emulation_p_2";
    }
}
