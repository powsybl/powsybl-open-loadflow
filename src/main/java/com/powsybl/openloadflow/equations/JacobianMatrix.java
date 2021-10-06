/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class JacobianMatrix<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationSystemListener<V, E>, AutoCloseable {

    static final class PartialDerivative<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        private final EquationTerm<V, E> equationTerm;

        private final int elementIndex;

        private final Variable<V> variable;

        PartialDerivative(EquationTerm<V, E> equationTerm, int elementIndex, Variable<V> variable) {
            this.equationTerm = Objects.requireNonNull(equationTerm);
            this.elementIndex = elementIndex;
            this.variable = Objects.requireNonNull(variable);
        }

        EquationTerm<V, E> getEquationTerm() {
            return equationTerm;
        }

        public int getElementIndex() {
            return elementIndex;
        }

        Variable<V> getVariable() {
            return variable;
        }
    }

    private final EquationSystem<V, E> equationSystem;

    private final MatrixFactory matrixFactory;

    private Matrix matrix;

    private List<PartialDerivative<V, E>> partialDerivatives;

    private LUDecomposition lu;

    private enum Status {
        VALID,
        MATRIX_INVALID, // structure has changed
        VALUES_INVALID // same structure but values have to be updated
    }

    private Status status = Status.MATRIX_INVALID;

    public JacobianMatrix(EquationSystem<V, E> equationSystem, MatrixFactory matrixFactory) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        equationSystem.addListener(this);
    }

    @Override
    public void onEquationChange(Equation<V, E> equation, EquationEventType eventType) {
        switch (eventType) {
            case EQUATION_CREATED:
            case EQUATION_REMOVED:
            case EQUATION_ACTIVATED:
            case EQUATION_DEACTIVATED:
                status = Status.MATRIX_INVALID;
                break;

            default:
                throw new IllegalStateException("Event type not supported: " + eventType);
        }
    }

    @Override
    public void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
        switch (eventType) {
            case EQUATION_TERM_ADDED:
            case EQUATION_TERM_ACTIVATED:
            case EQUATION_TERM_DEACTIVATED:
                // FIXME
                // Note for later, it might be possible in the future in case of a term change to not fully rebuild
                // the matrix as the structure has not changed (same equations and variables). But... we have for that
                // to handle the case where the invalidation of an equation term break the graph connectivity. This
                // is typically the case when the equation term is the active power flow of a branch which is also a
                // bridge (so necessary for the connectivity). Normally in that case a bus equation should also have been
                // deactivated and so it should work but for an unknown reason it fails with a KLU singular error (which
                // means most of the time we have created the matrix with a non fully connected network)
                status = Status.MATRIX_INVALID;
                break;

            default:
                throw new IllegalStateException("Event type not supported: " + eventType);
        }
    }

    @Override
    public void onStateUpdate(double[] x) {
        if (status == Status.VALID) {
            status = Status.VALUES_INVALID;
        }
    }

    @Override
    public void onIndexUpdate() {
        // nothing to do
    }

    private void clear() {
        matrix = null;
        partialDerivatives = null;
        if (lu != null) {
            lu.close();
        }
        lu = null;
    }

    private void initMatrix() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        int rowCount = equationSystem.getSortedEquationsToSolve().size();
        int columnCount = equationSystem.getSortedVariablesToFind().size();
        if (rowCount != columnCount) {
            throw new PowsyblException("Expected to have same number of equations (" + rowCount
                    + ") and variables (" + columnCount + ")");
        }

        int estimatedNonZeroValueCount = rowCount * 3;
        matrix = matrixFactory.create(rowCount, columnCount, estimatedNonZeroValueCount);
        partialDerivatives = new ArrayList<>(estimatedNonZeroValueCount);

        for (Map.Entry<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> e : equationSystem.getSortedEquationsToSolve().entrySet()) {
            Equation<V, E> eq = e.getKey();
            int column = eq.getColumn();
            for (Map.Entry<Variable<V>, List<EquationTerm<V, E>>> e2 : e.getValue().entrySet()) {
                Variable<V> var = e2.getKey();
                int row = var.getRow();
                for (EquationTerm<V, E> equationTerm : e2.getValue()) {
                    double value = equationTerm.der(var);
                    int elementIndex = matrix.addAndGetIndex(row, column, value);
                    partialDerivatives.add(new JacobianMatrix.PartialDerivative<>(equationTerm, elementIndex, var));
                }
            }
        }

        System.out.println("Init jac done in " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }

    private void updateValues() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        matrix.reset();
        for (PartialDerivative<V, E> partialDerivative : partialDerivatives) {
            EquationTerm<V, E> equationTerm = partialDerivative.getEquationTerm();
            int elementIndex = partialDerivative.getElementIndex();
            Variable<V> var = partialDerivative.getVariable();
            double value = equationTerm.der(var);
            matrix.addAtIndex(elementIndex, value);
        }

        System.out.println("Update jac done in " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");

        if (lu != null) {
            lu.update();
        }
    }

    public Matrix getMatrix() {
        if (status != Status.VALID) {
            switch (status) {
                case MATRIX_INVALID:
                    clear();
                    initMatrix();
                    break;

                case VALUES_INVALID:
                    updateValues();
                    break;

                default:
                    break;
            }
            status = Status.VALID;
        }
        return matrix;
    }

    private LUDecomposition getLUDecomposition() {
        Matrix matrix = getMatrix();
        if (lu == null) {
            lu = matrix.decomposeLU();
        }
        return lu;
    }

    public void solve(double[] b) {
        getLUDecomposition().solve(b);
    }

    public void solveTransposed(double[] b) {
        getLUDecomposition().solveTransposed(b);
    }

    public void solve(DenseMatrix b) {
        getLUDecomposition().solve(b);
    }

    public void solveTransposed(DenseMatrix b) {
        getLUDecomposition().solveTransposed(b);
    }

    @Override
    public void close() {
        equationSystem.removeListener(this);
        clear();
    }
}
