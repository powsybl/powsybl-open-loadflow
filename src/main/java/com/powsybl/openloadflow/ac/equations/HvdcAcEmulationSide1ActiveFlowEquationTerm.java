/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class HvdcAcEmulationSide1ActiveFlowEquationTerm extends AbstractHvdcAcEmulationFlowEquationTerm {

    private double sign;

    private double multiplier;

    public HvdcAcEmulationSide1ActiveFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet);
        sign = hvdc.getConverterStation1().isRectifier() ? 1 : -1;
        multiplier = 1 + sign * hvdc.getConverterStation1().getLossFactor() / 100;
    }

    private double p1() {
        return  multiplier * (p0 + k * (ph1() - ph2()));
    }

    private double dp1dv1() {
        return 0.0;
    }

    private double dp1dv2() {
        return 0.0;
    }

    private double dp1dph1() {
        return multiplier * k;
    }

    private double dp1dph2() {
        return -dp1dph1();
    }

    @Override
    public double eval() {
        return p1();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1();
        } else if (variable.equals(v2Var)) {
            return dp1dv2();
        } else if (variable.equals(ph1Var)) {
            return dp1dph1();
        } else if (variable.equals(ph2Var)) {
            return dp1dph2();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_emulation_p_1";
    }
}
