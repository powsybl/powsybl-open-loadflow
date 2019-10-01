/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.equations;

import com.powsybl.loadflow.simple.equations.BusPhaseEquationTerm;
import com.powsybl.loadflow.simple.equations.EquationSystem;
import com.powsybl.loadflow.simple.equations.EquationType;
import com.powsybl.loadflow.simple.equations.VariableSet;
import com.powsybl.loadflow.simple.network.LfBranch;
import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.LfNetwork;
import com.powsybl.loadflow.simple.network.LfShunt;

import java.util.Objects;

import static com.powsybl.loadflow.simple.equations.EquationType.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class AcEquationSystem {

    private AcEquationSystem() {
    }

    public static EquationSystem create(LfNetwork network) {
        return create(network, new VariableSet());
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(variableSet);

        EquationSystem equationSystem = new EquationSystem(network);

        for (LfBus bus : network.getBuses()) {
            if (bus.isSlack()) {
                equationSystem.createEquation(bus.getNum(), BUS_PHI).addTerm(new BusPhaseEquationTerm(bus, variableSet));
                equationSystem.createEquation(bus.getNum(), BUS_P).setActive(false);
            }
            if (bus.hasVoltageControl()) {
                equationSystem.createEquation(bus.getNum(), BUS_V).addTerm(new BusVoltageEquationTerm(bus, variableSet));
                equationSystem.createEquation(bus.getNum(), BUS_Q).setActive(false);
            }
            for (LfShunt shunt : bus.getShunts()) {
                ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, network, variableSet);
                equationSystem.createEquation(bus.getNum(), BUS_Q).addTerm(q);
                shunt.setQ(q);
            }
        }

        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                ClosedBranchSide1ActiveFlowEquationTerm p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet);
                ClosedBranchSide1ReactiveFlowEquationTerm q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet);
                ClosedBranchSide2ActiveFlowEquationTerm p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, variableSet);
                ClosedBranchSide2ReactiveFlowEquationTerm q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet);
                equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
                equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
                branch.setP1(p1);
                branch.setQ1(q1);
                branch.setP2(p2);
                branch.setQ2(q2);
            } else if (bus1 != null) {
                OpenBranchSide2ActiveFlowEquationTerm p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, variableSet);
                OpenBranchSide2ReactiveFlowEquationTerm q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, variableSet);
                equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
                branch.setP1(p1);
                branch.setQ1(q1);
            } else if (bus2 != null) {
                OpenBranchSide1ActiveFlowEquationTerm p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, variableSet);
                OpenBranchSide1ReactiveFlowEquationTerm q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, variableSet);
                equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
                branch.setP2(p2);
                branch.setQ2(q2);
            }
        }

        return equationSystem;
    }
}
