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
import com.powsybl.math.matrix.SparseMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class JacobianMatrix<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        implements EquationSystemIndexListener<V, E>, StateVectorListener, AutoCloseable {

    protected static final Logger LOGGER = LoggerFactory.getLogger(JacobianMatrix.class);

    protected final EquationSystem<V, E> equationSystem;

    protected final MatrixFactory matrixFactory;

    protected Matrix matrix;

    private LUDecomposition lu;

    protected enum Status {
        VALID,
        VALUES_INVALID, // same structure but values have to be updated
        VALUES_AND_ZEROS_INVALID, // same structure but values have to be updated and non zero values might have changed
        STRUCTURE_INVALID, // structure has changed
    }

    private Status status = Status.STRUCTURE_INVALID;

    private boolean allowIncrementalUpdateOnZeroChanges = false;

    // ---- partial value update ----
    // Post-contingency solves restart from a restored state: their first Jacobian equals the last one computed at
    // that very state, except in the columns touched by the topology events (contingency apply/restore, alternative
    // switches). A snapshot of the matrix values pinned at that state allows a restore-and-patch update of only the
    // touched columns instead of a full derivation walk. Exactness is guaranteed by a strict state vector equality
    // test; any untrackable event falls back to the full walk.
    // disabled by default: the restore-and-patch update only pays off when topology events preserve the matrix
    // structure (alternative equation switches), so it is enabled only on that path (see AcLoadFlowContext).
    // Kept off elsewhere to avoid the per-update state-vector equality check on the plain load flow hot path.
    private boolean partialValueUpdateEnabled = false;

    private double[] valuesSnapshot;

    private double[] stateSnapshot;

    private boolean touchedTrackable = true;

    private boolean eventsSinceLastDer = false;

    private final Set<SingleEquation<V, E>> touchedEquations = new LinkedHashSet<>();

    private final Map<EquationArray<V, E>, BitSet> touchedArrayElements = new LinkedHashMap<>();

    private int partialValueUpdateCount = 0;

    public JacobianMatrix<V, E> setPartialValueUpdateEnabled(boolean partialValueUpdateEnabled) {
        this.partialValueUpdateEnabled = partialValueUpdateEnabled;
        return this;
    }

    public int getPartialValueUpdateCount() {
        return partialValueUpdateCount;
    }

    protected boolean supportsPartialValueUpdate() {
        return true;
    }

    private void clearSnapshot() {
        valuesSnapshot = null;
        stateSnapshot = null;
        touchedEquations.clear();
        touchedArrayElements.clear();
        touchedTrackable = true;
    }

    private void recordTouchedEquation(Equation<V, E> equation) {
        eventsSinceLastDer = true;
        if (!touchedTrackable) {
            return;
        }
        if (equation instanceof SingleEquation<V, E> singleEquation) {
            touchedEquations.add(singleEquation);
        } else if (equation != null) {
            // an equation array element facade: resolve to the array and element
            var equationArray = equationSystem.getEquationArray(equation.getType()).orElse(null);
            if (equationArray != null) {
                touchedArrayElements.computeIfAbsent(equationArray, k -> new BitSet()).set(equation.getElementNum());
            } else {
                touchedTrackable = false;
            }
        } else {
            touchedTrackable = false;
        }
    }

    private void recordTouchedArrayElement(EquationArray<V, E> equationArray, int elementNum) {
        eventsSinceLastDer = true;
        touchedArrayElements.computeIfAbsent(equationArray, k -> new BitSet()).set(elementNum);
    }

    public JacobianMatrix(EquationSystem<V, E> equationSystem, MatrixFactory matrixFactory) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        equationSystem.getIndex().addListener(this);
        equationSystem.getStateVector().addListener(this);
    }

    /**
     * Allow trying an incremental LU update (reusing the previous pivoting) even when non zero values may have
     * changed, typically after a alternative equation alternative switch where the sparsity pattern is preserved
     * but matrix entries flip between zero and non zero. A full numerical refactorization is automatically retried
     * when the incremental update fails (e.g. on a pivot becoming zero).
     */
    public JacobianMatrix<V, E> setAllowIncrementalUpdateOnZeroChanges(boolean allowIncrementalUpdateOnZeroChanges) {
        this.allowIncrementalUpdateOnZeroChanges = allowIncrementalUpdateOnZeroChanges;
        return this;
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
        recordTouchedEquation(term.getEquation());
    }

    @Override
    public void onEquationAlternativeChange(SingleEquation<V, E> equation) {
        // the structure (and so the symbolic factorization) is preserved, only values have to be updated
        updateStatus(Status.VALUES_AND_ZEROS_INVALID);
        recordTouchedEquation(equation);
    }

    @Override
    public void onEquationArrayAlternativeChange(EquationArray<V, E> equationArray, int elementNum) {
        // the structure (and so the symbolic factorization) is preserved, only values have to be updated
        updateStatus(Status.VALUES_AND_ZEROS_INVALID);
        recordTouchedArrayElement(equationArray, elementNum);
    }

    @Override
    public void onEquationArrayChange(EquationArray<V, E> equationArray, ChangeType changeType) {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onEquationTermArrayChange(EquationTermArray<V, E> equationTermArray, int termNum, ChangeType changeType) {
        updateStatus(Status.VALUES_AND_ZEROS_INVALID);
        recordTouchedArrayElement(equationTermArray.getEquationArray(), equationTermArray.getEquationElementNum(termNum));
    }

    @Override
    public void onEquationIndexOrderChanged() {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onStateUpdate() {
        updateStatus(Status.VALUES_INVALID);
    }

    /**
     * When the equation system is not square (a bug leaves a variable without its determining equation, typically a
     * single control variable orphaned by a contingency), report the count of variables and of active equations
     * grouped by type, so the offending control type stands out even on a large network (e.g. more BRANCH_RHO1
     * variables than BRANCH_TARGET_RHO1 + DISTR_RHO equations points at a transformer voltage control).
     */
    private String describeEquationVariableImbalance() {
        Map<String, Integer> variablesByType = new java.util.TreeMap<>();
        for (Variable<V> variable : equationSystem.getIndex().getSortedVariablesToFind()) {
            variablesByType.merge(variable.getType().toString(), 1, Integer::sum);
        }
        Map<String, Integer> equationsByType = new java.util.TreeMap<>();
        for (var equation : equationSystem.getIndex().getSortedSingleEquationsToSolve()) {
            equationsByType.merge(equation.getType().toString(), 1, Integer::sum);
        }
        for (var equationArray : equationSystem.getIndex().getSortedEquationArraysToSolve()) {
            equationsByType.merge(equationArray.getType().toString(), equationArray.getLength(), Integer::sum);
        }
        return "variables by type=" + variablesByType + ", active equations by type=" + equationsByType;
    }

    protected void initDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        int rowCount = equationSystem.getIndex().getRowCount();
        int columnCount = equationSystem.getIndex().getColumnCount();
        if (rowCount != columnCount) {
            throw new PowsyblException("Expected to have same number of equations (" + columnCount
                    + ") and variables (" + rowCount + "). " + describeEquationVariableImbalance());
        }

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

    private void clearLu() {
        if (lu != null) {
            lu.close();
        }
        lu = null;
    }

    private void initMatrix() {
        initDer();
        clearLu();
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

    private void updateLu(boolean allowIncrementalUpdate) {
        if (lu != null) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            lu.update(allowIncrementalUpdate);

            LOGGER.debug(PERFORMANCE_MARKER, "LU decomposition updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
        }
    }

    private void updateValues(boolean allowIncrementalUpdate) {
        updateDer();
        afterFullDer();
        updateLuWithFallback(allowIncrementalUpdate);
    }

    private void updateLuWithFallback(boolean allowIncrementalUpdate) {
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

    /**
     * Snapshot policy: take (or move) the values snapshot after a full derivation walk that followed topology
     * events — that walk runs at a restart state (e.g. the post-contingency first solve), the state the snapshot
     * must be pinned at. Full walks between solver iterations (pure state moves, no events) keep the pin.
     */
    private void afterFullDer() {
        boolean events = eventsSinceLastDer;
        eventsSinceLastDer = false;
        if (!partialValueUpdateEnabled || !supportsPartialValueUpdate() || !(matrix instanceof SparseMatrix)) {
            return;
        }
        if (events || valuesSnapshot == null) {
            takeSnapshot();
        }
    }

    private void takeSnapshot() {
        SparseMatrix sparseMatrix = (SparseMatrix) matrix;
        int nonZeroCount = sparseMatrix.getColumnStart()[sparseMatrix.getColumnCount()];
        valuesSnapshot = Arrays.copyOf(sparseMatrix.getValues(), nonZeroCount);
        double[] state = equationSystem.getStateVector().get();
        stateSnapshot = Arrays.copyOf(state, state.length);
        touchedEquations.clear();
        touchedArrayElements.clear();
        touchedTrackable = true;
    }

    private boolean tryPartialUpdateValues(boolean allowIncrementalUpdate) {
        if (!partialValueUpdateEnabled || !supportsPartialValueUpdate()
                || valuesSnapshot == null || !touchedTrackable
                || !(matrix instanceof SparseMatrix sparseMatrix)) {
            return false;
        }
        double[] state = equationSystem.getStateVector().get();
        if (stateSnapshot.length != state.length || !Arrays.equals(stateSnapshot, state)) {
            return false;
        }
        for (EquationArray<V, E> equationArray : touchedArrayElements.keySet()) {
            if (!equationArray.isPartialDerReady()) {
                return false;
            }
        }
        Stopwatch stopwatch = Stopwatch.createStarted();

        double[] values = sparseMatrix.getValues();
        System.arraycopy(valuesSnapshot, 0, values, 0, valuesSnapshot.length);
        int[] columnStart = sparseMatrix.getColumnStart();
        int patchedColumns = 0;
        for (SingleEquation<V, E> equation : touchedEquations) {
            int column = equation.isActive() ? equation.getColumn() : -1;
            if (column < 0) {
                continue;
            }
            Arrays.fill(values, columnStart[column], columnStart[column + 1], 0);
            equation.der((variable, value, matrixElementIndex) -> {
                matrix.addAtIndex(matrixElementIndex, value);
                return matrixElementIndex;
            });
            patchedColumns++;
        }
        for (Map.Entry<EquationArray<V, E>, BitSet> entry : touchedArrayElements.entrySet()) {
            EquationArray<V, E> equationArray = entry.getKey();
            BitSet elements = entry.getValue();
            for (int elementNum = elements.nextSetBit(0); elementNum >= 0; elementNum = elements.nextSetBit(elementNum + 1)) {
                int column = equationArray.isElementActive(elementNum) ? equationArray.getElementNumToColumn(elementNum) : -1;
                if (column < 0) {
                    continue;
                }
                Arrays.fill(values, columnStart[column], columnStart[column + 1], 0);
                equationArray.derElementPartial((c, row, value, matrixElementIndex) -> {
                    matrix.addAtIndex(matrixElementIndex, value);
                    return matrixElementIndex;
                }, elementNum);
                patchedColumns++;
            }
        }
        // move the pin to the patched topology so the touched set stays bounded per contingency
        System.arraycopy(values, 0, valuesSnapshot, 0, valuesSnapshot.length);
        touchedEquations.clear();
        touchedArrayElements.clear();
        eventsSinceLastDer = false;
        partialValueUpdateCount++;

        LOGGER.debug(PERFORMANCE_MARKER, "Jacobian matrix values partially updated ({} columns) in {} us",
                patchedColumns, stopwatch.elapsed(TimeUnit.MICROSECONDS));

        updateLuWithFallback(allowIncrementalUpdate);
        return true;
    }

    public void forceUpdate() {
        update();
    }

    private void update() {
        if (status != Status.VALID) {
            switch (status) {
                case STRUCTURE_INVALID:
                    clearSnapshot();
                    initMatrix();
                    eventsSinceLastDer = false;
                    break;

                case VALUES_INVALID:
                    if (!tryPartialUpdateValues(true)) {
                        updateValues(true);
                    }
                    break;

                case VALUES_AND_ZEROS_INVALID:
                    if (!tryPartialUpdateValues(allowIncrementalUpdateOnZeroChanges)) {
                        updateValues(allowIncrementalUpdateOnZeroChanges);
                    }
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
        clearLu();
    }
}
