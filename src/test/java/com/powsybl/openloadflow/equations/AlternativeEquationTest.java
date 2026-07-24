/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.equations.EquationSystemIndexListener.ChangeType;
import com.powsybl.openloadflow.equations.EquationSystemIndexTest.TestEquationType;
import com.powsybl.openloadflow.equations.EquationSystemIndexTest.TestVariableType;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
class AlternativeEquationTest {

    private EquationSystem<TestVariableType, TestEquationType> equationSystem;

    private LfBus bus0;

    private Variable<TestVariableType> a;

    private Variable<TestVariableType> b;

    private Variable<TestVariableType> c;

    @BeforeEach
    void setUp() {
        LfNetwork network = Mockito.mock(LfNetwork.class);
        equationSystem = new EquationSystem<>(TestEquationType.class, network);
        bus0 = Mockito.mock(LfBus.class);
        Mockito.when(bus0.getNum()).thenReturn(0);
        Mockito.when(bus0.getType()).thenReturn(ElementType.BUS);
        a = equationSystem.getVariableSet().getVariable(0, TestVariableType.A);
        b = equationSystem.getVariableSet().getVariable(0, TestVariableType.B);
        c = equationSystem.getVariableSet().getVariable(0, TestVariableType.C);
    }

    private static final class StructuralChangeCounter implements EquationSystemIndexListener<TestVariableType, TestEquationType> {

        private int structuralChangeCount = 0;

        private int alternativeChangeCount = 0;

        @Override
        public void onVariableChange(Variable<TestVariableType> variable, ChangeType changeType) {
            structuralChangeCount++;
        }

        @Override
        public void onEquationChange(SingleEquation<TestVariableType, TestEquationType> equation, ChangeType changeType) {
            structuralChangeCount++;
        }

        @Override
        public void onEquationTermChange(SingleEquationTerm<TestVariableType, TestEquationType> term) {
            // value level change, nothing to do
        }

        @Override
        public void onEquationAlternativeChange(SingleEquation<TestVariableType, TestEquationType> equation) {
            alternativeChangeCount++;
        }

        @Override
        public void onEquationArrayChange(EquationArray<TestVariableType, TestEquationType> equationArray, ChangeType changeType) {
            structuralChangeCount++;
        }

        @Override
        public void onEquationTermArrayChange(EquationTermArray<TestVariableType, TestEquationType> equationTermArray, int termNum, ChangeType changeType) {
            structuralChangeCount++;
        }

        @Override
        public void onEquationIndexOrderChanged() {
            structuralChangeCount++;
        }
    }

    @Test
    void testIndexStructureStability() {
        // alternative equation on element 0, primary type X:
        //   alternative X: a + c
        //   alternative Y: a
        // plain equation on element 1: x_1 = b
        AlternativeEquation<TestVariableType, TestEquationType> slot = equationSystem.createAlternativeEquation(bus0, TestEquationType.X);
        int altX = slot.addAlternative(TestEquationType.X, List.of(a.createTerm(), c.createTerm()));
        int altY = slot.addAlternative(TestEquationType.Y, List.of(a.<TestEquationType>createTerm()));
        var eq2 = equationSystem.createEquation(1, TestEquationType.X)
                .addTerm(b.createTerm());

        // variables of ALL alternatives are registered, including 'c' which is only used by the inactive-able
        // alternative X
        assertEquals(List.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind());
        assertEquals(2, equationSystem.getIndex().getColumnCount());
        assertEquals(0, slot.getColumn());
        assertEquals(1, eq2.getColumn());
        assertEquals(2, slot.getAlternativeCount());
        assertEquals(altX, slot.getActiveAlternativeNum());
        assertEquals(TestEquationType.X, slot.getActiveType());
        assertEquals(0, slot.getActiveElementNum());

        StructuralChangeCounter counter = new StructuralChangeCounter();
        equationSystem.getIndex().addListener(counter);

        // switching the active alternative is NOT a structural change
        slot.setActiveAlternative(altY);
        assertEquals(0, counter.structuralChangeCount);
        assertEquals(1, counter.alternativeChangeCount);
        assertEquals(TestEquationType.Y, slot.getActiveType());
        // 'c' stays registered even if only referenced by the now inactive alternative
        assertEquals(List.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind());
        assertEquals(2, equationSystem.getIndex().getColumnCount());
        assertEquals(0, slot.getColumn());

        // switching back
        slot.setActiveAlternative(altX);
        assertEquals(0, counter.structuralChangeCount);
        assertEquals(2, counter.alternativeChangeCount);
        assertEquals(List.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind());

        // switching to the already active alternative is a no-op
        slot.setActiveAlternative(altX);
        assertEquals(2, counter.alternativeChangeCount);

        // switching by type
        slot.setActiveAlternative(TestEquationType.Y);
        assertEquals(TestEquationType.Y, slot.getActiveType());
        assertEquals(0, counter.structuralChangeCount);

        // evaluation only takes the active alternative into account
        equationSystem.getStateVector().set(new double[] {1, 2, 3}); // a, b, c
        assertEquals(1, slot.eval()); // alternative Y: a
        slot.setActiveAlternative(altX);
        assertEquals(4, slot.eval()); // alternative X: a + c

        // error cases
        assertThrows(IndexOutOfBoundsException.class, () -> slot.setActiveAlternative(2));
        PowsyblException e = assertThrows(PowsyblException.class, () -> equationSystem.createAlternativeEquation(bus0, TestEquationType.X));
        assertEquals("An equation already exists for element 0 and type X", e.getMessage());
    }

