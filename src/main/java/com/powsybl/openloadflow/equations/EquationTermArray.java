/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.util.trove.TBooleanArrayList;
import com.powsybl.openloadflow.network.ElementType;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTermArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    public interface Evaluator {

        void eval(TIntArrayList termElementNums, double[] values);

        int getDerCount();

        void der(TIntArrayList termElementNums, double[] values);
    }

    @FunctionalInterface
    public interface VariableCreator<V extends Enum<V> & Quantity> {

        List<Variable<V>> create(int elementNum);
    }

    private final ElementType elementType;

    private EquationSystem<V, E> equationSystem;

    final Evaluator evaluator;

    final VariableCreator<V> variableCreator;

    // for each term, equation element number
    final TIntArrayList equationElementNums = new TIntArrayList();

    // for each term, term element number
    final TIntArrayList equationTermElementNums = new TIntArrayList();

    // for each variable, term count
    final TIntIntMap equationTermCountByVariableNum = new TIntIntHashMap();

    // for each term, term active status
    final TBooleanArrayList equationTermElementActive = new TBooleanArrayList(1);

    // for each term, list of dependent variables
    final List<List<Variable<V>>> equationTermsVariables = new ArrayList<>();

    // number of equation term derivatives
    int equationTermDerCount = 0;

    public EquationTermArray(ElementType elementType, Evaluator evaluator, VariableCreator<V> variableCreator) {
        this.elementType = Objects.requireNonNull(elementType);
        this.evaluator = Objects.requireNonNull(evaluator);
        this.variableCreator = Objects.requireNonNull(variableCreator);
    }

    public ElementType getElementType() {
        return elementType;
    }

    void setEquationSystem(EquationSystem<V, E> equationSystem) {
        this.equationSystem = equationSystem;
    }

    public EquationTermArray<V, E> addTerm(int equationElementNum, int equationTermElementNum) {
        equationElementNums.add(equationElementNum);
        equationTermElementNums.add(equationTermElementNum);
        equationTermElementActive.add(true);
        List<Variable<V>> variables = variableCreator.create(equationTermElementNum);
        equationTermsVariables.add(variables);
        for (Variable<V> variable : variables) {
            int variableNum = variable.getNum();
            equationTermCountByVariableNum.adjustValue(variableNum, 1);
        }
        equationTermDerCount += variables.size();
        equationSystem.notifyEquationTermArrayChange(this, equationElementNum, equationTermElementNum, variables);
        return this;
    }
}
