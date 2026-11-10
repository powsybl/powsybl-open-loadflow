/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public abstract class AbstractJacobianMatrix<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        implements EquationSystemIndexListener<V, E>, StateVectorListener, AutoCloseable {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractJacobianMatrix.class);

    protected final EquationSystem<V, E> equationSystem;

    protected final MatrixFactory matrixFactory;

    private final boolean invalidateValuesWhenStateUpdate; // in DC mode, there is no need to recompute derivatives after state update

    protected Matrix matrix;

    protected enum Status {
        VALID,
        VALUES_INVALID, // same structure but values have to be updated
        VALUES_AND_ZEROS_INVALID, // same structure but values have to be updated and non zero values might have changed
        STRUCTURE_INVALID, // structure has changed
    }

    private Status status = Status.STRUCTURE_INVALID;

    protected AbstractJacobianMatrix(EquationSystem<V, E> equationSystem, MatrixFactory matrixFactory,
                                     boolean invalidateValuesWhenStateUpdate) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.invalidateValuesWhenStateUpdate = invalidateValuesWhenStateUpdate;
        equationSystem.getIndex().addListener(this);
        equationSystem.getStateVector().addListener(this);
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    protected void updateStatus(Status status) {
        if (status.ordinal() > this.status.ordinal()) {
            this.status = status;
        }
    }

    @Override
    public void onEquationChange(SingleEquation<V, E> equation, ChangeType changeType) {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onVariableChange(Variable<V> variable, ChangeType changeType) {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onEquationTermChange(SingleEquationTerm<V, E> term) {
        updateStatus(Status.VALUES_AND_ZEROS_INVALID);
    }

    @Override
    public void onEquationArrayChange(EquationArray<V, E> equationArray, ChangeType changeType) {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onEquationTermArrayChange(EquationTermArray<V, E> equationTermArray, int termNum, ChangeType changeType) {
        updateStatus(Status.VALUES_AND_ZEROS_INVALID);
    }

    @Override
    public void onEquationIndexOrderChanged() {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onEquationTermConstantsChanged() {
        updateStatus(Status.VALUES_INVALID);
    }

    @Override
    public void onStateUpdate() {
        if (invalidateValuesWhenStateUpdate) {
            updateStatus(Status.VALUES_INVALID);
        }
    }

    protected void initDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        int rowCount = equationSystem.getIndex().getRowCount();
        int columnCount = equationSystem.getIndex().getColumnCount();

        int estimatedNonZeroValueCount = rowCount * 3;
        matrix = matrixFactory.create(rowCount, columnCount, estimatedNonZeroValueCount);

        // When initializing the matrix, it must be filled in the column order (in case of SparseMatrix)
        //
        // SingleEquations are sorted by their column number
        // EquationArrays are sorted by their first column number (and all following column numbers are contiguous for a defined length)
        //
        // Example of EquationSystemIndex organization (e.g. in Fast Decoupled) with each index (corresponding to unique column number) :
        //   0    |   1    | ... | 12 | 13 | ... | 31 | 32 | 33 | ... | 51 |   52   |   53   | ... | 64 | ... | 83 | 84 | ... | 101 |  <-- index
        // Single | Single | ... |        Array       |        Array       | Single | Single | ... |     Array     |      Array     |

        Iterator<SingleEquation<V, E>> itSortedSingleEquation = equationSystem.getIndex().getSortedSingleEquationsToSolve().iterator();
        Iterator<EquationArray<V, E>> itSortedEquationArray = equationSystem.getIndex().getSortedEquationArraysToSolve().iterator();

        SingleEquation<V, E> eq = itSortedSingleEquation.hasNext() ? itSortedSingleEquation.next() : null;
        EquationArray<V, E> eqArray = itSortedEquationArray.hasNext() ? itSortedEquationArray.next() : null;
        int index = 0; // index is either the column number of SingleEquation, either the first column of EquationArray
        while (eq != null || eqArray != null) {
            while (eq != null && index == eq.getColumn()) { // Compute derivatives of all SingleEquations until next EquationArray
                final int column = index;
                eq.der((variable, value, matrixElementIndex) -> {
                    int row = variable.getRow();
                    return matrix.addAndGetIndex(row, column, value);
                });
                index++;
                eq = itSortedSingleEquation.hasNext() ? itSortedSingleEquation.next() : null;
            }
            while (eqArray != null && index == eqArray.getFirstColumn()) { // Compute derivatives of next EquationArrays
                eqArray.der((column, row, value, matrixElementIndex) ->
                        matrix.addAndGetIndex(row, column, value));
                index += eqArray.getLength();
                eqArray = itSortedEquationArray.hasNext() ? itSortedEquationArray.next() : null;
            }
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    protected void updateDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        matrix.reset();
        for (SingleEquation<V, E> eq : equationSystem.getIndex().getSortedSingleEquationsToSolve()) {
            eq.der((variable, value, matrixElementIndex) -> {
                matrix.addAtIndex(matrixElementIndex, value);
                return matrixElementIndex; // don't change element index
            });
        }
        for (var eq : equationSystem.getIndex().getSortedEquationArraysToSolve()) {
            eq.der((column, row, value, matrixElementIndex) -> {
                matrix.addAtIndex(matrixElementIndex, value);
                return matrixElementIndex; // don't change element index
            });
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Jacobian matrix values updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    protected void initMatrix() {
        initDer();
    }

    protected void updateValues(boolean allowIncrementalUpdate) {
        updateDer();
    }

    public void forceUpdate() {
        update();
    }

    private void update() {
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
    }

    public Matrix getMatrix() {
        update();
        return matrix;
    }

    @Override
    public void close() {
        equationSystem.getIndex().removeListener(this);
        equationSystem.getStateVector().removeListener(this);
        matrix = null;
    }
}
