/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class JacobianMatrix {

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

    private final Matrix matrix;

    private final List<PartialDerivative> partialDerivatives;

    private LUDecomposition lu;

    JacobianMatrix(Matrix matrix, List<PartialDerivative> partialDerivatives) {
        this.matrix = Objects.requireNonNull(matrix);
        this.partialDerivatives = Objects.requireNonNull(partialDerivatives);
    }

    public static JacobianMatrix create(EquationSystem equationSystem, MatrixFactory matrixFactory) {
        Objects.requireNonNull(equationSystem);
        Objects.requireNonNull(matrixFactory);

        int rowCount = equationSystem.getSortedEquationsToSolve().size();
        int columnCount = equationSystem.getSortedVariablesToFind().size();
        if (rowCount != columnCount) {
            throw new PowsyblException("Expected to have same number of equations (" + rowCount
                    + ") and variables (" + columnCount + ")");
        }

        int estimatedNonZeroValueCount = rowCount * 3;
        Matrix j = matrixFactory.create(rowCount, columnCount, estimatedNonZeroValueCount);
        List<JacobianMatrix.PartialDerivative> partialDerivatives = new ArrayList<>(estimatedNonZeroValueCount);

        for (Map.Entry<Variable, NavigableMap<Equation, List<EquationTerm>>> e : equationSystem.getSortedVariablesToFind().entrySet()) {
            Variable var = e.getKey();
            int column = var.getColumn();
            for (Map.Entry<Equation, List<EquationTerm>> e2 : e.getValue().entrySet()) {
                Equation eq = e2.getKey();
                int row = eq.getRow();
                for (EquationTerm equationTerm : e2.getValue()) {
                    double value = equationTerm.der(var);
                    Matrix.Element element = j.addAndGetElement(row, column, value);
                    partialDerivatives.add(new JacobianMatrix.PartialDerivative(equationTerm, element, var));
                }
            }
        }

        return new JacobianMatrix(j, partialDerivatives);
    }

    public Matrix getMatrix() {
        return matrix;
    }

    public void update() {
        matrix.reset();
        for (PartialDerivative partialDerivative : partialDerivatives) {
            EquationTerm equationTerm = partialDerivative.getEquationTerm();
            Matrix.Element element = partialDerivative.getMatrixElement();
            Variable var = partialDerivative.getVariable();
            double value = equationTerm.der(var);
            element.add(value);
        }
    }

    public LUDecomposition decomposeLU() {
        if (lu == null) {
            lu = matrix.decomposeLU();
        } else {
            lu.update();
        }
        return lu;
    }

    public void cleanLU() {
        if (lu != null) {
            lu.close();
            lu = null;
        }
    }
}
