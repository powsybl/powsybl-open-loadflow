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
import com.powsybl.openloadflow.network.TransformerPhaseControl.Mode;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemCreator {

    private final LfNetwork network;

    private final AcEquationSystemCreationParameters creationParameters;

    public AcEquationSystemCreator(LfNetwork network) {
        this(network, new AcEquationSystemCreationParameters());
    }

    public AcEquationSystemCreator(LfNetwork network, AcEquationSystemCreationParameters creationParameters) {
        this.network = Objects.requireNonNull(network);
        this.creationParameters = Objects.requireNonNull(creationParameters);
    }

    private void createBusEquation(LfBus bus,
                                   EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        var p = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P);
        bus.setP(p);
        var q = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q);
        bus.setQ(q);

        if (bus.isReference()) {
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_PHI)
                    .addTerm(equationSystem.getVariable(bus.getNum(), AcVariableType.BUS_PHI)
                            .createTerm());
        }
        if (bus.isSlack()) {
            p.setActive(false);
        }

        // maybe to fix later, but there is so part of OLF (like sensitivity) that needs a voltage target equation
        // deactivated
        EquationTerm<AcVariableType, AcEquationType> vTerm = equationSystem.getVariable(bus.getNum(), AcVariableType.BUS_V).createTerm();
        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_V)
                .addTerm(vTerm)
                .setActive(false);
        bus.setCalculatedV(vTerm);

        createShuntEquations(bus, equationSystem);
    }

    private void createVoltageControlEquations(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (LfBus bus : network.getBuses()) {
            createGeneratorVoltageControlEquations(bus, equationSystem);
            createTransformerVoltageControlEquations(bus, equationSystem);
            createShuntVoltageControlEquations(bus, equationSystem);
        }
    }

    private void createBusesEquations(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (LfBus bus : network.getBuses()) {
            createBusEquation(bus, equationSystem);
        }
    }

    private void createGeneratorVoltageControlEquations(LfBus bus,
                                                        EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        bus.getGeneratorVoltageControl()
                .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                .ifPresent(voltageControl -> {
                    if (bus.isGeneratorVoltageControlled()) {
                        if (voltageControl.isLocalControl()) {
                            createGeneratorLocalVoltageControlEquation(bus, equationSystem);
                        } else {
                            createGeneratorRemoteVoltageControlEquations(voltageControl, equationSystem);
                        }
                        updateGeneratorVoltageControl(voltageControl, equationSystem);
                    }
                });
    }

    private void createGeneratorLocalVoltageControlEquation(LfBus bus,
                                                            EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        if (bus.hasGeneratorsWithSlope()) {
            // take first generator with slope: network loading ensures that there's only one generator with slope
            double slope = bus.getGeneratorsControllingVoltageWithSlope().get(0).getSlope();

            // we only support one generator controlling voltage with a non zero slope at a bus.
            // equation is: V + slope * qSVC = targetV
            // which is modeled here with: V + slope * (sum_branch qBranch) = TargetV - slope * qLoads + slope * qGenerators
            equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).orElseThrow()
                    .addTerms(createReactiveTerms(bus, equationSystem.getVariableSet(), creationParameters)
                            .stream()
                            .map(term -> term.multiply(slope))
                            .collect(Collectors.toList()));
        }

        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q);
    }

    private static void createReactivePowerControlBranchEquation(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                                 boolean deriveA1, boolean deriveR1) {
        if (bus1 != null && bus2 != null) {
            branch.getReactivePowerControl().ifPresent(rpc -> {
                EquationTerm<AcVariableType, AcEquationType> q = rpc.getControlledSide() == ControlledSide.ONE
                        ? new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1)
                        : new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
                equationSystem.createEquation(branch, AcEquationType.BRANCH_TARGET_Q)
                        .addTerm(q);

                // if bus has both voltage and remote reactive power controls, then only voltage control has been kept
                equationSystem.createEquation(rpc.getControllerBus(), AcEquationType.BUS_TARGET_Q);

                updateReactivePowerControlBranchEquations(rpc, equationSystem);
            });
        }
    }

    public static void updateReactivePowerControlBranchEquations(ReactivePowerControl reactivePowerControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        equationSystem.getEquation(reactivePowerControl.getControlledBranch().getNum(), AcEquationType.BRANCH_TARGET_Q)
                .orElseThrow()
                .setActive(!reactivePowerControl.getControllerBus().isDisabled()
                        && !reactivePowerControl.getControlledBranch().isDisabled());
        equationSystem.getEquation(reactivePowerControl.getControllerBus().getNum(), AcEquationType.BUS_TARGET_Q)
                .orElseThrow()
                .setActive(false);
    }

    private static void createShuntEquation(LfShunt shunt, LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem, boolean deriveB) {
        ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, equationSystem.getVariableSet(), deriveB);
        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(q);
        shunt.setQ(q);
        ShuntCompensatorActiveFlowEquationTerm p = new ShuntCompensatorActiveFlowEquationTerm(shunt, bus, equationSystem.getVariableSet());
        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P).addTerm(p);
    }

    private static void createShuntEquations(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        bus.getShunt().ifPresent(shunt -> createShuntEquation(shunt, bus, equationSystem, false));
        bus.getControllerShunt().ifPresent(shunt -> createShuntEquation(shunt, bus, equationSystem, shunt.hasVoltageControlCapability()));
        bus.getSvcShunt().ifPresent(shunt -> createShuntEquation(shunt, bus, equationSystem, false));
    }

    private static void createReactivePowerDistributionEquations(GeneratorVoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                                 AcEquationSystemCreationParameters creationParameters) {
        List<LfBus> controllerBuses = voltageControl.getMergedControllerElements();
        for (LfBus controllerBus : controllerBuses) {
            // reactive power at controller bus i (supposing this voltage control is enabled)
            // q_i = qPercent_i * sum_j(q_j) where j are all the voltage controller buses
            // 0 = qPercent_i * sum_j(q_j) - q_i
            // which can be rewritten in a more simple way
            // 0 = (qPercent_i - 1) * q_i + qPercent_i * sum_j(q_j) where j are all the voltage controller buses except i
            Equation<AcVariableType, AcEquationType> zero = equationSystem.createEquation(controllerBus, AcEquationType.DISTR_Q)
                    .addTerms(createReactiveTerms(controllerBus, equationSystem.getVariableSet(), creationParameters).stream()
                            .map(term -> term.multiply(() -> controllerBus.getRemoteVoltageControlReactivePercent() - 1))
                            .collect(Collectors.toList()));
            for (LfBus otherControllerBus : controllerBuses) {
                if (otherControllerBus != controllerBus) {
                    zero.addTerms(createReactiveTerms(otherControllerBus, equationSystem.getVariableSet(), creationParameters).stream()
                            .map(term -> term.multiply(controllerBus::getRemoteVoltageControlReactivePercent))
                            .collect(Collectors.toList()));
                }
            }
        }
    }

    public static void recreateReactivePowerDistributionEquations(GeneratorVoltageControl voltageControl,
                                                                  EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                                  AcEquationSystemCreationParameters parameters) {
        for (LfBus controllerBus : voltageControl.getMergedControllerElements()) {
            equationSystem.removeEquation(controllerBus.getNum(), AcEquationType.DISTR_Q);
        }
        if (!voltageControl.isLocalControl()) {
            createReactivePowerDistributionEquations(voltageControl, equationSystem, parameters);
        }
        updateGeneratorVoltageControl(voltageControl, equationSystem);
    }

    private void createGeneratorRemoteVoltageControlEquations(GeneratorVoltageControl voltageControl,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (LfBus controllerBus : voltageControl.getMergedControllerElements()) {
            equationSystem.createEquation(controllerBus, AcEquationType.BUS_TARGET_Q);
        }

        // create reactive power distribution equations at voltage controller buses
        createReactivePowerDistributionEquations(voltageControl, equationSystem, creationParameters);
    }

    static <T extends LfElement> void updateRemoteVoltageControlEquations(VoltageControl<T> voltageControl,
                                                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                                          AcEquationType distrEqType, AcEquationType ctrlEqType) {
        checkNotDependentVoltageControl(voltageControl);

        LfBus controlledBus = voltageControl.getControlledBus();

        List<T> controllerElements = voltageControl.getMergedControllerElements()
                .stream()
                .filter(b -> !b.isDisabled()) // discard disabled controller elements
                .collect(Collectors.toList());

        Equation<AcVariableType, AcEquationType> vEq = equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                .orElseThrow();

        List<Equation<AcVariableType, AcEquationType>> vEqMergedList = voltageControl.getMergedDependentVoltageControls().stream()
                .map(vc -> equationSystem.getEquation(vc.getControlledBus().getNum(), AcEquationType.BUS_TARGET_V).orElseThrow())
                .collect(Collectors.toList());

        if (controlledBus.isDisabled()) {
            // we disable all voltage control equations
            vEq.setActive(false);
            for (T controllerElement : controllerElements) {
                equationSystem.getEquation(controllerElement.getNum(), distrEqType)
                        .orElseThrow()
                        .setActive(false);
                equationSystem.getEquation(controllerElement.getNum(), ctrlEqType)
                        .orElseThrow()
                        .setActive(!controllerElement.isDisabled());
            }
        } else {
            if (voltageControl.isHidden()) {
                for (T controllerElement : controllerElements) {
                    equationSystem.getEquation(controllerElement.getNum(), distrEqType)
                            .orElseThrow()
                            .setActive(false);
                    equationSystem.getEquation(controllerElement.getNum(), ctrlEqType)
                            .orElseThrow()
                            .setActive(true);
                }
            } else {
                List<T> enabledControllerElements = controllerElements.stream()
                        .filter(voltageControl::isControllerEnabled).collect(Collectors.toList());
                List<T> disabledControllerElements = controllerElements.stream()
                        .filter(Predicate.not(voltageControl::isControllerEnabled)).collect(Collectors.toList());

                // activate voltage control at controlled bus only if at least one controller element is enabled
                vEq.setActive(!enabledControllerElements.isEmpty());

                // deactivate voltage control for merged controlled buses
                for (var vEqMerged : vEqMergedList) {
                    vEqMerged.setActive(false);
                }

                // deactivate distribution equations and reactivate control equations
                for (T controllerElement : disabledControllerElements) {
                    equationSystem.getEquation(controllerElement.getNum(), distrEqType)
                            .orElseThrow()
                            .setActive(false);
                    equationSystem.getEquation(controllerElement.getNum(), ctrlEqType)
                            .orElseThrow()
                            .setActive(true);
                }

                // activate distribution equation and deactivate control equation at all enabled controller buses except one (first)
                for (int i = 0; i < enabledControllerElements.size(); i++) {
                    boolean active = i != 0;
                    T controllerElement = enabledControllerElements.get(i);
                    equationSystem.getEquation(controllerElement.getNum(), distrEqType)
                            .orElseThrow()
                            .setActive(active);
                    equationSystem.getEquation(controllerElement.getNum(), ctrlEqType)
                            .orElseThrow()
                            .setActive(false);
                }
            }
        }
    }

    static void updateRemoteGeneratorVoltageControlEquations(GeneratorVoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // ensure reactive keys are up-to-date
        voltageControl.updateReactiveKeys();

        updateRemoteVoltageControlEquations(voltageControl, equationSystem, AcEquationType.DISTR_Q, AcEquationType.BUS_TARGET_Q);
    }

    private static List<EquationTerm<AcVariableType, AcEquationType>> createReactiveTerms(LfBus controllerBus,
                                                                                          VariableSet<AcVariableType> variableSet,
                                                                                          AcEquationSystemCreationParameters creationParameters) {
        List<EquationTerm<AcVariableType, AcEquationType>> terms = new ArrayList<>();
        for (LfBranch branch : controllerBus.getBranches()) {
            EquationTerm<AcVariableType, AcEquationType> q;
            if (branch.isZeroImpedance(LoadFlowModel.AC)) {
                if (!branch.isSpanningTreeEdge(LoadFlowModel.AC)) {
                    continue;
                }
                if (branch.getBus1() == controllerBus) {
                    q = variableSet.getVariable(branch.getNum(), AcVariableType.DUMMY_Q).createTerm();
                } else {
                    q = variableSet.getVariable(branch.getNum(), AcVariableType.DUMMY_Q).<AcEquationType>createTerm()
                                    .minus();
                }
            } else {
                boolean deriveA1 = isDeriveA1(branch, creationParameters);
                boolean deriveR1 = isDeriveR1(branch);
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

    public static void updateGeneratorVoltageControl(GeneratorVoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        checkNotDependentVoltageControl(voltageControl);
        LfBus controlledBus = voltageControl.getControlledBus();
        if (voltageControl.isLocalControl()) {
            if (voltageControl.isHidden()) {
                equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                        .orElseThrow()
                        .setActive(false);
                equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_Q)
                        .orElseThrow()
                        .setActive(false);
            } else {
                equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                        .orElseThrow()
                        .setActive(controlledBus.isGeneratorVoltageControlEnabled());
                equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_Q)
                        .orElseThrow()
                        .setActive(!controlledBus.isGeneratorVoltageControlEnabled());
            }
        } else {
            updateRemoteGeneratorVoltageControlEquations(voltageControl, equationSystem);
        }
    }

    private static <T extends LfElement> void checkNotDependentVoltageControl(VoltageControl<T> voltageControl) {
        if (voltageControl.getMergeStatus() == VoltageControl.MergeStatus.DEPENDENT) {
            throw new IllegalArgumentException("Cannot update a merged dependent voltage control");
        }
    }

    private static void createNonImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2,
                                                EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                boolean spanningTreeEdge) {
        if (bus1 != null && bus2 != null) {
            Optional<Equation<AcVariableType, AcEquationType>> v1 = equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_V);
            Optional<Equation<AcVariableType, AcEquationType>> v2 = equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_V);
            boolean hasV1 = v1.isPresent() && v1.get().isActive(); // may be inactive if the equation has been created for sensitivity
            boolean hasV2 = v2.isPresent() && v2.get().isActive(); // may be inactive if the equation has been created for sensitivity
            boolean enabled = !branch.isDisabled() && spanningTreeEdge;
            if (!(hasV1 && hasV2)) {
                // create voltage magnitude coupling equation
                // 0 = v1 - v2 * rho
                PiModel piModel = branch.getPiModel();
                double rho = PiModel.R2 / piModel.getR1();
                EquationTerm<AcVariableType, AcEquationType> vTerm = equationSystem.getVariable(bus1.getNum(), AcVariableType.BUS_V)
                        .createTerm();
                EquationTerm<AcVariableType, AcEquationType> bus2vTerm = equationSystem.getVariable(bus2.getNum(), AcVariableType.BUS_V)
                        .createTerm();
                equationSystem.createEquation(branch, AcEquationType.ZERO_V)
                        .addTerm(vTerm)
                        .addTerm(bus2vTerm.multiply(-rho))
                        .setActive(enabled);

                // add a dummy reactive power variable to both sides of the non impedant branch and with an opposite sign
                // to ensure we have the same number of equation and variables
                var dummyQ = equationSystem.getVariable(branch.getNum(), AcVariableType.DUMMY_Q);
                equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_Q)
                        .orElseThrow()
                        .addTerm(dummyQ.createTerm());

                equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_Q)
                        .orElseThrow()
                        .addTerm(dummyQ.<AcEquationType>createTerm()
                                .minus());

                // create an inactive dummy reactive power target equation set to zero that could be activated
                // on case of switch opening
                equationSystem.createEquation(branch, AcEquationType.DUMMY_TARGET_Q)
                        .addTerm(dummyQ.createTerm())
                        .setActive(!enabled); // inverted logic
            } else {
                // nothing to do in case of v1 and v2 are found, we just have to ensure
                // target v are equals.
            }

            boolean hasPhi1 = equationSystem.hasEquation(bus1.getNum(), AcEquationType.BUS_TARGET_PHI);
            boolean hasPhi2 = equationSystem.hasEquation(bus2.getNum(), AcEquationType.BUS_TARGET_PHI);
            if (!(hasPhi1 && hasPhi2)) {
                // create voltage angle coupling equation
                // alpha = phi1 - phi2
                equationSystem.createEquation(branch, AcEquationType.ZERO_PHI)
                        .addTerm(equationSystem.getVariable(bus1.getNum(), AcVariableType.BUS_PHI).createTerm())
                        .addTerm(equationSystem.getVariable(bus2.getNum(), AcVariableType.BUS_PHI).<AcEquationType>createTerm()
                                .minus())
                        .setActive(enabled);

                // add a dummy active power variable to both sides of the non impedant branch and with an opposite sign
                // to ensure we have the same number of equation and variables
                var dummyP = equationSystem.getVariable(branch.getNum(), AcVariableType.DUMMY_P);
                equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P)
                        .orElseThrow()
                        .addTerm(dummyP.createTerm());

                equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P)
                        .orElseThrow()
                        .addTerm(dummyP.<AcEquationType>createTerm()
                                .minus());

                // create an inactive dummy active power target equation set to zero that could be activated
                // on case of switch opening
                equationSystem.createEquation(branch, AcEquationType.DUMMY_TARGET_P)
                        .addTerm(dummyP.createTerm())
                        .setActive(!enabled); // inverted logic
            } else {
                throw new IllegalStateException("Cannot happen because only there is one slack bus per model");
            }
        }
    }

    private static void createTransformerPhaseControlEquations(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                               boolean deriveA1, boolean deriveR1) {
        if (deriveA1) {
            EquationTerm<AcVariableType, AcEquationType> a1 = equationSystem.getVariable(branch.getNum(), AcVariableType.BRANCH_ALPHA1)
                    .createTerm();
            branch.setA1(a1);
            equationSystem.createEquation(branch, AcEquationType.BRANCH_TARGET_ALPHA1)
                    .addTerm(a1);
        }

        if (branch.isPhaseControlled()) {
            TransformerPhaseControl phaseControl = branch.getPhaseControl().orElseThrow();
            if (phaseControl.getMode() == Mode.CONTROLLER) {
                if (phaseControl.getUnit() == TransformerPhaseControl.Unit.A) {
                    throw new PowsyblException("Phase control in A is not yet supported");
                }

                EquationTerm<AcVariableType, AcEquationType> p = phaseControl.getControlledSide() == ControlledSide.ONE
                        ? new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1)
                        : new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
                equationSystem.createEquation(branch, AcEquationType.BRANCH_TARGET_P)
                        .addTerm(p)
                        .setActive(false); // by default BRANCH_TARGET_ALPHA1 is active and BRANCH_TARGET_P inactive
            }
        }
    }

    public static void updateTransformerPhaseControlEquations(TransformerPhaseControl phaseControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        LfBranch controllerBranch = phaseControl.getControllerBranch();
        LfBranch controlledBranch = phaseControl.getControlledBranch();

        if (phaseControl.getMode() == Mode.CONTROLLER) {
            boolean enabled = !controllerBranch.isDisabled() && !controlledBranch.isDisabled();

            // activate/de-activate phase control equation
            equationSystem.getEquation(controlledBranch.getNum(), AcEquationType.BRANCH_TARGET_P)
                    .orElseThrow()
                    .setActive(enabled && controllerBranch.isPhaseControlEnabled());

            // de-activate/activate constant A1 equation
            equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_ALPHA1)
                    .orElseThrow()
                    .setActive(enabled && !controllerBranch.isPhaseControlEnabled());
        } else {
            equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_ALPHA1)
                    .orElseThrow()
                    .setActive(!controllerBranch.isDisabled());
        }
    }

    private static void createTransformerVoltageControlEquations(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        bus.getTransformerVoltageControl()
                .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                .ifPresent(voltageControl -> {
                    // add transformer ratio distribution equations
                    createR1DistributionEquations(voltageControl, equationSystem);

                    // we also create an equation per controller that will be used later to maintain R1 variable constant
                    for (LfBranch controllerBranch : voltageControl.getMergedControllerElements()) {
                        equationSystem.createEquation(controllerBranch, AcEquationType.BRANCH_TARGET_RHO1)
                                .addTerm(equationSystem.getVariable(controllerBranch.getNum(), AcVariableType.BRANCH_RHO1).createTerm());
                    }

                    updateTransformerVoltageControlEquations(voltageControl, equationSystem);
                });
    }

    public static void createR1DistributionEquations(TransformerVoltageControl voltageControl,
                                                     EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        var controllerBranches = voltageControl.getMergedControllerElements();
        for (int i = 0; i < controllerBranches.size(); i++) {
            LfBranch controllerBranch = controllerBranches.get(i);
            // r1 at controller branch i
            // r1_i = sum_j(r1_j) / controller_count where j are all the controller branches
            // 0 = sum_j(r1_j) / controller_count - r1_i
            // which can be rewritten in a more simple way
            // 0 = (1 / controller_count - 1) * r1_i + sum_j(r1_j) / controller_count where j are all the controller branches except i
            EquationTerm<AcVariableType, AcEquationType> r1 = equationSystem.getVariable(controllerBranch.getNum(), AcVariableType.BRANCH_RHO1)
                    .createTerm();
            Equation<AcVariableType, AcEquationType> zero = equationSystem.createEquation(controllerBranch, AcEquationType.DISTR_RHO)
                    .addTerm(r1.multiply(() -> 1d / controllerBranches.stream().filter(b -> !b.isDisabled()).count() - 1));
            for (LfBranch otherControllerBranch : controllerBranches) {
                if (otherControllerBranch != controllerBranch) {
                    EquationTerm<AcVariableType, AcEquationType> otherR1 = equationSystem.getVariable(otherControllerBranch.getNum(), AcVariableType.BRANCH_RHO1)
                            .createTerm();
                    zero.addTerm(otherR1.multiply(() -> 1d / controllerBranches.stream().filter(b -> !b.isDisabled()).count()));
                }
            }
        }
    }

    static void updateTransformerVoltageControlEquations(TransformerVoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        updateRemoteVoltageControlEquations(voltageControl, equationSystem, AcEquationType.DISTR_RHO, AcEquationType.BRANCH_TARGET_RHO1);
    }

    public static void recreateR1DistributionEquations(TransformerVoltageControl voltageControl,
                                                       EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (LfBranch controllerBranch : voltageControl.getMergedControllerElements()) {
            equationSystem.removeEquation(controllerBranch.getNum(), AcEquationType.DISTR_RHO);
        }
        createR1DistributionEquations(voltageControl, equationSystem);
        updateTransformerVoltageControlEquations(voltageControl, equationSystem);
    }

    private static void createShuntVoltageControlEquations(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        bus.getShuntVoltageControl()
                .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                .ifPresent(voltageControl -> {
                    // add shunt distribution equations
                    createShuntSusceptanceDistributionEquations(voltageControl, equationSystem);

                    for (LfShunt controllerShunt : voltageControl.getMergedControllerElements()) {
                        // we also create an equation that will be used later to maintain B variable constant
                        // this equation is now inactive
                        equationSystem.createEquation(controllerShunt, AcEquationType.SHUNT_TARGET_B)
                                .addTerm(equationSystem.getVariable(controllerShunt.getNum(), AcVariableType.SHUNT_B).createTerm());
                    }

                    updateShuntVoltageControlEquations(voltageControl, equationSystem);
                });
    }

    public static void createShuntSusceptanceDistributionEquations(ShuntVoltageControl voltageControl,
                                                                   EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        var controllerShunts = voltageControl.getMergedControllerElements();
        for (LfShunt controllerShunt : controllerShunts) {
            // shunt b at controller bus i
            // b_i = sum_j(b_j) / controller_count where j are all the controller buses
            // 0 = sum_j(b_j) / controller_count - b_i
            // which can be rewritten in a more simple way
            // 0 = (1 / controller_count - 1) * b_i + sum_j(b_j) / controller_count where j are all the controller buses except i
            EquationTerm<AcVariableType, AcEquationType> shuntB = equationSystem.getVariable(controllerShunt.getNum(), AcVariableType.SHUNT_B)
                    .createTerm();
            Equation<AcVariableType, AcEquationType> zero = equationSystem.createEquation(controllerShunt, AcEquationType.DISTR_SHUNT_B)
                    .addTerm(shuntB.multiply(() -> 1d / controllerShunts.stream().filter(b -> !b.isDisabled()).count() - 1));
            for (LfShunt otherControllerShunt : controllerShunts) {
                if (otherControllerShunt != controllerShunt) {
                    EquationTerm<AcVariableType, AcEquationType> otherShuntB = equationSystem.getVariable(otherControllerShunt.getNum(), AcVariableType.SHUNT_B)
                            .createTerm();
                    zero.addTerm(otherShuntB.multiply(() -> 1d / controllerShunts.stream().filter(b -> !b.isDisabled()).count()));
                }
            }
        }
    }

    static void updateShuntVoltageControlEquations(ShuntVoltageControl voltageControl, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        updateRemoteVoltageControlEquations(voltageControl, equationSystem, AcEquationType.DISTR_SHUNT_B, AcEquationType.SHUNT_TARGET_B);
    }

    public static void recreateShuntSusceptanceDistributionEquations(ShuntVoltageControl voltageControl,
                                                                     EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (LfShunt controllerShunt : voltageControl.getMergedControllerElements()) {
            equationSystem.removeEquation(controllerShunt.getNum(), AcEquationType.DISTR_SHUNT_B);
        }
        createShuntSusceptanceDistributionEquations(voltageControl, equationSystem);
        updateShuntVoltageControlEquations(voltageControl, equationSystem);
    }

    private static boolean isDeriveA1(LfBranch branch, AcEquationSystemCreationParameters creationParameters) {
        return branch.isPhaseController()
                || (creationParameters.isForceA1Var() && branch.hasPhaseControllerCapability() && branch.isConnectedAtBothSides());
    }

    private static boolean isDeriveR1(LfBranch branch) {
        return branch.isVoltageController();
    }

    private void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2,
                                      EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        EquationTerm<AcVariableType, AcEquationType> p1 = null;
        EquationTerm<AcVariableType, AcEquationType> q1 = null;
        EquationTerm<AcVariableType, AcEquationType> p2 = null;
        EquationTerm<AcVariableType, AcEquationType> q2 = null;
        EquationTerm<AcVariableType, AcEquationType> i1 = null;
        EquationTerm<AcVariableType, AcEquationType> i2 = null;
        boolean deriveA1 = isDeriveA1(branch, creationParameters);
        boolean deriveR1 = isDeriveR1(branch);
        if (bus1 != null && bus2 != null) {
            p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
        } else if (bus1 != null) {
            p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            i1 = new OpenBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
        } else if (bus2 != null) {
            p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            i2 = new OpenBranchSide1CurrentMagnitudeEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
        }

        if (p1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P)
                    .orElseThrow()
                    .addTerm(p1);
            branch.setP1(p1);
        }
        if (q1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_Q)
                    .orElseThrow()
                    .addTerm(q1);
            branch.setQ1(q1);
        }
        if (p2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P)
                    .orElseThrow()
                    .addTerm(p2);
            branch.setP2(p2);
        }
        if (q2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_Q)
                    .orElseThrow()
                    .addTerm(q2);
            branch.setQ2(q2);
        }

        if (i1 != null) {
            equationSystem.attach(i1);
            branch.setI1(i1);
        }

        if (i2 != null) {
            equationSystem.attach(i2);
            branch.setI2(i2);
        }

        createReactivePowerControlBranchEquation(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);

        createTransformerPhaseControlEquations(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);
    }

    private static void createHvdcAcEmulationEquations(LfHvdc hvdc, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        EquationTerm<AcVariableType, AcEquationType> p1 = null;
        EquationTerm<AcVariableType, AcEquationType> p2 = null;
        if (hvdc.getBus1() != null && hvdc.getBus2() != null) {
            p1 = new HvdcAcEmulationSide1ActiveFlowEquationTerm(hvdc, hvdc.getBus1(), hvdc.getBus2(), equationSystem.getVariableSet());
            p2 = new HvdcAcEmulationSide2ActiveFlowEquationTerm(hvdc, hvdc.getBus1(), hvdc.getBus2(), equationSystem.getVariableSet());
        } else {
            // nothing to do
        }

        if (p1 != null) {
            equationSystem.getEquation(hvdc.getBus1().getNum(), AcEquationType.BUS_TARGET_P)
                    .orElseThrow()
                    .addTerm(p1);
            hvdc.setP1(p1);
        }
        if (p2 != null) {
            equationSystem.getEquation(hvdc.getBus2().getNum(), AcEquationType.BUS_TARGET_P)
                    .orElseThrow()
                    .addTerm(p2);
            hvdc.setP2(p2);
        }
    }

    private void createBranchEquations(LfBranch branch,
                                       EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // create zero and non zero impedance branch equations
        if (branch.isZeroImpedance(LoadFlowModel.AC)) {
            createNonImpedantBranch(branch, branch.getBus1(), branch.getBus2(), equationSystem, branch.isSpanningTreeEdge(LoadFlowModel.AC));
        } else {
            createImpedantBranch(branch, branch.getBus1(), branch.getBus2(), equationSystem);
        }
    }

    private void createBranchesEquations(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (LfBranch branch : network.getBranches()) {
            createBranchEquations(branch, equationSystem);
        }
    }

    private List<EquationTerm<AcVariableType, AcEquationType>> createActiveInjectionTerms(LfBus bus,
                                                                                          VariableSet<AcVariableType> variableSet) {
        List<EquationTerm<AcVariableType, AcEquationType>> terms = new ArrayList<>();
        for (LfBranch branch : bus.getBranches()) {
            if (branch.isZeroImpedance(LoadFlowModel.AC)) {
                if (branch.isSpanningTreeEdge(LoadFlowModel.AC)) {
                    EquationTerm<AcVariableType, AcEquationType> p = variableSet.getVariable(branch.getNum(), AcVariableType.DUMMY_P).createTerm();
                    if (branch.getBus2() == bus) {
                        p = p.minus();
                    }
                    terms.add(p);
                }
            } else {
                boolean deriveA1 = isDeriveA1(branch, creationParameters);
                boolean deriveR1 = isDeriveR1(branch);
                if (branch.getBus1() == bus) {
                    LfBus otherSideBus = branch.getBus2();
                    var p = otherSideBus != null ? new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus, otherSideBus, variableSet, deriveA1, deriveR1)
                                                 : new OpenBranchSide2ActiveFlowEquationTerm(branch, bus, variableSet, deriveA1, deriveR1);
                    terms.add(p);
                } else {
                    LfBus otherSideBus = branch.getBus1();
                    var p = otherSideBus != null ? new ClosedBranchSide2ActiveFlowEquationTerm(branch, otherSideBus, bus, variableSet, deriveA1, deriveR1)
                                                 : new OpenBranchSide1ActiveFlowEquationTerm(branch, bus, variableSet, deriveA1, deriveR1);
                    terms.add(p);
                }
            }
        }
        return terms;
    }

    private void createMultipleSlackBusesEquations(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        List<LfBus> slackBuses = network.getSlackBuses();
        if (slackBuses.size() > 1) {
            LfBus firstSlackBus = slackBuses.get(0);
            for (int i = 1; i < slackBuses.size(); i++) {
                LfBus slackBus = slackBuses.get(i);
                // example for 2 slack buses
                // 0 = slack_p1 - slack_p2
                // 0 = slack_p1 - slack_p3
                equationSystem.createEquation(slackBus, AcEquationType.BUS_DISTR_SLACK_P)
                        .addTerms(createActiveInjectionTerms(firstSlackBus, equationSystem.getVariableSet()))
                        .addTerms(createActiveInjectionTerms(slackBus, equationSystem.getVariableSet()).stream()
                                .map(EquationTerm::minus)
                                .collect(Collectors.toList()));
            }
        }
    }

    public EquationSystem<AcVariableType, AcEquationType> create() {

        EquationSystem<AcVariableType, AcEquationType> equationSystem = new EquationSystem<>();

        AcNetworkVector networkVector = new AcNetworkVector(network, equationSystem);

        createBusesEquations(equationSystem);
        createMultipleSlackBusesEquations(equationSystem);
        createBranchesEquations(equationSystem);

        for (LfHvdc hvdc : network.getHvdcs()) {
            createHvdcAcEmulationEquations(hvdc, equationSystem);
        }

        createVoltageControlEquations(equationSystem);

        EquationSystemPostProcessor.findAll().forEach(pp -> pp.onCreate(equationSystem));

        network.addListener(LfNetworkListenerTracer.trace(new AcEquationSystemUpdater(equationSystem, creationParameters)));

        networkVector.startListening();

        return equationSystem;
    }
}
