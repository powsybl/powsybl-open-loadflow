/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.util.Profiler;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class JacobianMatrix implements EquationSystemListener, AutoCloseable {

    static final class PartialDerivative {

        private final EquationTerm equationTerm;

        private final Matrix.Element matrixElement;

        private final Variable variable;

        PartialDerivative(EquationTerm equationTerm, Matrix.Element matrixElement, Variable variable) {
            this.equationTerm = Objects.requireNonNull(equationTerm);
            this.matrixElement = Objects.requireNonNull(matrixElement);
            this.variable = Objects.requireNonNull(variable);
        }

        EquationTerm getEquationTerm() {
            return equationTerm;
        }

        Matrix.Element getMatrixElement() {
            return matrixElement;
        }

        Variable getVariable() {
            return variable;
        }
    }

    private final EquationSystem equationSystem;

    private final MatrixFactory matrixFactory;

    private final Profiler profiler;

    private Matrix matrix;

    private List<PartialDerivative> partialDerivatives;

    private LUDecomposition lu;

    private enum Status {
        VALID,
        MATRIX_INVALID, // structure has changed
        VALUES_INVALID // same structure but values have to be updated
    }

    private Status status = Status.MATRIX_INVALID;

    public JacobianMatrix(EquationSystem equationSystem, MatrixFactory matrixFactory, Profiler profiler) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.profiler = Objects.requireNonNull(profiler);
        equationSystem.addListener(this);
    }

    @Override
    public void onEquationChange(Equation equation, EquationEventType eventType) {
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
    public void onEquationTermChange(EquationTerm term, EquationTermEventType eventType) {
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

    private void clear() {
        matrix = null;
        partialDerivatives = null;
        if (lu != null) {
            lu.close();
        }
        lu = null;
    }

    private void initMatrix() {
        profiler.beforeTask("JacobianCreation");

        int rowCount = equationSystem.getSortedEquationsToSolve().size();
        int columnCount = equationSystem.getSortedVariablesToFind().size();
        if (rowCount != columnCount) {
            throw new PowsyblException("Expected to have same number of equations (" + rowCount
                    + ") and variables (" + columnCount + ")");
        }

        int estimatedNonZeroValueCount = rowCount * 3;
        matrix = matrixFactory.create(rowCount, columnCount, estimatedNonZeroValueCount);
        partialDerivatives = new ArrayList<>(estimatedNonZeroValueCount);

        for (Map.Entry<Equation, NavigableMap<Variable, List<EquationTerm>>> e : equationSystem.getSortedEquationsToSolve().entrySet()) {
            Equation eq = e.getKey();
            int column = eq.getColumn();
            for (Map.Entry<Variable, List<EquationTerm>> e2 : e.getValue().entrySet()) {
                Variable var = e2.getKey();
                int row = var.getRow();
                for (EquationTerm equationTerm : e2.getValue()) {
                    double value = equationTerm.der(var);
                    Matrix.Element element = matrix.addAndGetElement(row, column, value);
                    partialDerivatives.add(new JacobianMatrix.PartialDerivative(equationTerm, element, var));
                }
            }
        }

        profiler.afterTask("JacobianCreation");
    }

    private void updateValues() {
        profiler.beforeTask("JacobianUpdate");

        matrix.reset();
        for (PartialDerivative partialDerivative : partialDerivatives) {
            EquationTerm equationTerm = partialDerivative.getEquationTerm();
            Matrix.Element element = partialDerivative.getMatrixElement();
            Variable var = partialDerivative.getVariable();
            double value = equationTerm.der(var);
            element.add(value);
        }
        if (lu != null) {
            lu.update();
        }

        profiler.afterTask("JacobianUpdate");
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
            profiler.beforeTask("LuDecomposition");

            lu = matrix.decomposeLU();

            profiler.afterTask("LuDecomposition");
        }
        return lu;
    }

    public void solve(double[] b) {
        profiler.beforeTask("EquationSystemSolve");

        getLUDecomposition().solve(b);

        profiler.afterTask("EquationSystemSolve");
    }

    public void solveTransposed(double[] b) {
        profiler.beforeTask("EquationSystemSolve");

        getLUDecomposition().solveTransposed(b);

        profiler.afterTask("EquationSystemSolve");
    }

    public void solve(DenseMatrix b) {
        profiler.beforeTask("EquationSystemSolve");

        getLUDecomposition().solve(b);

        profiler.afterTask("EquationSystemSolve");
    }

    public void solveTransposed(DenseMatrix b) {
        profiler.beforeTask("EquationSystemSolve");

        getLUDecomposition().solveTransposed(b);

        profiler.afterTask("EquationSystemSolve");
    }

    @Override
    public void close() {
        equationSystem.removeListener(this);
        clear();
    }
}
