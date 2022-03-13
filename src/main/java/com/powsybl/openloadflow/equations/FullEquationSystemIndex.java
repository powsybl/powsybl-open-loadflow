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
class FullEquationSystemIndex<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        extends AbstractEquationSystemIndex<V, E> implements EquationSystemListener<V, E> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FullEquationSystemIndex.class);

    private final EquationSystem<V, E> equationSystem;

    private final TreeSet<Equation<V, E>> sortedEquationsToSolve = new TreeSet<>();

    private final TreeSet<Variable<V>> sortedVariablesToFind = new TreeSet<>();

    private boolean valid = false;

    public FullEquationSystemIndex(EquationSystem<V, E> equationSystem) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        equationSystem.addListener(this);
    }

    private void update() {
        if (valid) {
            return;
        }

        for (Equation<V, E> equation : sortedEquationsToSolve) {
            equation.setColumn(-1);
        }
        for (Variable<V> equation : sortedVariablesToFind) {
            equation.setRow(-1);
        }

        sortedEquationsToSolve.clear();
        sortedVariablesToFind.clear();
        for (var equation : equationSystem.getEquations()) {
            if (!equation.isActive()) {
                continue;
            }
            sortedEquationsToSolve.add(equation);
            for (var term : equation.getTerms()) {
                if (!term.isActive()) {
                    continue;
                }
                sortedVariablesToFind.addAll(term.getVariables());
            }
        }

        int columnCount = 0;
        for (Equation<V, E> equation : sortedEquationsToSolve) {
            equation.setColumn(columnCount++);
        }
        LOGGER.debug("Equations index updated ({} columns)", columnCount);

        int rowCount = 0;
        for (Variable<V> variable : sortedVariablesToFind) {
            variable.setRow(rowCount++);
        }
        LOGGER.debug("Variables index updated ({} rows)", rowCount);

        valid = true;
    }

    private void invalidate() {
        valid = false;
        notifyEquationChange();
        notifyVariableChange();
    }

    @Override
    public void onEquationChange(Equation<V, E> equation, EquationEventType eventType) {
        invalidate();
    }

    @Override
    public void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
        invalidate();
    }

    public NavigableSet<Equation<V, E>> getSortedEquationsToSolve() {
        update();
        return sortedEquationsToSolve;
    }

    public NavigableSet<Variable<V>> getSortedVariablesToFind() {
        update();
        return sortedVariablesToFind;
    }
}
