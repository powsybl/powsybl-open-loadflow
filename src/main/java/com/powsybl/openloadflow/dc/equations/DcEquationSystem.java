/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.BusPhaseEquationTerm;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
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

    private static void createBusEquations(LfNetwork network, VariableSet variableSet, EquationSystem equationSystem) {
        for (LfBus bus : network.getBuses()) {
            if (bus.isSlack()) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_PHI).addTerm(new BusPhaseEquationTerm(bus, variableSet));
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_P).setActive(false);
            }
        }
    }

    private static void createBranchEquations(LfNetwork network, VariableSet variableSet, EquationSystem equationSystem) {
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (branch.getPiModel().isPresent()) {
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
            } else {
                // non impedant branch
                if (bus1 != null && bus2 != null) {
                    throw new UnsupportedOperationException("Non impedant branch not yet supported: " + branch.getId());
                }
            }
        }
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet) {
        EquationSystem equationSystem = new EquationSystem(network);

        createBusEquations(network, variableSet, equationSystem);
        createBranchEquations(network, variableSet, equationSystem);

        return equationSystem;
    }
}
