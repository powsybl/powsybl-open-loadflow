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
@SuppressWarnings("squid:S00107")
public class HvdcAcEmulationSide1ActiveFlowEquationTerm extends AbstractHvdcAcEmulationFlowEquationTerm {

    public HvdcAcEmulationSide1ActiveFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet);
    }

    public static double p1(double p0, double k, double pMaxFromCS1toCS2, double pMaxFromCS2toCS1, double lossFactor1, double lossFactor2, double r, double ph1, double ph2) {
        double rawP = rawP(p0, k, ph1, ph2);
        // if converterStation1 is controller, then p1 is positive, otherwise it is negative
        return isController(rawP) ? boundedP(rawP, pMaxFromCS1toCS2, pMaxFromCS2toCS1) : -getAbsActivePowerWithLosses(boundedP(rawP, pMaxFromCS1toCS2, pMaxFromCS2toCS1), lossFactor1, lossFactor2, r);
    }

    private static boolean isController(double rawP) {
        return rawP >= 0;
    }

    private static boolean isInOperatingRange(double rawP, double pMaxFromCS1toCS2, double pMaxFromCS2toCS1) {
        return rawP < pMaxFromCS1toCS2 && rawP > -pMaxFromCS2toCS1;
    }

    public static double dp1dph1(double p0, double k, double pMaxFromCS1toCS2, double pMaxFromCS2toCS1, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        double rawP = rawP(p0, k, ph1, ph2);
        if (isInOperatingRange(rawP, pMaxFromCS1toCS2, pMaxFromCS2toCS1)) {
            return (isController(rawP) ? 1 : getVscLossMultiplier(lossFactor1, lossFactor2)) * k; // derivative of cable loss is neglected
        } else {
            return 0;
        }
    }

    public static double dp1dph2(double p0, double k, double pMaxFromCS1toCS2, double pMaxFromCS2toCS1, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return -dp1dph1(p0, k, pMaxFromCS1toCS2, pMaxFromCS2toCS1, lossFactor1, lossFactor2, ph1, ph2);
    }

    @Override
    public double eval() {
        return p1(p0, k, pMaxFromCS1toCS2, pMaxFromCS2toCS1, lossFactor1, lossFactor2, r, ph1(), ph2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(ph1Var)) {
            return dp1dph1(p0, k, pMaxFromCS1toCS2, pMaxFromCS2toCS1, lossFactor1, lossFactor2, ph1(), ph2());
        } else if (variable.equals(ph2Var)) {
            return dp1dph2(p0, k, pMaxFromCS1toCS2, pMaxFromCS2toCS1, lossFactor1, lossFactor2, ph1(), ph2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_emulation_p_1";
    }
}
