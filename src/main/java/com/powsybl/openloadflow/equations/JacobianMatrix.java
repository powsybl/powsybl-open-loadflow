/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;

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

    private Matrix matrix;

    private List<PartialDerivative> partialDerivatives;

    private LUDecomposition lu;

    private enum Status {
        VALID,
        MATRIX_INVALID, // structure has changed
        VALUES_INVALID // same structure but values have to be updated
    }

    private Status status = Status.MATRIX_INVALID;

    public JacobianMatrix(EquationSystem equationSystem, MatrixFactory matrixFactory) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        equationSystem.addListener(this);
    }

    @Override
    public void equationListChanged(Equation equation, EquationEventType eventType) {
        switch (eventType) {
            case EQUATION_CREATED:
            case EQUATION_REMOVED:
            case EQUATION_ACTIVATED:
            case EQUATION_DEACTIVATED:
            case EQUATION_UPDATED:
                status = Status.MATRIX_INVALID;
                break;

            default:
                throw new IllegalStateException("Event type not supported: " + eventType);
        }
    }

    @Override
    public void stateUpdated(double[] x) {
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
    }

    private void updateValues() {
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

    public LUDecomposition decomposeLU() {
        if (lu == null) {
            lu = getMatrix().decomposeLU();
        }
        return lu;
    }

    @Override
    public void close() {
        equationSystem.removeListener(this);
        clear();
    }
}
