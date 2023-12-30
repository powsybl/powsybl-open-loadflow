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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTermArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    public interface Evaluator {

        double[] eval(TIntArrayList termElementNums);

        double[] der(TIntArrayList termElementNums);
    }

    @FunctionalInterface
    public interface VariableCreator<V extends Enum<V> & Quantity> {

        List<Variable<V>> create(int termElementNum);
    }

    private final ElementType elementType;

    private EquationSystem<V, E> equationSystem;

    private final Evaluator evaluator;

    private final VariableCreator<V> variableCreator;

    // for each equation element number, term numbers
    private final List<TIntArrayList> termNumsByEquationElementNum = new ArrayList<>();

    // for each term number, corresponding element number
    private final TIntArrayList termElementNums = new TIntArrayList();

    // for each term number, activity status
    private final TBooleanArrayList termActive = new TBooleanArrayList(1);

    // for each term number, list of dependent variables
    private final List<List<Variable<V>>> termVariables = new ArrayList<>();

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

    public TIntArrayList getTermNums(int equationElementNum) {
        while (termNumsByEquationElementNum.size() <= equationElementNum) {
            termNumsByEquationElementNum.add(new TIntArrayList());
        }
        return termNumsByEquationElementNum.get(equationElementNum);
    }

    public boolean isTermActive(int termNum) {
        return termActive.get(termNum);
    }

    public int getTermElementNum(int termNum) {
        return termElementNums.get(termNum);
    }

    public List<Variable<V>> getTermVariables(int termNum) {
        return termVariables.get(termNum);
    }

    public EquationTermArray<V, E> addTerm(int equationElementNum, int termElementNum) {
        int termNum = termElementNums.size();
        getTermNums(equationElementNum).add(termNum);
        termElementNums.add(termElementNum);
        termActive.add(true);
        List<Variable<V>> variables = variableCreator.create(termElementNum);
        termVariables.add(variables);
        equationSystem.notifyEquationTermArrayChange(this, equationElementNum, termElementNum, variables);
        return this;
    }

    public double[] eval() {
        return evaluator.eval(termElementNums);
    }

    public double[] der() {
        return evaluator.der(termElementNums);
    }
}