    @Test
    void testJacobianStructureReuseDense() {
        // alternative equation (column 0):
        //   alternative X: a + 3b
        //   alternative Y: a
        // plain equation (column 1): x_1 = b
        AlternativeEquation<TestVariableType, TestEquationType> slot = equationSystem.createAlternativeEquation(bus0, TestEquationType.X);
        slot.addAlternative(TestEquationType.X, List.of(a.createTerm(), b.<TestEquationType>createTerm().multiply(3)));
        int altY = slot.addAlternative(TestEquationType.Y, List.of(a.<TestEquationType>createTerm()));
        equationSystem.createEquation(1, TestEquationType.X)
                .addTerm(b.createTerm());
        equationSystem.getStateVector().set(new double[2]);

        try (JacobianMatrix<TestVariableType, TestEquationType> j = new JacobianMatrix<>(equationSystem, new DenseMatrixFactory())) {
            DenseMatrix m1 = (DenseMatrix) j.getMatrix();
            assertEquals(1, m1.get(0, 0)); // d(slot)/da
            assertEquals(3, m1.get(1, 0)); // d(slot)/db
            assertEquals(1, m1.get(1, 1)); // d(eq2)/db

            slot.setActiveAlternative(altY);
            Matrix m2 = j.getMatrix();
            // the matrix has NOT been rebuilt, only its values have been updated
            assertSame(m1, m2);
            assertEquals(1, m1.get(0, 0)); // d(slot)/da
            assertEquals(0, m1.get(1, 0)); // explicit zero for the inactive alternative
            assertEquals(1, m1.get(1, 1)); // d(eq2)/db
        }
    }

    @Test
    void testJacobianStructureReuseSparseWithLuUpdate() {
        AlternativeEquation<TestVariableType, TestEquationType> slot = equationSystem.createAlternativeEquation(bus0, TestEquationType.X);
        int altX = slot.addAlternative(TestEquationType.X, List.of(a.createTerm(), b.<TestEquationType>createTerm().multiply(3)));
        int altY = slot.addAlternative(TestEquationType.Y, List.of(a.<TestEquationType>createTerm()));
        equationSystem.createEquation(1, TestEquationType.X)
                .addTerm(b.createTerm());
        equationSystem.getStateVector().set(new double[2]);

        try (JacobianMatrix<TestVariableType, TestEquationType> j = new JacobianMatrix<>(equationSystem, new SparseMatrixFactory())) {
            Matrix m1 = j.getMatrix();

            // matrix is [[1, 0], [3, 1]], solve and factorize a first time
            double[] rhs = {4, 1};
            j.solve(rhs);
            assertArrayEquals(new double[] {4, -11}, rhs);

            // switch alternative: the sparse matrix and its symbolic factorization are reused, the entry of the
            // inactive alternative becomes an explicit zero and the LU decomposition is numerically updated
            slot.setActiveAlternative(altY);
            assertSame(m1, j.getMatrix());

            // matrix is now [[1, 0], [0, 1]]
            double[] rhs2 = {4, 1};
            j.solve(rhs2);
            assertArrayEquals(new double[] {4, 1}, rhs2);

            // and back
            slot.setActiveAlternative(altX);
            assertSame(m1, j.getMatrix());
            double[] rhs3 = {4, 1};
            j.solve(rhs3);
            assertArrayEquals(new double[] {4, -11}, rhs3);
        }
    }

