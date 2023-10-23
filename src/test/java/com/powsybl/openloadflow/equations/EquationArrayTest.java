/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.util.Fortescue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class EquationArrayTest {

    private EquationSystem<AcVariableType, AcEquationType> createEquationSystem(LfNetwork lfNetwork) {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new EquationSystem<>(AcEquationType.class, lfNetwork);
        AcEquationSystemCreationParameters creationParameters = new AcEquationSystemCreationParameters();
        AcNetworkVector networkVector = new AcNetworkVector(lfNetwork, equationSystem, creationParameters);
        AcBranchVector branchVector = networkVector.getBranchVector();
        for (LfBus bus : lfNetwork.getBuses()) {
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P);
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P).orElseThrow()
                    .addTerm(new ClosedBranchSide1ActiveFlowEquationTerm(branchVector, branch.getNum(), bus1.getNum(), bus2.getNum(), equationSystem.getVariableSet(), false, false));
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P).orElseThrow()
                    .addTerm(new ClosedBranchSide2ActiveFlowEquationTerm(branchVector, branch.getNum(), bus1.getNum(), bus2.getNum(), equationSystem.getVariableSet(), false, false));
        }
        networkVector.startListening();
        NewtonRaphson.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        return equationSystem;
    }

    private EquationSystem<AcVariableType, AcEquationType> createEquationSystemUsingArrayEquations(LfNetwork lfNetwork) {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new EquationSystem<>(AcEquationType.class, lfNetwork);
        VariableSet<AcVariableType> variableSet = equationSystem.getVariableSet();
        AcEquationSystemCreationParameters creationParameters = new AcEquationSystemCreationParameters();
        AcNetworkVector networkVector = new AcNetworkVector(lfNetwork, equationSystem, creationParameters);
        AcBranchVector branchVector = networkVector.getBranchVector();
        EquationArray<AcVariableType, AcEquationType> p = equationSystem.createEquationArray(AcEquationType.BUS_TARGET_P);
        EquationTermArray<AcVariableType, AcEquationType> p1Array = new EquationTermArray<>(
                ElementType.BRANCH,
                (branchNums, values) -> ClosedBranchSide1ActiveFlowEquationTerm.eval(branchVector, branchNums, values),
                branchNum -> AbstractClosedBranchAcFlowEquationTerm.createVariable(branchVector, branchNum, variableSet, branchVector.deriveA1[branchNum], branchVector.deriveR1[branchNum], Fortescue.SequenceType.POSITIVE));
        p.addTermArray(p1Array);
        EquationTermArray<AcVariableType, AcEquationType> p2Array = new EquationTermArray<>(
                ElementType.BRANCH,
                (branchNum, values) -> ClosedBranchSide2ActiveFlowEquationTerm.eval(branchVector, branchNum, values),
                branchNum -> AbstractClosedBranchAcFlowEquationTerm.createVariable(branchVector, branchNum, variableSet, branchVector.deriveA1[branchNum], branchVector.deriveR1[branchNum], Fortescue.SequenceType.POSITIVE));
        p.addTermArray(p2Array);
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            p1Array.addTerm(bus1.getNum(), branch.getNum());
            p2Array.addTerm(bus2.getNum(), branch.getNum());
        }
        networkVector.startListening();
        NewtonRaphson.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        return equationSystem;
    }

    private static DenseMatrix calculateDer(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        int rowCount = equationSystem.getIndex().getSortedVariablesToFind().size();
        int columnCount = equationSystem.getIndex().getSortedEquationsToSolve().size();
        DenseMatrix m = new DenseMatrix(rowCount, columnCount);
        for (var eq : equationSystem.getIndex().getSortedEquationsToSolve()) {
            int column = eq.getColumn();
            eq.der((variable, value, matrixElementIndex) -> {
                int row = variable.getRow();
                m.set(row, column, value);
                return matrixElementIndex;
            });
        }
        for (var eq : equationSystem.getEquationArrays()) {
            eq.der((column, variable, value, matrixElementIndex) -> {
                int row = variable.getRow();
                m.set(row, column, value);
                return matrixElementIndex;
            });
        }
        return m;
    }

    @Test
    void test() {
        Network network = EurostagTutorialExample1Factory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = createEquationSystem(lfNetwork);
        double[] values = new double[lfNetwork.getBuses().size()];
        for (var equation : equationSystem.getIndex().getSortedEquationsToSolve()) {
            values[equation.getColumn()] += equation.eval();
        }

        EquationSystem<AcVariableType, AcEquationType> equationSystem2 = createEquationSystemUsingArrayEquations(lfNetwork);
        double[] values2 = new double[lfNetwork.getBuses().size()];
        for (var equationArray : equationSystem2.getEquationArrays()) {
            equationArray.eval(values2);
        }

        assertArrayEquals(values, values2);
        DenseMatrix derValues = calculateDer(equationSystem);
        DenseMatrix derValues2 = calculateDer(equationSystem2);
        assertEquals(derValues, derValues2);
    }
}
