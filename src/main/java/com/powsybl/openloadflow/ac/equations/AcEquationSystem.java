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

    private static void createBusEquations(LfNetwork network, VariableSet variableSet, AcEquationSystemCreationParameters creationParameters,
                                           EquationSystem equationSystem) {
        for (LfBus bus : network.getBuses()) {
            if (bus.isSlack()) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_PHI).addTerm(new BusPhaseEquationTerm(bus, variableSet));
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_P).setActive(false);
            }

            if (bus.hasVoltageControl()) {
                // local voltage control
                if (!creationParameters.isVoltageRemoteControl() || !bus.getControlledBus().isPresent()) {
                    equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(new BusVoltageEquationTerm(bus, variableSet));
                }
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).setActive(false);
            }

            // in case voltage remote control is activated, set voltage equation on this controlled bus
            if (creationParameters.isVoltageRemoteControl() && !bus.getControllerBuses().isEmpty()) {
                createVoltageControlledBusEquations(bus, equationSystem, variableSet);
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

    private static void createVoltageControlledBusEquations(LfBus bus, EquationSystem equationSystem, VariableSet variableSet) {
        // create voltage equation at voltage controlled bus
        Equation vEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V)
                .addTerm(new BusVoltageEquationTerm(bus, variableSet));

        List<LfBus> controllerBuses = bus.getControllerBuses().stream()
                .filter(LfBus::hasVoltageControl)
                .collect(Collectors.toList());
        if (controllerBuses.isEmpty()) {
            vEq.setActive(false);
        } else {
            // create reactive power distribution equations at voltage controller buses (except one)
            createReactivePowerDistributionEquations(equationSystem, variableSet, controllerBuses);
        }
    }

    public static void createReactivePowerDistributionEquations(EquationSystem equationSystem, VariableSet variableSet,
                                                                List<LfBus> controllerBuses) {
        double[] qKeys = createReactiveKeys(controllerBuses);

        // we choose first controller bus as reference for reactive power
        LfBus firstControllerBus = controllerBuses.get(0);

        // create a reactive power distribution equation for all the other controller buses
        for (int i = 1; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            double c = qKeys[0] / qKeys[i];

            // 0 = q0 + c * qi
            AcEquationSystemCreationParameters creationParameters = new AcEquationSystemCreationParameters(false, false); // TODO could not be the right parameters
            Equation zero = equationSystem.createEquation(controllerBus.getNum(), EquationType.ZERO_Q);
            for (LfBranch branch : firstControllerBus.getBranches()) {
                LfBus otherSideBus = branch.getBus1() == firstControllerBus ? branch.getBus2() : branch.getBus1();
                boolean deriveA = creationParameters.isPhaseControl() && branch.getPhaseControl().isPresent();
                EquationTerm q = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, firstControllerBus, otherSideBus, variableSet, deriveA);
                zero.addTerm(q);
            }
            for (LfBranch branch : controllerBus.getBranches()) {
                LfBus otherSideBus = branch.getBus1() == controllerBus ? branch.getBus2() : branch.getBus1();
                boolean deriveA = creationParameters.isPhaseControl() && branch.getPhaseControl().isPresent();
                EquationTerm q = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, controllerBus, otherSideBus, variableSet, deriveA);
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

    private static double[] createReactiveKeys(List<LfBus> controllerBuses) {
        double[] qKeys = new double[controllerBuses.size()];
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            for (LfGenerator generator : controllerBus.getGenerators()) {
                double qKey = generator.getRemoteControlReactiveKey().orElse(Double.NaN);
                if (Double.isNaN(qKey) || qKey == 0) {
                    if (qKey == 0) {
                        LOGGER.error("Generator '{}' remote control reactive key value is zero", generator.getId());
                    }
                    // in case of one missing key, we fallback to same reactive power for all buses
                    return createReactiveKeysFallBack(controllerBuses.size());
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
            // nothing to do in case of v1 and v2 are found, we just have to ensure
            // target v are equals.
        }

        // voltage angle coupling equation creation is shared with DC loadflow
        DcEquationSystem.createNonImpedantBranch(variableSet, equationSystem, branch, bus1, bus2);
    }

    private static void createBranchEquations(LfNetwork network, VariableSet variableSet, AcEquationSystemCreationParameters creationParameters,
                                              EquationSystem equationSystem) {
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            PiModel piModel = branch.getPiModel();
            if (piModel.getZ() <= DcEquationSystem.LOW_IMPEDANCE_THRESHOLD) {
                if (bus1 != null && bus2 != null) {
                    createNonImpedantBranch(variableSet, equationSystem, branch, bus1, bus2);
                }
            } else {
                EquationTerm p1 = null;
                EquationTerm q1 = null;
                EquationTerm p2 = null;
                EquationTerm q2 = null;
                if (bus1 != null && bus2 != null) {
                    boolean deriveA = creationParameters.isPhaseControl() && branch.getPhaseControl().isPresent();
                    p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA);
                    q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA);
                    p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA);
                    q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA);
                } else if (bus1 != null) {
                    p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, variableSet);
                    q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, variableSet);
                } else if (bus2 != null) {
                    p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, variableSet);
                    q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, variableSet);
                }

                if (p1 != null) {
                    equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                    branch.setP1(p1);

                    if (creationParameters.isPhaseControl()) {
                        PhaseControl phaseControl = branch.getPhaseControl().orElse(null);
                        if (phaseControl != null && phaseControl.getMode() == PhaseControl.Mode.CONTROLLER) {
                            equationSystem.createEquation(branch.getNum(), EquationType.BRANCH_P)
                                    .addTerm(p1);
                        }
                    }
                }
                if (q1 != null) {
                    equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
                    branch.setQ1(q1);
                }
                if (p2 != null) {
                    equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                    branch.setP2(p2);
                }
                if (q2 != null) {
                    equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
                    branch.setQ2(q2);
                }
            }
        }
    }

    public static EquationSystem create(LfNetwork network) {
        return create(network, new VariableSet());
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet) {
        return create(network, variableSet, new AcEquationSystemCreationParameters(false, false));
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet, AcEquationSystemCreationParameters creationParameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(variableSet);
        Objects.requireNonNull(creationParameters);

        EquationSystem equationSystem = new EquationSystem(network);

        createBusEquations(network, variableSet, creationParameters, equationSystem);
        createBranchEquations(network, variableSet, creationParameters, equationSystem);

        return equationSystem;
    }
}
