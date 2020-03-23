/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.ac.equations.DummyActivePowerEquationTerm;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.EvaluableConstants;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class DcEquationSystem {

    private DcEquationSystem() {
    }

    public static EquationSystem create(LfNetwork network) {
        return create(network, new VariableSet());
    }

    public static void createNonImpedantBranch(VariableSet variableSet, EquationSystem equationSystem,
                                               LfBranch branch, LfBus bus1, LfBus bus2) {
        boolean hasPhi1 = equationSystem.hasEquation(bus1.getNum(), EquationType.BUS_PHI);
        boolean hasPhi2 = equationSystem.hasEquation(bus2.getNum(), EquationType.BUS_PHI);
        if (!(hasPhi1 && hasPhi2)) {
            // create voltage angle coupling equation
            // alpha = phi1 - phi2
            equationSystem.createEquation(branch.getNum(), EquationType.ZERO_PHI)
                    .addTerm(new BusPhaseEquationTerm(bus1, variableSet))
                    .addTerm(EquationTerm.multiply(new BusPhaseEquationTerm(bus2, variableSet), -1));

            // add a dummy active power variable to both sides of the non impedant branch and with an opposite sign
            // to ensure we have the same number of equation and variables
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P)
                    .addTerm(new DummyActivePowerEquationTerm(branch, variableSet));
            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P)
                    .addTerm(EquationTerm.multiply(new DummyActivePowerEquationTerm(branch, variableSet), -1));
        } else {
            throw new IllegalStateException("Cannot happen because only there is one slack bus per model");
        }
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet) {
        EquationSystem equationSystem = new EquationSystem(network);

        for (LfBus bus : network.getBuses()) {
            if (bus.isSlack()) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_PHI).addTerm(new BusPhaseEquationTerm(bus, variableSet));
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_P).setActive(false);
            }
        }

        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            PiModel piModel = branch.getPiModel();
            if (piModel.getX() == 0) {
                if (bus1 != null && bus2 != null) {
                    createNonImpedantBranch(variableSet, equationSystem, branch, bus1, bus2);
                }
            } else {
                if (bus1 != null && bus2 != null) {
                    ClosedBranchSide1DcFlowEquationTerm p1 = ClosedBranchSide1DcFlowEquationTerm.create(branch, bus1, bus2, variableSet);
                    ClosedBranchSide2DcFlowEquationTerm p2 = ClosedBranchSide2DcFlowEquationTerm.create(branch, bus1, bus2, variableSet);
                    equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                    equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                    branch.setP1(p1);
                    branch.setP2(p2);
                } else if (bus1 != null) {
                    branch.setP1(EvaluableConstants.ZERO);
                } else if (bus2 != null) {
                    branch.setP2(EvaluableConstants.ZERO);
                }
            }
        }

        return equationSystem;
    }
}
