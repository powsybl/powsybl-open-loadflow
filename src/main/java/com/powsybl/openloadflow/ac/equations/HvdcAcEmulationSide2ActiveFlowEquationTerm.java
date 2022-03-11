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
public class HvdcAcEmulationSide2ActiveFlowEquationTerm extends AbstractHvdcAcEmulationFlowEquationTerm {

    public HvdcAcEmulationSide2ActiveFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet);
    }

    private double p2() {
        return -(isController() ? 1 :  getLossMultiplier()) * (p0 + k * (ph1() - ph2()));
    }

    private boolean isController() {
        return (ph1() - ph2()) < 0;
    }

    private double dp2dph1() {
        return -(isController() ? 1 :  getLossMultiplier()) * k;
    }

    private double dp2dph2() {
        return -dp2dph1();
    }

    @Override
    public double eval() {
        return p2();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return 0.0;
        } else if (variable.equals(v2Var)) {
            return 0.0;
        } else if (variable.equals(ph1Var)) {
            return dp2dph1();
        } else if (variable.equals(ph2Var)) {
            return dp2dph2();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_emulation_p_2";
    }
}
