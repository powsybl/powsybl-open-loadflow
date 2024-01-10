/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.AbstractHvdcAcEmulationFlowEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.Objects;

/**
 * @author Anne Tilloy {@literal anne.tilloy at rte-france.com}
 */

public class HvdcAcEmulationSide2ActiveFlowEquationTerm extends AbstractHvdcAcEmulationFlowEquationTerm<DcVariableType, DcEquationType> {

    @Override
    protected DcVariableType getBusPhi() {
        return DcVariableType.BUS_PHI;
    }

    public HvdcAcEmulationSide2ActiveFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2,
                                                      VariableSet<DcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet);
    }

    @Override
    protected boolean isController(double ph1, double ph2) {
        return (ph1 - ph2) < 0;
    }

    @Override
    public double eval() {
        return p2(p0, k, lossFactor1, lossFactor2, ph1(), ph2());
    }

    @Override
    public double der(Variable<DcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(ph1Var)) {
            return dp2dph1(k, lossFactor1, lossFactor2, ph1(), ph2());
        } else if (variable.equals(ph2Var)) {
            return dp2dph2(k, lossFactor1, lossFactor2, ph1(), ph2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_emulation_p_2";
    }
}
