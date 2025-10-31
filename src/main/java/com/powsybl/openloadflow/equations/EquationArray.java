/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfElement;
import gnu.trove.iterator.TIntDoubleIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
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

    private final boolean[] hasAtomicTerms;

    private int firstColumn = -1;

    private int[] elementNumToColumn;

    private TIntIntMap columnToElementNum;

    private int length;

    // All terms that are in a vectorized view (in EquationTermArrays)
    private final List<EquationTermArray<V, E>> termArrays = new ArrayList<>();

    // All additional terms that are not vectorized (AtomicEquationTerms) stored in different views
    private final Map<Integer, List<AtomicEquationTerm<V, E>>> atomicTermsByTermElementNum = new TreeMap<>();
    private final Map<Integer, AdditionalAtomicTermsByEquation> atomicTermsByEquationElementNum = new TreeMap<>();

    private final int[] equationDerivativeVectorStartIndices;
    private EquationDerivativeVector equationDerivativeVector;

    private final class AdditionalAtomicTermsByEquation {
        private final List<AtomicEquationTerm<V, E>> terms = new ArrayList<>();
        private TreeMap<Variable<V>, List<AtomicEquationTerm<V, E>>> termsByVariable = new TreeMap<>();

        void addAtomicTerm(AtomicEquationTerm<V, E> termImpl, Equation<V, E> equation) {
            terms.add(termImpl);
            atomicTermsByTermElementNum.computeIfAbsent(termImpl.getElementNum(), k -> new ArrayList<>())
                    .add(termImpl);
            for (Variable<V> v : termImpl.getVariables()) {
                termsByVariable.computeIfAbsent(v, k -> new ArrayList<>())
                        .add(termImpl);
            }
            termImpl.setEquation(equation);
            equationSystem.addEquationTerm(termImpl);
            matrixElementIndexes.reset();
            equationSystem.notifyEquationTermChange(termImpl, EquationTermEventType.EQUATION_TERM_ADDED);
            if (termImpl.hasRhs()) {
                throw new UnsupportedOperationException("Rhs not supported yet");
            }
        }
    }

    static class MatrixElementIndexes {
        private final TIntArrayList indexes = new TIntArrayList();

        private int get(int i) {
            if (i >= indexes.size()) {
                indexes.add(-1);
            }
            return indexes.getQuick(i);
        }

        private void set(int i, int index) {
            indexes.setQuick(i, index);
        }

        void reset() {
            indexes.clear();
        }
    }

    private MatrixElementIndexes matrixElementIndexes = new MatrixElementIndexes();

    public EquationArray(E type, int elementCount, EquationSystem<V, E> equationSystem) {
        this.type = Objects.requireNonNull(type);
        this.elementCount = elementCount;
        this.equationSystem = Objects.requireNonNull(equationSystem);
        elementActive = new boolean[elementCount];
        Arrays.fill(elementActive, true);
        hasAtomicTerms = new boolean[elementCount];
        Arrays.fill(hasAtomicTerms, false);
        this.length = elementCount; // all activated initially
        this.equationDerivativeVectorStartIndices = new int[elementCount + 1];
    }

    public E getType() {
        return type;
    }

    public int getElementCount() {
        return elementCount;
    }

    public List<AtomicEquationTerm<V, E>> getAtomicTerms(int elementNum) {
        if (hasAtomicTerms[elementNum]) {
            return atomicTermsByEquationElementNum.get(elementNum).terms;
        }
        return Collections.emptyList();
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

    public void updateElementEquation(LfElement element, boolean enable) {
        if (getType().getElementType() == element.getType()) {
            setElementActive(element.getNum(), enable);
        }
        for (var termArray : getTermArrays()) {
            if (termArray.getElementType() == element.getType()) {
                if (termArray.hasTermElement(element.getNum())) {
                    termArray.setTermElementActive(element.getNum(), enable);
                }
            }
        }
        if (atomicTermsByTermElementNum.containsKey(element.getNum())) {
            for (var atomicTerm : atomicTermsByTermElementNum.get(element.getNum())) {
                if (atomicTerm.getElementType() == element.getType()) {
                    atomicTerm.setActive(enable);
                }
            }
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
                // Either the term is in an EquationTermArray (vectorized term)
                if (term instanceof EquationTermArray.EquationTermArrayElementImpl<V, E> termArrayElement) {
                    termArrayElement.setEquation(this);
                    termArrayElement.equationTermArray.addTerm(elementNum, termArrayElement.termElementNum);
                // Either the term is an additional atomic term that is related to specific equations (atomic term)
                } else if (term instanceof AtomicEquationTerm<V, E> atomicEquationTerm) {
                    if (atomicEquationTerm.getEquation() != null) {
                        throw new PowsyblException("Equation term already added to another equation: "
                                + term.getEquation());
                    }
                    atomicTermsByEquationElementNum.computeIfAbsent(elementNum, k -> new AdditionalAtomicTermsByEquation())
                            .addAtomicTerm(atomicEquationTerm, this);
                    hasAtomicTerms[elementNum] = true;
                }
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
                    int[] termNumsConcatenatedStartIndices = termArray.getTermNumsConcatenatedStartIndices();
                    int iStart = termNumsConcatenatedStartIndices[elementNum];
                    int iEnd = termNumsConcatenatedStartIndices[elementNum + 1];
                    var termNums = termArray.getTermNumsConcatenated();
                    for (int i = iStart; i < iEnd; i++) {
                        int termNum = termNums.getQuick(i);
                        int termElementNum = termArray.getTermElementNum(termNum);
                        terms.add((T) new EquationTermArray.EquationTermArrayElementImpl<>(termArray, termElementNum));
                    }
                }
                if (atomicTermsByEquationElementNum.containsKey(elementNum)) {
                    for (EquationTerm<V, E> atomicTerm : atomicTermsByEquationElementNum.get(elementNum).terms) {
                        terms.add((T) atomicTerm);
                    }
                }
                return terms;
            }

            @Override
            public EquationSystem<V, E> getEquationSystem() {
                return equationSystem;
            }

            @Override
            public double eval() {
                double value = 0;
                for (EquationTermArray<V, E> termArray : termArrays) {
                    int[] termNumsConcatenatedStartIndices = termArray.getTermNumsConcatenatedStartIndices();
                    int iStart = termNumsConcatenatedStartIndices[elementNum];
                    int iEnd = termNumsConcatenatedStartIndices[elementNum + 1];
                    var termNums = termArray.getTermNumsConcatenated();
                    for (int i = iStart; i < iEnd; i++) {
                        int termNum = termNums.getQuick(i);
                        // skip inactive terms
                        if (termArray.isTermActive(termNum)) {
                            int termElementNum = termArray.getTermElementNum(termNum);
                            value += termArray.eval(termElementNum);
                        }
                    }
                }

                if (hasAtomicTerms[elementNum]) {
                    for (AtomicEquationTerm<V, E> atomicTerm : atomicTermsByEquationElementNum.get(elementNum).terms) {
                        if (atomicTerm.isActive()) {
                            value += atomicTerm.eval();
                        }
                    }
                }
                return value;
            }
        };
    }

    public void eval(double[] values) {
        for (EquationTermArray<V, E> termArray : termArrays) {
            double[] termValues = termArray.eval();
            int[] termNumsConcatenatedStartIndices = termArray.getTermNumsConcatenatedStartIndices();
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                // skip inactive equations
                if (!elementActive[elementNum]) {
                    continue;
                }
                int column = getElementNumToColumn(elementNum);
                var termNums = termArray.getTermNumsConcatenated();
                int iStart = termNumsConcatenatedStartIndices[elementNum];
                int iEnd = termNumsConcatenatedStartIndices[elementNum + 1];
                for (int i = iStart; i < iEnd; i++) {
                    int termNum = termNums.getQuick(i);
                    // skip inactive terms
                    if (termArray.isTermActive(termNum)) {
                        int termElementNum = termArray.getTermElementNum(termNum);
                        values[column] += termValues[termElementNum];
                    }
                }
            }
        }
        for (Map.Entry<Integer, AdditionalAtomicTermsByEquation> atomicTermsEntry : atomicTermsByEquationElementNum.entrySet()) {
            if (!elementActive[atomicTermsEntry.getKey()]) {
                continue;
            }
            for (AtomicEquationTerm<V, E> atomicTerm : atomicTermsEntry.getValue().terms) {
                if (atomicTerm.isActive()) {
                    values[getElementNumToColumn(atomicTermsEntry.getKey())] += atomicTerm.eval();
                }
            }
        }
    }

    public interface DerHandler {

        int onDer(int column, int row, double value, int matrixElementIndex);
    }

    private void updateEquationDerivativeVectors() {
        if (equationDerivativeVector == null) {
            List<EquationDerivativeElement<?>> allTerms = new ArrayList<>();
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                equationDerivativeVectorStartIndices[elementNum] = allTerms.size();
                addEquationDerivativeVectorSortedTerms(elementNum, allTerms);
            }
            equationDerivativeVectorStartIndices[elementCount] = allTerms.size();
            equationDerivativeVector = new EquationDerivativeVector(allTerms, this);
        }
    }

    private void addEquationDerivativeVectorSortedTerms(int elementNum, List<EquationDerivativeElement<?>> allTerms) {
        // vectorize terms to evaluate
        List<EquationDerivativeElement<?>> terms = new ArrayList<>();
        for (int termArrayNum = 0; termArrayNum < termArrays.size(); termArrayNum++) {
            EquationTermArray<V, E> termArray = termArrays.get(termArrayNum);
            int[] termNumsConcatenatedStartIndices = termArray.getTermNumsConcatenatedStartIndices();
            int iStart = termNumsConcatenatedStartIndices[elementNum];
            int iEnd = termNumsConcatenatedStartIndices[elementNum + 1];
            var termNums = termArray.getTermNumsConcatenated();
            for (int i = iStart; i < iEnd; i++) {
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

    public void der(DerHandler handler) {
        Objects.requireNonNull(handler);

        updateEquationDerivativeVectors();
        equationDerivativeVector.update(this);

        // calculate all derivative values
        // process column by column so equation by equation of the array
        int valueIndex = 0;
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            List<Integer> computedRows = new ArrayList<>();
            // skip inactive elements
            if (!elementActive[elementNum]) {
                continue;
            }

            int column = getElementNumToColumn(elementNum);
            // for each equation of the array we already have the list of terms to derive and its variable sorted
            // by variable row (required by solvers)

            // process term by term
            double value = 0;
            int row = 0;

            AdditionalAtomicTermsByEquation additionalTerms = null;
            if (hasAtomicTerms[elementNum]) {
                additionalTerms = atomicTermsByEquationElementNum.get(elementNum);
            }

            int prevRow = -1;
            int iStart = this.equationDerivativeVectorStartIndices[elementNum];
            int iEnd = this.equationDerivativeVectorStartIndices[elementNum + 1];
            for (int i = iStart; i < iEnd; i++) {

                // the derivative variable row
                row = equationDerivativeVector.rows[i];

                // if an element at (row, column) is complete (we switch to another row), notify
                if (prevRow != -1 && row != prevRow) {
                    if (additionalTerms != null) {
                        computedRows.add(prevRow);
                        for (Variable<V> v : additionalTerms.termsByVariable.keySet()) {
                            if (v.getRow() == prevRow) {
                                for (var term : additionalTerms.termsByVariable.get(v)) {
                                    if (term.isActive()) {
                                        value += term.der(v);
                                    }
                                }
                            }
                        }
                    }
                    onDer(handler, column, prevRow, value, valueIndex);
                    valueIndex++;
                    value = 0;
                }
                prevRow = row;
                value += equationDerivativeVector.values[i];
            }

            // remaining notif
            if (prevRow != -1) {
                if (additionalTerms != null) {
                    computedRows.add(prevRow);
                    for (Variable<V> v : additionalTerms.termsByVariable.keySet()) {
                        if (v.getRow() == prevRow) {
                            for (var term : additionalTerms.termsByVariable.get(v)) {
                                if (term.isActive()) {
                                    value += term.der(v);
                                }
                            }
                        }
                    }
                }
                onDer(handler, column, prevRow, value, valueIndex);
                valueIndex++;
            }

            if (additionalTerms != null) {
                for (Variable<V> v : additionalTerms.termsByVariable.keySet()) {
                    if (v.getRow() != -1 && !computedRows.contains(v.getRow())) {
                        value = 0;
                        for (var term : additionalTerms.termsByVariable.get(v)) {
                            if (term.isActive()) {
                                value += term.der(v);
                            }
                        }
                        onDer(handler, column, v.getRow(), value, valueIndex);
                        valueIndex++;
                    }
                }
            }
        }
    }

    private void onDer(DerHandler handler, int column, int row, double value, int valueIndex) {
        int matrixElementIndex = handler.onDer(column, row, value, matrixElementIndexes.get(valueIndex));
        matrixElementIndexes.set(valueIndex, matrixElementIndex);
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
