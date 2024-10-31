/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationSystemPostProcessor;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.EvaluableConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcEquationSystemCreator {

    private final LfNetwork network;

    private final DcEquationSystemCreationParameters creationParameters;

    public DcEquationSystemCreator(LfNetwork network) {
        this(network, new DcEquationSystemCreationParameters());
    }

    public DcEquationSystemCreator(LfNetwork network, DcEquationSystemCreationParameters creationParameters) {
        this.network = Objects.requireNonNull(network);
        this.creationParameters = Objects.requireNonNull(creationParameters);
    }

    private void createBuses(EquationSystem<DcVariableType, DcEquationType> equationSystem) {

        List<LfBus> buses = network.getBuses();
        List<LfBus> slackBuses = network.getSlackBuses();
        for (LfBus bus : buses) {
            var p = equationSystem.createEquation(bus, DcEquationType.BUS_TARGET_P);
            bus.setP(p);
            if (bus.isReference()) {
                equationSystem.createEquation(bus, DcEquationType.BUS_TARGET_PHI)
                        .addTerm(equationSystem.getVariable(bus.getNum(), DcVariableType.BUS_PHI).createTerm());
            }
            if (bus.isSlack() || bus.isReference()) {
                p.setActive(false);
            }
        }

        Optional<LfBus> refSlackBusOpt = slackBuses.stream().filter(LfBus::isReference).findFirst();
        if (refSlackBusOpt.isEmpty()) {
            throw new IllegalStateException("No reference bus among slack buses");
        }
        LfBus refSlackBus = refSlackBusOpt.get();
        if (slackBuses.size() > 1) {
            List<LfBus> otherSlackBuses = slackBuses.stream().filter(b -> b != refSlackBus).toList();
            for (LfBus slackBus : otherSlackBuses) {
                // example for 2 slack buses
                // 0 = slack_p1 - slack_p2
                // 0 = slack_p1 - slack_p3
                equationSystem.createEquation(slackBus, DcEquationType.BUS_TARGET_P)
                        .addTerms(createActiveInjectionTerms(refSlackBus, equationSystem.getVariableSet()))
                        .addTerms(createActiveInjectionTerms(slackBus, equationSystem.getVariableSet()).stream()
                                .map(EquationTerm::minus)
                                .toList())
                        .setActive(true);
            }
        }
    }

    private List<EquationTerm<DcVariableType, DcEquationType>> createActiveInjectionTerms(LfBus bus,
                                                                                          VariableSet<DcVariableType> variableSet) {
        List<EquationTerm<DcVariableType, DcEquationType>> terms = new ArrayList<>();
        for (LfBranch branch : bus.getBranches()) {
            if (branch.isZeroImpedance(LoadFlowModel.DC)) {
                if (branch.isSpanningTreeEdge(LoadFlowModel.DC)) {
                    EquationTerm<DcVariableType, DcEquationType> p = variableSet.getVariable(branch.getNum(), DcVariableType.DUMMY_P).createTerm();
                    if (branch.getBus2() == bus) {
                        p = p.minus();
                    }
                    terms.add(p);
                }
            } else {
                boolean deriveA1 = isDeriveA1(branch, creationParameters);
                EquationTerm<DcVariableType, DcEquationType> p = null;
                if (branch.getBus1() == bus) {
                    LfBus otherSideBus = branch.getBus2();
                    if (otherSideBus != null) {
                        p = ClosedBranchSide1DcFlowEquationTerm.create(branch, bus, otherSideBus, variableSet, deriveA1, creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType());
                        branch.addAdditionalClosedP1(p);
                        if (branch.isDisconnectionAllowedSide2()) {
                            branch.setP1(EvaluableConstants.ZERO);
                        }
                    } else {
                        branch.setP1(EvaluableConstants.ZERO);
                    }
                } else {
                    LfBus otherSideBus = branch.getBus1();
                    if (otherSideBus != null) {
                        p = ClosedBranchSide2DcFlowEquationTerm.create(branch, bus, otherSideBus, variableSet, deriveA1, creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType());
                        branch.addAdditionalClosedP2(p);
                        if (branch.isDisconnectionAllowedSide1()) {
                            branch.setP2(EvaluableConstants.ZERO);
                        }
                    } else {
                        branch.setP2(EvaluableConstants.ZERO);
                    }
                }
                if (p != null) {
                    terms.add(p);
                }
            }
        }
        return terms;
    }

    public static void createNonImpedantBranch(EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                               LfBranch branch, LfBus bus1, LfBus bus2, boolean spanningTree) {
        if (bus1 != null && bus2 != null) {
            boolean hasPhi1 = equationSystem.hasEquation(bus1.getNum(), DcEquationType.BUS_TARGET_PHI);
            boolean hasPhi2 = equationSystem.hasEquation(bus2.getNum(), DcEquationType.BUS_TARGET_PHI);
            if (!(hasPhi1 && hasPhi2)) {
                // create voltage angle coupling equation
                // alpha = phi1 - phi2
                equationSystem.createEquation(branch, DcEquationType.ZERO_PHI)
                        .addTerm(equationSystem.getVariable(bus1.getNum(), DcVariableType.BUS_PHI).createTerm())
                        .addTerm(equationSystem.getVariable(bus2.getNum(), DcVariableType.BUS_PHI).<DcEquationType>createTerm()
                                .minus())
                        .setActive(!branch.isDisabled() && spanningTree);

                // add a dummy active power variable to both sides of the non impedant branch and with an opposite sign
                // to ensure we have the same number of equation and variables
                var dummyP = equationSystem.getVariable(branch.getNum(), DcVariableType.DUMMY_P);
                equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P)
                        .orElseThrow()
                        .addTerm(dummyP.createTerm());

                equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P)
                        .orElseThrow()
                        .addTerm(dummyP.<DcEquationType>createTerm().minus());

                // create an inactive dummy active power target equation set to zero that could be activated
                // on case of switch opening
                equationSystem.createEquation(branch, DcEquationType.DUMMY_TARGET_P)
                        .addTerm(dummyP.createTerm())
                        .setActive(branch.isDisabled() || !spanningTree); // inverted logic
            } else {
                throw new IllegalStateException("Cannot happen because only there is one slack bus per model");
            }
        }
    }

    private static void createImpedantBranch(EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                             DcEquationSystemCreationParameters creationParameters, LfBranch branch,
                                             LfBus bus1, LfBus bus2) {
        if (bus1 != null && bus2 != null) {
            boolean deriveA1 = isDeriveA1(branch, creationParameters);
            ClosedBranchSide1DcFlowEquationTerm p1 = ClosedBranchSide1DcFlowEquationTerm.create(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType());
            ClosedBranchSide2DcFlowEquationTerm p2 = ClosedBranchSide2DcFlowEquationTerm.create(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType());
            equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P)
                    .ifPresent(e -> e.addTerm(p1));
            equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P)
                    .ifPresent(e -> e.addTerm(p2));
            if (deriveA1) {
                EquationTerm<DcVariableType, DcEquationType> a1 = equationSystem.getVariable(branch.getNum(), DcVariableType.BRANCH_ALPHA1)
                        .createTerm();
                branch.setA1(a1);
                equationSystem.createEquation(branch, DcEquationType.BRANCH_TARGET_ALPHA1)
                        .addTerm(a1);
            }
            if (creationParameters.isUpdateFlows()) {
                branch.setP1(p1);
                branch.setClosedP1(p1);
                branch.setP2(p2);
                branch.setClosedP2(p2);
                ClosedBranchDcCurrent i1 = new ClosedBranchDcCurrent(branch, TwoSides.ONE, creationParameters.getDcPowerFactor());
                ClosedBranchDcCurrent i2 = new ClosedBranchDcCurrent(branch, TwoSides.TWO, creationParameters.getDcPowerFactor());
                branch.setI1(i1);
                branch.setI2(i2);
            }
        } else if (bus1 != null && creationParameters.isUpdateFlows()) {
            branch.setP1(EvaluableConstants.ZERO);
        } else if (bus2 != null && creationParameters.isUpdateFlows()) {
            branch.setP2(EvaluableConstants.ZERO);
        }
    }

    protected static boolean isDeriveA1(LfBranch branch, DcEquationSystemCreationParameters creationParameters) {
        return branch.isPhaseController()
                || creationParameters.isForcePhaseControlOffAndAddAngle1Var() && branch.hasPhaseControllerCapability() && branch.isConnectedAtBothSides();
    }

    private void createBranches(EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (branch.isZeroImpedance(LoadFlowModel.DC)) {
                createNonImpedantBranch(equationSystem, branch, bus1, bus2, branch.isSpanningTreeEdge(LoadFlowModel.DC));
            } else {
                createImpedantBranch(equationSystem, creationParameters, branch, bus1, bus2);
            }
        }
    }

    private void createHvdcs(EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        for (LfHvdc hvdc : network.getHvdcs()) {
            EquationTerm<DcVariableType, DcEquationType> p1 = null;
            EquationTerm<DcVariableType, DcEquationType> p2 = null;
            if (hvdc.getBus1() != null && hvdc.getBus2() != null && hvdc.isAcEmulation()) {
                p1 = new HvdcAcEmulationSide1DCFlowEquationTerm(hvdc, hvdc.getBus1(), hvdc.getBus2(), equationSystem.getVariableSet());
                p2 = new HvdcAcEmulationSide2DCFlowEquationTerm(hvdc, hvdc.getBus1(), hvdc.getBus2(), equationSystem.getVariableSet());
            }

            if (p1 != null) {
                equationSystem.getEquation(hvdc.getBus1().getNum(), DcEquationType.BUS_TARGET_P)
                        .orElseThrow()
                        .addTerm(p1);
                hvdc.setP1(p1);
            }

            if (p2 != null) {
                equationSystem.getEquation(hvdc.getBus2().getNum(), DcEquationType.BUS_TARGET_P)
                        .orElseThrow()
                        .addTerm(p2);
                hvdc.setP2(p2);
            }
        }
    }

    // TODO adapt to DC system
