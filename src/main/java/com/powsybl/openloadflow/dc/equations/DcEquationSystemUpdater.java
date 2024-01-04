/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.lf.AbstractEquationSystemUpdater;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LoadFlowModel;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class DcEquationSystemUpdater extends AbstractEquationSystemUpdater<DcVariableType, DcEquationType> {

    public DcEquationSystemUpdater(EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        super(equationSystem, LoadFlowModel.DC);
    }

    @Override
    protected void updateNonImpedantBranchEquations(LfBranch branch, boolean enable) {
        // depending on the switch status, we activate either v1 = v2, ph1 = ph2 equations
        // or equations that set dummy p and q variable to zero
        equationSystem.getEquation(branch.getNum(), DcEquationType.ZERO_PHI)
                .orElseThrow()
                .setActive(enable);
        equationSystem.getEquation(branch.getNum(), DcEquationType.DUMMY_TARGET_P)
                .orElseThrow()
                .setActive(!enable);
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        updateElementEquations(element, !disabled);
        switch (element.getType()) {
            case BUS:
                LfBus bus = (LfBus) element;
                checkSlackBus(bus, disabled);
                equationSystem.getEquation(bus.getNum(), DcEquationType.BUS_TARGET_PHI)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled()));
                equationSystem.getEquation(bus.getNum(), DcEquationType.BUS_TARGET_P)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled() && !bus.isSlack()));
                break;
            case BRANCH, HVDC, SHUNT_COMPENSATOR:
                // nothing to do.
                break;
            default:
                throw new IllegalStateException("Unknown element type: " + element.getType());
        }
    }

    @Override
    public void onBranchConnectionStatusChange(LfBranch branch, TwoSides side, boolean connected) {
        super.onBranchConnectionStatusChange(branch, side, connected);
    }
}
