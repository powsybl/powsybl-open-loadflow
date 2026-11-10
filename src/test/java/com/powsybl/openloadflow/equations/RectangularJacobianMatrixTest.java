/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
class RectangularJacobianMatrixTest {

    private LfNetwork lfNetwork;

    @BeforeEach
    void setUp() {
        Network network = IeeeCdfNetworkFactory.create14();
        lfNetwork = Networks.load(network, new FirstSlackBusSelector()).get(0);
    }

    /**
     * Two equations per branch, one for the active flow at each side, so the system holds more
     * equations than there are voltages and angles to find.
     */
    private EquationSystem<AcVariableType, AcEquationType> createOverDeterminedSystem() {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new EquationSystem<>(AcEquationType.class, lfNetwork);
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 == null || bus2 == null) {
                continue;
            }
            equationSystem.createEquation(branch, AcEquationType.BRANCH_TARGET_P1)
                    .addTerm(new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2,
                            equationSystem.getVariableSet(), false, false));
            equationSystem.createEquation(branch, AcEquationType.BRANCH_TARGET_P2)
                    .addTerm(new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2,
                            equationSystem.getVariableSet(), false, false));
        }
        AcSolverUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        return equationSystem;
    }

    private static int countNonZero(DenseMatrix matrix) {
        int count = 0;
        for (int row = 0; row < matrix.getRowCount(); row++) {
            for (int column = 0; column < matrix.getColumnCount(); column++) {
                if (matrix.get(row, column) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void testOverDeterminedSystem() {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = createOverDeterminedSystem();

        try (RectangularJacobianMatrix<AcVariableType, AcEquationType> j =
                     new RectangularJacobianMatrix<>(equationSystem, new DenseMatrixFactory())) {
            int variableCount = equationSystem.getIndex().getRowCount();
            int equationCount = equationSystem.getIndex().getColumnCount();
            assertTrue(equationCount > variableCount, "the system is expected to be over determined");

            DenseMatrix matrix = j.getMatrix().toDense();
            assertEquals(variableCount, matrix.getRowCount());
            assertEquals(equationCount, matrix.getColumnCount());
            assertTrue(countNonZero(matrix) > 0, "the state vector is expected to give non zero derivatives");
        }
    }

    @Test
    void testSameDerivativesAsSquareJacobian() {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new AcEquationSystemCreator(lfNetwork).create();
        AcSolverUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());

        DenseMatrix square;
        DenseMatrix rectangular;
        try (JacobianMatrix<AcVariableType, AcEquationType> j =
                     new JacobianMatrix<>(equationSystem, new DenseMatrixFactory())) {
            square = j.getMatrix().toDense();
        }
        try (RectangularJacobianMatrix<AcVariableType, AcEquationType> j =
                     new RectangularJacobianMatrix<>(equationSystem, new DenseMatrixFactory())) {
            rectangular = j.getMatrix().toDense();
        }

        assertEquals(square.getRowCount(), square.getColumnCount());
        assertTrue(countNonZero(square) > 0);
        assertMatrixEquals(square, rectangular);
    }

    @Test
    void testSquareJacobianRejectsOverDeterminedSystem() {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = createOverDeterminedSystem();

        try (JacobianMatrix<AcVariableType, AcEquationType> j =
                     new JacobianMatrix<>(equationSystem, new DenseMatrixFactory())) {
            PowsyblException e = assertThrows(PowsyblException.class, j::getMatrix);
            assertTrue(e.getMessage().contains("same number of equations"), e.getMessage());
        }
    }

    @Test
    void testSparseAndDenseGiveSameDerivatives() {
        DenseMatrix dense;
        DenseMatrix sparse;
        try (RectangularJacobianMatrix<AcVariableType, AcEquationType> j =
                     new RectangularJacobianMatrix<>(createOverDeterminedSystem(), new DenseMatrixFactory())) {
            dense = j.getMatrix().toDense();
        }
        try (RectangularJacobianMatrix<AcVariableType, AcEquationType> j =
                     new RectangularJacobianMatrix<>(createOverDeterminedSystem(), new SparseMatrixFactory())) {
            sparse = j.getMatrix().toDense();
        }

        assertMatrixEquals(dense, sparse);
    }

    private static void assertMatrixEquals(DenseMatrix expected, DenseMatrix actual) {
        assertEquals(expected.getRowCount(), actual.getRowCount());
        assertEquals(expected.getColumnCount(), actual.getColumnCount());
        for (int row = 0; row < expected.getRowCount(); row++) {
            for (int column = 0; column < expected.getColumnCount(); column++) {
                assertEquals(expected.get(row, column), actual.get(row, column), 0);
            }
        }
    }
}
