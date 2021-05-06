package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;

import java.util.Objects;

public interface MatrixUtil {

    /**
     * Utility method for creating a single row matrix from a java array.
     *
     * @param r a row array
     * @param matrixFactory matrix factory to allow creating the matrix with different implementations.
     * @return the single row matrix
     */
    static Matrix createFromRow(double[] r, MatrixFactory matrixFactory) {
        Objects.requireNonNull(r);
        Objects.requireNonNull(matrixFactory);
        Matrix m = matrixFactory.create(1, r.length, r.length);
        for (int j = 0; j < r.length; j++) {
            m.set(0, j, r[j]);
        }
        return m;
    }
}