    @Test
    void testIncrementalLuUpdateOnAlternativeSwitch() {
        // with incremental updates allowed on zero changes, an alternative switch tries to reuse the previous
        // pivoting; when a pivot becomes zero (here the diagonal entry of the slot column flips from the 'a' variable
        // to the 'b' variable), the LU update falls back to a full numerical refactorization and stays correct
        AlternativeEquation<TestVariableType, TestEquationType> slot = equationSystem.createAlternativeEquation(bus0, TestEquationType.X);
        int altA = slot.addAlternative(TestEquationType.X, List.of(a.<TestEquationType>createTerm()));
        int altB = slot.addAlternative(TestEquationType.Y, List.of(b.<TestEquationType>createTerm()));
        equationSystem.createEquation(1, TestEquationType.X)
                .addTerm(a.createTerm())
                .addTerm(b.createTerm());
        equationSystem.getStateVector().set(new double[2]);

        try (JacobianMatrix<TestVariableType, TestEquationType> j = new JacobianMatrix<>(equationSystem, new SparseMatrixFactory())
                .setAllowIncrementalUpdateOnZeroChanges(true)) {
            Matrix m1 = j.getMatrix();
            // matrix is [[1, 1], [0, 1]]
            double[] rhs = {3, 2};
            j.solve(rhs);
            assertArrayEquals(new double[] {1, 2}, rhs);

            // matrix becomes [[0, 1], [1, 1]]: zero diagonal pivot, incremental update must fall back
            slot.setActiveAlternative(altB);
            assertSame(m1, j.getMatrix());
            double[] rhs2 = {3, 2};
            j.solve(rhs2);
            assertArrayEquals(new double[] {-1, 3}, rhs2);

            // and back: incremental update succeeds with non zero pivots
            slot.setActiveAlternative(altA);
            assertSame(m1, j.getMatrix());
            double[] rhs3 = {3, 2};
            j.solve(rhs3);
            assertArrayEquals(new double[] {1, 2}, rhs3);
        }
    }

    @Test
    void testPlainEquationActivationStillRebuildsMatrix() {
        // contrast case: toggling a plain equation IS a structural change and rebuilds the matrix
        var eq1 = equationSystem.createEquation(0, TestEquationType.X)
                .addTerm(a.createTerm());
        var eq2 = equationSystem.createEquation(1, TestEquationType.X)
                .addTerm(b.createTerm());
        equationSystem.getStateVector().set(new double[2]);

        try (JacobianMatrix<TestVariableType, TestEquationType> j = new JacobianMatrix<>(equationSystem, new DenseMatrixFactory())) {
            Matrix m1 = j.getMatrix();
            assertEquals(2, m1.getRowCount());

            eq2.setActive(false);
            Matrix m2 = j.getMatrix();
            assertNotSame(m1, m2);
            assertEquals(1, m2.getRowCount());

            eq1.setActive(true); // no-op, already active
            assertSame(m2, j.getMatrix());
        }
    }

