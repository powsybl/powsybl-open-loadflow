/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class IncrementalEquationSystemIndex<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        extends AbstractEquationSystemIndex<V, E> implements EquationSystemListener<V, E> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalEquationSystemIndex.class);

    // variable reference counting in equation terms
    private final NavigableMap<Variable<V>, MutableInt> sortedVariablesRefCount = new TreeMap<>();

    private boolean equationsIndexValid = false;

    private boolean variablesIndexValid = false;

    public IncrementalEquationSystemIndex(EquationSystem<V, E> equationSystem) {
        Objects.requireNonNull(equationSystem).addListener(this);
    }

    private void update() {
        if (!equationsIndexValid) {
            int columnCount = 0;
            for (Equation<V, E> equation : sortedEquationsToSolve.keySet()) {
                equation.setColumn(columnCount++);
            }
            equationsIndexValid = true;
            LOGGER.debug("Equations index updated ({} columns)", columnCount);
            notifyEquationsIndexUpdate();
        }

        if (!variablesIndexValid) {
            int rowCount = 0;
            for (Variable<V> variable : sortedVariablesRefCount.keySet()) {
                variable.setRow(rowCount++);
            }
            variablesIndexValid = true;
            LOGGER.debug("Variables index updated ({} rows)", rowCount);
            notifyVariablesIndexUpdate();
        }
    }

    private void addTerm(EquationTerm<V, E> term) {
        for (Variable<V> variable : term.getVariables()) {
            sortedEquationsToSolve.computeIfAbsent(term.getEquation(), k -> new TreeMap<>())
                    .computeIfAbsent(variable, k -> new ArrayList<>())
                    .add(term);
            sortedVariablesRefCount.computeIfAbsent(variable, k -> {
                variablesIndexValid = false;
                return new MutableInt();
            }).increment();
        }
    }

    private void addEquation(Equation<V, E> equation) {
        for (EquationTerm<V, E> term : equation.getTerms()) {
            if (term.isActive()) {
                addTerm(term);
            }
        }
        equationsIndexValid = false;
    }

    private void removeVariable(EquationTerm<V, E> term, NavigableMap<Variable<V>, List<EquationTerm<V, E>>> termsByVariable,
                                Variable<V> variable) {
        if (termsByVariable != null) {
            List<EquationTerm<V, E>> terms = termsByVariable.get(variable);
            if (terms != null) {
                terms.remove(term);
                if (terms.isEmpty()) {
                    termsByVariable.remove(variable);
                    if (termsByVariable.isEmpty()) {
                        sortedEquationsToSolve.remove(term.getEquation());
                    }
                }
            }
        }
    }

    private void removeTerm(EquationTerm<V, E> term) {
        NavigableMap<Variable<V>, List<EquationTerm<V, E>>> termsByVariable = sortedEquationsToSolve.get(term.getEquation());
        for (Variable<V> variable : term.getVariables()) {
            removeVariable(term, termsByVariable, variable);
            MutableInt variableRefCount = sortedVariablesRefCount.get(variable);
            if (variableRefCount != null) {
                variableRefCount.decrement();
                if (variableRefCount.intValue() == 0) {
                    variable.setRow(-1);
                    sortedVariablesRefCount.remove(variable);
                    variablesIndexValid = false;
                }
            }
        }
    }

    private void removeEquation(Equation<V, E> equation) {
        equation.setColumn(-1);
        sortedEquationsToSolve.remove(equation);
        for (EquationTerm<V, E> term : equation.getTerms()) {
            if (term.isActive()) {
                removeTerm(term);
            }
        }
        equationsIndexValid = false;
    }

    @Override
    public void onEquationChange(Equation<V, E> equation, EquationEventType eventType) {
        switch (eventType) {
            case EQUATION_REMOVED:
                if (equation.isActive()) {
                    removeEquation(equation);
                }
                break;

            case EQUATION_DEACTIVATED:
                removeEquation(equation);
                break;

            case EQUATION_CREATED:
                if (equation.isActive()) {
                    addEquation(equation);
                }
                break;

            case EQUATION_ACTIVATED:
                addEquation(equation);
                break;

            default:
                throw new IllegalStateException("Event type not supported: " + eventType);
        }
    }

    @Override
    public void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
        if (term.getEquation().isActive()) {
            switch (eventType) {
                case EQUATION_TERM_ADDED:
                    if (term.isActive()) {
                        addTerm(term);
                    }
                    break;

                case EQUATION_TERM_ACTIVATED:
                    addTerm(term);
                    break;

                case EQUATION_TERM_DEACTIVATED:
                    removeTerm(term);
                    break;

                default:
                    throw new IllegalStateException("Event type not supported: " + eventType);
            }
        }
    }

    public NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve() {
        update();
        return sortedEquationsToSolve;
    }

    public NavigableSet<Variable<V>> getSortedVariablesToFind() {
        update();
        return sortedVariablesRefCount.navigableKeySet();
    }
}
