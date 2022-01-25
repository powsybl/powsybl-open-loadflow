/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Used just for debugging as a reference
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class FullEquationSystemIndex<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationSystemIndex<V, E>, EquationSystemListener<V, E> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FullEquationSystemIndex.class);

    private final EquationSystem<V, E> equationSystem;

    private final TreeMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> sortedEquationsToSolve = new TreeMap<>();

    private final TreeSet<Variable<V>> sortedVariables = new TreeSet<>();

    private boolean valid = false;

    public FullEquationSystemIndex(EquationSystem<V, E> equationSystem) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        equationSystem.addListener(this);
    }

    private void update() {
        if (valid) {
            return;
        }

        sortedEquationsToSolve.clear();
        sortedVariables.clear();
        for (var equation : equationSystem.getEquations()) {
            if (!equation.isActive()) {
                break;
            }
            for (var term : equation.getTerms()) {
                if (!term.isActive()) {
                    break;
                }
                for (var v : term.getVariables()) {
                    sortedEquationsToSolve.computeIfAbsent(equation, k -> new TreeMap<>())
                            .computeIfAbsent(v, k -> new ArrayList<>())
                            .add(term);
                    sortedVariables.add(v);
                }
            }
        }

        int columnCount = 0;
        for (Equation<V, E> equation : sortedEquationsToSolve.keySet()) {
            equation.setColumn(columnCount++);
        }
        LOGGER.debug("Equations index updated ({} columns)", columnCount);

        int rowCount = 0;
        for (Variable<V> variable : sortedVariables) {
            variable.setRow(rowCount++);
        }
        LOGGER.debug("Variables index updated ({} rows)", rowCount);

        valid = true;
    }

    @Override
    public void onEquationChange(Equation<V, E> equation, EquationEventType eventType) {
        valid = false;
    }

    @Override
    public void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
        valid = false;
    }

    public NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve() {
        update();
        return sortedEquationsToSolve;
    }

    public NavigableSet<Variable<V>> getSortedVariablesToFind() {
        update();
        return sortedVariables;
    }
}
