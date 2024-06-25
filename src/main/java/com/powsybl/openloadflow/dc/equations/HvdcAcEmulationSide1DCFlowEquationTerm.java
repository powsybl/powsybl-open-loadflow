/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
public class HvdcAcEmulationSide1DCFlowEquationTerm extends AbstractHvdcAcEmulationDcFlowEquationTerm {

    public HvdcAcEmulationSide1DCFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet);
    }

    @Override
    protected String getName() {
        return "dc_p_1_hvdc";
    }

    @Override
    public double eval() {
        if (element.getAcEmulationControl().getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.BOUNDED) {
            return element.getAcEmulationControl().getFeedingSide() == TwoSides.ONE ? pMaxFromCS1toCS2 : -pMaxFromCS2toCS1;
        } else { // free
            return rawP(p0, k, ph1(), ph2());
        }
    }

    @Override
    public double der(Variable<DcVariableType> variable) {
        double der = 0;
        if (element.getAcEmulationControl().getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.FREE) {
            der = k;
        }
        if (variable.equals(ph1Var)) {
            return der;
        } else if (variable.equals(ph2Var)) {
            return -der;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public boolean hasRhs() {
        return true;
    }

    @Override
    public double rhs() {
        if (element.getAcEmulationControl().getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.BOUNDED) {
            return element.getAcEmulationControl().getFeedingSide() == TwoSides.ONE ? pMaxFromCS1toCS2 : -pMaxFromCS2toCS1;
        } else { // free
            return p0;
        }
    }
}
