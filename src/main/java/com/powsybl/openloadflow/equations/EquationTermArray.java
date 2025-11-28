/**
 * Copyright (c) 2023-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfElement;
import gnu.trove.impl.Constants;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

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

        default double calculateSensi(int termElementNum, DenseMatrix dx, int column) {
            throw new UnsupportedOperationException("calculateSensi not yet implemented");
        }

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
    private TIntArrayList[] termNumsByEquationElementNum;
    private TIntArrayList termNumsConcatenated;
    private int[] termNumsConcatenatedStartIndices;

    // for each term element number, corresponding term number
    private TIntIntMap termNumByTermElementNum = new TIntIntHashMap(3, Constants.DEFAULT_LOAD_FACTOR, -1, -1);

    // for each term number, corresponding equation element number
    private final TIntArrayList equationElementNums = new TIntArrayList();

    // for each term number, corresponding term element number
    private final TIntArrayList termElementNums = new TIntArrayList();

    // for each term number, activity status
    private final TByteArrayList termActive = new TByteArrayList();

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
        termNumsConcatenatedStartIndices = new int[equationArray.getElementCount() + 1];
        termNumsByEquationElementNum = new TIntArrayList[equationArray.getElementCount()];
        for (int elementNum = 0; elementNum < equationArray.getElementCount(); elementNum++) {
            termNumsByEquationElementNum[elementNum] = new TIntArrayList(10);
        }
    }

    public TIntArrayList getTermNumsForEquationElementNum(int equationElementNum) {
        return termNumsByEquationElementNum[equationElementNum];
    }

    public int[] getTermNumsConcatenatedStartIndices() {
        return termNumsConcatenatedStartIndices;
    }

    public TIntArrayList getTermNumsConcatenated() {
        return termNumsConcatenated;
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
        termNumsByEquationElementNum[equationElementNum].add(termNum);
        if (termNumByTermElementNum.put(termElementNum, termNum) != -1) {
            throw new PowsyblException("A term element with same number already exists: " + termElementNum);
        }
        equationElementNums.add(equationElementNum);
        termElementNums.add(termElementNum);
        termActive.add((byte) (evaluator.isDisabled(termElementNum) ? 0 : 1));
        termDerivatives.add(evaluator.getDerivatives(termElementNum));
        equationArray.invalidateEquationDerivativeVectors();
        equationArray.getEquationSystem().notifyEquationTermArrayChange(this, termNum, EquationTermEventType.EQUATION_TERM_ADDED);
        return this;
    }

    public void compress() {
        termNumsConcatenated = new TIntArrayList(equationArray.getElementCount() * 2);
        for (int i = 0; i < termNumsByEquationElementNum.length; i++) {
            int iStart = termNumsConcatenated.size();
            termNumsConcatenated.addAll(termNumsByEquationElementNum[i]);
            termNumsConcatenatedStartIndices[i] = iStart;
        }
        termNumsConcatenatedStartIndices[termNumsByEquationElementNum.length] = termNumsConcatenated.size();

        this.termNumByTermElementNum = new TIntIntHashMap(this.termNumByTermElementNum);
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

    public boolean hasTermElement(int termElementNum) {
        return termNumByTermElementNum.containsKey(termElementNum);
    }

    public boolean isTermElementActive(int termElementNum) {
        int termNum = termNumByTermElementNum.get(termElementNum);
        if (termNum == -1) {
            throw new PowsyblException("Array term element num not found");
        }
        return termActive.getQuick(termNum) == 1;
    }

    public void setTermElementActive(int termElementNum, boolean active) {
        int termNum = termNumByTermElementNum.get(termElementNum);
        if (termNum == -1) {
            throw new PowsyblException("Array term element num not found");
        }
        boolean oldActive = termActive.getQuick(termNum) == 1;
        if (active != oldActive) {
            termActive.setQuick(termNum, (byte) (active ? 1 : 0));
            equationArray.getEquationSystem().notifyEquationTermArrayChange(this, termNum, active ? EquationTermEventType.EQUATION_TERM_ACTIVATED : EquationTermEventType.EQUATION_TERM_DEACTIVATED);
        }
    }

    public static class EquationTermArrayElementImpl<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationTerm<V, E> {

        final EquationTermArray<V, E> equationTermArray;

        final int termElementNum;

        int elementNum;

        public EquationTermArrayElementImpl(EquationTermArray<V, E> equationTermArray, int termElementNum) {
            this.equationTermArray = Objects.requireNonNull(equationTermArray);
            this.termElementNum = termElementNum;
        }

        public Evaluator<V> getEvaluator() {
            return equationTermArray.evaluator;
        }

        @Override
        public double eval() {
            return equationTermArray.eval(termElementNum);
        }

        @Override
        public boolean isActive() {
            return equationTermArray.isTermElementActive(termElementNum);
        }

        @Override
        public void setActive(boolean active) {
            equationTermArray.setTermElementActive(termElementNum, active);
        }

        @Override
        public ElementType getElementType() {
            return equationTermArray.getElementType();
        }

        @Override
        public int getElementNum() {
            return elementNum;
        }

        @Override
        public double der(Variable<V> variable) {
            Objects.requireNonNull(variable);
            for (Derivative<V> derivative : equationTermArray.termDerivatives.get(equationTermArray.termNumByTermElementNum.get(termElementNum))) {
                if (derivative.getVariable() == variable) {
                    return equationTermArray.evaluator.evalDer()[derivative.getLocalIndex()][termElementNum];
                }
            }
            throw new PowsyblException("Variable not found to calculate der");
        }

        @Override
        public double calculateSensi(DenseMatrix x, int column) {
            return equationTermArray.evaluator.calculateSensi(termElementNum, x, column);
        }

        @Override
        public EquationTerm<V, E> multiply(DoubleSupplier scalarSupplier) {
            throw new UnsupportedOperationException("Term multiply not supported for arrays");
        }

        @Override
        public EquationTerm<V, E> multiply(double scalar) {
            throw new UnsupportedOperationException("Term multiply not supported for arrays");
        }

        @Override
        public EquationTerm<V, E> minus() {
            throw new UnsupportedOperationException("Term minus not supported for arrays");
        }

        @Override
        public Equation<V, E> getEquation() {
            return equationTermArray.getEquationArray().getElement(elementNum);
        }

        @Override
        public void setEquation(Equation<V, E> equation) {
            elementNum = equation.getElementNum();
        }

        @Override
        public List<Variable<V>> getVariables() {
            return equationTermArray.termDerivatives
                    .get(equationTermArray.termNumByTermElementNum.get(termElementNum))
                    .stream()
                    .map(Derivative::getVariable)
                    .distinct()
                    .toList();
        }
    }

    public EquationTerm<V, E> getElement(int termElementNum) {
        return new EquationTermArrayElementImpl<>(this, termElementNum);
    }

    public boolean write(Writer writer, boolean writeInactiveTerms, int elementNum, boolean first) throws IOException {
        int iStart = termNumsConcatenatedStartIndices[elementNum];
        int iEnd = termNumsConcatenatedStartIndices[elementNum + 1];
        boolean isFirst = first;
        for (int i = iStart; i < iEnd; i++) {
            int termNum = termNumsConcatenated.getQuick(i);
            if (writeInactiveTerms || termActive.getQuick(termNum) == 1) {
                if (!isFirst) {
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
                isFirst = false;
            }
        }
        return iStart < iEnd;
    }
}
