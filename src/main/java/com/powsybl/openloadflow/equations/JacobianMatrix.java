/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.math.matrix.MatrixFactory;

import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class JacobianMatrix<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        extends AbstractJacobianMatrix<V, E> {

    private LUDecomposition lu;

    public JacobianMatrix(EquationSystem<V, E> equationSystem, MatrixFactory matrixFactory, boolean invalidateValuesWhenStateUpdate) {
        super(equationSystem, matrixFactory, invalidateValuesWhenStateUpdate);
    }

    public JacobianMatrix(EquationSystem<V, E> equationSystem, MatrixFactory matrixFactory) {
        this(equationSystem, matrixFactory, true);
    }

    @Override
    protected void initDer() {
        int rowCount = equationSystem.getIndex().getRowCount();
        int columnCount = equationSystem.getIndex().getColumnCount();
        if (rowCount != columnCount) {
            throw new PowsyblException("Expected to have same number of equations (" + columnCount
                    + ") and variables (" + rowCount + ")");
        }
        super.initDer();
    }

    private void clearLu() {
        if (lu != null) {
            lu.close();
        }
        lu = null;
    }

    @Override
    protected void initMatrix() {
        super.initMatrix();
        clearLu();
    }

    private void updateLu(boolean allowIncrementalUpdate) {
        if (lu != null) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            lu.update(allowIncrementalUpdate);

            LOGGER.debug(PERFORMANCE_MARKER, "LU decomposition updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
        }
    }

    @Override
    protected void updateValues(boolean allowIncrementalUpdate) {
        super.updateValues(allowIncrementalUpdate);
        try {
            updateLu(allowIncrementalUpdate);
        } catch (MatrixException ex) {
            if (allowIncrementalUpdate) {
                // Try another time without incremental
                LOGGER.warn("Exception when updating LU matrix in incremental mode. Retrying without incremental mode");
                updateLu(false);
            } else {
                // Rethrow the exception
                throw ex;
            }
        }
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
        super.close();
        clearLu();
    }
}
