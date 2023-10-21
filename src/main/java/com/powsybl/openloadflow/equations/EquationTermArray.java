/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import gnu.trove.list.array.TIntArrayList;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTermArray<V extends Enum<V> & Quantity> {

    @FunctionalInterface
    public interface Evaluator {

        void eval(TIntArrayList termElementNums, double[] values);
    }

    @FunctionalInterface
    public interface VariableCreator<V extends Enum<V> & Quantity> {

        List<Variable<V>> create(int elementNum);
    }

    final Evaluator evaluator;

    final VariableCreator<V> variableCreator;

    final TIntArrayList elementNums = new TIntArrayList();
    final TIntArrayList termElementNums = new TIntArrayList();

    public EquationTermArray(Evaluator evaluator, VariableCreator<V> variableCreator) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.variableCreator = Objects.requireNonNull(variableCreator);
    }

    public EquationTermArray<V> addTerm(int elementNum, int termElementNum) {
        elementNums.add(elementNum);
        termElementNums.add(termElementNum);
        variableCreator.create(termElementNum);
        return this;
    }
}
