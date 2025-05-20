/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.Matrix;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    private final E type;

    private final int elementCount;

    private final EquationSystem<V, E> equationSystem;

    private final boolean[] elementActive;

    private int firstColumn = -1;

    private int[] elementNumToColumn;

    private TIntIntMap columnToElementNum;

    private int length;

    private final List<EquationTermArray<V, E>> termArrays = new ArrayList<>();

    private EquationDerivativeVectorIndices[] equationDerivativeVectorIndices;
    private EquationDerivativeVector equationDerivativeVector;

    static class MatrixElementIndexes {
        private final TIntArrayList indexes = new TIntArrayList();

        private int get(int i) {
            return indexes.getQuick(i);
        }

        private void set(int i, int index) {
            if (index >= indexes.size()) {
                indexes.add(-1);
            }
            indexes.setQuick(i, index);
        }

        void reset() {
            indexes.clear();
        }
    }

    private final MatrixElementIndexes matrixElementIndexes = new MatrixElementIndexes();

    public EquationArray(E type, int elementCount, EquationSystem<V, E> equationSystem) {
        this.type = Objects.requireNonNull(type);
        this.elementCount = elementCount;
        this.equationSystem = Objects.requireNonNull(equationSystem);
        elementActive = new boolean[elementCount];
        Arrays.fill(elementActive, true);
        this.length = elementCount; // all activated initially
        this.equationDerivativeVectorIndices = new EquationDerivativeVectorIndices[elementCount];
    }

    public E getType() {
        return type;
    }

    public int getElementCount() {
        return elementCount;
    }

    public int[] getElementNumToColumn() {
        if (elementNumToColumn == null) {
            elementNumToColumn = new int[elementCount];
            int column = firstColumn;
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                if (elementActive[elementNum]) {
                    elementNumToColumn[elementNum] = column++;
                } else {
                    elementNumToColumn[elementNum] = -1;
                }
            }
        }
        return elementNumToColumn;
    }

    public int getElementNumToColumn(int elementNum) {
        return getElementNumToColumn()[elementNum];
    }

    public int getColumnToElementNum(int column) {
        if (columnToElementNum == null) {
            columnToElementNum = new TIntIntHashMap(elementCount);
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                int c = getElementNumToColumn(elementNum);
                if (c != -1) {
                    columnToElementNum.put(c, elementNum);
                }
            }
        }
        return columnToElementNum.get(column);
    }

    private void invalidateElementNumToColumn() {
        elementNumToColumn = null;
        columnToElementNum = null;
        matrixElementIndexes.reset();
    }

    public EquationSystem<V, E> getEquationSystem() {
        return equationSystem;
    }

    public int getFirstColumn() {
        return firstColumn;
    }

    public void setFirstColumn(int firstColumn) {
        this.firstColumn = firstColumn;
        invalidateElementNumToColumn();
    }

    public int getLength() {
        return length;
    }

    public boolean isElementActive(int elementNum) {
        return elementActive[elementNum];
    }

    public void setElementActive(int elementNum, boolean active) {
        if (active != this.elementActive[elementNum]) {
            this.elementActive[elementNum] = active;
            if (active) {
                length++;
            } else {
                length--;
            }
            invalidateElementNumToColumn();
            equationSystem.notifyEquationArrayChange(this, elementNum,
                    active ? EquationEventType.EQUATION_ACTIVATED : EquationEventType.EQUATION_DEACTIVATED);
        }
    }

    public List<EquationTermArray<V, E>> getTermArrays() {
        return termArrays;
    }

    public void addTermArray(EquationTermArray<V, E> termArray) {
        Objects.requireNonNull(termArray);
        termArray.setEquationArray(this);
        termArrays.add(termArray);
        invalidateEquationDerivativeVectors();
    }

    public Equation<V, E> getElement(int elementNum) {
        return new Equation<>() {
            @Override
            public E getType() {
                return EquationArray.this.getType();
            }

            @Override
            public int getElementNum() {
                return elementNum;
            }

            @Override
            public boolean isActive() {
                return isElementActive(elementNum);
            }

            @Override
            public void setActive(boolean active) {
                setElementActive(elementNum, active);
            }

            @Override
            public int getColumn() {
                return getElementNumToColumn(elementNum);
            }

            @Override
            public Equation<V, E> addTerm(EquationTerm<V, E> term) {
                var termImpl = (EquationTermArray.EquationTermArrayElementImpl<V, E>) term;
                termImpl.equationTermArray.addTerm(elementNum, termImpl.termElementNum);
                return this;
            }

            @Override
            public <T extends EquationTerm<V, E>> Equation<V, E> addTerms(List<T> terms) {
                for (T term : terms) {
                    addTerm(term);
                }
                return this;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T extends EquationTerm<V, E>> List<T> getTerms() {
                List<T> terms = new ArrayList<>();
                for (EquationTermArray<V, E> termArray : termArrays) {
                    var indices = termArray.getTermNumsConcatenatedIndices(elementNum);
                    var termNums = termArray.getTermNumsConcatenated();
                    for (int i = indices.iStart(); i < indices.iEnd(); i++) {
                        int termNum = termNums.getQuick(i);
                        int termElementNum = termArray.getTermElementNum(termNum);
                        terms.add((T) new EquationTermArray.EquationTermArrayElementImpl<>(termArray, termElementNum));
                    }
                }
                return terms;
            }

            @Override
            public double eval() {
                double value = 0;
                for (EquationTermArray<V, E> termArray : termArrays) {
                    var indices = termArray.getTermNumsConcatenatedIndices(elementNum);
                    var termNums = termArray.getTermNumsConcatenated();
                    for (int i = indices.iStart(); i < indices.iEnd(); i++) {
                        int termNum = termNums.getQuick(i);
                        // skip inactive terms
                        if (termArray.isTermActive(termNum)) {
                            int termElementNum = termArray.getTermElementNum(termNum);
                            value += termArray.eval(termElementNum);
                        }
                    }
                }
                return value;
            }
        };
    }

    public void eval(double[] values) {
        Arrays.fill(values, firstColumn, firstColumn + length, 0);
        for (EquationTermArray<V, E> termArray : termArrays) {
            double[] termValues = termArray.eval();

            var termNums = termArray.getTermNumsConcatenated();
            double[] values0 = new double[termNums.size()];
            for (int i = 0; i < termNums.size(); i++) {
                int termNum = termNums.getQuick(i);
                // skip inactive terms
                if (termArray.isTermActive(termNum)) {
                    int termElementNum = termArray.getTermElementNum(termNum);
                    values0[i] = termValues[termElementNum];
                }
            }

            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                // skip inactive equations
                if (!elementActive[elementNum]) {
                    continue;
                }
                int column = getElementNumToColumn(elementNum);
                var indices = termArray.getTermNumsConcatenatedIndices(elementNum);
                for (int i = indices.iStart(); i < indices.iEnd(); i++) {
                    values[column] += values0[i];
                }
            }
        }
    }

    public interface DerHandler<V extends Enum<V> & Quantity> {

        int onDer(int column, int row, double value, int matrixElementIndex);
    }

    private void updateEquationDerivativeVectors() {
        if (equationDerivativeVector == null) {
            List<EquationDerivativeElement<?>> allTerms = new ArrayList<>();
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                int iStart = allTerms.size();
                addEquationDerivativeVectorSortedTerms(elementNum, allTerms);
                equationDerivativeVectorIndices[elementNum] = new EquationDerivativeVectorIndices(iStart, allTerms.size());
            }
            equationDerivativeVector = new EquationDerivativeVector(allTerms);
        }
    }

    private void addEquationDerivativeVectorSortedTerms(int elementNum, List<EquationDerivativeElement<?>> allTerms) {
        // vectorize terms to evaluate
        List<EquationDerivativeElement<?>> terms = new ArrayList<>();
        for (int termArrayNum = 0; termArrayNum < termArrays.size(); termArrayNum++) {
            EquationTermArray<V, E> termArray = termArrays.get(termArrayNum);
            var indices = termArray.getTermNumsConcatenatedIndices(elementNum);
            var termNums = termArray.getTermNumsConcatenated();
            for (int i = indices.iStart(); i < indices.iEnd(); i++) {
                int termNum = termNums.getQuick(i);
                // for each term of each, add an entry for each derivative operation we need
                var termDerivatives = termArray.getTermDerivatives(termNum);
                for (Derivative<V> derivative : termDerivatives) {
                    terms.add(new EquationDerivativeElement<>(termArrayNum, termNum, derivative));
                }
            }
        }
        // sortByVariableRowAndVectorizedLocalNum
        terms.sort(Comparator.comparingInt(o -> o.derivative.getVariable().getRow()));

        allTerms.addAll(terms);
    }

    void invalidateEquationDerivativeVectors() {
        equationDerivativeVector = null;
        matrixElementIndexes.reset();
    }

    public void derInit(Matrix matrix) {
        Objects.requireNonNull(matrix);

        updateEquationDerivativeVectors();

        equationDerivativeVector.update(this);

        // calculate all derivative values
        // process column by column so equation by equation of the array
        int valueIndex = 0;
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            // skip inactive elements
            if (!elementActive[elementNum]) {
                continue;
            }

            int column = getElementNumToColumn(elementNum);
            // for each equation of the array we already have the list of terms to derive and its variable sorted
            // by variable row (required by solvers)

            // process term by term
            double value = 0;
            int prevRow = -1;
            var derivativeIndices = this.equationDerivativeVectorIndices[elementNum];
            int iStart = derivativeIndices.iStart();
            int iEnd = derivativeIndices.iEnd();
            for (int i = iStart; i < iEnd; i++) {

                // the derivative variable row
                int row = equationDerivativeVector.rows[i];

                // if an element at (row, column) is complete (we switch to another row), notify
                if (prevRow != -1 && row != prevRow) {
                    onDerInit(matrix, column, prevRow, value, valueIndex);
                    valueIndex++;
                    value = 0;
                }
                prevRow = row;

                value += equationDerivativeVector.values[i];
            }

            // remaining notif
            if (prevRow != -1) {
                onDerInit(matrix, column, prevRow, value, valueIndex);
                valueIndex++;
            }
        }
    }

    public void derUpdate(Matrix matrix) {
        Objects.requireNonNull(matrix);

        updateEquationDerivativeVectors();

        equationDerivativeVector.update(this);

        // calculate all derivative values
        // process column by column so equation by equation of the array
        int valueIndex = 0;
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            // skip inactive elements
            if (!elementActive[elementNum]) {
                continue;
            }

            // for each equation of the array we already have the list of terms to derive and its variable sorted
            // by variable row (required by solvers)

            // process term by term
            double value = 0;
            int prevRow = -1;
            var derivativeIndices = this.equationDerivativeVectorIndices[elementNum];
            int iStart = derivativeIndices.iStart();
            int iEnd = derivativeIndices.iEnd();
            for (int i = iStart; i < iEnd; i++) {

                // the derivative variable row
                int row = equationDerivativeVector.rows[i];

                // if an element at (row, column) is complete (we switch to another row), notify
                if (prevRow != -1 && row != prevRow) {
                    onDerUpdate(matrix, value, valueIndex);
                    valueIndex++;
                    value = 0;
                }
                prevRow = row;

                value += equationDerivativeVector.values[i];
            }

            // remaining notif
            if (prevRow != -1) {
                onDerUpdate(matrix, value, valueIndex);
                valueIndex++;
            }
        }
    }

    private void onDerInit(Matrix matrix, int column, int row, double value, int valueIndex) {
        int matrixElementIndex = matrix.addAndGetIndex(row, column, value);
        matrixElementIndexes.set(valueIndex, matrixElementIndex);
    }

    private void onDerUpdate(Matrix matrix, double value, int valueIndex) {
        matrix.addAtIndex(matrixElementIndexes.get(valueIndex), value);
    }

    public void write(Writer writer, boolean writeInactiveEquations) throws IOException {
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            if (writeInactiveEquations || isElementActive(elementNum)) {
                if (!isElementActive(elementNum)) {
                    writer.write("[ ");
                }
                writer.append(type.getSymbol())
                        .append("[")
                        .append(String.valueOf(elementNum))
                        .append("] = ");
                boolean first = true;
                for (EquationTermArray<V, E> termArray : termArrays) {
                    if (termArray.write(writer, writeInactiveEquations, elementNum, first)) {
                        first = false;
                    }
                }
                if (!isElementActive(elementNum)) {
                    writer.write(" ]");
                }
                writer.append(System.lineSeparator());
            }
        }
    }
}
