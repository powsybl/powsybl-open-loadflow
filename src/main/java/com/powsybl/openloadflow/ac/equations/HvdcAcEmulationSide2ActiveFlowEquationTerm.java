/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.Objects;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class HvdcAcEmulationSide2ActiveFlowEquationTerm extends AbstractHvdcAcEmulationFlowEquationTerm {

    public HvdcAcEmulationSide2ActiveFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet);
    }

    private double getSide2LossMultiplier() {
        return element.getAcEmulationControl().getFeedingSide() == TwoSides.TWO ? 1 : getVscLossMultiplier();
    }

    private double p2(double ph1, double ph2) {
        double boundedP = switch (element.getAcEmulationControl().getAcEmulationStatus()) {
            case FREE -> rawP(p0, k, ph2, ph1);
            case BOUNDED -> element.getAcEmulationControl().getFeedingSide() == TwoSides.TWO ? pMaxFromCS2toCS1 : -pMaxFromCS1toCS2;
            default -> 0;
        };
        return getSide2LossMultiplier() * boundedP;
    }

    private double dp2dph1() {
        if (element.getAcEmulationControl().getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.FREE) {
            return getSide2LossMultiplier() * k;
        } else {
            return 0;
        }
    }

    private double dp2dph2() {
        return -dp2dph1();
    }

    @Override
    public double eval() {
        return p2(ph1(), ph2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(ph1Var)) {
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
