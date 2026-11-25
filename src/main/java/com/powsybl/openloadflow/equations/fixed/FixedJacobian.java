/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations.fixed;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Quantity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;

/**
 * The Jacobian of a {@link FixedEquationSystemIndex}, factorised. The matrix is created once on the matrix structure, through the
 * matrix factory: with OLF's default one, a sparse matrix in compressed columns, whose values are then filled in
 * place. Its LU decomposition is analysed once: afterwards only the numerical factorisation is updated, as long as
 * the matrix structure does not grow.
 *
 * <p>Right-hand sides and solutions are in the numbering of OLF's index, like those of {@code JacobianMatrix}: by
 * equation column and by variable row. They are moved to the positions and back. A position that holds no equation gets a
 * zero right-hand side: its variable does not move.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class FixedJacobian<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements LUDecomposition {

    /** Right-hand sides are moved to the positions, solved together and moved back by groups of at most this many values. */
    private static final int GROUP_VALUE_COUNT = 1 << 20;

    private final FixedEquationSystemIndex<V, E> fixed;
    private final MatrixFactory matrixFactory;

    private Matrix matrix;
    private LUDecomposition lu;
    /** The row indices of the matrix structure the matrix was created on: another array means another matrix structure. */
    private int[] structureRowIndices;
    /** For a matrix that does not store its values in the order of the matrix structure: where each one goes. */
    private int[] elementIndices;
    private boolean valid;

    private int[] rowOfPosition;
    private int[] columnOfPosition;
    private double[] byPosition;

    private int analysisCount;
    private int pivotingCount;

    public FixedJacobian(EquationSystem<V, E> equationSystem, VariablePairing<V, E> pairing, MatrixFactory matrixFactory) {
        this.fixed = new FixedEquationSystemIndex<>(equationSystem, pairing);
        this.matrixFactory = matrixFactory;
    }

    /** To call when the state, an activation or a constant of the equation system changes. */
    public void invalidate() {
        valid = false;
    }

    /**
     * The values of the matrix changed: the factorisation is updated before the next solve. Whether it may keep its
     * pivots is decided then.
     */
    @Override
    public void update(boolean allowIncrementalUpdate) {
        invalidate();
    }

    private void factorise() {
        if (valid) {
            return;
        }
        FixedStructure<V, E> structure = fixed.getStructure();
        if (structureRowIndices != structure.getRowIndices()) {
            closeLu();
            createMatrix(structure);
        }
        if (elementIndices == null) {
            fixed.updateDer(((SparseMatrix) matrix).getValues());
        } else {
            fixed.updateDer();
            matrix.reset();
            for (int i = 0; i < elementIndices.length; i++) {
                matrix.addAtIndex(elementIndices[i], fixed.getValues()[i]);
            }
        }
        try {
            if (lu == null) {
                lu = matrix.decomposeLU();
                analysisCount++;
            } else {
                updateLu();
            }
        } catch (MatrixException e) {
            closeLu();
            throw e;
        }
        for (int position = 0; position < byPosition.length; position++) {
            int equation = fixed.getEquationOfPosition(position);
            rowOfPosition[position] = structure.getVariable(position).getRow();
            columnOfPosition[position] = equation < 0 ? -1 : structure.getColumnInEquationSystem(equation);
        }
        valid = true;
    }

    /**
     * The numerical factorisation only, on the same analysis. The pivots of the previous one are tried first, whatever
     * changed since, values or the equations the positions hold: the decomposition checks their growth and chooses new
     * ones when they are no longer good. When one of them has become zero the update fails, and new ones are asked for.
     */
    private void updateLu() {
        try {
            lu.update(true);
        } catch (MatrixException e) {
            pivotingCount++;
            lu.update(false);
        }
    }

    private void createMatrix(FixedStructure<V, E> structure) {
        int variableCount = structure.getVariableCount();
        int[] columnStart = structure.getColumnStart();
        int[] rowIndices = structure.getRowIndices();
        matrix = matrixFactory.create(variableCount, variableCount, rowIndices.length);
        int[] indices = new int[rowIndices.length];
        boolean inStructureOrder = matrix instanceof SparseMatrix;
        for (int position = 0; position < variableCount; position++) {
            for (int i = columnStart[position]; i < columnStart[position + 1]; i++) {
                indices[i] = matrix.addAndGetIndex(rowIndices[i], position, 0);
                inStructureOrder &= indices[i] == i;
            }
        }
        elementIndices = inStructureOrder ? null : indices;
        structureRowIndices = rowIndices;
        rowOfPosition = new int[variableCount];
        columnOfPosition = new int[variableCount];
        byPosition = new double[variableCount];
    }

    private void solve(double[] b, boolean transposed) {
        factorise();
        int[] from = transposed ? columnOfPosition : rowOfPosition;
        int[] to = transposed ? rowOfPosition : columnOfPosition;
        for (int position = 0; position < byPosition.length; position++) {
            byPosition[position] = from[position] >= 0 ? b[from[position]] : 0;
        }
        if (transposed) {
            lu.solveTransposed(byPosition);
        } else {
            lu.solve(byPosition);
        }
        for (int position = 0; position < byPosition.length; position++) {
            if (to[position] >= 0) {
                b[to[position]] = byPosition[position];
            }
        }
    }

    private void solve(DenseMatrix b, boolean transposed) {
        factorise();
        int[] from = transposed ? columnOfPosition : rowOfPosition;
        int[] to = transposed ? rowOfPosition : columnOfPosition;
        int variableCount = byPosition.length;
        int groupColumnCount = Math.max(1, GROUP_VALUE_COUNT / Math.max(1, variableCount));
        DenseMatrix group = null;
        DoubleBuffer groupValues = null;
        double[] values = null;
        for (int first = 0; first < b.getColumnCount(); first += groupColumnCount) {
            int columnCount = Math.min(groupColumnCount, b.getColumnCount() - first);
            if (group == null || group.getColumnCount() != columnCount) {
                ByteBuffer buffer = ByteBuffer.allocateDirect(variableCount * columnCount * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
                group = new DenseMatrix(variableCount, columnCount, () -> buffer);
                groupValues = buffer.asDoubleBuffer();
                values = new double[variableCount * columnCount];
            }
            for (int j = 0; j < columnCount; j++) {
                for (int position = 0; position < variableCount; position++) {
                    values[j * variableCount + position] = from[position] >= 0 ? b.get(from[position], first + j) : 0;
                }
            }
            groupValues.put(0, values);
            if (transposed) {
                lu.solveTransposed(group);
            } else {
                lu.solve(group);
            }
            groupValues.get(0, values);
            for (int j = 0; j < columnCount; j++) {
                for (int position = 0; position < variableCount; position++) {
                    if (to[position] >= 0) {
                        b.set(to[position], first + j, values[j * variableCount + position]);
                    }
                }
            }
        }
    }

    /** Solves for a right-hand side by variable row; the solution is by equation column. */
    @Override
    public void solve(double[] b) {
        solve(b, false);
    }

    /** Solves the transposed system, the one of a Newton step: right-hand side by equation column, solution by variable row. */
    @Override
    public void solveTransposed(double[] b) {
        solve(b, true);
    }

    /** The same as {@link #solve(double[])} for a block of right-hand sides, one per column. */
    @Override
    public void solve(DenseMatrix b) {
        solve(b, false);
    }

    /** The same as {@link #solveTransposed(double[])} for a block of right-hand sides, one per column. */
    @Override
    public void solveTransposed(DenseMatrix b) {
        solve(b, true);
    }

    /** How many times the matrix was analysed: once, plus once each time the matrix structure grew or a factorisation failed. */
    public int getAnalysisCount() {
        return analysisCount;
    }

    /** How many times a pivot of the previous factorisation had become zero, so that new ones were chosen. */
    public int getPivotingCount() {
        return pivotingCount;
    }

    private void closeLu() {
        if (lu != null) {
            lu.close();
            lu = null;
        }
    }

    @Override
    public void close() {
        closeLu();
        fixed.close();
    }
}
