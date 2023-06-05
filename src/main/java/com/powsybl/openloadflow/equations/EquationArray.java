/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    private final E type;

    private final int elementCount;

    private final EquationSystem<V, E> equationSystem;

    private final boolean[] elementActive;

    private final int[] elementColumn;

    private int length;

    class ArrayElementEquation implements ElementEquation<V, E> {

        private final int elementNum;

        ArrayElementEquation(int elementNum) {
            this.elementNum = elementNum;
        }

        @Override
        public int getElementNum() {
            return elementNum;
        }

        @Override
        public E getType() {
            return type;
        }

        @Override
        public EquationSystem<V, E> getEquationSystem() {
            return equationSystem;
        }

        @Override
        public int getColumn() {
            return elementColumn[elementNum];
        }

        @Override
        public boolean isActive() {
            return elementActive[elementNum];
        }
    }

    class ElementContext {

        private final List<EquationTerm<V, E>> terms = new ArrayList<>();

        private final Map<Variable<V>, List<EquationTerm<V, E>>> termsByVariable = new TreeMap<>();

        /**
         * Element index of a two dimensions matrix (equations * variables) indexed by variable index (order of the variable
         * in {@link @termsByVariable}.
         */
        private int[] matrixElementIndexes;

        private final ElementEquation<V, E> elementEquation;

        ElementContext(int elementNum) {
            elementEquation = new ArrayElementEquation(elementNum);
        }
    }

    private final List<ElementContext> elementContexts;

    public EquationArray(E type, int elementCount, EquationSystem<V, E> equationSystem) {
        this.type = Objects.requireNonNull(type);
        this.elementCount = elementCount;
        this.equationSystem = Objects.requireNonNull(equationSystem);
        elementActive = new boolean[elementCount];
        elementColumn = new int[elementCount];
        Arrays.fill(elementActive, true);
        this.length = elementCount; // all activated initially
        elementContexts = new ArrayList<>(Collections.nCopies(elementCount, null));
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

    public int getElementColumn(int elementNum) {
        return elementColumn[elementNum];
    }

    public void setElementColumn(int elementNum, int column) {
        elementColumn[elementNum] = column;
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

    public EquationArray<V, E> addTerm(int elementNum, EquationTerm<V, E> term) {
        Objects.requireNonNull(term);
        if (term.getEquation() != null) {
            throw new PowsyblException("Equation term already added to another equation: "
                    + term.getEquation());
        }
        ElementContext context = elementContexts.get(elementNum);
        if (context == null) {
            context = new ElementContext(elementNum);
            elementContexts.set(elementNum, context);
        }
        context.terms.add(term);
        for (Variable<V> v : term.getVariables()) {
            context.termsByVariable.computeIfAbsent(v, k -> new ArrayList<>())
                    .add(term);
        }
        context.matrixElementIndexes = null;
        term.setEquation(context.elementEquation);
        equationSystem.addEquationTerm(term);
//        equationSystem.notifyEquationTermChange(term, EquationTermEventType.EQUATION_TERM_ADDED);
        return this;
    }

    public void eval(double[] values) {
        // term arrays
        // TODO

        // individual terms
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            var context = elementContexts.get(elementNum);
            if (elementActive[elementNum] && context != null) {
                int column = elementColumn[elementNum];
                for (var term : context.terms) {
                    if (term.isActive()) {
                        values[column] += term.eval();
                        if (term.hasRhs()) {
                            values[column] -= term.rhs();
                        }
                    }
                }
            }
        }
    }

    interface DerHandler<V extends Enum<V> & Quantity> {

        int onDer(int column, Variable<V> variable, double value, int matrixElementIndex);
    }

    public void der(DerHandler<V> handler) {
        // term arrays
        // TODO

        // individual terms
        // TODO
    }
}
