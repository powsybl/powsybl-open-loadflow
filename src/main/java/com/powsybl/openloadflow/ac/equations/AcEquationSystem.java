/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class AcEquationSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcEquationSystem.class);

    private AcEquationSystem() {
    }

    public static EquationSystem create(LfNetwork network) {
        return create(network, new VariableSet(), false, 0);
    }

    private static void createBusEquations(LfNetwork network, VariableSet variableSet, boolean voltageRemoteControl,
                                           EquationSystem equationSystem) {
        for (LfBus bus : network.getBuses()) {
            if (bus.isSlack()) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_PHI).addTerm(new BusPhaseEquationTerm(bus, variableSet));
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_P).setActive(false);
            }

            if (bus.hasVoltageControl()) {
                if (!voltageRemoteControl || !bus.getRemoteControlTargetBus().isPresent()) {
                    equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(new BusVoltageEquationTerm(bus, variableSet));
                }
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).setActive(false);
            }

            // in case of voltage remote control set target equations
            if (voltageRemoteControl && !bus.getRemoteControlSourceBuses().isEmpty()) {
                createTargetBusEquations(bus, equationSystem, variableSet);
            }

            createShuntEquations(network, variableSet, equationSystem, bus);
        }
    }

    private static void createShuntEquations(LfNetwork network, VariableSet variableSet, EquationSystem equationSystem, LfBus bus) {
        for (LfShunt shunt : bus.getShunts()) {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, network, variableSet);
            equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).addTerm(q);
            shunt.setQ(q);
        }
    }

    private static void createTargetBusEquations(LfBus bus, EquationSystem equationSystem, VariableSet variableSet) {
        // create voltage equation at remote control target bus
        Equation vEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V)
                .addTerm(new BusVoltageEquationTerm(bus, variableSet));

        List<LfBus> sourceBusesControllingVoltage = bus.getRemoteControlSourceBuses().stream()
                .filter(LfBus::hasVoltageControl)
                .collect(Collectors.toList());
        if (sourceBusesControllingVoltage.isEmpty()) {
            vEq.setActive(false);
        } else {
            // create reactive power distribution equations at remote control sources buses (except one)
            createReactivePowerDistributionEquations(equationSystem, variableSet, sourceBusesControllingVoltage);
        }
    }

    public static void createReactivePowerDistributionEquations(EquationSystem equationSystem, VariableSet variableSet,
                                                                List<LfBus> sourceBusesControllingVoltage) {
        double[] qKeys = createReactiveKeys(sourceBusesControllingVoltage);

        // we choose first source bus as reference one for reactive power
        LfBus firstSourceBus = sourceBusesControllingVoltage.get(0);

        // create a reactive power distribution equation for all the other source buses
        for (int i = 1; i < sourceBusesControllingVoltage.size(); i++) {
            LfBus sourceBus = sourceBusesControllingVoltage.get(i);
            double c = qKeys[0] / qKeys[i];

            // 0 = q0 + c * qi
            Equation zero = equationSystem.createEquation(sourceBus.getNum(), EquationType.ZERO_Q);
            for (LfBranch branch : firstSourceBus.getBranches()) {
                LfBus otherSideBus = branch.getBus1() == firstSourceBus ? branch.getBus2() : branch.getBus1();
                EquationTerm q = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, firstSourceBus, otherSideBus, variableSet);
                zero.addTerm(q);
            }
            for (LfBranch branch : sourceBus.getBranches()) {
                LfBus otherSideBus = branch.getBus1() == sourceBus ? branch.getBus2() : branch.getBus1();
                EquationTerm q = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, sourceBus, otherSideBus, variableSet);
                EquationTerm minusQ = EquationTerm.multiply(q, -c);
                zero.addTerm(minusQ);
            }
        }
    }

    private static double[] createReactiveKeysFallBack(int n) {
        double[] qKeys = new double[n];
        Arrays.fill(qKeys, 1d);
        return qKeys;
    }

    private static double[] createReactiveKeys(List<LfBus> sourceBuses) {
        double[] qKeys = new double[sourceBuses.size()];
        for (int i = 0; i < sourceBuses.size(); i++) {
            LfBus sourceBus = sourceBuses.get(i);
            for (LfGenerator generator : sourceBus.getGenerators()) {
                double qKey = generator.getRemoteControlReactiveKey().orElse(Double.NaN);
                if (Double.isNaN(qKey) || qKey == 0) {
                    if (qKey == 0) {
                        LOGGER.error("Generator '{}' remote control reactive key value is zero", generator.getId());
                    }
                    // in case of one missing key, we fallback to same reactive power for all buses
                    return createReactiveKeysFallBack(sourceBuses.size());
                } else {
                    qKeys[i] += qKey;
                }
            }
        }
        return qKeys;
    }

    private static void createNonImpedantBranch(VariableSet variableSet, EquationSystem equationSystem,
                                                LfBranch branch, LfBus bus1, LfBus bus2) {
        boolean hasV1 = equationSystem.hasEquation(bus1.getNum(), EquationType.BUS_V);
        boolean hasV2 = equationSystem.hasEquation(bus2.getNum(), EquationType.BUS_V);
        if (!(hasV1 && hasV2)) {
            // create voltage magnitude coupling equation
            // 0 = v1 - v2 * rho
            PiModel piModel = branch.getPiModel();
            double rho = piModel.getR2() / piModel.getR1();
            equationSystem.createEquation(branch.getNum(), EquationType.ZERO_V)
                    .addTerm(new BusVoltageEquationTerm(bus1, variableSet))
                    .addTerm(EquationTerm.multiply(new BusVoltageEquationTerm(bus2, variableSet), -1 * rho));

            // add a dummy reactive power variable to both sides of the non impedant branch and with an opposite sign
            // to ensure we have the same number of equation and variables
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q)
                    .addTerm(new DummyReactivePowerEquationTerm(branch, variableSet));
            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q)
                    .addTerm(EquationTerm.multiply(new DummyReactivePowerEquationTerm(branch, variableSet), -1));
        } else {
            // nothing to do in case of v1 and v2 are not found, we just have to ensure
            // target v are equals.
        }

        // voltage angle coupling equation creation is shared with DC loadflow
        DcEquationSystem.createNonImpedantBranch(variableSet, equationSystem, branch, bus1, bus2);
    }

    private static void createBranchEquations(LfNetwork network, VariableSet variableSet, double lowImpedanceThreshold,
                                              EquationSystem equationSystem) {
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            PiModel piModel = branch.getPiModel();
            if (piModel.getZ() <= lowImpedanceThreshold) {
                if (bus1 != null && bus2 != null) {
                    createNonImpedantBranch(variableSet, equationSystem, branch, bus1, bus2);
                }
            } else {
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
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet, boolean voltageRemoteControl,
                                        double lowImpedanceThreshold) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(variableSet);

        EquationSystem equationSystem = new EquationSystem(network);

        createBusEquations(network, variableSet, voltageRemoteControl, equationSystem);
        createBranchEquations(network, variableSet, lowImpedanceThreshold, equationSystem);

        return equationSystem;
    }
}
