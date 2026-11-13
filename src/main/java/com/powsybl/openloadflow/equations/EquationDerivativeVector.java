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

    private final int[] termArrayNums;
    private final int[] termNums;

    // cache
    private final Number[] rowRefsByCell;
    protected final int[] rowsByCell;
    protected final int[] termElementNum;
    private final double[] valuesByTerm;
    private final double[][] termDerValues;
    private final MutableBoolean[] termActiveRef;
    private final boolean[] termActive;

    protected final double[] valuesByCell;
    protected final int[] cellIndexByEquationElementNum;
    protected final int[] nbCellsByEquationElementNum;
    protected final boolean[] isNewCell;
    private final int nbCells;

    EquationDerivativeVector(List<EquationDerivativeElement<?>> elements, EquationArray<?, ?> equationArray) {
        int size = elements.size();

        // compute all derivatives for each of the term array
        var termArrays = equationArray.getTermArrays();

        termArrayNums = new int[size];
        termNums = new int[size];
        termElementNum = new int[size];
        termDerValues = new double[size][];
        valuesByTerm = new double[size];
        termActiveRef = new MutableBoolean[size];
        termActive = new boolean[size];

        isNewCell = new boolean[size]; // is true if row or equationElementNum of term i is different than term i-1

        cellIndexByEquationElementNum = new int[equationArray.getElementCount()];
        nbCellsByEquationElementNum = new int[equationArray.getElementCount()];

        // each potential cell of the jacobian matrix will be stored in values (one cell can be the sum of multiple terms)
        // so there are fewer values than size (but pre allocating all size) (nbCells < size)
        rowRefsByCell = new Number[size];

        Variable<?> previousVariable = null;
        int previousEquationElementNum = -1;
        int cellIndex = 0;
        int nbCellsByEquation = 0;
        for (int i = 0; i < size; i++) {
            EquationDerivativeElement<?> element = elements.get(i);
            termNums[i] = element.termNum;
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
                isNewCell[i] = true;
                rowRefsByCell[cellIndex] = variable.getRowRef();
                cellIndex++;
                nbCellsByEquation++;
            }
        }
        if (previousEquationElementNum != -1) {
            nbCellsByEquationElementNum[previousEquationElementNum] = nbCellsByEquation;
        }
        nbCells = cellIndex;

        valuesByCell = new double[cellIndex];
        rowsByCell = new int[cellIndex];
    }

    private void updateRows() {
        for (int cellIndex = 0; cellIndex < nbCells; cellIndex++) {
            rowsByCell[cellIndex] = rowRefsByCell[cellIndex].intValue(); // updating row of the current cell
        }
        for (int i = 0; i < termNums.length; i++) {
            termActive[i] = termActiveRef[i].booleanValue(); // updating active status of the terms
        }
    }

    private void updateValuesByTerms() {
        for (int i = 0; i < termNums.length; i++) {
            valuesByTerm[i] = termActive[i] ? termDerValues[i][termElementNum[i]] : 0.0;
        }
    }


    void update(boolean recomputeRows) {
        if (termNums.length > 0) {
            if (recomputeRows) {
                updateRows();
            }
            updateValuesByTerms();
            double sum = valuesByTerm[0];
            int cellIndex = 0;
            for (int i = 1; i < termNums.length; i++) {
                if (isNewCell[i]) {
                    valuesByCell[cellIndex++] = sum;
                    sum = 0.0;
                }
                sum += valuesByTerm[i];
            }
            valuesByCell[cellIndex] = sum;
        }
    }
}
