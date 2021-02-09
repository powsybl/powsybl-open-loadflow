/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

            boolean hasLocalControl = bus.isVoltageController() && bus.getVoltageControl().isVoltageControlLocal();
            boolean hasLocalControlOnly = hasLocalControl && bus.getVoltageControl().getControllerBuses().size() == 1;
            if (hasLocalControlOnly) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(new BusVoltageEquationTerm(bus, variableSet));
            }

            if (bus.isVoltageController()) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).setActive(false);
            }

            // in case voltage remote control is activated, set voltage equation on this controlled bus
            boolean hasRemoteControl = bus.isVoltageControlled() && !hasLocalControlOnly;
            if (hasRemoteControl) {
                createVoltageControlledBusEquations(bus, equationSystem, variableSet);
            }

            createShuntEquations(variableSet, equationSystem, bus);

            if (creationParameters.isTransformerVoltageControl()) {
                createDiscreteVoltageControlEquation(bus, variableSet, equationSystem);
            }
        }
    }

    private static void createShuntEquations(VariableSet variableSet, EquationSystem equationSystem, LfBus bus) {
        for (LfShunt shunt : bus.getShunts()) {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, variableSet);
            equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).addTerm(q);
            shunt.setQ(q);
        }
    }

    private static void createVoltageControlledBusEquations(LfBus controlledBus, EquationSystem equationSystem, VariableSet variableSet) {
        // create voltage equation at voltage controlled bus
        Equation vEq = equationSystem.createEquation(controlledBus.getNum(), EquationType.BUS_V)
                .addTerm(new BusVoltageEquationTerm(controlledBus, variableSet));

        List<LfBus> controllerBuses = controlledBus.getVoltageControl().getControllerBuses().stream()
                .filter(LfBus::isVoltageController)
                .collect(Collectors.toList());
        if (controllerBuses.isEmpty()) {
            vEq.setActive(false);
        } else {
            // create reactive power distribution equations at voltage controller buses (except one)
            createReactivePowerDistributionEquations(equationSystem, variableSet, controllerBuses);
        }
    }

    private static List<EquationTerm> createReactiveTerms(LfBus controllerBus, VariableSet variableSet, AcEquationSystemCreationParameters creationParameters) {
        List<EquationTerm> terms = new ArrayList<>();
        for (LfBranch branch : controllerBus.getBranches()) {
            EquationTerm q;
            if (LfNetwork.isZeroImpedanceBranch(branch)) {
                if (branch.getBus1() == controllerBus) {
                    q = new DummyReactivePowerEquationTerm(branch, variableSet);
                } else {
                    q = EquationTerm.multiply(new DummyReactivePowerEquationTerm(branch, variableSet), -1);
                }
            } else {
                boolean deriveA1 = creationParameters.isPhaseControl() && branch.isPhaseController()
                    && branch.getDiscretePhaseControl().getMode() == DiscretePhaseControl.Mode.CONTROLLER;
                boolean deriveR1 = creationParameters.isTransformerVoltageControl() && branch.isVoltageController();
                if (branch.getBus1() == controllerBus) {
                    LfBus otherSideBus = branch.getBus2();
                    q = otherSideBus != null ? new ClosedBranchSide1ReactiveFlowEquationTerm(branch, controllerBus, otherSideBus, variableSet, deriveA1, deriveR1)
                                             : new OpenBranchSide2ReactiveFlowEquationTerm(branch, controllerBus, variableSet, deriveA1, deriveR1);
                } else {
                    LfBus otherSideBus = branch.getBus1();
                    q = otherSideBus != null ? new ClosedBranchSide2ReactiveFlowEquationTerm(branch, otherSideBus, controllerBus, variableSet, deriveA1, deriveR1)
                                             : new OpenBranchSide1ReactiveFlowEquationTerm(branch, controllerBus, variableSet, deriveA1, deriveR1);
                }
            }
            terms.add(q);
        }
        for (LfShunt shunt : controllerBus.getShunts()) {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, controllerBus, variableSet);
            terms.add(q);
        }
        return terms;
    }

    public static void createReactivePowerDistributionEquations(EquationSystem equationSystem, VariableSet variableSet,
                                                                List<LfBus> controllerBuses) {
        double[] qKeys = createReactiveKeys(controllerBuses);

        // we choose first controller bus as reference for reactive power
        LfBus firstControllerBus = controllerBuses.get(0);
        AcEquationSystemCreationParameters creationParameters = new AcEquationSystemCreationParameters(false, false); // TODO could not be the right parameters
        List<EquationTerm> firstControllerBusReactiveTerms = createReactiveTerms(firstControllerBus, variableSet, creationParameters);

        // create a reactive power distribution equation for all the other controller buses
        for (int i = 1; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            double c = qKeys[0] / qKeys[i];

            // l0 - c * li = q0 - c * qi
            Equation zero = equationSystem.createEquation(controllerBus.getNum(), EquationType.ZERO_Q);
            zero.setData(new DistributionData(firstControllerBus.getNum(), c)); // for later use
            zero.addTerms(firstControllerBusReactiveTerms);
            zero.addTerms(createReactiveTerms(controllerBus, variableSet, creationParameters).stream().map(term -> EquationTerm.multiply(term, -c)).collect(Collectors.toList()));
        }
    }

    private static double[] createUniformReactiveKeys(List<LfBus> controllerBuses) {
        double[] qKeys = new double[controllerBuses.size()];
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            qKeys[i] = controllerBus.getGenerators().stream().filter(LfGenerator::hasVoltageControl).count();
        }
        return qKeys;
    }

    private static double[] createReactiveKeysFromMaxReactivePowerRange(List<LfBus> controllerBuses) {
        double[] qKeys = new double[controllerBuses.size()];
        // try to build keys from reactive power range
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            for (LfGenerator generator : controllerBus.getGenerators()) {
                double maxRangeQ = generator.getMaxRangeQ();
                // if one reactive range is not plausible, we fallback to uniform keys
                if (maxRangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB || maxRangeQ > PlausibleValues.MAX_REACTIVE_RANGE / PerUnit.SB) {
                    return createUniformReactiveKeys(controllerBuses);
                } else {
                    qKeys[i] += maxRangeQ;
                }
            }
        }
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
                    // in case of one missing key, we fallback to keys based on reactive power range
                    return createReactiveKeysFromMaxReactivePowerRange(controllerBuses);
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
            double rho = PiModel.R2 / piModel.getR1();
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

    private static void createBranchActivePowerTargetEquation(LfBranch branch, DiscretePhaseControl.ControlledSide controlledSide,
                                                              EquationSystem equationSystem, EquationTerm p) {
        if (branch.isPhaseControlled(controlledSide)) {
            DiscretePhaseControl phaseControl = branch.getDiscretePhaseControl();
            if (phaseControl.getMode() == DiscretePhaseControl.Mode.CONTROLLER) {
                if (phaseControl.getUnit() == DiscretePhaseControl.Unit.A) {
                    throw new PowsyblException("Phase control in A is not yet supported");
                }
                equationSystem.createEquation(branch.getNum(), EquationType.BRANCH_P).addTerm(p);
            }
        }
    }

    private static void createDiscreteVoltageControlEquation(LfBus bus,  VariableSet variableSet, EquationSystem equationSystem) {
        if (bus.isDiscreteVoltageControlled()) {
            equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(new BusVoltageEquationTerm(bus, variableSet));
            if (bus.getDiscreteVoltageControl().getControllers().size() > 1) {
                createR1DistributionEquations(equationSystem, variableSet, bus.getDiscreteVoltageControl().getControllers());
            }
        }
    }

    public static void createR1DistributionEquations(EquationSystem equationSystem, VariableSet variableSet,
                                                     List<LfBranch> controllerBranches) {
        // we choose first controller bus as reference for reactive power
        LfBranch firstControllerBranch = controllerBranches.get(0);

        // create a R1 distribution equation for all the other controller branches
        for (int i = 1; i < controllerBranches.size(); i++) {
            LfBranch controllerBranch = controllerBranches.get(i);
            Equation zero = equationSystem.createEquation(controllerBranch.getNum(), EquationType.ZERO_RHO1)
                    .addTerm(new BranchRho1EquationTerm(controllerBranch, variableSet))
                    .addTerm(EquationTerm.multiply(new BranchRho1EquationTerm(firstControllerBranch, variableSet), -1));
            zero.setData(new DistributionData(firstControllerBranch.getNum(), 1)); // for later use
        }
    }

    private static void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                             AcEquationSystemCreationParameters creationParameters,
                                             EquationSystem equationSystem) {
        EquationTerm p1 = null;
        EquationTerm q1 = null;
        EquationTerm p2 = null;
        EquationTerm q2 = null;
        boolean deriveA1 = creationParameters.isPhaseControl() && branch.isPhaseController()
                && branch.getDiscretePhaseControl().getMode() == DiscretePhaseControl.Mode.CONTROLLER;
        boolean deriveR1 = creationParameters.isTransformerVoltageControl() && branch.isVoltageController();
        if (bus1 != null && bus2 != null) {
            p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
            q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
            p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
            q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
        } else if (bus1 != null) {
            p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, variableSet, deriveA1, deriveR1);
            q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, variableSet, deriveA1, deriveR1);
        } else if (bus2 != null) {
            p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, variableSet, deriveA1, deriveR1);
            q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, variableSet, deriveA1, deriveR1);
        }

        if (p1 != null) {
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
            branch.setP1(p1);
            if (creationParameters.isPhaseControl()) {
                createBranchActivePowerTargetEquation(branch, DiscretePhaseControl.ControlledSide.ONE, equationSystem, p1);
            }
        }
        if (q1 != null) {
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
            branch.setQ1(q1);
        }
        if (p2 != null) {
            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
            branch.setP2(p2);
            if (creationParameters.isPhaseControl()) {
                createBranchActivePowerTargetEquation(branch, DiscretePhaseControl.ControlledSide.TWO, equationSystem, p2);
            }
        }
        if (q2 != null) {
            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
            branch.setQ2(q2);
        }
    }

    private static void createBranchEquations(LfNetwork network, VariableSet variableSet, AcEquationSystemCreationParameters creationParameters,
                                              EquationSystem equationSystem) {

        // create zero and non zero impedance branch equations
        network.getBranches().stream()
            .filter(b -> !LfNetwork.isZeroImpedanceBranch(b))
            .forEach(b -> createImpedantBranch(b, b.getBus1(), b.getBus2(), variableSet, creationParameters, equationSystem));

        // create zero impedance equations only on minimum spanning forest calculated from zero impedance sub graph
        Graph<LfBus, LfBranch> zeroImpedanceSubGraph = network.createZeroImpedanceSubGraph();
        if (!zeroImpedanceSubGraph.vertexSet().isEmpty()) {
            List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(zeroImpedanceSubGraph).connectedSets();
            for (Set<LfBus> connectedSet : connectedSets) {
                if (connectedSet.size() > 2 && connectedSet.stream().filter(LfBus::isVoltageController).count() > 1) {
                    String problBuses = connectedSet.stream().map(LfBus::getId).collect(Collectors.joining(", "));
                    throw new PowsyblException(
                        "Zero impedance branches that connect at least two buses with voltage control (buses: " + problBuses + ")");
                }
            }

            SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree = new KruskalMinimumSpanningTree<>(zeroImpedanceSubGraph).getSpanningTree();
            for (LfBranch branch : spanningTree.getEdges()) {
                createNonImpedantBranch(variableSet, equationSystem, branch, branch.getBus1(), branch.getBus2());
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

        EquationSystem equationSystem = new EquationSystem(network, true);

        createBusEquations(network, variableSet, creationParameters, equationSystem);
        createBranchEquations(network, variableSet, creationParameters, equationSystem);

        return equationSystem;
    }
}
