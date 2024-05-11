/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.lf.AbstractEquationSystemUpdater;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.util.EvaluableConstants;

import static com.powsybl.openloadflow.equations.EquationTerm.setActive;

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
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled() && bus.isReference()));
                equationSystem.getEquation(bus.getNum(), DcEquationType.BUS_TARGET_P)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled() && !bus.isSlack()));
                break;
            case BRANCH, HVDC, SHUNT_COMPENSATOR:
                // nothing to do
                break;
            default:
                throw new IllegalStateException("Unknown element type: " + element.getType());
        }
    }

    @Override
    public void onBranchConnectionStatusChange(LfBranch branch, TwoSides side, boolean connected) {
        super.onBranchConnectionStatusChange(branch, side, connected);
        if (!branch.isDisabled() && !branch.isZeroImpedance(LoadFlowModel.DC)) {
            if (branch.isConnectedSide1() && branch.isConnectedSide2()) {
                setActive(branch.getClosedP1(), true);
                setActive(branch.getClosedP2(), true);
                branch.setP1(branch.getClosedP1());
                branch.setP2(branch.getClosedP2());
            } else if (!branch.isConnectedSide1() && branch.isConnectedSide2()
                    || branch.isConnectedSide1() && !branch.isConnectedSide2()) {
                setActive(branch.getClosedP1(), false);
                setActive(branch.getClosedP2(), false);
                branch.setP1(EvaluableConstants.ZERO);
                branch.setP2(EvaluableConstants.ZERO);
            } else {
                setActive(branch.getClosedP1(), false);
                setActive(branch.getClosedP2(), false);
                branch.setP1(EvaluableConstants.NAN);
                branch.setP2(EvaluableConstants.NAN);
            }
        }
    }

    @Override
    public void onSlackBusChange(LfBus bus, boolean slack) {
        equationSystem.getEquation(bus.getNum(), DcEquationType.BUS_TARGET_P)
                .orElseThrow()
                .setActive(!slack);
    }

    @Override
    public void onReferenceBusChange(LfBus bus, boolean reference) {
        if (reference) {
            Equation<DcVariableType, DcEquationType> phiEq = equationSystem.getEquation(bus.getNum(), DcEquationType.BUS_TARGET_PHI).orElse(null);
            if (phiEq == null) {
                phiEq = equationSystem.createEquation(bus, DcEquationType.BUS_TARGET_PHI)
                        .addTerm(equationSystem.getVariable(bus.getNum(), DcVariableType.BUS_PHI)
                                .createTerm());
            }
            phiEq.setActive(true);
        } else {
            equationSystem.getEquation(bus.getNum(), DcEquationType.BUS_TARGET_PHI)
                    .orElseThrow()
                    .setActive(false);
        }
    }
}
