/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.util.trove.TBooleanArrayList;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfElement;
import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTermArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    public interface Evaluator<V extends Enum<V> & Quantity> {

        double[] eval();

        double eval(int termElementNum);

        int getDerivativeCount();

        double[][] evalDer();

        List<Derivative<V>> getDerivatives(int termElementNum);
    }

    private final ElementType elementType;

    private EquationArray<V, E> equationArray;

    private final Evaluator<V> evaluator;

    // for each equation element number, term numbers
    private final List<TIntArrayList> termNumsByEquationElementNum = new ArrayList<>();

    // for each term number, corresponding element number
    private final TIntArrayList termElementNums = new TIntArrayList();

    // for each term number, activity status
    private final TBooleanArrayList termActive = new TBooleanArrayList(1);

    // for each term number, list of derivative variables
    private final List<List<Derivative<V>>> termDerivatives = new ArrayList<>();

    public EquationTermArray(ElementType elementType, Evaluator<V> evaluator) {
        this.elementType = Objects.requireNonNull(elementType);
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    public ElementType getElementType() {
        return elementType;
    }

    void setEquationArray(EquationArray<V, E> equationArray) {
        if (this.equationArray != null) {
            throw new IllegalArgumentException("Equation term array already added to an equation array");
        }
        this.equationArray = Objects.requireNonNull(equationArray);
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

    public List<Derivative<V>> getTermDerivatives(int termNum) {
        return termDerivatives.get(termNum);
    }

    public EquationTermArray<V, E> addTerm(LfElement equationElement, LfElement termElement) {
        return addTerm(Objects.requireNonNull(equationElement).getNum(), Objects.requireNonNull(termElement).getNum());
    }

    public EquationTermArray<V, E> addTerm(int equationElementNum, int termElementNum) {
        int termNum = termElementNums.size();
        getTermNums(equationElementNum).add(termNum);
        termElementNums.add(termElementNum);
        termActive.add(true);
        List<Derivative<V>> derivatives = evaluator.getDerivatives(termElementNum);
        termDerivatives.add(derivatives);
        equationArray.invalidateEquationDerivativeVectors();
        equationArray.getEquationSystem().notifyEquationTermArrayChange(this, equationElementNum, termElementNum, derivatives);
        return this;
    }

    public double[] eval() {
        return evaluator.eval();
    }

    public double eval(int termElementNum) {
        return evaluator.eval(termElementNum);
    }

    public int getDerivativeCount() {
        return evaluator.getDerivativeCount();
    }

    public double[][] evalDer() {
        return evaluator.evalDer();
    }
}
