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

import java.util.*;
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
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_PHI).addTerm(EquationTerm.createVariableTerm(bus, VariableType.BUS_PHI, variableSet));
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_P).setActive(false);
            }

            bus.getVoltageControl().ifPresent(vc -> createVoltageControlEquations(vc, bus, variableSet, equationSystem, creationParameters));

            createShuntEquations(variableSet, equationSystem, bus);

            if (creationParameters.isTransformerVoltageControl()) {
                createDiscreteVoltageControlEquation(bus, variableSet, equationSystem);
            }
            Equation v = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V);
            if (v.getTerms().isEmpty()) {
                v.setActive(false);
                EquationTerm vTerm = EquationTerm.createVariableTerm(bus, VariableType.BUS_V, variableSet, bus.getV().eval());
                v.addTerm(vTerm);
                bus.setV(vTerm);
                v.setUpdateType(EquationSystem.EquationUpdateType.AFTER_NR);
            }
        }
    }

    private static void createVoltageControlEquations(VoltageControl voltageControl, LfBus bus, VariableSet variableSet,
                                                      EquationSystem equationSystem, AcEquationSystemCreationParameters creationParameters) {
        if (voltageControl.isVoltageControlLocal()) {
            EquationTerm vTerm;
            if (creationParameters.isVoltagePerReactivePowerControl() && voltageControl.getControllerBuses().size() == 1 && bus.getGeneratorControllingVoltageWithSlope() != null) {
                vTerm = EquationTerm.createVariableTerm(bus, VariableType.BUS_V, variableSet, bus.getV().eval());
                createBusWithSlopeEquation(bus, creationParameters, variableSet, equationSystem, vTerm);

            } else {
                vTerm = EquationTerm.createVariableTerm(bus, VariableType.BUS_V, variableSet, bus.getV().eval());
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(vTerm);
            }
            bus.setV(vTerm);
        } else if (bus.isVoltageControlled()) {
            // remote controlled: set voltage equation on this controlled bus
            createVoltageControlledBusEquations(voltageControl, equationSystem, variableSet, creationParameters);
        }

        if (bus.isVoltageControllerEnabled()) {
            equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).setActive(false);
        }
    }

    private static void createShuntEquations(VariableSet variableSet, EquationSystem equationSystem, LfBus bus) {
        for (LfShunt shunt : bus.getShunts()) {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, variableSet);
            equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q).addTerm(q);
            shunt.setQ(q);
        }
    }

    private static void createVoltageControlledBusEquations(VoltageControl voltageControl, EquationSystem equationSystem, VariableSet variableSet,
                                                            AcEquationSystemCreationParameters creationParameters) {
        LfBus controlledBus = voltageControl.getControlledBus();

        // create voltage equation at voltage controlled bus
        EquationTerm vTerm = EquationTerm.createVariableTerm(controlledBus, VariableType.BUS_V, variableSet, controlledBus.getV().eval());
        Equation vEq = equationSystem.createEquation(controlledBus.getNum(), EquationType.BUS_V)
                .addTerm(vTerm);
        controlledBus.setV(vTerm);

        List<LfBus> controllerBuses = voltageControl.getControllerBuses().stream()
                .filter(LfBus::isVoltageControllerEnabled)
                .collect(Collectors.toList());
        if (controllerBuses.isEmpty()) {
            vEq.setActive(false);
        } else {
            // create reactive power distribution equations at voltage controller buses (except one)
            createReactivePowerDistributionEquations(equationSystem, variableSet, creationParameters, controllerBuses);
        }
    }

    private static List<EquationTerm> createReactiveTerms(LfBus controllerBus, VariableSet variableSet, AcEquationSystemCreationParameters creationParameters) {
        List<EquationTerm> terms = new ArrayList<>();
        for (LfBranch branch : controllerBus.getBranches()) {
            EquationTerm q;
            if (LfNetwork.isZeroImpedanceBranch(branch)) {
                if (branch.getBus1() == controllerBus) {
                    q = EquationTerm.createVariableTerm(branch, VariableType.DUMMY_Q, variableSet);
                } else {
                    q = EquationTerm.multiply(EquationTerm.createVariableTerm(branch, VariableType.DUMMY_Q, variableSet), -1);
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
                                                                AcEquationSystemCreationParameters creationParameters,
                                                                List<LfBus> controllerBuses) {
        double[] qKeys = createReactiveKeys(controllerBuses);

        // we choose first controller bus as reference for reactive power
        LfBus firstControllerBus = controllerBuses.get(0);
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
        Optional<Equation> v1 = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_V);
        Optional<Equation> v2 = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_V);
        boolean hasV1 = v1.isPresent() && v1.get().isActive(); // may be inactive if the equation has been created for sensitivity
        boolean hasV2 = v2.isPresent() && v2.get().isActive(); // may be inactive if the equation has been created for sensitivity
        if (!(hasV1 && hasV2)) {
            // create voltage magnitude coupling equation
            // 0 = v1 - v2 * rho
            PiModel piModel = branch.getPiModel();
            double rho = PiModel.R2 / piModel.getR1();
            EquationTerm vTerm = EquationTerm.createVariableTerm(bus1, VariableType.BUS_V, variableSet, bus1.getV().eval());
            EquationTerm bus2vTerm = EquationTerm.createVariableTerm(bus2, VariableType.BUS_V, variableSet, bus2.getV().eval());
            equationSystem.createEquation(branch.getNum(), EquationType.ZERO_V)
                    .addTerm(vTerm)
                    .addTerm(EquationTerm.multiply(bus2vTerm, -1 * rho));
            bus1.setV(vTerm);
            // add a dummy reactive power variable to both sides of the non impedant branch and with an opposite sign
            // to ensure we have the same number of equation and variables
            Equation sq1 = equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q);
            if (sq1.getTerms().isEmpty()) {
                bus1.setQ(sq1);
            }
            sq1.addTerm(EquationTerm.createVariableTerm(branch, VariableType.DUMMY_Q, variableSet));

            Equation sq2 = equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q);
            if (sq2.getTerms().isEmpty()) {
                bus2.setQ(sq2);
            }
            sq2.addTerm(EquationTerm.multiply(EquationTerm.createVariableTerm(branch, VariableType.DUMMY_Q, variableSet), -1));
        } else {
            // nothing to do in case of v1 and v2 are found, we just have to ensure
            // target v are equals.
        }

        // voltage angle coupling equation creation is shared with DC loadflow
        DcEquationSystem.createNonImpedantBranch(variableSet, equationSystem, branch, bus1, bus2);
    }

    private static void createBranchActivePowerTargetEquation(LfBranch branch, DiscretePhaseControl.ControlledSide controlledSide,
                                                              EquationSystem equationSystem, VariableSet variableSet, EquationTerm p) {
        if (branch.isPhaseControlled(controlledSide)) {
            DiscretePhaseControl phaseControl = branch.getDiscretePhaseControl();
            if (phaseControl.getMode() == DiscretePhaseControl.Mode.CONTROLLER) {
                if (phaseControl.getUnit() == DiscretePhaseControl.Unit.A) {
                    throw new PowsyblException("Phase control in A is not yet supported");
                }
                equationSystem.createEquation(branch.getNum(), EquationType.BRANCH_P).addTerm(p);

                // we also create an equation that will be used later to maintain A1 variable constant
                // this equation is now inactive
                LfBranch controller = phaseControl.getController();
                equationSystem.createEquation(controller.getNum(), EquationType.BRANCH_ALPHA1)
                        .addTerm(EquationTerm.createVariableTerm(controller, VariableType.BRANCH_ALPHA1, variableSet))
                        .setActive(false);
            }
        }
    }

    private static void createDiscreteVoltageControlEquation(LfBus bus,  VariableSet variableSet, EquationSystem equationSystem) {
        if (bus.isDiscreteVoltageControlled()) {
            EquationTerm vTerm = EquationTerm.createVariableTerm(bus, VariableType.BUS_V, variableSet, bus.getV().eval());
            equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(vTerm);
            bus.setV(vTerm);

            // add transformer distribution equations
            createR1DistributionEquations(equationSystem, variableSet, bus.getDiscreteVoltageControl().getControllers());

            for (LfBranch controllerBranch : bus.getDiscreteVoltageControl().getControllers()) {
                // we also create an equation that will be used later to maintain R1 variable constant
                // this equation is now inactive
                equationSystem.createEquation(controllerBranch.getNum(), EquationType.BRANCH_RHO1)
                        .addTerm(EquationTerm.createVariableTerm(controllerBranch, VariableType.BRANCH_RHO1, variableSet))
                        .setActive(false);
            }
        }
    }

    private static void createBusWithSlopeEquation(LfBus bus, AcEquationSystemCreationParameters creationParameters, VariableSet variableSet, EquationSystem equationSystem, EquationTerm vTerm) {
        // we only support one generator controlling voltage with a non zero slope at a bus.
        // equation is: V + slope * qSVC = targetV
        // which is modeled here with: V + slope * (sum_branch qBranch) = TargetV - slope * qLoads + slope * qGenerators
        Equation eq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V_SLOPE);
        eq.addTerm(vTerm);
        List<EquationTerm> controllerBusReactiveTerms = createReactiveTerms(bus, variableSet, creationParameters);
        double slope = bus.getGeneratorControllingVoltageWithSlope().getSlope();
        eq.setData(new DistributionData(bus.getNum(), slope)); // for later use
        for (EquationTerm eqTerm : controllerBusReactiveTerms) {
            eq.addTerm(EquationTerm.multiply(eqTerm, slope));
        }
    }

    public static void createR1DistributionEquations(EquationSystem equationSystem, VariableSet variableSet,
                                                     List<LfBranch> controllerBranches) {
        if (controllerBranches.size() > 1) {
            // we choose first controller bus as reference for reactive power
            LfBranch firstControllerBranch = controllerBranches.get(0);

            // create a R1 distribution equation for all the other controller branches
            for (int i = 1; i < controllerBranches.size(); i++) {
                LfBranch controllerBranch = controllerBranches.get(i);
                Equation zero = equationSystem.createEquation(controllerBranch.getNum(), EquationType.ZERO_RHO1)
                        .addTerm(EquationTerm.createVariableTerm(controllerBranch, VariableType.BRANCH_RHO1, variableSet))
                        .addTerm(EquationTerm.multiply(EquationTerm.createVariableTerm(firstControllerBranch, VariableType.BRANCH_RHO1, variableSet), -1));
                zero.setData(new DistributionData(firstControllerBranch.getNum(), 1)); // for later use
            }
        }
    }

    private static void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                             AcEquationSystemCreationParameters creationParameters,
                                             EquationSystem equationSystem) {
        EquationTerm p1 = null;
        EquationTerm q1 = null;
        EquationTerm p2 = null;
        EquationTerm q2 = null;
        EquationTerm i1 = null;
        EquationTerm i2 = null;
        boolean deriveA1 = creationParameters.isPhaseControl() && branch.isPhaseController()
                && branch.getDiscretePhaseControl().getMode() == DiscretePhaseControl.Mode.CONTROLLER;
        deriveA1 = deriveA1 || (creationParameters.isForceA1Var() && branch.hasPhaseControlCapability());
        boolean createCurrent = creationParameters.getBranchesWithCurrent() == null || creationParameters.getBranchesWithCurrent().contains(branch.getId());
        boolean deriveR1 = creationParameters.isTransformerVoltageControl() && branch.isVoltageController();
        if (bus1 != null && bus2 != null) {
            p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
            q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
            p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
            q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
            if (createCurrent) {
                i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
                i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
            }
        } else if (bus1 != null) {
            p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, variableSet, deriveA1, deriveR1);
            q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, variableSet, deriveA1, deriveR1);
            if (createCurrent) {
                i1 = new OpenBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, variableSet, deriveA1, deriveR1);
            }
        } else if (bus2 != null) {
            p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, variableSet, deriveA1, deriveR1);
            q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, variableSet, deriveA1, deriveR1);
            if (createCurrent) {
                i2 = new OpenBranchSide1CurrentMagnitudeEquationTerm(branch, bus2, variableSet, deriveA1, deriveR1);
            }
        }

        if (p1 != null) {
            Equation sp1 = equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P);
            if (sp1.getTerms().isEmpty()) {
                bus1.setP(sp1);
            }
            sp1.addTerm(p1);
            branch.setP1(p1);
            if (creationParameters.isPhaseControl()) {
                createBranchActivePowerTargetEquation(branch, DiscretePhaseControl.ControlledSide.ONE, equationSystem, variableSet, p1);
            }
        }
        if (q1 != null) {
            Equation sq1 = equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q);
            if (sq1.getTerms().isEmpty()) {
                bus1.setQ(sq1);
            }
            sq1.addTerm(q1);
            branch.setQ1(q1);
        }
        if (p2 != null) {
            Equation sp2 = equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P);
            if (sp2.getTerms().isEmpty()) {
                bus2.setP(sp2);
            }
            sp2.addTerm(p2);
            branch.setP2(p2);
            if (creationParameters.isPhaseControl()) {
                createBranchActivePowerTargetEquation(branch, DiscretePhaseControl.ControlledSide.TWO, equationSystem, variableSet, p2);
            }
        }
        if (q2 != null) {
            Equation sq2 = equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q);
            if (sq2.getTerms().isEmpty()) {
                bus2.setQ(sq2);
            }
            sq2.addTerm(q2);
            branch.setQ2(q2);
        }

        if (creationParameters.isForceA1Var() && branch.hasPhaseControlCapability()) {
            equationSystem.createEquation(branch.getNum(), EquationType.BRANCH_ALPHA1)
                .addTerm(EquationTerm.createVariableTerm(branch, VariableType.BRANCH_ALPHA1, variableSet));
        }

        if (i1 != null) {
            Equation i =  equationSystem.createEquation(bus1.getNum(), EquationType.BUS_I).addTerm(i1);
            i.setUpdateType(EquationSystem.EquationUpdateType.AFTER_NR); // only update those equations after the newton raphson
            branch.setI1(i1);
        }

        if (i2 != null) {
            Equation i =  equationSystem.createEquation(bus2.getNum(), EquationType.BUS_I).addTerm(i2);
            i.setUpdateType(EquationSystem.EquationUpdateType.AFTER_NR); // only update those equations after the newton raphson
            branch.setI2(i2);
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
                if (connectedSet.size() > 2 && connectedSet.stream().filter(LfBus::isVoltageControllerEnabled).count() > 1) {
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

        network.addListener(new AcEquationSystemUpdater(equationSystem, variableSet, creationParameters));

        return equationSystem;
    }
}
