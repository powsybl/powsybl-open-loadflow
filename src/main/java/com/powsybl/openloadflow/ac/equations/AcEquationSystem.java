/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.DiscretePhaseControl.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class AcEquationSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcEquationSystem.class);

    private AcEquationSystem() {
    }

    private static void createBusEquation(BranchVector branchVec, LfBus bus, LfNetworkParameters networkParameters,
                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                          AcEquationSystemCreationParameters creationParameters) {
        if (bus.isSlack()) {
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_PHI).addTerm(EquationTerm.createVariableTerm(bus, AcVariableType.BUS_PHI, equationSystem.getVariableSet()));
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_P).setActive(false);
        }

        createGeneratorControlEquations(branchVec, bus, networkParameters, equationSystem, creationParameters);

        createShuntEquations(bus, equationSystem);

        if (networkParameters.isTransformerVoltageControl()) {
            createDiscreteVoltageControlEquation(bus, equationSystem);
        }
        Equation<AcVariableType, AcEquationType> v = equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_V);
        if (v.getTerms().isEmpty()) {
            v.setActive(false);
            EquationTerm<AcVariableType, AcEquationType> vTerm = EquationTerm.createVariableTerm(bus, AcVariableType.BUS_V, equationSystem.getVariableSet());
            v.addTerm(vTerm);
            bus.setCalculatedV(vTerm);
        }
    }

    private static void createBusesEquations(LfNetwork network, BranchVector branchVec, LfNetworkParameters networkParameters,
                                             EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                             AcEquationSystemCreationParameters creationParameters) {
        for (LfBus bus : network.getBuses()) {
            createBusEquation(branchVec, bus, networkParameters, equationSystem, creationParameters);
        }
    }

    private static void createGeneratorControlEquations(BranchVector branchVec, LfBus bus, LfNetworkParameters networkParameters,
                                                        EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                        AcEquationSystemCreationParameters creationParameters) {
        Optional<VoltageControl> optVoltageControl = bus.getVoltageControl();
        if (optVoltageControl.isPresent()) {
            VoltageControl voltageControl = optVoltageControl.get();
            if (voltageControl.isVoltageControlLocal()) {
                createLocalVoltageControlEquation(branchVec, bus, networkParameters, equationSystem, creationParameters);
            } else if (bus.isVoltageControlled()) {
                // remote controlled: set voltage equation on this controlled bus
                createVoltageControlledBusEquations(branchVec, voltageControl, networkParameters, equationSystem, creationParameters);
            }

            if (bus.isVoltageControllerEnabled()) {
                equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_Q).setActive(false);
            }
        } else { // If bus has both voltage and remote reactive power controls, then only voltage control has been kept
            bus.getReactivePowerControl()
                .ifPresent(rpc -> equationSystem.createEquation(rpc.getControllerBus().getNum(), AcEquationType.BUS_Q).setActive(false));
        }
    }

    private static void createLocalVoltageControlEquation(BranchVector branchVec, LfBus bus, LfNetworkParameters networkParameters,
                                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                          AcEquationSystemCreationParameters creationParameters) {
        EquationTerm<AcVariableType, AcEquationType> vTerm = EquationTerm.createVariableTerm(bus, AcVariableType.BUS_V, equationSystem.getVariableSet());
        bus.setCalculatedV(vTerm);
        if (bus.hasGeneratorsWithSlope()) {
            // take first generator with slope: network loading ensures that there's only one generator with slope
            double slope = bus.getGeneratorsControllingVoltageWithSlope().get(0).getSlope();
            createBusWithSlopeEquation(branchVec, bus, slope, networkParameters, equationSystem, vTerm, creationParameters);
        } else {
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_V).addTerm(vTerm);
        }
    }

    private static void createReactivePowerControlBranchEquation(LfBranch branch, ReactivePowerControl.ControlledSide controlledSide,
                                                                 EquationSystem<AcVariableType, AcEquationType> equationSystem, EquationTerm<AcVariableType, AcEquationType> q) {
        branch.getReactivePowerControl().ifPresent(reactivePowerControl -> {
            if (reactivePowerControl.getControlledSide() == controlledSide) {
                equationSystem.createEquation(branch.getNum(), AcEquationType.BRANCH_Q).addTerm(q);
            }
        });
    }

    private static void createShuntEquations(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (LfShunt shunt : bus.getShunts()) {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, equationSystem.getVariableSet());
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_Q).addTerm(q);
            shunt.setQ(q);
        }
    }

    private static void createVoltageControlledBusEquations(BranchVector branchVec, VoltageControl voltageControl, LfNetworkParameters networkParameters,
                                                            EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                            AcEquationSystemCreationParameters creationParameters) {
        LfBus controlledBus = voltageControl.getControlledBus();

        // create voltage equation at voltage controlled bus
        EquationTerm<AcVariableType, AcEquationType> vTerm = EquationTerm.createVariableTerm(controlledBus, AcVariableType.BUS_V, equationSystem.getVariableSet());
        Equation<AcVariableType, AcEquationType> vEq = equationSystem.createEquation(controlledBus.getNum(), AcEquationType.BUS_V)
                .addTerm(vTerm);
        controlledBus.setCalculatedV(vTerm);

        List<LfBus> controllerBuses = voltageControl.getControllerBuses().stream()
                .filter(LfBus::isVoltageControllerEnabled)
                .collect(Collectors.toList());
        if (controllerBuses.isEmpty()) {
            vEq.setActive(false);
        } else {
            // create reactive power distribution equations at voltage controller buses (except one)
            createReactivePowerDistributionEquations(branchVec, controllerBuses, networkParameters, equationSystem, creationParameters);
        }
    }

    private static List<EquationTerm<AcVariableType, AcEquationType>> createReactiveTerms(BranchVector branchVec, LfBus controllerBus, LfNetworkParameters networkParameters,
                                                                                          VariableSet<AcVariableType> variableSet, AcEquationSystemCreationParameters creationParameters) {
        List<EquationTerm<AcVariableType, AcEquationType>> terms = new ArrayList<>();
        for (LfBranch branch : controllerBus.getBranches()) {
            EquationTerm<AcVariableType, AcEquationType> q;
            if (LfNetwork.isZeroImpedanceBranch(branch)) {
                if (!branch.isSpanningTreeEdge()) {
                    continue;
                }
                if (branch.getBus1() == controllerBus) {
                    q = EquationTerm.createVariableTerm(branch, AcVariableType.DUMMY_Q, variableSet);
                } else {
                    q = EquationTerm.multiply(EquationTerm.<AcVariableType, AcEquationType>createVariableTerm(branch, AcVariableType.DUMMY_Q, variableSet), -1);
                }
            } else {
                boolean deriveA1 = isDeriveA1(branch, networkParameters, creationParameters);
                boolean deriveR1 = isDeriveR1(branch, networkParameters);
                if (branch.getBus1() == controllerBus) {
                    LfBus otherSideBus = branch.getBus2();
                    q = otherSideBus != null ? new ClosedBranchSide1ReactiveFlowEquationTerm(branchVec, branch.getNum(), controllerBus, otherSideBus, variableSet, deriveA1, deriveR1)
                                             : new OpenBranchSide2ReactiveFlowEquationTerm(branchVec, branch.getNum(), controllerBus, variableSet, deriveA1, deriveR1);
                } else {
                    LfBus otherSideBus = branch.getBus1();
                    q = otherSideBus != null ? new ClosedBranchSide2ReactiveFlowEquationTerm(branchVec, branch.getNum(), otherSideBus, controllerBus, variableSet, deriveA1, deriveR1)
                                             : new OpenBranchSide1ReactiveFlowEquationTerm(branchVec, branch.getNum(), controllerBus, variableSet, deriveA1, deriveR1);
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

    public static void createReactivePowerDistributionEquations(BranchVector branchVec, List<LfBus> controllerBuses, LfNetworkParameters networkParameters,
                                                                EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                                AcEquationSystemCreationParameters creationParameters) {
        double[] qKeys = createReactiveKeys(controllerBuses);

        // we choose first controller bus as reference for reactive power
        LfBus firstControllerBus = controllerBuses.get(0);
        List<EquationTerm<AcVariableType, AcEquationType>> firstControllerBusReactiveTerms
                = createReactiveTerms(branchVec, firstControllerBus, networkParameters, equationSystem.getVariableSet(), creationParameters);

        // create a reactive power distribution equation for all the other controller buses
        for (int i = 1; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            double c = qKeys[0] / qKeys[i];

            // l0 - c * li = q0 - c * qi
            Equation<AcVariableType, AcEquationType> zero = equationSystem.createEquation(controllerBus.getNum(), AcEquationType.ZERO_Q);
            zero.setData(new DistributionData(firstControllerBus.getNum(), c)); // for later use
            zero.addTerms(firstControllerBusReactiveTerms);
            zero.addTerms(createReactiveTerms(branchVec, controllerBus, networkParameters, equationSystem.getVariableSet(), creationParameters).
                    stream()
                    .map(term -> EquationTerm.multiply(term, -c))
                    .collect(Collectors.toList()));
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

    private static void createNonImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2,
                                                EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        Optional<Equation<AcVariableType, AcEquationType>> v1 = equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_V);
        Optional<Equation<AcVariableType, AcEquationType>> v2 = equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_V);
        boolean hasV1 = v1.isPresent() && v1.get().isActive(); // may be inactive if the equation has been created for sensitivity
        boolean hasV2 = v2.isPresent() && v2.get().isActive(); // may be inactive if the equation has been created for sensitivity
        if (!(hasV1 && hasV2)) {
            // create voltage magnitude coupling equation
            // 0 = v1 - v2 * rho
            PiModel piModel = branch.getPiModel();
            double rho = PiModel.R2 / piModel.getR1();
            EquationTerm<AcVariableType, AcEquationType> vTerm = EquationTerm.createVariableTerm(bus1, AcVariableType.BUS_V, equationSystem.getVariableSet());
            EquationTerm<AcVariableType, AcEquationType> bus2vTerm = EquationTerm.createVariableTerm(bus2, AcVariableType.BUS_V, equationSystem.getVariableSet());
            equationSystem.createEquation(branch.getNum(), AcEquationType.ZERO_V)
                    .addTerm(vTerm)
                    .addTerm(EquationTerm.multiply(bus2vTerm, -1 * rho));
            bus1.setCalculatedV(vTerm);
            // add a dummy reactive power variable to both sides of the non impedant branch and with an opposite sign
            // to ensure we have the same number of equation and variables
            Equation<AcVariableType, AcEquationType> sq1 = equationSystem.createEquation(bus1.getNum(), AcEquationType.BUS_Q);
            if (sq1.getTerms().isEmpty()) {
                bus1.setQ(sq1);
            }
            sq1.addTerm(EquationTerm.createVariableTerm(branch, AcVariableType.DUMMY_Q, equationSystem.getVariableSet()));

            Equation<AcVariableType, AcEquationType> sq2 = equationSystem.createEquation(bus2.getNum(), AcEquationType.BUS_Q);
            if (sq2.getTerms().isEmpty()) {
                bus2.setQ(sq2);
            }
            sq2.addTerm(EquationTerm.multiply(EquationTerm.<AcVariableType, AcEquationType>createVariableTerm(branch, AcVariableType.DUMMY_Q, equationSystem.getVariableSet()), -1));
        } else {
            // nothing to do in case of v1 and v2 are found, we just have to ensure
            // target v are equals.
        }

        boolean hasPhi1 = equationSystem.hasEquation(bus1.getNum(), AcEquationType.BUS_PHI);
        boolean hasPhi2 = equationSystem.hasEquation(bus2.getNum(), AcEquationType.BUS_PHI);
        if (!(hasPhi1 && hasPhi2)) {
            // create voltage angle coupling equation
            // alpha = phi1 - phi2
            equationSystem.createEquation(branch.getNum(), AcEquationType.ZERO_PHI)
                    .addTerm(EquationTerm.createVariableTerm(bus1, AcVariableType.BUS_PHI, equationSystem.getVariableSet()))
                    .addTerm(EquationTerm.multiply(EquationTerm.<AcVariableType, AcEquationType>createVariableTerm(bus2, AcVariableType.BUS_PHI, equationSystem.getVariableSet()), -1));

            // add a dummy active power variable to both sides of the non impedant branch and with an opposite sign
            // to ensure we have the same number of equation and variables
            Equation<AcVariableType, AcEquationType> sp1 = equationSystem.createEquation(bus1.getNum(), AcEquationType.BUS_P);
            if (sp1.getTerms().isEmpty()) {
                bus1.setP(sp1);
            }
            sp1.addTerm(EquationTerm.createVariableTerm(branch, AcVariableType.DUMMY_P, equationSystem.getVariableSet()));

            Equation<AcVariableType, AcEquationType> sp2 = equationSystem.createEquation(bus2.getNum(), AcEquationType.BUS_P);
            if (sp2.getTerms().isEmpty()) {
                bus2.setP(sp2);
            }
            sp2.addTerm(EquationTerm.multiply(EquationTerm.<AcVariableType, AcEquationType>createVariableTerm(branch, AcVariableType.DUMMY_P, equationSystem.getVariableSet()), -1));
        } else {
            throw new IllegalStateException("Cannot happen because only there is one slack bus per model");
        }
    }

    private static void createBranchActivePowerTargetEquation(LfBranch branch, DiscretePhaseControl.ControlledSide controlledSide,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem, EquationTerm<AcVariableType, AcEquationType> p) {
        branch.getDiscretePhaseControl()
            .filter(dpc -> branch.isPhaseControlled(controlledSide) && dpc.getMode() == Mode.CONTROLLER)
            .ifPresent(dpc -> {
                if (dpc.getUnit() == DiscretePhaseControl.Unit.A) {
                    throw new PowsyblException("Phase control in A is not yet supported");
                }
                equationSystem.createEquation(branch.getNum(), AcEquationType.BRANCH_P).addTerm(p);

                // we also create an equation that will be used later to maintain A1 variable constant
                // this equation is now inactive
                LfBranch controller = dpc.getController();
                EquationTerm.VariableEquationTerm<AcVariableType, AcEquationType> a1 = EquationTerm.createVariableTerm(controller, AcVariableType.BRANCH_ALPHA1, equationSystem.getVariableSet());
                branch.setA1(a1);
                equationSystem.createEquation(controller.getNum(), AcEquationType.BRANCH_ALPHA1)
                        .addTerm(a1)
                        .setActive(false);
            });
    }

    private static void createDiscreteVoltageControlEquation(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        bus.getDiscreteVoltageControl()
            .filter(dvc -> bus.isDiscreteVoltageControlled())
            .map(DiscreteVoltageControl::getControllers)
            .ifPresent(controllers -> {
                EquationTerm<AcVariableType, AcEquationType> vTerm = EquationTerm.createVariableTerm(bus, AcVariableType.BUS_V, equationSystem.getVariableSet());
                equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_V).addTerm(vTerm);
                bus.setCalculatedV(vTerm);

                // add transformer distribution equations
                createR1DistributionEquations(controllers, equationSystem);

                for (LfBranch controllerBranch : controllers) {
                    // we also create an equation that will be used later to maintain R1 variable constant
                    // this equation is now inactive
                    equationSystem.createEquation(controllerBranch.getNum(), AcEquationType.BRANCH_RHO1)
                        .addTerm(EquationTerm.createVariableTerm(controllerBranch, AcVariableType.BRANCH_RHO1, equationSystem.getVariableSet()))
                        .setActive(false);
                }
            });
    }

    private static void createBusWithSlopeEquation(BranchVector branchVec, LfBus bus, double slope, LfNetworkParameters networkParameters,
                                                   EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                   EquationTerm<AcVariableType, AcEquationType> vTerm, AcEquationSystemCreationParameters creationParameters) {
        // we only support one generator controlling voltage with a non zero slope at a bus.
        // equation is: V + slope * qSVC = targetV
        // which is modeled here with: V + slope * (sum_branch qBranch) = TargetV - slope * qLoads + slope * qGenerators
        Equation<AcVariableType, AcEquationType> eq = equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_V_SLOPE);
        eq.addTerm(vTerm);
        List<EquationTerm<AcVariableType, AcEquationType>> controllerBusReactiveTerms = createReactiveTerms(branchVec, bus, networkParameters, equationSystem.getVariableSet(), creationParameters);
        eq.setData(new DistributionData(bus.getNum(), slope)); // for later use
        for (EquationTerm<AcVariableType, AcEquationType> eqTerm : controllerBusReactiveTerms) {
            eq.addTerm(EquationTerm.multiply(eqTerm, slope));
        }
    }

    public static void createR1DistributionEquations(List<LfBranch> controllerBranches,
                                                     EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        if (controllerBranches.size() > 1) {
            // we choose first controller bus as reference for reactive power
            LfBranch firstControllerBranch = controllerBranches.get(0);

            // create a R1 distribution equation for all the other controller branches
            for (int i = 1; i < controllerBranches.size(); i++) {
                LfBranch controllerBranch = controllerBranches.get(i);
                Equation<AcVariableType, AcEquationType> zero = equationSystem.createEquation(controllerBranch.getNum(), AcEquationType.ZERO_RHO1)
                        .addTerm(EquationTerm.createVariableTerm(controllerBranch, AcVariableType.BRANCH_RHO1, equationSystem.getVariableSet()))
                        .addTerm(EquationTerm.multiply(EquationTerm.<AcVariableType, AcEquationType>createVariableTerm(firstControllerBranch, AcVariableType.BRANCH_RHO1, equationSystem.getVariableSet()), -1));
                zero.setData(new DistributionData(firstControllerBranch.getNum(), 1)); // for later use
            }
        }
    }

    private static boolean isDeriveA1(LfBranch branch, LfNetworkParameters networkParameters, AcEquationSystemCreationParameters creationParameters) {
        return (networkParameters.isPhaseControl()
                && branch.isPhaseController()
                && branch.getDiscretePhaseControl().filter(dpc -> dpc.getMode() != DiscretePhaseControl.Mode.OFF).isPresent())
                || (creationParameters.isForceA1Var() && branch.hasPhaseControlCapability() && branch.isConnectedAtBothSides());
    }

    private static boolean isDeriveR1(LfBranch branch, LfNetworkParameters networkParameters) {
        return networkParameters.isTransformerVoltageControl() && branch.isVoltageController();
    }

    private static void createImpedantBranch(BranchVector branchVec, LfBranch branch, LfBus bus1, LfBus bus2, LfNetworkParameters networkParameters,
                                             EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                             AcEquationSystemCreationParameters creationParameters) {
        EquationTerm<AcVariableType, AcEquationType> p1 = null;
        EquationTerm<AcVariableType, AcEquationType> q1 = null;
        EquationTerm<AcVariableType, AcEquationType> p2 = null;
        EquationTerm<AcVariableType, AcEquationType> q2 = null;
        EquationTerm<AcVariableType, AcEquationType> i1 = null;
        EquationTerm<AcVariableType, AcEquationType> i2 = null;
        boolean deriveA1 = isDeriveA1(branch, networkParameters, creationParameters);
        boolean deriveR1 = isDeriveR1(branch, networkParameters);
        boolean createCurrent = creationParameters.getBranchesWithCurrent() == null || creationParameters.getBranchesWithCurrent().contains(branch.getId());
        if (bus1 != null && bus2 != null) {
            p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branchVec, branch.getNum(), bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branchVec, branch.getNum(), bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branchVec, branch.getNum(), bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branchVec, branch.getNum(), bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            if (createCurrent) {
                i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branchVec, branch.getNum(), bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
                i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branchVec, branch.getNum(), bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            }
        } else if (bus1 != null) {
            p1 = new OpenBranchSide2ActiveFlowEquationTerm(branchVec, branch.getNum(), bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branchVec, branch.getNum(), bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            if (createCurrent) {
                i1 = new OpenBranchSide2CurrentMagnitudeEquationTerm(branchVec, branch.getNum(), bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            }
        } else if (bus2 != null) {
            p2 = new OpenBranchSide1ActiveFlowEquationTerm(branchVec, branch.getNum(), bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branchVec, branch.getNum(), bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            if (createCurrent) {
                i2 = new OpenBranchSide1CurrentMagnitudeEquationTerm(branchVec, branch.getNum(), bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            }
        }

        if (p1 != null) {
            Equation<AcVariableType, AcEquationType> sp1 = equationSystem.createEquation(bus1.getNum(), AcEquationType.BUS_P);
            if (sp1.getTerms().isEmpty()) {
                bus1.setP(sp1);
            }
            sp1.addTerm(p1);
            branch.setP1(p1);
            if (networkParameters.isPhaseControl()) {
                createBranchActivePowerTargetEquation(branch, DiscretePhaseControl.ControlledSide.ONE, equationSystem, p1);
            }
        }
        if (q1 != null) {
            Equation<AcVariableType, AcEquationType> sq1 = equationSystem.createEquation(bus1.getNum(), AcEquationType.BUS_Q);
            if (sq1.getTerms().isEmpty()) {
                bus1.setQ(sq1);
            }
            sq1.addTerm(q1);
            branch.setQ1(q1);
            createReactivePowerControlBranchEquation(branch, ReactivePowerControl.ControlledSide.ONE, equationSystem, q1);
        }
        if (p2 != null) {
            Equation<AcVariableType, AcEquationType> sp2 = equationSystem.createEquation(bus2.getNum(), AcEquationType.BUS_P);
            if (sp2.getTerms().isEmpty()) {
                bus2.setP(sp2);
            }
            sp2.addTerm(p2);
            branch.setP2(p2);
            if (networkParameters.isPhaseControl()) {
                createBranchActivePowerTargetEquation(branch, DiscretePhaseControl.ControlledSide.TWO, equationSystem, p2);
            }
        }
        if (q2 != null) {
            Equation<AcVariableType, AcEquationType> sq2 = equationSystem.createEquation(bus2.getNum(), AcEquationType.BUS_Q);
            if (sq2.getTerms().isEmpty()) {
                bus2.setQ(sq2);
            }
            sq2.addTerm(q2);
            branch.setQ2(q2);
            createReactivePowerControlBranchEquation(branch, ReactivePowerControl.ControlledSide.TWO, equationSystem, q2);
        }

        if ((creationParameters.isForceA1Var() && branch.hasPhaseControlCapability()) || (networkParameters.isPhaseControl() && branch.isPhaseController()
                && branch.getDiscretePhaseControl().filter(dpc -> dpc.getMode() == Mode.LIMITER).isPresent())) {
            EquationTerm.VariableEquationTerm<AcVariableType, AcEquationType> a1 = EquationTerm.createVariableTerm(branch, AcVariableType.BRANCH_ALPHA1, equationSystem.getVariableSet());
            branch.setA1(a1);
            equationSystem.createEquation(branch.getNum(), AcEquationType.BRANCH_ALPHA1)
                    .addTerm(a1);
        }

        if (i1 != null) {
            equationSystem.attach(i1);
            branch.setI1(i1);
        }

        if (i2 != null) {
            equationSystem.attach(i2);
            branch.setI2(i2);
        }
    }

    private static void createBranchEquations(BranchVector branchVec, LfBranch branch, LfNetworkParameters networkParameters,
                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                              AcEquationSystemCreationParameters creationParameters) {
        // create zero and non zero impedance branch equations
        if (LfNetwork.isZeroImpedanceBranch(branch)) {
            if (branch.isSpanningTreeEdge()) {
                createNonImpedantBranch(branch, branch.getBus1(), branch.getBus2(), equationSystem);
            }
        } else {
            createImpedantBranch(branchVec, branch, branch.getBus1(), branch.getBus2(), networkParameters, equationSystem, creationParameters);
        }
    }

    private static void createBranchesEquations(LfNetwork network, BranchVector branchVec, LfNetworkParameters networkParameters,
                                                EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                AcEquationSystemCreationParameters creationParameters) {
        for (LfBranch branch : network.getBranches()) {
            createBranchEquations(branchVec, branch, networkParameters, equationSystem, creationParameters);
        }
    }

    public static EquationSystem<AcVariableType, AcEquationType> create(LfNetwork network) {
        return create(network, new LfNetworkParameters());
    }

    public static EquationSystem<AcVariableType, AcEquationType> create(LfNetwork network, LfNetworkParameters networkParameters) {
        return create(network, networkParameters, new AcEquationSystemCreationParameters());
    }

    public static EquationSystem<AcVariableType, AcEquationType> create(LfNetwork network, LfNetworkParameters networkParameters,
                                                                        AcEquationSystemCreationParameters creationParameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(creationParameters);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = new EquationSystem<>(true);

        BranchVector branchVec = new BranchVector(network.getBranches());
        createBusesEquations(network, branchVec, networkParameters, equationSystem, creationParameters);
        createBranchesEquations(network, branchVec, networkParameters, equationSystem, creationParameters);

        EquationSystemPostProcessor.findAll().forEach(pp -> pp.onCreate(equationSystem));

        network.addListener(new AcEquationSystemUpdater(equationSystem, creationParameters, branchVec, networkParameters));

        return equationSystem;
    }
}
