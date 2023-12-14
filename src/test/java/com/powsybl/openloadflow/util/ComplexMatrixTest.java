package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.DenseMatrix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplexMatrixTest {

    @Test
    void complexMatrixTest() {
        DenseMatrix fortescueMatrix = Fortescue.createMatrix();
        ComplexMatrix complexFortescueMatrix = Fortescue.createComplexMatrix(false);
        System.out.println("Matrix Fortescue = ");
        fortescueMatrix.print(System.out);

        System.out.println("Matrix Complex Fortescue = ");
        DenseMatrix computedMatrix = complexFortescueMatrix.toRealCartesianMatrix();

        for (int i = 0; i < fortescueMatrix.getRowCount(); i++) {
            for (int j = 0; j < fortescueMatrix.getColumnCount(); j++) {
                assertEquals(computedMatrix.get(i, j), fortescueMatrix.get(i, j));
            }
        }
    }

    @Test
    void realToComplexMatrixTest() {
        ComplexMatrix complexFortescueMatrix = Fortescue.createComplexMatrix(false);
        DenseMatrix realFortescueMatrix = complexFortescueMatrix.toRealCartesianMatrix();

        ComplexMatrix computedComplexFortescueMatrix = ComplexMatrix.fromRealCartesian(realFortescueMatrix);
        DenseMatrix computedMatrix = computedComplexFortescueMatrix.toRealCartesianMatrix();

        for (int i = 0; i < realFortescueMatrix.getRowCount(); i++) {
            for (int j = 0; j < realFortescueMatrix.getColumnCount(); j++) {
                assertEquals(computedMatrix.get(i, j), realFortescueMatrix.get(i, j));
            }
        }
    }
}
