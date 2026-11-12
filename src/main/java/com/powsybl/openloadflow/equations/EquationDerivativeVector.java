/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattened view of all the derivative values of an {@link EquationArray}, ordered by equation element number and,
 * inside an equation, by variable (so by matrix row).
 *
 * <p>Values are not gathered from the term derivative vectors (which would mean jumping all over them, as terms of a
 * same equation are typically spread over the whole network), but scattered: the term derivative vectors are read
 * sequentially and each value is written at its place in this vector, using an input to output index mapping built
 * once. Same thing for the variable rows, which are read once per variable instead of once per derivative.
 *
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class EquationDerivativeVector {

    private static final int ROW_UNDEFINED = Integer.MIN_VALUE;

    /**
     * Input to output index mapping for one (equation term array, derivative local index) couple, which corresponds
     * to one term derivative vector.
     */
    private static final class Scatter {

        private final EquationTermArray<?, ?> termArray;

        // the term derivative vector to read
        private final double[] termDerValues;

        // read at this term element number ...
        private final int[] termElementNums;

        // ... and write at this index of the equation derivative vector
        private final int[] indices;

        // corresponding term number, to check term activity
        private final int[] termNums;

        private Scatter(EquationTermArray<?, ?> termArray, double[] termDerValues, int[] termElementNums,
                        int[] indices, int[] termNums) {
            this.termArray = termArray;
            this.termDerValues = termDerValues;
            this.termElementNums = termElementNums;
            this.indices = indices;
            this.termNums = termNums;
        }

        private void update(double[] values) {
            for (int k = 0; k < indices.length; k++) {
                // inactive terms contribute for zero
                values[indices[k]] = termArray.isTermActive(termNums[k]) ? termDerValues[termElementNums[k]] : 0;
            }
        }
    }

    private final Scatter[] scatters;

    // distinct variables of this equation array, and their current row
    private final Variable<?>[] variables;
    private final int[] variableRows;

    // for each derivative, index of its variable in the above arrays
    private final int[] variableIndices;

    protected final int[] rows;
    protected final double[] values;

    private boolean rowsValid = false;

    EquationDerivativeVector(List<EquationDerivativeElement<?>> elements, EquationArray<?, ?> equationArray) {
        int size = elements.size();
        rows = new int[size];
        values = new double[size];
        variableIndices = new int[size];

        var termArrays = equationArray.getTermArrays();

        // one bucket per (term array, derivative local index) couple, so one bucket per term derivative vector
        double[][][] termDerValuesByTermArrayNum = new double[termArrays.size()][][];
        int[] bucketNumOffsets = new int[termArrays.size() + 1];
        for (int termArrayNum = 0; termArrayNum < termArrays.size(); termArrayNum++) {
            termDerValuesByTermArrayNum[termArrayNum] = termArrays.get(termArrayNum).evalDer();
            bucketNumOffsets[termArrayNum + 1] = bucketNumOffsets[termArrayNum]
                    + termDerValuesByTermArrayNum[termArrayNum].length;
        }
        int bucketCount = bucketNumOffsets[termArrays.size()];

        Map<Variable<?>, Integer> variableIndexByVariable = new HashMap<>();
        List<Variable<?>> variableList = new ArrayList<>();
        int[] bucketNums = new int[size];
        int[] bucketSizes = new int[bucketCount];
        for (int i = 0; i < size; i++) {
            EquationDerivativeElement<?> element = elements.get(i);
            int bucketNum = bucketNumOffsets[element.termArrayNum] + element.derivative.getLocalIndex();
            bucketNums[i] = bucketNum;
            bucketSizes[bucketNum]++;
            Variable<?> variable = element.derivative.getVariable();
            Integer variableIndex = variableIndexByVariable.get(variable);
            if (variableIndex == null) {
                variableIndex = variableList.size();
                variableIndexByVariable.put(variable, variableIndex);
                variableList.add(variable);
            }
            variableIndices[i] = variableIndex;
        }
        variables = variableList.toArray(new Variable<?>[0]);
        variableRows = new int[variables.length];
        Arrays.fill(variableRows, ROW_UNDEFINED);

        // dispatch derivatives to their bucket
        int[][] bucketTermElementNums = new int[bucketCount][];
        int[][] bucketIndices = new int[bucketCount][];
        int[][] bucketTermNums = new int[bucketCount][];
        for (int bucketNum = 0; bucketNum < bucketCount; bucketNum++) {
            bucketTermElementNums[bucketNum] = new int[bucketSizes[bucketNum]];
            bucketIndices[bucketNum] = new int[bucketSizes[bucketNum]];
            bucketTermNums[bucketNum] = new int[bucketSizes[bucketNum]];
        }
        int[] bucketFillCounts = new int[bucketCount];
        for (int i = 0; i < size; i++) {
            EquationDerivativeElement<?> element = elements.get(i);
            int bucketNum = bucketNums[i];
            int k = bucketFillCounts[bucketNum]++;
            bucketTermElementNums[bucketNum][k] = termArrays.get(element.termArrayNum).getTermElementNum(element.termNum);
            bucketIndices[bucketNum][k] = i;
            bucketTermNums[bucketNum][k] = element.termNum;
        }

        List<Scatter> scatterList = new ArrayList<>(bucketCount);
        for (int termArrayNum = 0; termArrayNum < termArrays.size(); termArrayNum++) {
            for (int localIndex = 0; localIndex < termDerValuesByTermArrayNum[termArrayNum].length; localIndex++) {
                int bucketNum = bucketNumOffsets[termArrayNum] + localIndex;
                if (bucketSizes[bucketNum] == 0) {
                    continue;
                }
                // sort the bucket by term element number, so that the term derivative vector is read sequentially
                sortByTermElementNum(bucketTermElementNums[bucketNum], bucketIndices[bucketNum], bucketTermNums[bucketNum]);
                scatterList.add(new Scatter(termArrays.get(termArrayNum),
                                            termDerValuesByTermArrayNum[termArrayNum][localIndex],
                                            bucketTermElementNums[bucketNum],
                                            bucketIndices[bucketNum],
                                            bucketTermNums[bucketNum]));
            }
        }
        scatters = scatterList.toArray(new Scatter[0]);
    }

    private static void sortByTermElementNum(int[] termElementNums, int[] indices, int[] termNums) {
        int n = termElementNums.length;
        // sort (term element number, position) couples packed in a long, term element number first
        long[] keys = new long[n];
        for (int k = 0; k < n; k++) {
            keys[k] = ((long) termElementNums[k] << 32) | k;
        }
        Arrays.sort(keys);
        int[] sortedTermElementNums = new int[n];
        int[] sortedIndices = new int[n];
        int[] sortedTermNums = new int[n];
        for (int k = 0; k < n; k++) {
            int prevK = (int) keys[k];
            sortedTermElementNums[k] = termElementNums[prevK];
            sortedIndices[k] = indices[prevK];
            sortedTermNums[k] = termNums[prevK];
        }
        System.arraycopy(sortedTermElementNums, 0, termElementNums, 0, n);
        System.arraycopy(sortedIndices, 0, indices, 0, n);
        System.arraycopy(sortedTermNums, 0, termNums, 0, n);
    }

    void update() {
        for (Scatter scatter : scatters) {
            scatter.update(values);
        }
        updateRows();
    }

    private void updateRows() {
        // variable rows may have changed since last update, but this is rare enough to be worth checking instead of
        // systematically refilling the row vector
        boolean rowsChanged = false;
        for (int variableIndex = 0; variableIndex < variables.length; variableIndex++) {
            int row = variables[variableIndex].getRow();
            if (row != variableRows[variableIndex]) {
                variableRows[variableIndex] = row;
                rowsChanged = true;
            }
        }
        if (rowsChanged || !rowsValid) {
            for (int i = 0; i < rows.length; i++) {
                rows[i] = variableRows[variableIndices[i]];
            }
            rowsValid = true;
        }
    }
}
