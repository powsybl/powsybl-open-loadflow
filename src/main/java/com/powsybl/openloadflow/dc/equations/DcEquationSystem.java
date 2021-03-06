/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.EvaluableConstants;
import net.jafama.FastMath;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.Pseudograph;

import java.util.ArrayList;
import java.util.List;

import static com.powsybl.openloadflow.network.LfNetwork.LOW_IMPEDANCE_THRESHOLD;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class DcEquationSystem {

    private DcEquationSystem() {
    }

    private static void createBuses(LfNetwork network, VariableSet variableSet, EquationSystem equationSystem) {
        for (LfBus bus : network.getBuses()) {
            if (bus.isSlack()) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_PHI).addTerm(EquationTerm.createVariableTerm(bus, VariableType.BUS_PHI, variableSet));
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_P).setActive(false);
            }
        }
    }

    public static void createNonImpedantBranch(VariableSet variableSet, EquationSystem equationSystem,
                                               LfBranch branch, LfBus bus1, LfBus bus2) {
        boolean hasPhi1 = equationSystem.hasEquation(bus1.getNum(), EquationType.BUS_PHI);
        boolean hasPhi2 = equationSystem.hasEquation(bus2.getNum(), EquationType.BUS_PHI);
        if (!(hasPhi1 && hasPhi2)) {
            // create voltage angle coupling equation
            // alpha = phi1 - phi2
            equationSystem.createEquation(branch.getNum(), EquationType.ZERO_PHI)
                    .addTerm(EquationTerm.createVariableTerm(bus1, VariableType.BUS_PHI, variableSet))
                    .addTerm(EquationTerm.multiply(EquationTerm.createVariableTerm(bus2, VariableType.BUS_PHI, variableSet), -1));

            // add a dummy active power variable to both sides of the non impedant branch and with an opposite sign
            // to ensure we have the same number of equation and variables
            Equation sp1 = equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P);
            if (sp1.getTerms().isEmpty()) {
                bus1.setP(sp1);
            }
            sp1.addTerm(EquationTerm.createVariableTerm(branch, VariableType.DUMMY_P, variableSet));

            Equation sp2 = equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P);
            if (sp2.getTerms().isEmpty()) {
                bus2.setP(sp2);
            }
            sp2.addTerm(EquationTerm.multiply(EquationTerm.createVariableTerm(branch, VariableType.DUMMY_P, variableSet), -1));
        } else {
            throw new IllegalStateException("Cannot happen because only there is one slack bus per model");
        }
    }

    private static void createImpedantBranch(VariableSet variableSet, EquationSystem equationSystem,
                                             DcEquationSystemCreationParameters creationParameters, LfBranch branch,
                                             LfBus bus1, LfBus bus2) {
        if (bus1 != null && bus2 != null) {
            boolean deriveA1 = creationParameters.isForcePhaseControlOffAndAddAngle1Var() && branch.hasPhaseControlCapability(); //TODO: phase control outer loop
            ClosedBranchSide1DcFlowEquationTerm p1 = ClosedBranchSide1DcFlowEquationTerm.create(branch, bus1, bus2, variableSet, deriveA1, creationParameters.isUseTransformerRatio());
            ClosedBranchSide2DcFlowEquationTerm p2 = ClosedBranchSide2DcFlowEquationTerm.create(branch, bus1, bus2, variableSet, deriveA1, creationParameters.isUseTransformerRatio());
            Equation sp1 = equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P);
            if (sp1.getTerms().isEmpty()) {
                bus1.setP(sp1);
            }
            sp1.addTerm(p1);
            Equation sp2 = equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P);
            if (sp2.getTerms().isEmpty()) {
                bus2.setP(sp2);
            }
            sp2.addTerm(p2);
            if (deriveA1) {
                if (creationParameters.isForcePhaseControlOffAndAddAngle1Var()) {
                    // use for sensitiviy analysis only: with this equation term, we force the a1 variable to be constant.
                    equationSystem.createEquation(branch.getNum(), EquationType.BRANCH_ALPHA1)
                            .addTerm(EquationTerm.createVariableTerm(branch, VariableType.BRANCH_ALPHA1, variableSet));
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

    private static void createBranches(LfNetwork network, VariableSet variableSet, EquationSystem equationSystem,
                                       DcEquationSystemCreationParameters creationParameters) {
        List<LfBranch> nonImpedantBranches = new ArrayList<>();

        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            PiModel piModel = branch.getPiModel();
            if (FastMath.abs(piModel.getX()) < LOW_IMPEDANCE_THRESHOLD) {
                if (bus1 != null && bus2 != null) {
                    nonImpedantBranches.add(branch);
                }
            } else {
                createImpedantBranch(variableSet, equationSystem, creationParameters, branch, bus1, bus2);
            }
        }

        // create non impedant equations only on minimum spanning forest calculated from non impedant subgraph
        if (!nonImpedantBranches.isEmpty()) {
            Graph<LfBus, LfBranch> nonImpedantSubGraph = new Pseudograph<>(LfBranch.class);
            for (LfBranch branch : nonImpedantBranches) {
                nonImpedantSubGraph.addVertex(branch.getBus1());
                nonImpedantSubGraph.addVertex(branch.getBus2());
                nonImpedantSubGraph.addEdge(branch.getBus1(), branch.getBus2(), branch);
            }

            SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree = new KruskalMinimumSpanningTree<>(nonImpedantSubGraph).getSpanningTree();
            for (LfBranch branch : spanningTree.getEdges()) {
                createNonImpedantBranch(variableSet, equationSystem, branch, branch.getBus1(), branch.getBus2());
            }
        }
    }

    public static EquationSystem create(LfNetwork network, DcEquationSystemCreationParameters creationParameters) {
        return create(network, new VariableSet(), creationParameters);
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet, DcEquationSystemCreationParameters creationParameters) {
        EquationSystem equationSystem = new EquationSystem(network, creationParameters.isIndexTerms());

        createBuses(network, variableSet, equationSystem);
        createBranches(network, variableSet, equationSystem, creationParameters);

        return equationSystem;
    }
}
