/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfShunt;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class AcEquationSystem {

    private AcEquationSystem() {
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
            if (bus.hasVoltageControl() && !bus.getRemoteControlTargetBus().isPresent()) {
                createLocalVoltageEquation(variableSet, equationSystem, bus);
            }
            List<LfBus> sourceBuses = bus.getRemoteControlSourceBuses().stream()
                    .filter(LfBus::hasVoltageControl)
                    .collect(Collectors.toList());
            if (!sourceBuses.isEmpty()) {
                createRemoteVoltageEquations(bus, sourceBuses, equationSystem, variableSet);
            }
            for (LfShunt shunt : bus.getShunts()) {
                ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, network, variableSet);
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).addTerm(q);
                shunt.setQ(q);
            }
        }
    }

    private static void createLocalVoltageEquation(VariableSet variableSet, EquationSystem equationSystem, LfBus bus) {
        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(new BusVoltageEquationTerm(bus, variableSet));
        equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).setActive(false);
    }

    private static void createRemoteVoltageEquations(LfBus bus, List<LfBus> sourceBuses, EquationSystem equationSystem, VariableSet variableSet) {
        // TODO check voltage target consistency

        // create voltage equation at remote control target bus
        equationSystem.createEquation(bus.getNum(), EquationType.BUS_REMOTE_V).addTerm(new BusVoltageEquationTerm(bus, variableSet));

        for (LfBus sourceBus : sourceBuses) {
            // deactivate reactive equation for all remote voltage source bus (where the generator is connected)
            equationSystem.createEquation(sourceBus.getNum(), EquationType.BUS_Q).setActive(false);
        }

        // create (number of source bus) - 1 equation to link the reactive equations together
        LfBus firstSourceBus = sourceBuses.get(0);
        for (int i = 1; i < sourceBuses.size(); i++) {
            LfBus sourceBus = sourceBuses.get(i);

            // 0 = q0 + qi
            Equation zero = equationSystem.createEquation(sourceBus.getNum(), EquationType.ZERO);
            for (LfBranch branch : firstSourceBus.getBranches()) {
                LfBus otherSideBus = branch.getBus1() == firstSourceBus ? branch.getBus2() : branch.getBus1();
                EquationTerm q = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, firstSourceBus, otherSideBus, variableSet);
                zero.addTerm(q);
            }
            for (LfBranch branch : sourceBus.getBranches()) {
                LfBus otherSideBus = branch.getBus1() == sourceBus ? branch.getBus2() : branch.getBus1();
                EquationTerm q = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, sourceBus, otherSideBus, variableSet);
                EquationTerm minusQ = EquationTerm.minus(q);
                zero.addTerm(minusQ);
            }
        }
    }

    private static void createBranchEquations(LfNetwork network, VariableSet variableSet, EquationSystem equationSystem) {
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
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(variableSet);

        EquationSystem equationSystem = new EquationSystem(network);

        createBusEquations(network, variableSet, equationSystem);
        createBranchEquations(network, variableSet, equationSystem);

        return equationSystem;
    }
}
