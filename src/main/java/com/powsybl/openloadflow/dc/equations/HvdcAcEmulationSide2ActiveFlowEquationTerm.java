/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.AbstractHvdcAcEmulationSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class HvdcAcEmulationSide2ActiveFlowEquationTerm extends AbstractHvdcAcEmulationSide2ActiveFlowEquationTerm<DcVariableType, DcEquationType> {

    public HvdcAcEmulationSide2ActiveFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet, DcVariableType.BUS_PHI);
    }

    @Override
    protected double boundedP(double rawP) {
        // to be linear
        return rawP;
    }

    @Override
    protected boolean isInOperatingRange(double rawP) {
        return true;
    }

    @Override
    protected double getVscLossMultiplier() {
        return 1;
    }
}
