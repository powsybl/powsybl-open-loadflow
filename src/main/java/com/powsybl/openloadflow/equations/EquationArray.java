/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.util.trove.TIntArrayListHack;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    private final E type;

    private final int elementCount;

    private final EquationSystem<V, E> equationSystem;

    private final boolean[] elementActive;

    private int firstColumn = -1;

    private int[] elementNumToColumn;

    private int length;

    private List<EquationDerivativeVector> equationDerivativeVectors;

    static class EquationDerivativeElement {
        int termArrayNum;
        int termNum;
        int derVariableRow;
        int derLocalIndex;

        EquationDerivativeElement(int termArrayNum, int termNum, int derVariableRow, int derLocalIndex) {
            this.termArrayNum = termArrayNum;
            this.termNum = termNum;
            this.derVariableRow = derVariableRow;
            this.derLocalIndex = derLocalIndex;
        }
    }

    static class EquationDerivativeVector {
        private final List<EquationDerivativeElement> elements = new ArrayList<>();

        private final TIntArrayListHack termArrayNums = new TIntArrayListHack();
        private final TIntArrayListHack termNums = new TIntArrayListHack();
        private final TIntArrayListHack derVariableRows = new TIntArrayListHack();
        private final TIntArrayListHack derLocalIndexes = new TIntArrayListHack();

        void addTerm(int termNum, int termArrayNum, int derVariableRow, int derLocalIndex) {
            elements.add(new EquationDerivativeElement(termArrayNum, termNum, derVariableRow, derLocalIndex));
        }

        void sortByVariableRowAndVectorizedLocalNum() {
            elements.sort(Comparator.comparingInt(o -> o.derVariableRow));
            for (EquationDerivativeElement element : elements) {
                termArrayNums.add(element.termArrayNum);
                termNums.add(element.termNum);
                derVariableRows.add(element.derVariableRow);
                derLocalIndexes.add(element.derLocalIndex);
            }
        }
    }

    public EquationArray(E type, int elementCount, EquationSystem<V, E> equationSystem) {
        this.type = Objects.requireNonNull(type);
        this.elementCount = elementCount;
        this.equationSystem = Objects.requireNonNull(equationSystem);
        elementActive = new boolean[elementCount];
        Arrays.fill(elementActive, true);
        this.length = elementCount; // all activated initially
    }

    public E getType() {
        return type;
    }

    public int getElementCount() {
        return elementCount;
    }

    public int[] getElementNumToColumn() {
        if (elementNumToColumn == null) {
            elementNumToColumn = new int[elementCount];
            int column = firstColumn;
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                if (elementActive[elementNum]) {
                    elementNumToColumn[elementNum] = column++;
                }
            }
        }
        return elementNumToColumn;
    }

    public int getElementNumToColumn(int elementNum) {
        return getElementNumToColumn()[elementNum];
    }

    private void invalidateElementNumToColumn() {
        elementNumToColumn = null;
    }

    public EquationSystem<V, E> getEquationSystem() {
        return equationSystem;
    }

    public int getFirstColumn() {
        return firstColumn;
    }

    public void setFirstColumn(int firstColumn) {
        this.firstColumn = firstColumn;
    }

    public int getLength() {
        return length;
    }

    public boolean isElementActive(int elementNum) {
        return elementActive[elementNum];
    }

    public void setElementActive(int elementNum, boolean active) {
        if (active != this.elementActive[elementNum]) {
            this.elementActive[elementNum] = active;
            if (active) {
                length++;
            } else {
                length--;
            }
            invalidateElementNumToColumn();
            // TODO notify equation system listeners
        }
    }

    private final List<EquationTermArray<V, E>> termArrays = new ArrayList<>();

    public void addTermArray(EquationTermArray<V, E> termArray) {
        Objects.requireNonNull(termArray);
        termArray.setEquationArray(this);
        termArrays.add(termArray);
        invalidateEquationDerivativeVectors();
    }

    public void eval(double[] values) {
        Arrays.fill(values, firstColumn, firstColumn + length, 0);
        for (EquationTermArray<V, E> termArray : termArrays) {
            double[] termValues = termArray.eval();
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                if (elementActive[elementNum]) {
                    var termNums = termArray.getTermNums(elementNum);
                    for (int i = 0; i < termNums.size(); i++) {
                        int termNum = termNums.get(i);
                        int termElementNum = termArray.getTermElementNum(termNum);
                        if (termArray.isTermActive(termNum)) {
                            values[getElementNumToColumn(elementNum)] += termValues[termElementNum];
                        }
                    }
                }
            }
        }
    }

    public interface DerHandler<V extends Enum<V> & Quantity> {

        int onDer(int column, int row, double value, int matrixElementIndex);
    }

    private void updateEquationDerivativeVectors() {
        if (equationDerivativeVectors == null) {
            equationDerivativeVectors = new ArrayList<>();
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                equationDerivativeVectors.add(null);
            }

            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                var equationDerivativeVector = equationDerivativeVectors.get(elementNum);
                if (equationDerivativeVector == null) {
                    equationDerivativeVector = new EquationDerivativeVector();
                    equationDerivativeVectors.set(elementNum, equationDerivativeVector);
                }
                for (int termArrayNum = 0; termArrayNum < termArrays.size(); termArrayNum++) {
                    EquationTermArray<V, E> termArray = termArrays.get(termArrayNum);
                    var termNums = termArray.getTermNums(elementNum);
                    for (int i = 0; i < termNums.size(); i++) {
                        int termNum = termNums.get(i);
                        var termDerivatives = termArray.getTermDerivatives(termNum);
                        for (Derivative<V> derivative : termDerivatives) {
                            int derVariableRow = derivative.getVariable().getRow();
                            int derLocalIndex = derivative.getLocalIndex();
                            equationDerivativeVector.addTerm(termNum, termArrayNum, derVariableRow, derLocalIndex);
                        }
                    }
                }
            }

            for (EquationDerivativeVector equationDerivativeVector : equationDerivativeVectors) {
                equationDerivativeVector.sortByVariableRowAndVectorizedLocalNum();
            }
        }
    }

    void invalidateEquationDerivativeVectors() {
        equationDerivativeVectors = null;
    }

    public void der(DerHandler<V> handler) {
        Objects.requireNonNull(handler);

        updateEquationDerivativeVectors();

        // compute all derivatives for each of the term array
        List<double[]> termDerValuesByArrayIndex = new ArrayList<>(termArrays.size());
        for (EquationTermArray<V, E> termArray : termArrays) {
            termDerValuesByArrayIndex.add(termArray.evalDer());
        }

        // calculate all derivative values
        int matrixElementIndex = 0; // FIXME
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            if (elementActive[elementNum]) {
                int column = getElementNumToColumn(elementNum);
                EquationDerivativeVector equationDerivativeVector = equationDerivativeVectors.get(elementNum);
                double value = 0;
                int prevRow = -1;
                for (int i = 0; i < equationDerivativeVector.termNums.size(); i++) {
                    int termNum = equationDerivativeVector.termNums.getQuick(i);
                    int termArrayNum = equationDerivativeVector.termArrayNums.getQuick(i);
                    EquationTermArray<V, E> termArray = termArrays.get(termArrayNum);
                    int row = equationDerivativeVector.derVariableRows.getQuick(i);
                    if (prevRow != -1 && row != prevRow && Math.abs(value) != 0) {
                        handler.onDer(column, prevRow, value, matrixElementIndex++);
                        value = 0;
                    }
                    prevRow = row;
                    if (termArray.isTermActive(termNum)) {
                        int derLocalIndex = equationDerivativeVector.derLocalIndexes.getQuick(i);
                        int termElementNum = termArray.getTermElementNum(termNum);
                        int derIndex = termElementNum * termArray.getDerivativeCount() + derLocalIndex;
                        double[] termDerValues = termDerValuesByArrayIndex.get(termArrayNum);
                        value += termDerValues[derIndex];
                    }
                }
                if (Math.abs(value) != 0) {
                    handler.onDer(column, prevRow, value, matrixElementIndex++);
                }
            }
        }
    }
}
