/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import gnu.trove.list.array.TDoubleArrayList;

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

    private List<Set<Variable<V>>> variablesByElementNum;

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
        invalidateTermsByVariableIndex();
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

    private void updateTermsByVariableIndex() {
        if (variablesByElementNum == null) {
            variablesByElementNum = new ArrayList<>(elementActive.length);
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                variablesByElementNum.add(null);
            }
            for (EquationTermArray<V, E> termArray : termArrays) {
                for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                    var termNums = termArray.getTermNums(elementNum);
                    for (int i = 0; i < termNums.size(); i++) {
                        int termNum = termNums.get(i);
                        var termVariables = termArray.getTermVariables(termNum);
                        Set<Variable<V>> variables = variablesByElementNum.get(elementNum);
                        if (variables == null) {
                            variables = new TreeSet<>();
                            variablesByElementNum.set(elementNum, variables);
                        }
                        variables.addAll(termVariables);
                    }
                }
            }
        }
    }

    void invalidateTermsByVariableIndex() {
        variablesByElementNum = null;
    }

    private double calculateDerValue(Variable<V> variable, List<TDoubleArrayList> termDerValuesByArrayIndex, int elementNum) {
        double value = 0;
        for (int arrayIndex = 0; arrayIndex < termArrays.size(); arrayIndex++) {
            var termArray = termArrays.get(arrayIndex);
            TDoubleArrayList termDerValues = termDerValuesByArrayIndex.get(arrayIndex);
            var termNums = termArray.getTermNums(elementNum);
            for (int i = 0; i < termNums.size(); i++) {
                int termNum = termNums.get(i);
                if (termArray.isTermActive(termNum)) {
                    int derIndex = termArray.getTermDerIndex(termNum, variable.getNum());
                    if (derIndex != -1) {
                        value += termDerValues.getQuick(derIndex);
                    }
                }
            }
        }
        return value;
    }

    public void der(DerHandler<V> handler) {
        Objects.requireNonNull(handler);

        updateTermsByVariableIndex();

        // compute all derivatives for each of the term array
        List<TDoubleArrayList> termDerValuesByArrayIndex = new ArrayList<>(termArrays.size());
        for (EquationTermArray<V, E> termArray : termArrays) {
            termDerValuesByArrayIndex.add(termArray.der());
        }

        // calculate all derivative values
        int matrixElementIndex = 0; // FIXME
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            if (elementActive[elementNum]) {
                int column = getElementNumToColumn(elementNum);
                Set<Variable<V>> variables = variablesByElementNum.get(elementNum);
                for (Variable<V> variable : variables) {
                    int row = variable.getRow();
                    double value = calculateDerValue(variable, termDerValuesByArrayIndex, elementNum);
                    handler.onDer(column, row, value, matrixElementIndex++);
                }
            }
        }
    }
}