//    static void updateBranchEquations(LfBranch branch) {
//        if (!branch.isDisabled() && !branch.isZeroImpedance(LoadFlowModel.DC)) {
//            if (branch.isConnectedSide1() && branch.isConnectedSide2()) {
//                setActive(branch.getP1(), );
//
//
//
//
//
//                setActive(branch.getOpenP1(), false);
//                setActive(branch.getOpenQ1(), false);
//                setActive(branch.getClosedP1(), true);
//                setActive(branch.getClosedQ1(), true);
//                setActive(branch.getOpenP2(), false);
//                setActive(branch.getOpenQ2(), false);
//                setActive(branch.getClosedP2(), true);
//                setActive(branch.getClosedQ2(), true);
//                branch.getAdditionalOpenP1().forEach(openP1 -> setActive(openP1, false));
//                branch.getAdditionalOpenQ1().forEach(openQ1 -> setActive(openQ1, false));
//                branch.getAdditionalClosedP1().forEach(closedP1 -> setActive(closedP1, true));
//                branch.getAdditionalClosedQ1().forEach(closedQ1 -> setActive(closedQ1, true));
//                branch.getAdditionalOpenP2().forEach(openP2 -> setActive(openP2, false));
//                branch.getAdditionalOpenQ2().forEach(openQ2 -> setActive(openQ2, false));
//                branch.getAdditionalClosedP2().forEach(closedP2 -> setActive(closedP2, true));
//                branch.getAdditionalClosedQ2().forEach(closedQ2 -> setActive(closedQ2, true));
//            } else if (branch.isConnectedSide1() && !branch.isConnectedSide2()) {
//                setActive(branch.getOpenP1(), true);
//                setActive(branch.getOpenQ1(), true);
//                setActive(branch.getClosedP1(), false);
//                setActive(branch.getClosedQ1(), false);
//                setActive(branch.getOpenP2(), false);
//                setActive(branch.getOpenQ2(), false);
//                setActive(branch.getClosedP2(), false);
//                setActive(branch.getClosedQ2(), false);
//                branch.getAdditionalOpenP1().forEach(openP1 -> setActive(openP1, true));
//                branch.getAdditionalOpenQ1().forEach(openQ1 -> setActive(openQ1, true));
//                branch.getAdditionalClosedP1().forEach(closedP1 -> setActive(closedP1, false));
//                branch.getAdditionalClosedQ1().forEach(closedQ1 -> setActive(closedQ1, false));
//                branch.getAdditionalOpenP2().forEach(openP2 -> setActive(openP2, false));
//                branch.getAdditionalOpenQ2().forEach(openQ2 -> setActive(openQ2, false));
//                branch.getAdditionalClosedP2().forEach(closedP2 -> setActive(closedP2, false));
//                branch.getAdditionalClosedQ2().forEach(closedQ2 -> setActive(closedQ2, false));
//            } else if (!branch.isConnectedSide1() && branch.isConnectedSide2()) {
//                setActive(branch.getOpenP2(), true);
//                setActive(branch.getOpenQ2(), true);
//                setActive(branch.getClosedP2(), false);
//                setActive(branch.getClosedQ2(), false);
//                setActive(branch.getOpenP1(), false);
//                setActive(branch.getOpenQ1(), false);
//                setActive(branch.getClosedP1(), false);
//                setActive(branch.getClosedQ1(), false);
//                branch.getAdditionalOpenP1().forEach(openP1 -> setActive(openP1, false));
//                branch.getAdditionalOpenQ1().forEach(openQ1 -> setActive(openQ1, false));
//                branch.getAdditionalClosedP1().forEach(closedP1 -> setActive(closedP1, false));
//                branch.getAdditionalClosedQ1().forEach(closedQ1 -> setActive(closedQ1, false));
//                branch.getAdditionalOpenP2().forEach(openP2 -> setActive(openP2, true));
//                branch.getAdditionalOpenQ2().forEach(openQ2 -> setActive(openQ2, true));
//                branch.getAdditionalClosedP2().forEach(closedP2 -> setActive(closedP2, false));
//                branch.getAdditionalClosedQ2().forEach(closedQ2 -> setActive(closedQ2, false));
//            } else {
//                setActive(branch.getOpenP1(), false);
//                setActive(branch.getOpenQ1(), false);
//                setActive(branch.getClosedP1(), false);
//                setActive(branch.getClosedQ1(), false);
//                setActive(branch.getOpenP2(), false);
//                setActive(branch.getOpenQ2(), false);
//                setActive(branch.getClosedP2(), false);
//                setActive(branch.getClosedQ2(), false);
//                branch.getAdditionalOpenP1().forEach(openP1 -> setActive(openP1, false));
//                branch.getAdditionalOpenQ1().forEach(openQ1 -> setActive(openQ1, false));
//                branch.getAdditionalClosedP1().forEach(closedP1 -> setActive(closedP1, false));
//                branch.getAdditionalClosedQ1().forEach(closedQ1 -> setActive(closedQ1, false));
//                branch.getAdditionalOpenP2().forEach(openP2 -> setActive(openP2, false));
//                branch.getAdditionalOpenQ2().forEach(openQ2 -> setActive(openQ2, false));
//                branch.getAdditionalClosedP2().forEach(closedP2 -> setActive(closedP2, false));
//                branch.getAdditionalClosedQ2().forEach(closedQ2 -> setActive(closedQ2, false));
//            }
//        }
//    }

    public EquationSystem<DcVariableType, DcEquationType> create(boolean withListener) {
        EquationSystem<DcVariableType, DcEquationType> equationSystem = new EquationSystem<>();

        createBuses(equationSystem);
        createBranches(equationSystem);
        createHvdcs(equationSystem);

        EquationSystemPostProcessor.findAll().forEach(pp -> pp.onCreate(equationSystem));

        if (withListener) {
            network.addListener(LfNetworkListenerTracer.trace(new DcEquationSystemUpdater(equationSystem)));
        }

        return equationSystem;
    }
}
