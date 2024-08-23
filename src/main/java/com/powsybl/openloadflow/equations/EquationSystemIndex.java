/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class EquationSystemIndex<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        implements EquationSystemListener<V, E> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquationSystemIndex.class);

    private final EquationSystem<V, E> equationSystem;

    private final Set<Equation<V, E>> equationsToSolve = new HashSet<>();

    private int columnCount = 0;

    // variable reference counting in equation terms
    private final Map<Variable<V>, MutableInt> variablesToFindRefCount = new HashMap<>();

    private int rowCount = 0;

    private List<Equation<V, E>> sortedEquationsToSolve = Collections.emptyList();

    private List<Variable<V>> sortedVariablesToFind = Collections.emptyList();

    private boolean equationsIndexValid = false;

    private boolean variablesIndexValid = false;

    private final List<EquationSystemIndexListener<V, E>> listeners = new ArrayList<>();

    public EquationSystemIndex(EquationSystem<V, E> equationSystem) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        equationSystem.addListener(this);
    }

    public void addListener(EquationSystemIndexListener<V, E> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(EquationSystemIndexListener<V, E> listener) {
        listeners.remove(Objects.requireNonNull(listener));
    }

    private void notifyEquationChange(Equation<V, E> equation, EquationSystemIndexListener.ChangeType changeType) {
        listeners.forEach(listener -> listener.onEquationChange(equation, changeType));
    }

    private void notifyVariableChange(Variable<V> variable, EquationSystemIndexListener.ChangeType changeType) {
        listeners.forEach(listener -> listener.onVariableChange(variable, changeType));
    }

    private void notifyEquationTermChange(EquationTerm<V, E> term) {
        listeners.forEach(listener -> listener.onEquationTermChange(term));
    }

    private void update() {
        if (!equationsIndexValid) {
            sortedEquationsToSolve = equationsToSolve.stream().sorted().collect(Collectors.toList());
            columnCount = 0;
            for (Equation<V, E> equation : sortedEquationsToSolve) {
                equation.setColumn(columnCount++);
            }
            int columnCountFromArrayEquations = 0;
            for (EquationArray<V, E> equationArray : equationSystem.getEquationArrays()) {
                equationArray.setFirstColumn(columnCount);
                columnCount += equationArray.getLength();
                columnCountFromArrayEquations += equationArray.getLength();
            }
            equationsIndexValid = true;
            LOGGER.debug("Equations index updated ({} columns including {} from array equations)",
                    columnCount, columnCountFromArrayEquations);
        }

        if (!variablesIndexValid) {
            sortedVariablesToFind = variablesToFindRefCount.keySet().stream().sorted().collect(Collectors.toList());
            rowCount = 0;
            for (Variable<V> variable : sortedVariablesToFind) {
                variable.setRow(rowCount++);
            }
            variablesIndexValid = true;
            LOGGER.debug("Variables index updated ({} rows)", rowCount);
        }
    }

    private void addTerm(EquationTerm<V, E> term) {
        notifyEquationTermChange(term);
        addVariables(term.getVariables());
    }

    private void addVariables(List<Variable<V>> variables) {
        for (Variable<V> variable : variables) {
            MutableInt variableRefCount = variablesToFindRefCount.get(variable);
            if (variableRefCount == null) {
                variableRefCount = new MutableInt(1);
                variablesToFindRefCount.put(variable, variableRefCount);
                variablesIndexValid = false;
                notifyVariableChange(variable, EquationSystemIndexListener.ChangeType.ADDED);
            } else {
                variableRefCount.increment();
            }
        }
    }

    private void addEquation(Equation<V, E> equation) {
        equationsToSolve.add(equation);
        equationsIndexValid = false;
        for (EquationTerm<V, E> term : equation.getTerms()) {
            if (term.isActive()) {
                addTerm(term);
            }
        }
        notifyEquationChange(equation, EquationSystemIndexListener.ChangeType.ADDED);
    }

    private void removeTerm(EquationTerm<V, E> term) {
        notifyEquationTermChange(term);
        for (Variable<V> variable : term.getVariables()) {
            MutableInt variableRefCount = variablesToFindRefCount.get(variable);
            if (variableRefCount != null) {
                variableRefCount.decrement();
                if (variableRefCount.intValue() == 0) {
                    variable.setRow(-1);
                    variablesToFindRefCount.remove(variable);
                    variablesIndexValid = false;
                    notifyVariableChange(variable, EquationSystemIndexListener.ChangeType.REMOVED);
                }
            }
        }
    }

    private void removeEquation(Equation<V, E> equation) {
        equation.setColumn(-1);
        equationsToSolve.remove(equation);
        equationsIndexValid = false;
        for (EquationTerm<V, E> term : equation.getTerms()) {
            if (term.isActive()) {
                removeTerm(term);
            }
        }
        notifyEquationChange(equation, EquationSystemIndexListener.ChangeType.REMOVED);
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

    @Override
    public void onEquationTermArrayChange(EquationTermArray<V, E> equationTermArray, int equationElementNum, int equationTermElementNum, List<Variable<V>> variables) {
        addVariables(variables);
        // TODO notif
    }

    public List<Equation<V, E>> getSortedEquationsToSolve() {
        update();
        return sortedEquationsToSolve;
    }

    public List<Variable<V>> getSortedVariablesToFind() {
        update();
        return sortedVariablesToFind;
    }

    public int getColumnCount() {
        update();
        return columnCount;
    }

    public int getRowCount() {
        update();
        return rowCount;
    }
}
