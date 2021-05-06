package com.powsybl.openloadflow.reduction.equations;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import net.jafama.FastMath;

import java.util.ArrayList;
import java.util.List;

import static com.powsybl.openloadflow.network.LfNetwork.LOW_IMPEDANCE_THRESHOLD;

/**
 * @author Jean-Baptiste Heyberger <jean-baptiste.heyberger at rte-france.com>
 */
public final class ReductionEquationSystem {

    private ReductionEquationSystem() {
    }

    //TODO : check where to put the equation term linked to the power injection at the node, depends on the reduction method
    private static void createBuses(LfNetwork network, VariableSet variableSet, EquationSystem equationSystem) {
        for (LfBus bus : network.getBuses()) { //This loop is useful to initialize the bus.getNum() otherwise it does work !!!
            if (bus.isSlack()) {
                System.out.println("Bus = " + bus.getNum() + " is slack");
            }
            //TODO
        }
    }

    //TODO : merge nodes linked with non-impedant branches
    public static void createNonImpedantBranch(VariableSet variableSet, EquationSystem equationSystem,
                                               LfBranch branch, LfBus bus1, LfBus bus2) {

    }

    //Equations are created based on the branches connections
    private static void createImpedantBranch(VariableSet variableSet, EquationSystem equationSystem,
                                             ReductionEquationSystemCreationParameters creationParameters, LfBranch branch,
                                             LfBus bus1, LfBus bus2) {
        if (bus1 != null && bus2 != null) { //TODO: check case when one bus is OK

            //Equation system Y*V = I (expressed in cartesian coordinates x,y)
            //I1x = (g1 + g12)V1x - (b1 + b12)V1y - g12 * V2x + b12 * V2y -> term type = 1
            //I1y = (b1 + b12)V1x + (g1 + g12)V1y - b12 * V2x - g12 - V2y -> term type = 2
            //I2x = -g21 * V1x + b21 * V1y + (g2 + g21)V2x - (b2 + b21)V2y -> term type = 3
            //I2y = -b21 * V1x - g21 * V1y + (b2 + b21)V2x + (g2 + g21)V2y -> term type = 4
            ClosedBranchReductionEquationTerm t1 = ClosedBranchReductionEquationTerm.create(branch, bus1, bus2, variableSet, 1);
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_YR).addTerm(t1);

            ClosedBranchReductionEquationTerm t2 = ClosedBranchReductionEquationTerm.create(branch, bus1, bus2, variableSet, 2);
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_YI).addTerm(t2);

            ClosedBranchReductionEquationTerm t3 = ClosedBranchReductionEquationTerm.create(branch, bus1, bus2, variableSet, 3);
            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_YR).addTerm(t3);

            ClosedBranchReductionEquationTerm t4 = ClosedBranchReductionEquationTerm.create(branch, bus1, bus2, variableSet, 4);
            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_YI).addTerm(t4);

        }
    }

    private static void createBranches(LfNetwork network, VariableSet variableSet, EquationSystem equationSystem,
                                       ReductionEquationSystemCreationParameters creationParameters) {
        List<LfBranch> nonImpedantBranches = new ArrayList<>();

        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            PiModel piModel = branch.getPiModel();
            if (FastMath.abs(piModel.getX()) < LOW_IMPEDANCE_THRESHOLD) {
                if (bus1 != null && bus2 != null) {
                    nonImpedantBranches.add(branch);
                    System.out.println("Warning: Branch = " + branch.getId() + " : Non impedant lines not supported in the current version of the reduction method");
                }
            } else {
                createImpedantBranch(variableSet, equationSystem, creationParameters, branch, bus1, bus2);
            }
        }

        //TODO: check how to handle non impedant lines
        /*if (!nonImpedantBranches.isEmpty()) {
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
        }*/
    }

    public static EquationSystem create(LfNetwork network, ReductionEquationSystemCreationParameters creationParameters) {
        return create(network, new VariableSet(), creationParameters);
    }

    public static EquationSystem create(LfNetwork network, VariableSet variableSet, ReductionEquationSystemCreationParameters creationParameters) {
        EquationSystem equationSystem = new EquationSystem(network, creationParameters.isIndexTerms());

        createBuses(network, variableSet, equationSystem);
        createBranches(network, variableSet, equationSystem, creationParameters);

        return equationSystem;
    }

}
