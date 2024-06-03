/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.Objects;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class HvdcAcEmulationSide1ActiveFlowEquationTerm extends AbstractHvdcAcEmulationFlowEquationTerm {

    public HvdcAcEmulationSide1ActiveFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet);
    }

    private double p1(double ph1, double ph2) {
        double rawP = rawP(p0, k, ph1, ph2);
        return (isController(rawP) ? 1 : getVscLossMultiplier()) * rawP;
    }

    private static boolean isController(double rawP) {
        return rawP >= 0;
    }

    private boolean isInOperatingRange(double rawP) {
        return rawP < pMaxFromCS1toCS2 && rawP > -pMaxFromCS2toCS1;
    }

    protected double dp1dph1(double ph1, double ph2) {
        double rawP = rawP(p0, k, ph1, ph2);
        if (isInOperatingRange(rawP)) {
            return (isController(rawP) ? 1 : getVscLossMultiplier()) * k;
        } else {
            return 0;
        }
    }

    protected double dp1dph2(double ph1, double ph2) {
        return -dp1dph1(ph1, ph2);
    }

    @Override
    public double eval() {
        return p1(ph1(), ph2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(ph1Var)) {
            return dp1dph1(ph1(), ph2());
        } else if (variable.equals(ph2Var)) {
            return dp1dph2(ph1(), ph2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_emulation_p_1";
    }
}