    @Test
    void testTargetVectorFollowsActiveAlternative() {
        AlternativeEquation<TestVariableType, TestEquationType> slot = equationSystem.createAlternativeEquation(bus0, TestEquationType.X);
        int altX = slot.addAlternative(TestEquationType.X, List.of(a.createTerm(), c.createTerm()));
        int altY = slot.addAlternative(TestEquationType.Y, List.of(a.<TestEquationType>createTerm()));
        equationSystem.createEquation(1, TestEquationType.X)
                .addTerm(b.createTerm());

        LfNetwork network = Mockito.mock(LfNetwork.class);
        TargetVector.Initializer<TestVariableType, TestEquationType> initializer = new TargetVector.Initializer<>() {
            @Override
            public void initialize(SingleEquation<TestVariableType, TestEquationType> equation, LfNetwork network, double[] targets) {
                // target depends on the type of the active alternative, like AcTargetVector does
                targets[equation.getColumn()] = equation.getActiveType() == TestEquationType.X ? 10 : 20;
            }

            @Override
            public void initialize(EquationArray<TestVariableType, TestEquationType> equationArray, LfNetwork network, double[] targets) {
                // no equation array
            }
        };
        try (TargetVector<TestVariableType, TestEquationType> targetVector = new TargetVector<>(network, equationSystem, initializer)) {
            double[] targets1 = targetVector.getArray();
            assertArrayEquals(new double[] {10, 10}, targets1);

            slot.setActiveAlternative(altY);
            double[] targets2 = targetVector.getArray();
            // the vector has not been rebuilt, only its values have been updated
            assertSame(targets1, targets2);
            assertArrayEquals(new double[] {20, 10}, targets2);

            slot.setActiveAlternative(altX);
            assertArrayEquals(new double[] {10, 10}, targetVector.getArray());
        }
    }

    @Test
    void testDefaultAlternativeRouting() {
        // terms added with plain addTerm() after setDefaultAlternative land in that alternative, like the reactive
        // power balance terms added by branch/shunt/load creation in the AC equation system creator
        AlternativeEquation<TestVariableType, TestEquationType> slot = equationSystem.createAlternativeEquation(bus0, TestEquationType.X);
        int altX = slot.addAlternative(TestEquationType.X, List.of(a.<TestEquationType>createTerm()));
        int altY = slot.addAlternative(TestEquationType.Y, List.of(c.<TestEquationType>createTerm()));
        slot.setDefaultAlternative(altX);
        slot.addTerm(b.createTerm()); // routed to alternative X

        assertEquals(2, slot.getAlternative(altX).getTerms().size());
        assertEquals(List.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind()); // assign variable rows
        equationSystem.getStateVector().set(new double[] {1, 2, 3}); // a, b, c
        assertEquals(3, slot.eval()); // alternative X: a + b
        slot.setActiveAlternative(altY);
        assertEquals(3, slot.eval()); // alternative Y: c
    }

    @Test
    void testAlternativeEvaluableIndependentOfActiveAlternative() {
        // an alternative stays evaluable whatever the active alternative is (like the reactive power balance of a
        // bus in PV mode), with the usual term activity semantic (terms of disabled elements are skipped)
        AlternativeEquation<TestVariableType, TestEquationType> slot = equationSystem.createAlternativeEquation(bus0, TestEquationType.X);
        SingleEquationTerm<TestVariableType, TestEquationType> aTerm = a.createTerm();
        SingleEquationTerm<TestVariableType, TestEquationType> cTerm = c.createTerm();
        int altX = slot.addAlternative(TestEquationType.X, List.of(aTerm, cTerm));
        int altY = slot.addAlternative(TestEquationType.Y, List.of(b.<TestEquationType>createTerm()));
        assertEquals(List.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind()); // assign variable rows
        equationSystem.getStateVector().set(new double[] {1, 2, 3}); // a, b, c

        slot.setActiveAlternative(altY);
        assertEquals(2, slot.eval()); // active alternative Y: b
        assertEquals(4, slot.getAlternative(altX).eval()); // alternative X still evaluable: a + c

        // term deactivation (element disabled) is taken into account by both the equation and the alternative
        cTerm.setActive(false);
        assertEquals(1, slot.getAlternative(altX).eval()); // a only
        slot.setActiveAlternative(altX);
        assertEquals(1, slot.eval());
        // and the variable of the deactivated term stays registered: no structural change
        assertEquals(List.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind());
    }

    @Test
    void testWriteShowsInactiveAlternativeTerms() {
        AlternativeEquation<TestVariableType, TestEquationType> slot = equationSystem.createAlternativeEquation(bus0, TestEquationType.X);
        slot.addAlternative(TestEquationType.X, List.of(a.createTerm()));
        slot.addAlternative(TestEquationType.Y, List.of(c.<TestEquationType>createTerm()));
        assertEquals("x_0 = a_0" + System.lineSeparator(), equationSystem.writeToString());
        slot.setActiveAlternative(TestEquationType.Y);
        assertEquals("x_0 = c_0" + System.lineSeparator(), equationSystem.writeToString());
    }
}
