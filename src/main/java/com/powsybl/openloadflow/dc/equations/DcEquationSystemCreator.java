/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationSystemPostProcessor;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.LfNetworkListenerTracer;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.EvaluableConstants;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcEquationSystemCreator {

    private final LfNetwork network;

    private final DcEquationSystemCreationParameters creationParameters;

    public DcEquationSystemCreator(LfNetwork network, DcEquationSystemCreationParameters creationParameters) {
        this.network = Objects.requireNonNull(network);
        this.creationParameters = Objects.requireNonNull(creationParameters);
    }

    private void createBuses(EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        for (LfBus bus : network.getBuses()) {
            var p = equationSystem.createEquation(bus, DcEquationType.BUS_TARGET_P);
            bus.setP(p);
            if (bus.isSlack()) {
                equationSystem.createEquation(bus, DcEquationType.BUS_TARGET_PHI)
                        .addTerm(equationSystem.getVariable(bus.getNum(), DcVariableType.BUS_PHI).createTerm());
                p.setActive(false);
            }
        }
    }

    public static void createNonImpedantBranch(EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                               LfBranch branch, LfBus bus1, LfBus bus2, boolean spanningTree) {
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

    private static void createImpedantBranch(EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                             DcEquationSystemCreationParameters creationParameters, LfBranch branch,
                                             LfBus bus1, LfBus bus2) {
        if (bus1 != null && bus2 != null) {
            boolean deriveA1 = creationParameters.isForcePhaseControlOffAndAddAngle1Var() && branch.hasPhaseControllerCapability(); //TODO: phase control outer loop
            ClosedBranchSide1DcFlowEquationTerm p1 = ClosedBranchSide1DcFlowEquationTerm.create(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, creationParameters.isUseTransformerRatio());
            ClosedBranchSide2DcFlowEquationTerm p2 = ClosedBranchSide2DcFlowEquationTerm.create(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, creationParameters.isUseTransformerRatio());
            equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P)
                    .orElseThrow()
                    .addTerm(p1);
            equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P)
                    .orElseThrow()
                    .addTerm(p2);
            if (deriveA1) {
                if (creationParameters.isForcePhaseControlOffAndAddAngle1Var()) {
                    // use for sensitiviy analysis only: with this equation term, we force the a1 variable to be constant.
                    EquationTerm<DcVariableType, DcEquationType> a1 = equationSystem.getVariable(branch.getNum(), DcVariableType.BRANCH_ALPHA1)
                            .createTerm();
                    branch.setA1(a1);
                    equationSystem.createEquation(branch, DcEquationType.BRANCH_TARGET_ALPHA1)
                            .addTerm(a1);
                } else {
                    //TODO
                }
            }
            if (creationParameters.isUpdateFlows()) {
                branch.setP1(p1);
                branch.setP2(p2);
            }
        } else if (bus1 != null && creationParameters.isUpdateFlows()) {
            branch.setP1(EvaluableConstants.ZERO);
        } else if (bus2 != null && creationParameters.isUpdateFlows()) {
            branch.setP2(EvaluableConstants.ZERO);
        }
    }

    private void createBranches(EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (branch.isZeroImpedance(true)) {
                createNonImpedantBranch(equationSystem, branch, bus1, bus2, branch.isSpanningTreeEdge(true));
            } else {
                createImpedantBranch(equationSystem, creationParameters, branch, bus1, bus2);
            }
        }
    }

    public EquationSystem<DcVariableType, DcEquationType> create(boolean withListener) {
        EquationSystem<DcVariableType, DcEquationType> equationSystem = new EquationSystem<>();

        createBuses(equationSystem);
        createBranches(equationSystem);

        EquationSystemPostProcessor.findAll().forEach(pp -> pp.onCreate(equationSystem));

        if (withListener) {
            network.addListener(LfNetworkListenerTracer.trace(new DcEquationSystemUpdater(equationSystem)));
        }

        return equationSystem;
    }
}
