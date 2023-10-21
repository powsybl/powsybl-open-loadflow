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
            // TODO notify equation system listeners
        }
    }

    private final List<EquationTermArray> termArrays = new ArrayList<>();

    public void addTermArray(EquationTermArray termArray) {
        termArrays.add(termArray);
    }

    public void eval(double[] values) {
        Arrays.fill(values, firstColumn, firstColumn + length, 0);
        for (EquationTermArray termArray : termArrays) {
            int column = firstColumn;
            for (int i = 0; i < termArray.elementNums.size(); i++) {
                int elementNum = termArray.elementNums.get(i);
                if (elementActive[elementNum]) {
                    int termElementNum = termArray.termElementNums.get(i);
                    values[column++] += termArray.evaluator.applyAsDouble(termElementNum);
                }
            }
        }
    }

    public interface DerHandler<V extends Enum<V> & Quantity> {

        int onDer(int column, Variable<V> variable, double value, int matrixElementIndex);
    }

    public void der(DerHandler<V> handler) {
        // term arrays
        // TODO
    }
}