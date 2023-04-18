package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixException;
import net.jafama.FastMath;
import org.apache.commons.math3.complex.Complex;

public class ComplexMatrix {

    private Complex[][] matrix;

    public ComplexMatrix(int nbRow, int nbCol) {
        Complex[][] complexMatrix = new Complex[nbRow][nbCol];
        for (int i = 0; i < complexMatrix.length; i++) {
            for (int j = 0; j < complexMatrix[i].length; j++) {
                complexMatrix[i][j] = new Complex(0, 0);
            }
        }
        this.matrix = complexMatrix;
    }

    // we suppose that the input indices start at 1
    public void set(int i, int j, Complex complex) {
        matrix[i - 1][j - 1] = complex;
    }

    public int getNbRow() {
        return matrix.length;
    }

    public int getNbCol() {
        return matrix[0].length;
    }

    public Complex getTerm(int i, int j) {
        return matrix[i - 1][j - 1];
    }

    public static ComplexMatrix complexMatrixIdentity(int nbRow) {
        ComplexMatrix complexMatrix = new ComplexMatrix(nbRow, nbRow);
        for (int i = 0; i < nbRow; i++) {
            complexMatrix.matrix[i][i] = new Complex(1., 0);
        }
        return complexMatrix;
    }

    public static ComplexMatrix getTransposed(ComplexMatrix cm) {
        ComplexMatrix complexMatrixTransposed = new ComplexMatrix(cm.getNbCol(), cm.getNbRow());
        for (int i = 0; i < cm.getNbCol(); i++) {
            for (int j = 0; j < cm.getNbRow(); j++) {
                complexMatrixTransposed.matrix[i][j] = cm.matrix[j][i];
            }
        }
        return complexMatrixTransposed;
    }

    public static ComplexMatrix getMatrixScaled(ComplexMatrix cm, Complex factor) {
        ComplexMatrix complexMatrixScaled = new ComplexMatrix(cm.getNbRow(), cm.getNbCol());
        for (int i = 0; i < cm.getNbRow(); i++) {
            for (int j = 0; j < cm.getNbCol(); j++) {
                complexMatrixScaled.matrix[i][j] = cm.matrix[i][j].multiply(factor);
            }
        }
        return complexMatrixScaled;
    }

    // utils to switch between complex and real cartesian representation of a complex matrix
    public DenseMatrix getRealCartesianMatrix() {
        DenseMatrix realMatrix = new DenseMatrix(matrix.length * 2, matrix[0].length * 2);
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                Complex complexTerm = matrix[i][j];
                realMatrix.add(2 * i, 2 * j, complexTerm.getReal());
                realMatrix.add(2 * i + 1, 2 * j + 1, complexTerm.getReal());
                realMatrix.add(2 * i, 2 * j + 1, -complexTerm.getImaginary());
                realMatrix.add(2 * i + 1, 2 * j, complexTerm.getImaginary());
            }
        }

        return realMatrix;
    }

    public static ComplexMatrix getComplexMatrixFromRealCartesian(DenseMatrix realMatrix) {

        int nbCol = realMatrix.getColumnCount();
        int nbRow = realMatrix.getRowCount();
        if (nbCol % 2 != 0 || nbRow % 2 != 0) { // dimensions have to be even
            throw new MatrixException("Incompatible matrices dimensions to build a complex matrix from a real cartesian");
        }

        ComplexMatrix complexMatrix = new ComplexMatrix(nbRow / 2, nbCol / 2);
        for (int i = 1; i <= nbRow / 2; i++) {
            for (int j = 1; j <= nbCol / 2; j++) {

                int rowIndexInCartesian = 2 * (i - 1);
                int colIndexInCartesian = 2 * (j - 1);

                // Before building the complex matrix term, check that the 4x4 cartesian bloc can be transformed into a Complex term
                double t11 = realMatrix.get(rowIndexInCartesian, colIndexInCartesian);
                double t12 = realMatrix.get(rowIndexInCartesian, colIndexInCartesian + 1);
                double t21 = realMatrix.get(rowIndexInCartesian + 1, colIndexInCartesian);
                double t22 = realMatrix.get(rowIndexInCartesian + 1, colIndexInCartesian + 1);

                double epsilon = 0.00000001;
                if (FastMath.abs(t11 - t22) > epsilon) {
                    throw new MatrixException("Incompatible bloc matrices terms to build a complex matrix from a real cartesian");
                }

                if (FastMath.abs(t12 + t21) > epsilon) {
                    throw new MatrixException("Incompatible bloc matrices terms to build a complex matrix from a real cartesian");
                }

                Complex complexTerm = new Complex(t11, t21);

                complexMatrix.set(i, j, complexTerm);
            }
        }

        return complexMatrix;
    }

    public static void printComplexMatrix(ComplexMatrix cm) {

        for (int i = 0; i < cm.getNbRow(); i++) {
            String line = " ";
            for (int j = 0; j < cm.getNbCol(); j++) {
                line = line + cm.matrix[i][j].toString() + "   ";
            }
            System.out.println(line);
        }
    }

}
