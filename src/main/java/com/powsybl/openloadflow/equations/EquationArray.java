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

    private int firstColumn = -1;

    private final boolean[] elementActive;

    private int length;

    private final List<EquationTermArray<V, E>> termArrays = new ArrayList<>();

    private final List<List<EquationTerm<V, E>>> termsByElementNum;

    public EquationArray(E type, int elementCount, EquationSystem<V, E> equationSystem) {
        this.type = Objects.requireNonNull(type);
        this.elementCount = elementCount;
        this.equationSystem = Objects.requireNonNull(equationSystem);
        elementActive = new boolean[elementCount];
        Arrays.fill(elementActive, true);
        this.length = elementCount; // all activated initially
        termsByElementNum = Collections.nCopies(elementCount, null);
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

    public EquationArray<V, E> addTermArray(EquationTermArray<V, E> termArray) {
        // TODO
        return this;
    }

    public EquationArray<V, E> addTerm(EquationTerm<V, E> term) {
        // TODO
        return this;
    }

    public void eval(double[] values) {
        // term arrays
        for (var termArray : termArrays) {
            termArray.eval(values, firstColumn, elementActive);
        }
        // individual terms
        int column = firstColumn;
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            var terms = termsByElementNum.get(elementNum);
            if (elementActive[elementNum]) {
                if (terms != null) {
                    for (var term : terms) {
                        if (term.isActive()) {
                            values[column] += term.eval();
                            if (term.hasRhs()) {
                                values[column] -= term.rhs();
                            }
                        }
                    }
                }
                column++;
            }
        }
    }

    interface DerHandler<V extends Enum<V> & Quantity> {

        int onDer(int column, Variable<V> variable, double value, int matrixElementIndex);
    }

    public void der(DerHandler<V> handler) {
        // TODO
    }
}
