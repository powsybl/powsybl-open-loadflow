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
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.equations.vector.*;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class EquationArrayTest {

    private EquationSystem<AcVariableType, AcEquationType> createEquationSystem(LfNetwork lfNetwork) {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new EquationSystem<>(AcEquationType.class, lfNetwork);
        for (LfBus bus : lfNetwork.getBuses()) {
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P);
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P).orElseThrow()
                    .addTerm(new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), false, false));
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P).orElseThrow()
                    .addTerm(new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), false, false));
        }
        AcSolverUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        return equationSystem;
    }

    private EquationSystem<AcVariableType, AcEquationType> createEquationSystemUsingArrayEquations(LfNetwork lfNetwork) {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new EquationSystem<>(AcEquationType.class, lfNetwork);
        VariableSet<AcVariableType> variableSet = equationSystem.getVariableSet();
        AcEquationSystemCreationParameters creationParameters = new AcEquationSystemCreationParameters();
        AcNetworkVector networkVector = new AcNetworkVector(lfNetwork, equationSystem, creationParameters);
        AcBranchVector branchVector = networkVector.getBranchVector();
        AcBusVector busVector = networkVector.getBusVector();
        EquationArray<AcVariableType, AcEquationType> p = equationSystem.createEquationArray(AcEquationType.BUS_TARGET_P);
        EquationTermArray<AcVariableType, AcEquationType> p1Array = new EquationTermArray<>(ElementType.BRANCH,
                                                                                            new ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator(branchVector, busVector, variableSet));
        p.addTermArray(p1Array);
        EquationTermArray<AcVariableType, AcEquationType> p2Array = new EquationTermArray<>(ElementType.BRANCH,
                                                                                            new ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator(branchVector, busVector, variableSet));
        p.addTermArray(p2Array);
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            p1Array.addTerm(bus1, branch);
            p2Array.addTerm(bus2, branch);
        }
        networkVector.startListening();
        AcSolverUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        p1Array.compress();
        p2Array.compress();
        return equationSystem;
    }

    private static DenseMatrix calculateDer(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        int rowCount = equationSystem.getIndex().getRowCount();
        int columnCount = equationSystem.getIndex().getColumnCount();
        DenseMatrix m = new DenseMatrix(rowCount, columnCount);
        for (var eq : equationSystem.getIndex().getSortedSingleEquationsToSolve()) {
            int column = eq.getColumn();
            eq.der((variable, value, matrixElementIndex) -> {
                int row = variable.getRow();
                m.set(row, column, value);
                return matrixElementIndex;
            });
        }
        int[] valueIndex = new int[1];
        for (var eq : equationSystem.getEquationArrays()) {
            eq.der((column, row, value, matrixElementIndex) -> {
                m.set(row, column, value);
                return valueIndex[0]++;
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
        for (var equation : equationSystem.getIndex().getSortedSingleEquationsToSolve()) {
            values[equation.getColumn()] += equation.eval();
        }

        EquationSystem<AcVariableType, AcEquationType> equationSystem2 = createEquationSystemUsingArrayEquations(lfNetwork);
        double[] values2 = new double[lfNetwork.getBuses().size()];
        for (var equationArray : equationSystem2.getEquationArrays()) {
            equationArray.eval(values2);
        }

        // Since there is only one equation type, both equation system sort the values by element num.
        // The values arrays are then expected to be equal.
        assertArrayEquals(values, values2);
        DenseMatrix derValues = calculateDer(equationSystem);
        DenseMatrix derValues2 = calculateDer(equationSystem2);
        assertEquals(derValues, derValues2);

        try (EquationVector<AcVariableType, AcEquationType> equationVector = new EquationVector<>(equationSystem);
             EquationVector<AcVariableType, AcEquationType> equationVector2 = new EquationVector<>(equationSystem2)) {
            assertArrayEquals(equationVector.getArray(), equationVector2.getArray());
        }

        assertEquals(equationSystem.getEquation(1, AcEquationType.BUS_TARGET_P).orElseThrow().eval(),
                     equationSystem2.getEquationArray(AcEquationType.BUS_TARGET_P).orElseThrow().getElement(1).eval());
    }

    /**
     * With SparseMatrixFactory, when calculating the Jacobian Matrix of an equation system with equation arrays,
     * all the terms are added in some order depending on the EquationDerivativeVector order (rows and columns do
     * not depend on it, but the order of stacking the values in the sparse structure is impacted). This test ensures
     * the order is always the same. (If not, very small non-reproducible numerical errors (10^-12) can occur during
     * Sparse LU decomposition)
     */
    @Test
    void testSparseReproducibility() {
        Network network = FourBusNetworkFactory.createWithTwoScs();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);

        try (var acContext = new AcLoadFlowContext(lfNetwork, new AcLoadFlowParameters().setMatrixFactory(new SparseMatrixFactory()))) {
            acContext.getJacobianMatrix().initDer();
            int[] expectedRowIndices = {0, 1, 6, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 6, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            int[] rowIndices = ((SparseMatrix) acContext.getJacobianMatrix().matrix).getRowIndices();
            int[] expectedColumnStarts = {0, 1, 2, 3, 9, 17, 23, 29, 37};
            int[] columnStarts = ((SparseMatrix) acContext.getJacobianMatrix().matrix).getColumnStart();
            assertArrayEquals(expectedRowIndices, rowIndices);
            assertArrayEquals(expectedColumnStarts, columnStarts);
        }
    }
}
