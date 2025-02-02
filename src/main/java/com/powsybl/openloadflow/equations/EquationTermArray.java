/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfElement;
import gnu.trove.list.array.TIntArrayList;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTermArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    public interface Evaluator<V extends Enum<V> & Quantity> {

        String getName();

        boolean isDisabled(int termElementNum);

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

    // for each term number, corresponding equation element number
    private final TIntArrayList equationElementNums = new TIntArrayList();

    // for each term number, corresponding term element number
    private final TIntArrayList termElementNums = new TIntArrayList();

    // for each term number, activity status
    private final TIntArrayList termActive = new TIntArrayList();

    // for each term number, list of derivative variables
    private final List<List<Derivative<V>>> termDerivatives = new ArrayList<>();

    public EquationTermArray(ElementType elementType, Evaluator<V> evaluator) {
        this.elementType = Objects.requireNonNull(elementType);
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    public ElementType getElementType() {
        return elementType;
    }

    public EquationArray<V, E> getEquationArray() {
        return equationArray;
    }

    void setEquationArray(EquationArray<V, E> equationArray) {
        if (this.equationArray != null) {
            throw new IllegalArgumentException("Equation term array already added to an equation array");
        }
        this.equationArray = Objects.requireNonNull(equationArray);
    }

    public TIntArrayList getTermNumsForEquationElementNum(int equationElementNum) {
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
        return termActive.getQuick(termNum) == 1;
    }

    public int getEquationElementNum(int termNum) {
        return equationElementNums.getQuick(termNum);
    }

    public int getTermElementNum(int termNum) {
        return termElementNums.getQuick(termNum);
    }

    public List<Derivative<V>> getTermDerivatives(int termNum) {
        return termDerivatives.get(termNum);
    }

    public EquationTermArray<V, E> addTerm(LfElement equationElement, LfElement termElement) {
        return addTerm(Objects.requireNonNull(equationElement).getNum(), Objects.requireNonNull(termElement).getNum());
    }

    public EquationTermArray<V, E> addTerm(int equationElementNum, int termElementNum) {
        int termNum = termElementNums.size();
        getTermNumsForEquationElementNum(equationElementNum).add(termNum);
        getTermNumsForTermElementNum(termElementNum).add(termNum);
        equationElementNums.add(equationElementNum);
        termElementNums.add(termElementNum);
        termActive.add(evaluator.isDisabled(termElementNum) ? 0 : 1);
        termDerivatives.add(evaluator.getDerivatives(termElementNum));
        equationArray.invalidateEquationDerivativeVectors();
        equationArray.getEquationSystem().notifyEquationTermArrayChange(this, termNum, EquationTermEventType.EQUATION_TERM_ADDED);
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
        for (int i = 0; i < termNums.size(); i++) {
            int termNum = termNums.getQuick(i);
            boolean oldActive = termActive.getQuick(termNum) == 1;
            if (active != oldActive) {
                termActive.setQuick(termNum, active ? 1 : 0);
                equationArray.getEquationSystem().notifyEquationTermArrayChange(this, termNum, active ? EquationTermEventType.EQUATION_TERM_ACTIVATED : EquationTermEventType.EQUATION_TERM_DEACTIVATED);
            }
        }
    }

    public static class EquationTermArrayElementImpl<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements BaseEquationTerm<V, E> {

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

        @Override
        public ElementType getElementType() {
            return equationTermArray.getElementType();
        }

        @Override
        public double calculateSensi(DenseMatrix x, int column) {
            throw new UnsupportedOperationException("Calculate sensi not supported for arrays");
        }

        @Override
        public BaseEquationTerm<V, E> multiply(DoubleSupplier scalarSupplier) {
            throw new UnsupportedOperationException("Term multiply not supported for arrays");
        }

        @Override
        public BaseEquationTerm<V, E> multiply(double scalar) {
            throw new UnsupportedOperationException("Term multiply not supported for arrays");
        }

        @Override
        public BaseEquationTerm<V, E> minus() {
            throw new UnsupportedOperationException("Term minus not supported for arrays");
        }
    }

    public BaseEquationTerm<V, E> getElement(int termElementNum) {
        return new EquationTermArrayElementImpl<>(this, termElementNum);
    }

    public boolean write(Writer writer, boolean writeInactiveTerms, int elementNum, boolean first) throws IOException {
        TIntArrayList termNums = getTermNumsForEquationElementNum(elementNum);
        for (int i = 0; i < termNums.size(); i++) {
            int termNum = termNums.get(i);
            if (writeInactiveTerms || termActive.getQuick(termNum) == 1) {
                if (!first) {
                    writer.append(" + ");
                }
                if (termActive.getQuick(termNum) == 0) {
                    writer.write("[ ");
                }
                writer.append(evaluator.getName());
                writer.write("(");
                for (Iterator<Derivative<V>> it = getTermDerivatives(termNum).iterator(); it.hasNext(); ) {
                    Variable<V> variable = it.next().getVariable();
                    variable.write(writer);
                    if (it.hasNext()) {
                        writer.write(", ");
                    }
                }
                writer.write(")");
                if (termActive.getQuick(termNum) == 0) {
                    writer.write(" ]");
                }
                if (i < termNums.size() - 1) {
                    writer.append(" + ");
                }
            }
        }
        return !termNums.isEmpty();
    }
}
