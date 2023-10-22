/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTermArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    @FunctionalInterface
    public interface Evaluator {

        void eval(TIntArrayList termElementNums, double[] values);
    }

    @FunctionalInterface
    public interface VariableCreator<V extends Enum<V> & Quantity> {

        List<Variable<V>> create(int elementNum);
    }

    private EquationSystem<V, E> equationSystem;

    final Evaluator evaluator;

    final VariableCreator<V> variableCreator;

    final TIntArrayList equationElementNums = new TIntArrayList();
    final TIntArrayList equationTermElementNums = new TIntArrayList();
    final List<List<Variable<V>>> variables = new ArrayList<>();

    public EquationTermArray(Evaluator evaluator, VariableCreator<V> variableCreator) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.variableCreator = Objects.requireNonNull(variableCreator);
    }

    void setEquationSystem(EquationSystem<V, E> equationSystem) {
        this.equationSystem = equationSystem;
    }

    public EquationTermArray<V, E> addTerm(int equationElementNum, int equationTermElementNum) {
        equationElementNums.add(equationElementNum);
        equationTermElementNums.add(equationTermElementNum);
        variables.add(variableCreator.create(equationTermElementNum));
        equationSystem.notifyEquationTermArrayChange(this, equationElementNum, equationTermElementNum);
        return this;
    }
}
