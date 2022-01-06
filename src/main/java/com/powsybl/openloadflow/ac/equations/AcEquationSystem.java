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

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class AcEquationSystem {

    private AcEquationSystem() {
    }

    private static void createBusEquation(LfBus bus, LfNetworkParameters networkParameters,
                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                          AcEquationSystemCreationParameters creationParameters) {
        if (bus.isSlack()) {
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_PHI)
                    .addTerm(equationSystem.getVariable(bus.getNum(), AcVariableType.BUS_PHI)
                                           .createTerm());
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_P).setActive(false);
        }

        createGeneratorControlEquations(bus, networkParameters, equationSystem, creationParameters);

        createShuntEquations(bus, equationSystem);

        if (networkParameters.isTransformerVoltageControl()) {
            createTransformerVoltageControlEquations(bus, equationSystem);
        }
        if (networkParameters.isShuntVoltageControl()) {
            createShuntVoltageControlEquations(bus, equationSystem);
        }
        Equation<AcVariableType, AcEquationType> v = equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V);
        if (v.getTerms().isEmpty()) {
            v.setActive(false);
            EquationTerm<AcVariableType, AcEquationType> vTerm = equationSystem.getVariable(bus.getNum(), AcVariableType.BUS_V).createTerm();
            v.addTerm(vTerm);
            bus.setCalculatedV(vTerm);
        }
    }

    private static void createBusesEquations(LfNetwork network, LfNetworkParameters networkParameters,
                                             EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                             AcEquationSystemCreationParameters creationParameters) {
        for (LfBus bus : network.getBuses()) {
            createBusEquation(bus, networkParameters, equationSystem, creationParameters);
        }
    }

    private static void createGeneratorControlEquations(LfBus bus, LfNetworkParameters networkParameters,
                                                        EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                        AcEquationSystemCreationParameters creationParameters) {
        Optional<VoltageControl> optVoltageControl = bus.getVoltageControl();
        if (optVoltageControl.isPresent()) {
            VoltageControl voltageControl = optVoltageControl.get();
            if (voltageControl.isVoltageControlLocal()) {
                createLocalVoltageControlEquation(bus, networkParameters, equationSystem, creationParameters);
            } else if (bus.isVoltageControlled()) {
                createRemoteVoltageControlEquations(voltageControl, networkParameters, equationSystem, creationParameters);
            }

            if (bus.isVoltageControllerEnabled()) {
                equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_Q).setActive(false);
            }
        } else { // If bus has both voltage and remote reactive power controls, then only voltage control has been kept
            bus.getReactivePowerControl()
                .ifPresent(rpc -> equationSystem.createEquation(rpc.getControllerBus().getNum(), AcEquationType.BUS_TARGET_Q).setActive(false));
        }
    }

    private static void createLocalVoltageControlEquation(LfBus bus, LfNetworkParameters networkParameters,
                                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                          AcEquationSystemCreationParameters creationParameters) {
        EquationTerm<AcVariableType, AcEquationType> vTerm = equationSystem.getVariable(bus.getNum(), AcVariableType.BUS_V)
                .createTerm();
        bus.setCalculatedV(vTerm);
        if (bus.hasGeneratorsWithSlope()) {
            // take first generator with slope: network loading ensures that there's only one generator with slope
            double slope = bus.getGeneratorsControllingVoltageWithSlope().get(0).getSlope();
            createBusWithSlopeEquation(bus, slope, networkParameters, equationSystem, vTerm, creationParameters);
        } else {
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).addTerm(vTerm);
        }
    }

    private static void createReactivePowerControlBranchEquation(LfBranch branch, ReactivePowerControl.ControlledSide controlledSide,
                                                                 EquationSystem<AcVariableType, AcEquationType> equationSystem, EquationTerm<AcVariableType, AcEquationType> q) {
        branch.getReactivePowerControl().ifPresent(reactivePowerControl -> {
            if (reactivePowerControl.getControlledSide() == controlledSide) {
                equationSystem.createEquation(branch.getNum(), AcEquationType.BRANCH_TARGET_Q).addTerm(q);
            }
        });
    }

    private static void createShuntEquations(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        bus.getShunt().ifPresent(shunt -> {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, equationSystem.getVariableSet(), false);
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_Q).addTerm(q);
        });
        bus.getControllerShunt().ifPresent(shunt -> {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, equationSystem.getVariableSet(), shunt.hasVoltageControl());
            equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_Q).addTerm(q);
        });
    }

    private static void createRemoteVoltageControlEquations(VoltageControl voltageControl, LfNetworkParameters networkParameters,
                                                            EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                            AcEquationSystemCreationParameters creationParameters) {
        LfBus controlledBus = voltageControl.getControlledBus();

        // create voltage equation at voltage controlled bus
        EquationTerm<AcVariableType, AcEquationType> vTerm = equationSystem.getVariable(controlledBus.getNum(), AcVariableType.BUS_V).createTerm();
        equationSystem.createEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                .addTerm(vTerm);
        controlledBus.setCalculatedV(vTerm);

        // create reactive power distribution equations at voltage controller buses
        createReactivePowerDistributionEquations(voltageControl.getControllerBuses(), networkParameters, equationSystem, creationParameters);

        updateRemoteVoltageControlEquations(voltageControl, equationSystem);
    }

    static void updateRemoteVoltageControlEquations(VoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // ensure reactive keys are up-to-date
        voltageControl.updateReactiveKeys();

        List<LfBus> controllerBuses = voltageControl.getControllerBuses()
                .stream()
                .filter(b -> !b.isDisabled())
                .collect(Collectors.toList());
        List<LfBus> enabledControllerBuses = new ArrayList<>(controllerBuses.size());
        List<LfBus> disabledControllerBuses = new ArrayList<>(controllerBuses.size());
        for (LfBus controllerBus : controllerBuses) {
            if (controllerBus.isVoltageControllerEnabled()) {
                enabledControllerBuses.add(controllerBus);
            } else {
                disabledControllerBuses.add(controllerBus);
            }
        }

        // activate voltage control at controlled bus only if at least one controller bus is enabled
        Equation<AcVariableType, AcEquationType> vEq = equationSystem.getEquation(voltageControl.getControlledBus().getNum(), AcEquationType.BUS_TARGET_V)
                .orElseThrow();
        vEq.setActive(!enabledControllerBuses.isEmpty());

        // deactivate reactive power distribution equation on all disabled (PQ) buses
        for (LfBus controllerBus : disabledControllerBuses) {
            equationSystem.getEquation(controllerBus.getNum(), AcEquationType.DISTR_Q)
                    .orElseThrow()
                    .setActive(false);
        }

        // activate reactive power distribution equation at all enabled controller buses except one (first)
        for (int i = 0; i < enabledControllerBuses.size(); i++) {
            LfBus controllerBus = enabledControllerBuses.get(i);
            var qDistrEq = equationSystem.getEquation(controllerBus.getNum(), AcEquationType.DISTR_Q)
                    .orElseThrow();
            qDistrEq.setActive(i != 0);
        }
    }

    private static List<EquationTerm<AcVariableType, AcEquationType>> createReactiveTerms(LfBus controllerBus, LfNetworkParameters networkParameters,
                                                                                          VariableSet<AcVariableType> variableSet, AcEquationSystemCreationParameters creationParameters) {
        List<EquationTerm<AcVariableType, AcEquationType>> terms = new ArrayList<>();
        for (LfBranch branch : controllerBus.getBranches()) {
            EquationTerm<AcVariableType, AcEquationType> q;
            if (LfNetwork.isZeroImpedanceBranch(branch)) {
                if (!branch.isSpanningTreeEdge()) {
                    continue;
                }
                if (branch.getBus1() == controllerBus) {
                    q = variableSet.getVariable(branch.getNum(), AcVariableType.DUMMY_Q).createTerm();
                } else {
                    q = variableSet.getVariable(branch.getNum(), AcVariableType.DUMMY_Q).<AcEquationType>createTerm()
                                    .minus();
                }
            } else {
                boolean deriveA1 = isDeriveA1(branch, networkParameters, creationParameters);
                boolean deriveR1 = isDeriveR1(branch, networkParameters);
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
        controllerBus.getShunt().ifPresent(shunt -> {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, controllerBus, variableSet, false);
            terms.add(q);
        });
        controllerBus.getControllerShunt().ifPresent(shunt -> {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, controllerBus, variableSet, false);
            terms.add(q);
        });
        return terms;
    }

    private static void createReactivePowerDistributionEquations(Collection<LfBus> controllerBuses, LfNetworkParameters networkParameters,
                                                                 EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                                 AcEquationSystemCreationParameters creationParameters) {
        for (LfBus controllerBus : controllerBuses) {
            // reactive power at controller bus i (supposing this voltage control is enabled)
            // q_i = qPercent_i * sum_j(q_j) where j are all the voltage controller buses
            // 0 = qPercent_i * sum_j(q_j) - q_i
            // which can be rewritten in a more simple way
            // 0 = (qPercent_i - 1) * q_i + qPercent_i * sum_j(q_j) where j are all the voltage controller buses except i
            Equation<AcVariableType, AcEquationType> zero = equationSystem.createEquation(controllerBus.getNum(), AcEquationType.DISTR_Q)
                    .addTerms(createReactiveTerms(controllerBus, networkParameters, equationSystem.getVariableSet(), creationParameters).stream()
                                .map(term -> term.multiply(() -> controllerBus.getRemoteVoltageControlReactivePercent() - 1))
                                .collect(Collectors.toList()));
            for (LfBus otherControllerBus : controllerBuses) {
                if (otherControllerBus != controllerBus) {
                    zero.addTerms(createReactiveTerms(otherControllerBus, networkParameters, equationSystem.getVariableSet(), creationParameters).stream()
                            .map(term -> term.multiply(controllerBus::getRemoteVoltageControlReactivePercent))
                            .collect(Collectors.toList()));
                }
            }
        }
    }

    private static void createBusWithSlopeEquation(LfBus bus, double slope, LfNetworkParameters networkParameters,
                                                   EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                   EquationTerm<AcVariableType, AcEquationType> vTerm, AcEquationSystemCreationParameters creationParameters) {
        // we only support one generator controlling voltage with a non zero slope at a bus.
        // equation is: V + slope * qSVC = targetV
        // which is modeled here with: V + slope * (sum_branch qBranch) = TargetV - slope * qLoads + slope * qGenerators
        equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V_WITH_SLOPE)
                .addTerm(vTerm)
                .addTerms(createReactiveTerms(bus, networkParameters, equationSystem.getVariableSet(), creationParameters)
                        .stream()
                        .map(term -> term.multiply(slope))
                        .collect(Collectors.toList()));
    }

    private static void createNonImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2,
                                                EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        Optional<Equation<AcVariableType, AcEquationType>> v1 = equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_V);
        Optional<Equation<AcVariableType, AcEquationType>> v2 = equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_V);
        boolean hasV1 = v1.isPresent() && v1.get().isActive(); // may be inactive if the equation has been created for sensitivity
        boolean hasV2 = v2.isPresent() && v2.get().isActive(); // may be inactive if the equation has been created for sensitivity
        if (!(hasV1 && hasV2)) {
            // create voltage magnitude coupling equation
            // 0 = v1 - v2 * rho
            PiModel piModel = branch.getPiModel();
            double rho = PiModel.R2 / piModel.getR1();
            EquationTerm<AcVariableType, AcEquationType> vTerm = equationSystem.getVariable(bus1.getNum(), AcVariableType.BUS_V)
                    .createTerm();
            EquationTerm<AcVariableType, AcEquationType> bus2vTerm = equationSystem.getVariable(bus2.getNum(), AcVariableType.BUS_V)
                    .createTerm();
            equationSystem.createEquation(branch.getNum(), AcEquationType.ZERO_V)
                    .addTerm(vTerm)
                    .addTerm(bus2vTerm.multiply(-rho));
            bus1.setCalculatedV(vTerm);
            // add a dummy reactive power variable to both sides of the non impedant branch and with an opposite sign
            // to ensure we have the same number of equation and variables
            Equation<AcVariableType, AcEquationType> sq1 = equationSystem.createEquation(bus1.getNum(), AcEquationType.BUS_TARGET_Q);
            if (sq1.getTerms().isEmpty()) {
                bus1.setQ(sq1);
            }
            sq1.addTerm(equationSystem.getVariable(branch.getNum(), AcVariableType.DUMMY_Q).createTerm());

            Equation<AcVariableType, AcEquationType> sq2 = equationSystem.createEquation(bus2.getNum(), AcEquationType.BUS_TARGET_Q);
            if (sq2.getTerms().isEmpty()) {
                bus2.setQ(sq2);
            }
            sq2.addTerm(equationSystem.getVariable(branch.getNum(), AcVariableType.DUMMY_Q).<AcEquationType>createTerm()
                                    .minus());
        } else {
            // nothing to do in case of v1 and v2 are found, we just have to ensure
            // target v are equals.
        }

        boolean hasPhi1 = equationSystem.hasEquation(bus1.getNum(), AcEquationType.BUS_TARGET_PHI);
        boolean hasPhi2 = equationSystem.hasEquation(bus2.getNum(), AcEquationType.BUS_TARGET_PHI);
        if (!(hasPhi1 && hasPhi2)) {
            // create voltage angle coupling equation
            // alpha = phi1 - phi2
            equationSystem.createEquation(branch.getNum(), AcEquationType.ZERO_PHI)
                    .addTerm(equationSystem.getVariable(bus1.getNum(), AcVariableType.BUS_PHI).createTerm())
                    .addTerm(equationSystem.getVariable(bus2.getNum(), AcVariableType.BUS_PHI).<AcEquationType>createTerm()
                                         .minus());

            // add a dummy active power variable to both sides of the non impedant branch and with an opposite sign
            // to ensure we have the same number of equation and variables
            Equation<AcVariableType, AcEquationType> sp1 = equationSystem.createEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P);
            if (sp1.getTerms().isEmpty()) {
                bus1.setP(sp1);
            }
            sp1.addTerm(equationSystem.getVariable(branch.getNum(), AcVariableType.DUMMY_P).createTerm());

            Equation<AcVariableType, AcEquationType> sp2 = equationSystem.createEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P);
            if (sp2.getTerms().isEmpty()) {
                bus2.setP(sp2);
            }
            sp2.addTerm(equationSystem.getVariable(branch.getNum(), AcVariableType.DUMMY_P).<AcEquationType>createTerm()
                                    .minus());
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
                equationSystem.createEquation(branch.getNum(), AcEquationType.BRANCH_TARGET_P).addTerm(p);

                // we also create an equation that will be used later to maintain A1 variable constant
                // this equation is now inactive
                LfBranch controller = dpc.getController();
                EquationTerm<AcVariableType, AcEquationType> a1 = equationSystem.getVariable(controller.getNum(), AcVariableType.BRANCH_ALPHA1)
                        .createTerm();
                branch.setA1(a1);
                equationSystem.createEquation(controller.getNum(), AcEquationType.BRANCH_TARGET_ALPHA1)
                        .addTerm(a1)
                        .setActive(false);
            });
    }

    private static void createTransformerVoltageControlEquations(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        bus.getTransformerVoltageControl()
                .filter(voltageControl -> bus.isTransformerVoltageControlled())
                .ifPresent(voltageControl -> {
                    // create voltage target equation at controlled bus
                    EquationTerm<AcVariableType, AcEquationType> vTerm = equationSystem.getVariable(bus.getNum(), AcVariableType.BUS_V)
                            .createTerm();
                    equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).addTerm(vTerm);
                    bus.setCalculatedV(vTerm);

                    // add transformer ratio distribution equations
                    createR1DistributionEquations(voltageControl.getControllers(), equationSystem);

                    // we also create an equation per controller that will be used later to maintain R1 variable constant
                    for (LfBranch controllerBranch : voltageControl.getControllers()) {
                        equationSystem.createEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_RHO1)
                                .addTerm(equationSystem.getVariable(controllerBranch.getNum(), AcVariableType.BRANCH_RHO1).createTerm());
                    }

                    updateTransformerVoltageControlEquations(voltageControl, equationSystem);
                });
    }

    public static void createR1DistributionEquations(List<LfBranch> controllerBranches,
                                                     EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (int i = 0; i < controllerBranches.size(); i++) {
            LfBranch controllerBranch = controllerBranches.get(i);
            // r1 at controller branch i
            // r1_i = sum_j(r1_j) / controller_count where j are all the controller branches
            // 0 = sum_j(r1_j) / controller_count - r1_i
            // which can be rewritten in a more simple way
            // 0 = (1 / controller_count - 1) * r1_i + sum_j(r1_j) / controller_count where j are all the controller branches except i
            EquationTerm<AcVariableType, AcEquationType> r1 = equationSystem.getVariable(controllerBranch.getNum(), AcVariableType.BRANCH_RHO1)
                    .createTerm();
            Equation<AcVariableType, AcEquationType> zero = equationSystem.createEquation(controllerBranch.getNum(), AcEquationType.DISTR_RHO)
                    .addTerm(r1.multiply(() -> 1d / controllerBranches.size() - 1));
            for (LfBranch otherControllerBranch : controllerBranches) {
                if (otherControllerBranch != controllerBranch) {
                    EquationTerm<AcVariableType, AcEquationType> otherR1 = equationSystem.getVariable(otherControllerBranch.getNum(), AcVariableType.BRANCH_RHO1)
                            .createTerm();
                    zero.addTerm(otherR1.multiply(() -> 1d / controllerBranches.size()));
                }
            }
        }
    }

    static void updateTransformerVoltageControlEquations(TransformerVoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        boolean on = voltageControl.getMode() == DiscreteVoltageControl.Mode.VOLTAGE;

        // activate voltage target equation if control is on
        equationSystem.getEquation(voltageControl.getControlled().getNum(), AcEquationType.BUS_TARGET_V)
                .orElseThrow()
                .setActive(on);

        List<LfBranch> controllerBranches = voltageControl.getControllers();
        for (int i = 0; i < controllerBranches.size(); i++) {
            LfBranch controllerBranch = controllerBranches.get(i);

            // activate all rho1 equations if voltage control is off
            equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_RHO1)
                    .orElseThrow()
                    .setActive(!on);

            // activate rho1 distribution equations except one if control is on
            equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.DISTR_RHO)
                    .orElseThrow()
                    .setActive(on && i < controllerBranches.size() - 1);
        }
    }

    private static void createShuntVoltageControlEquations(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        bus.getShuntVoltageControl()
                .filter(voltageControl -> bus.isShuntVoltageControlled())
                .ifPresent(voltageControl -> {
                    EquationTerm<AcVariableType, AcEquationType> vTerm = equationSystem.getVariable(bus.getNum(), AcVariableType.BUS_V)
                            .createTerm();
                    equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).addTerm(vTerm);
                    bus.setCalculatedV(vTerm);

                    // add shunt distribution equations
                    createShuntSusceptanceDistributionEquations(voltageControl.getControllers(), equationSystem);

                    for (LfBus controllerBus : voltageControl.getControllers()) {
                        // we also create an equation that will be used later to maintain B variable constant
                        // this equation is now inactive
                        controllerBus.getControllerShunt()
                            .ifPresent(shunt ->
                                equationSystem.createEquation(shunt.getNum(), AcEquationType.SHUNT_TARGET_B)
                                        .addTerm(equationSystem.getVariable(shunt.getNum(), AcVariableType.SHUNT_B).createTerm())
                            );
                    }

                    updateShuntVoltageControlEquations(voltageControl, equationSystem);
                });
    }

    public static void createShuntSusceptanceDistributionEquations(List<LfBus> controllerBuses,
                                                                   EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            controllerBus.getControllerShunt()
                .ifPresent(shunt -> {
                    // shunt b at controller bus i
                    // b_i = sum_j(b_j) / controller_count where j are all the controller buses
                    // 0 = sum_j(b_j) / controller_count - b_i
                    // which can be rewritten in a more simple way
                    // 0 = (1 / controller_count - 1) * b_i + sum_j(b_j) / controller_count where j are all the controller buses except i
                    EquationTerm<AcVariableType, AcEquationType> shuntB = equationSystem.getVariable(shunt.getNum(), AcVariableType.SHUNT_B)
                            .createTerm();
                    Equation<AcVariableType, AcEquationType> zero = equationSystem.createEquation(controllerBus.getNum(), AcEquationType.DISTR_SHUNT_B)
                            .addTerm(shuntB.multiply(() -> 1d / controllerBuses.size() - 1));
                    for (LfBus otherControllerBus : controllerBuses) {
                        if (otherControllerBus != controllerBus) {
                            otherControllerBus.getControllerShunt()
                                .ifPresent(otherShunt -> {
                                    EquationTerm<AcVariableType, AcEquationType> otherShuntB = equationSystem.getVariable(otherShunt.getNum(), AcVariableType.SHUNT_B)
                                            .createTerm();
                                    zero.addTerm(otherShuntB.multiply(() -> 1d / controllerBuses.size()));
                                });
                        }
                    }
                });
        }
    }

    static void updateShuntVoltageControlEquations(ShuntVoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        boolean on = voltageControl.getMode() == DiscreteVoltageControl.Mode.VOLTAGE;

        // activate voltage target equation if control is on
        equationSystem.getEquation(voltageControl.getControlled().getNum(), AcEquationType.BUS_TARGET_V)
                .orElseThrow()
                .setActive(on);

        List<LfBus> controllerBuses = voltageControl.getControllers();
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);

            // activate all b target equations if voltage control is off
            controllerBus.getControllerShunt().ifPresent(shunt ->
                equationSystem.getEquation(shunt.getNum(), AcEquationType.SHUNT_TARGET_B)
                        .orElseThrow()
                        .setActive(!on)
            );

            // activate shunt b distribution equations except one if control is on
            equationSystem.getEquation(controllerBus.getNum(), AcEquationType.DISTR_SHUNT_B)
                    .orElseThrow()
                    .setActive(on && i < controllerBuses.size() - 1);
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

    private static void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2, LfNetworkParameters networkParameters,
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
            p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            if (createCurrent) {
                i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
                i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            }
        } else if (bus1 != null) {
            p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            if (createCurrent) {
                i1 = new OpenBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            }
        } else if (bus2 != null) {
            p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            if (createCurrent) {
                i2 = new OpenBranchSide1CurrentMagnitudeEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            }
        }

        if (p1 != null) {
            Equation<AcVariableType, AcEquationType> sp1 = equationSystem.createEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P);
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
            Equation<AcVariableType, AcEquationType> sq1 = equationSystem.createEquation(bus1.getNum(), AcEquationType.BUS_TARGET_Q);
            if (sq1.getTerms().isEmpty()) {
                bus1.setQ(sq1);
            }
            sq1.addTerm(q1);
            branch.setQ1(q1);
            createReactivePowerControlBranchEquation(branch, ReactivePowerControl.ControlledSide.ONE, equationSystem, q1);
        }
        if (p2 != null) {
            Equation<AcVariableType, AcEquationType> sp2 = equationSystem.createEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P);
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
            Equation<AcVariableType, AcEquationType> sq2 = equationSystem.createEquation(bus2.getNum(), AcEquationType.BUS_TARGET_Q);
            if (sq2.getTerms().isEmpty()) {
                bus2.setQ(sq2);
            }
            sq2.addTerm(q2);
            branch.setQ2(q2);
            createReactivePowerControlBranchEquation(branch, ReactivePowerControl.ControlledSide.TWO, equationSystem, q2);
        }

        if ((creationParameters.isForceA1Var() && branch.hasPhaseControlCapability()) || (networkParameters.isPhaseControl() && branch.isPhaseController()
                && branch.getDiscretePhaseControl().filter(dpc -> dpc.getMode() == Mode.LIMITER).isPresent())) {
            EquationTerm<AcVariableType, AcEquationType> a1 = equationSystem.getVariable(branch.getNum(), AcVariableType.BRANCH_ALPHA1)
                    .createTerm();
            branch.setA1(a1);
            equationSystem.createEquation(branch.getNum(), AcEquationType.BRANCH_TARGET_ALPHA1)
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

    private static void createBranchEquations(LfBranch branch, LfNetworkParameters networkParameters,
                                                EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                AcEquationSystemCreationParameters creationParameters) {
        // create zero and non zero impedance branch equations
        if (LfNetwork.isZeroImpedanceBranch(branch)) {
            if (branch.isSpanningTreeEdge()) {
                createNonImpedantBranch(branch, branch.getBus1(), branch.getBus2(), equationSystem);
            }
        } else {
            createImpedantBranch(branch, branch.getBus1(), branch.getBus2(), networkParameters, equationSystem, creationParameters);
        }
    }

    private static void createBranchesEquations(LfNetwork network, LfNetworkParameters networkParameters,
                                                EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                AcEquationSystemCreationParameters creationParameters) {
        for (LfBranch branch : network.getBranches()) {
            createBranchEquations(branch, networkParameters, equationSystem, creationParameters);
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

        createBusesEquations(network, networkParameters, equationSystem, creationParameters);
        createBranchesEquations(network, networkParameters, equationSystem, creationParameters);

        EquationSystemPostProcessor.findAll().forEach(pp -> pp.onCreate(equationSystem));

        network.addListener(new AcEquationSystemUpdater(equationSystem));

        return equationSystem;
    }
}
