/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

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
        termArray.setEquationSystem(equationSystem);
        termArrays.add(termArray);
    }

    public void eval(double[] values) {
        Arrays.fill(values, firstColumn, firstColumn + length, 0);
        for (EquationTermArray<V, E> termArray : termArrays) {
            double[] termValues = termArray.evaluator.eval(termArray.equationTermElementNums);
            for (int termNum = 0; termNum < termArray.equationElementNums.size(); termNum++) {
                int elementNum = termArray.equationElementNums.get(termNum);
                if (elementActive[elementNum] && termArray.equationTermElementActive.get(termNum)) {
                    values[getElementNumToColumn(elementNum)] += termValues[termNum];
                }
            }
        }
    }

    public interface DerHandler<V extends Enum<V> & Quantity> {

        int onDer(int column, int row, double value, int matrixElementIndex);
    }

    public void der(DerHandler<V> handler) {
        Objects.requireNonNull(handler);
        List<Set<Variable<V>>> variablesByEquationElementNum = new ArrayList<>(elementActive.length);
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            variablesByEquationElementNum.add(null);
        }
        // index terms by variables
        for (EquationTermArray<V, E> termArray : termArrays) {
            for (int termNum = 0; termNum < termArray.equationElementNums.size(); termNum++) {
                int elementNum = termArray.equationElementNums.get(termNum);
                if (elementActive[elementNum]) {
                    boolean termElementActive = termArray.equationTermElementActive.get(termNum);
                    if (termElementActive) {
                        var termVariables = termArray.equationTermVariables.get(termNum);
                        Set<Variable<V>> variables = variablesByEquationElementNum.get(elementNum);
                        if (variables == null) {
                            variables = new TreeSet<>();
                            variablesByEquationElementNum.set(elementNum, variables);
                        }
                        variables.addAll(termVariables);
                    }
                }
            }
        }

        // precompute derivatives by term array
        for (EquationTermArray<V, E> termArray : termArrays) {
            if (termArray.termDerValues == null) { // TODO how to invalidate
                termArray.termDerValues = termArray.evaluator.der(termArray.equationTermElementNums);
            }
        }

        // calculate
        int matrixElementIndex = 0;
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            if (elementActive[elementNum]) {
                Set<Variable<V>> variables = variablesByEquationElementNum.get(elementNum);
                for (Variable<V> variable : variables) {
                    int column = getElementNumToColumn(elementNum);
                    int row = variable.getRow();
                    // we need to find term nums for this equation
                    double value = 1;
                    handler.onDer(column, row, value, matrixElementIndex++);
                }
            }
        }
    }
}
