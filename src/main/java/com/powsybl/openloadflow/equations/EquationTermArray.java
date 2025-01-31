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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTermArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    public interface Evaluator<V extends Enum<V> & Quantity> {

        String getName();

        double[] eval();

        double eval(int termElementNum);

        double[][] evalDer();

        List<Derivative<V>> getDerivatives(int termElementNum);
    }

    private final ElementType elementType;

    private EquationArray<V, E> equationArray;

    private final Evaluator<V> evaluator;

    // for each equation element number, term numbers
    private final List<TIntArrayList> termNumsByEquationElementNum = new ArrayList<>();

    // for each term element number, term numbers
    private final List<TIntArrayList> termNumsByTermElementNum = new ArrayList<>();

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

    public TIntArrayList getTermNumsForElementNum(int equationElementNum) {
        while (termNumsByEquationElementNum.size() <= equationElementNum) {
            termNumsByEquationElementNum.add(new TIntArrayList());
        }
        return termNumsByEquationElementNum.get(equationElementNum);
    }

    public TIntArrayList getTermNumsForTermElementNum(int termElementNum) {
        while (termNumsByTermElementNum.size() <= termElementNum) {
            termNumsByTermElementNum.add(new TIntArrayList());
        }
        return termNumsByTermElementNum.get(termElementNum);
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
        getTermNumsForElementNum(equationElementNum).add(termNum);
        getTermNumsForTermElementNum(termElementNum).add(termNum);
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

    public double[][] evalDer() {
        return evaluator.evalDer();
    }

    public void setActive(int termElementNum, boolean active) {
        TIntArrayList termNums = getTermNumsForTermElementNum(termElementNum);
        for (int termNum = 0; termNum < termNums.size(); termNum++) {
            termActive.set(termNum, active);
        }
    }

    public static class EquationTermArrayElementImpl<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationTermArrayElement<V, E> {

        final EquationTermArray<V, E> equationTermArray;

        final int termElementNum;

        public EquationTermArrayElementImpl(EquationTermArray<V, E> equationTermArray, int termElementNum) {
            this.equationTermArray = Objects.requireNonNull(equationTermArray);
            this.termElementNum = termElementNum;
        }

        @Override
        public double eval() {
            return equationTermArray.eval(termElementNum);
        }

        @Override
        public void setActive(boolean active) {
            equationTermArray.setActive(termElementNum, active);
        }
    }

    public EquationTermArrayElement<V, E> getElement(int termElementNum) {
        return new EquationTermArrayElementImpl<>(this, termElementNum);
    }

    public boolean write(Writer writer, int elementNum) throws IOException {
        TIntArrayList termNums = getTermNumsForElementNum(elementNum);
        boolean written = false;
        for (int termNum = 0; termNum < termNums.size(); termNum++) {
            if (termActive.get(termNum)) {
                writer.append(evaluator.getName());
                writer.write("(");
                for (Iterator<Derivative<V>> it = getTermDerivatives(termNum).iterator(); it.hasNext();) {
                    Variable<V> variable = it.next().getVariable();
                    variable.write(writer);
                    if (it.hasNext()) {
                        writer.write(", ");
                    }
                }
                writer.write(")");
                if (termNum < termNums.size() - 1) {
                    writer.append(" + ");
                }
                written = true;
            }
        }
        return written;
    }
}
