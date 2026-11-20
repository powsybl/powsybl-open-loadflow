/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.Arrays;
import java.util.List;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class EquationDerivativeVector {

    // cache indexed by term
    private final int nbTerms;
    private final int[] termElementNum;
    private final double[] valuesByTerm;
    private final double[][] termDerValues;
    private final MutableBoolean[] termActiveRef;
    private final boolean[] termActive;

    // cache indexed by cell of the matrix (multiple terms can be summed in the same cell)
    private final int nbCells;
    private final Number[] rowRefsByCell;
    private final int[] cellStarts;

    // values accessed by EquationArray when computing der()
    protected final int[] rowsByCell;
    protected final double[] valuesByCell;
    protected final int[] cellIndexByEquationElementNum;
    protected final int[] nbCellsByEquationElementNum;

    EquationDerivativeVector(List<EquationDerivativeElement<?>> elements, EquationArray<?, ?> equationArray) {
        nbTerms = elements.size();

        // compute all derivatives for each of the term array
        var termArrays = equationArray.getTermArrays();

        termElementNum = new int[nbTerms];
        termDerValues = new double[nbTerms][];
        valuesByTerm = new double[nbTerms];
        termActiveRef = new MutableBoolean[nbTerms];
        termActive = new boolean[nbTerms];

        cellIndexByEquationElementNum = new int[equationArray.getElementCount()];
        nbCellsByEquationElementNum = new int[equationArray.getElementCount()];
        rowRefsByCell = new Number[nbTerms];
        int[] cellIndices = new int[nbTerms + 1];

        Variable<?> previousVariable = null;
        int previousEquationElementNum = -1;
        int cellIndex = 0;
        int nbCellsByEquation = 0;
        for (int i = 0; i < nbTerms; i++) {
            EquationDerivativeElement<?> element = elements.get(i);
            var termArray = termArrays.get(element.termArrayNum);
            termDerValues[i] = termArray.evalDer()[element.derivative.getLocalIndex()];
            termElementNum[i] = termArray.getTermElementNum(element.termNum);
            termActiveRef[i] = termArray.getTermActiveRef(element.termNum);
            Variable<?> variable = element.derivative.getVariable();
            if (variable != previousVariable || element.equationElementNum != previousEquationElementNum) {
                if (element.equationElementNum != previousEquationElementNum) {
                    cellIndexByEquationElementNum[element.equationElementNum] = cellIndex;
                    if (previousEquationElementNum != -1) {
                        nbCellsByEquationElementNum[previousEquationElementNum] = nbCellsByEquation;
                        nbCellsByEquation = 0;
                    }
                }
                previousVariable = variable;
                previousEquationElementNum = element.equationElementNum;
                cellIndices[cellIndex] = i;
                rowRefsByCell[cellIndex] = variable.getRowRef();
                cellIndex++;
                nbCellsByEquation++;
            }
        }
        cellIndices[cellIndex] = nbTerms;
        if (previousEquationElementNum != -1) {
            nbCellsByEquationElementNum[previousEquationElementNum] = nbCellsByEquation;
        }
        nbCells = cellIndex;
        cellStarts = Arrays.copyOf(cellIndices, nbCells + 1);
        valuesByCell = new double[nbCells];
        rowsByCell = new int[nbCells];
    }

    private void updateRowsByCell() {
        for (int cellIndex = 0; cellIndex < nbCells; cellIndex++) {
            rowsByCell[cellIndex] = rowRefsByCell[cellIndex].intValue(); // updating row of the current cell
        }
    }

    private void updateActiveStatuses() {
        for (int i = 0; i < nbTerms; i++) {
            termActive[i] = termActiveRef[i].booleanValue();
        }
    }

    private void updateValuesByTerms() {
        for (int i = 0; i < nbTerms; i++) {
            valuesByTerm[i] = termActive[i] ? termDerValues[i][termElementNum[i]] : 0.0;
        }
    }

    void update(boolean recomputeRows, boolean recomputeActiveStatuses) {
        if (nbTerms > 0) {
            if (recomputeRows) {
                updateRowsByCell();
            }
            if (recomputeActiveStatuses) {
                updateActiveStatuses();
            }
            updateValuesByTerms();
            int start;
            int end = cellStarts[0];
            for (int c = 0; c < nbCells; c++) {
                start = end;
                end = cellStarts[c + 1];
                double sum = 0.0;
                for (int i = start; i < end; i++) {
                    sum += valuesByTerm[i];
                }
                valuesByCell[c] = sum;
            }
        }
    }
}
