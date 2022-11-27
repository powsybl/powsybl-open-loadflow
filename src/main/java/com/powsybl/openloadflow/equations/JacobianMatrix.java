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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class JacobianMatrix<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        implements EquationSystemIndexListener<V, E>, StateVectorListener, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(JacobianMatrix.class);

    static final class PartialDerivative<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        private final Equation<V, E> equation;

        private final int elementIndex;

        private final Variable<V> variable;

        PartialDerivative(Equation<V, E> equation, int elementIndex, Variable<V> variable) {
            this.equation = Objects.requireNonNull(equation);
            this.elementIndex = elementIndex;
            this.variable = Objects.requireNonNull(variable);
        }

        Equation<V, E> getEquation() {
            return equation;
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
        VALUES_INVALID, // same structure but values have to be updated
        VALUES_AND_ZEROS_INVALID, // same structure but values have to be updated and non zero values might have changed
        STRUCTURE_INVALID, // structure has changed
    }

    private Status status = Status.STRUCTURE_INVALID;

    public JacobianMatrix(EquationSystem<V, E> equationSystem, MatrixFactory matrixFactory) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        equationSystem.getIndex().addListener(this);
        equationSystem.getStateVector().addListener(this);
    }

    private void updateStatus(Status status) {
        if (status.ordinal() > this.status.ordinal()) {
            this.status = status;
        }
    }

    @Override
    public void onEquationChange(Equation<V, E> equation, ChangeType changeType) {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onVariableChange(Variable<V> variable, ChangeType changeType) {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onEquationUpdated() {
        updateStatus(Status.VALUES_AND_ZEROS_INVALID);
    }

    @Override
    public void onStateUpdate() {
        updateStatus(Status.VALUES_INVALID);
    }

    private void clearLu() {
        if (lu != null) {
            lu.close();
        }
        lu = null;
    }

    private void initMatrix() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        int rowCount = equationSystem.getIndex().getSortedEquationsToSolve().size();
        int columnCount = equationSystem.getIndex().getSortedVariablesToFind().size();
        if (rowCount != columnCount) {
            throw new PowsyblException("Expected to have same number of equations (" + rowCount
                    + ") and variables (" + columnCount + ")");
        }

        int estimatedNonZeroValueCount = rowCount * 3;
        matrix = matrixFactory.create(rowCount, columnCount, estimatedNonZeroValueCount);
        partialDerivatives = new ArrayList<>(estimatedNonZeroValueCount);

        for (Equation<V, E> eq : equationSystem.getIndex().getSortedEquationsToSolve()) {
            int column = eq.getColumn();
            for (Variable<V> v : eq.getVariables()) {
                int row = v.getRow();
                if (row != -1) {
                    int elementIndex = matrix.addAndGetIndex(row, column, eq.der(v));
                    partialDerivatives.add(new JacobianMatrix.PartialDerivative<>(eq, elementIndex, v));
                }
            }
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));

        clearLu();
    }

    private void updateLu(boolean allowIncrementalUpdate) {
        if (lu != null) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            lu.update(allowIncrementalUpdate);

            LOGGER.debug(PERFORMANCE_MARKER, "LU decomposition updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
        }
    }

    private void updateValues(boolean allowIncrementalUpdate) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        matrix.reset();
        for (PartialDerivative<V, E> partialDerivative : partialDerivatives) {
            Equation<V, E> eq = partialDerivative.getEquation();
            int elementIndex = partialDerivative.getElementIndex();
            Variable<V> v = partialDerivative.getVariable();
            double value = eq.der(v);
            matrix.addAtIndex(elementIndex, value);
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Jacobian matrix values updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));

        updateLu(allowIncrementalUpdate);
    }

    public Matrix getMatrix() {
        if (status != Status.VALID) {
            switch (status) {
                case STRUCTURE_INVALID:
                    initMatrix();
                    break;

                case VALUES_INVALID:
                    updateValues(true);
                    break;

                case VALUES_AND_ZEROS_INVALID:
                    updateValues(false);
                    break;

                default:
                    break;
            }
            status = Status.VALID;
        }
        return matrix;
    }

    private LUDecomposition getLUDecomposition() {
        Matrix m = getMatrix();
        if (lu == null) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            lu = m.decomposeLU();

            LOGGER.debug(PERFORMANCE_MARKER, "LU decomposition done in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
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
        equationSystem.getIndex().removeListener(this);
        equationSystem.getStateVector().removeListener(this);
        matrix = null;
        partialDerivatives = null;
        clearLu();
    }
}
