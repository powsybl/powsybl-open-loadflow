/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfElement;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    private final E type;

    private final int elementCount;

    private final EquationSystem<V, E> equationSystem;

    private final boolean[] elementActive;

    private final boolean[] hasSingleEquationTerms;

    private int firstColumn = -1;

    private int[] elementNumToColumn;

    private TIntIntMap columnToElementNum;

    private int length;

    // All terms that are in a vectorized view (in EquationTermArrays)
    private final List<EquationTermArray<V, E>> termArrays = new ArrayList<>();

    // All additional terms that are not vectorized (SingleEquationTerms) stored in different views
    private final Map<Integer, List<SingleEquationTerm<V, E>>> singleTermsByTermElementNum = new TreeMap<>();
    private final Map<Integer, AdditionalSingleTermsByEquation> singleTermsByEquationElementNum = new TreeMap<>();

    private EquationDerivativeVector equationDerivativeVector;
    private int[][] additionalTermsRows;
    private double[][] additionalTermsDers;

    private final class AdditionalSingleTermsByEquation {
        private final List<SingleEquationTerm<V, E>> terms = new ArrayList<>();
        private final TreeMap<Variable<V>, List<SingleEquationTerm<V, E>>> termsByVariable = new TreeMap<>();
        private int[] variableRows;
        private double[] derValues;

        void addSingleTerm(SingleEquationTerm<V, E> termImpl, Equation<V, E> equation) {
            terms.add(termImpl);
            singleTermsByTermElementNum.computeIfAbsent(termImpl.getElementNum(), k -> new ArrayList<>())
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

        void computeRows() {
            final int nbVariables = termsByVariable.size();
            variableRows = new int[nbVariables];
            derValues = new double[nbVariables];
            int i = 0;
            for (Map.Entry<Variable<V>, List<SingleEquationTerm<V, E>>> entry : termsByVariable.entrySet()) {
                int row = entry.getKey().getRow();
                if (row != -1) {
                    variableRows[i] = row;
                    i++;
                }
            }
            variableRows = Arrays.copyOf(variableRows, i);
            derValues = Arrays.copyOf(derValues, i);
        }

        void computeDer() {
            int i = 0;
            for (Map.Entry<Variable<V>, List<SingleEquationTerm<V, E>>> entry : termsByVariable.entrySet()) {
                int row = entry.getKey().getRow();
                if (row != -1) {
                    derValues[i] = 0.0;
                    for (var term : entry.getValue()) {
                        if (term.isActive()) {
                            derValues[i] += term.der(entry.getKey());
                        }
                    }
                    i++;
                }
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

    private final MatrixElementIndexes matrixElementIndexes = new MatrixElementIndexes();

    private int valueIndex;

    public EquationArray(E type, int elementCount, EquationSystem<V, E> equationSystem) {
        this.type = Objects.requireNonNull(type);
        this.elementCount = elementCount;
        this.equationSystem = Objects.requireNonNull(equationSystem);
        elementActive = new boolean[elementCount];
        Arrays.fill(elementActive, true);
        hasSingleEquationTerms = new boolean[elementCount];
        Arrays.fill(hasSingleEquationTerms, false);
        this.length = elementCount; // all activated initially
    }

    public E getType() {
        return type;
    }

    public int getElementCount() {
        return elementCount;
    }

    public List<SingleEquationTerm<V, E>> getSingleEquationTerms(int elementNum) {
        if (hasSingleEquationTerms[elementNum]) {
            return singleTermsByEquationElementNum.get(elementNum).terms;
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
            if (termArray.getElementType() == element.getType() && termArray.hasTermElement(element.getNum())) {
                termArray.setTermElementActive(element.getNum(), enable);
            }
        }
        if (singleTermsByTermElementNum.containsKey(element.getNum())) {
            for (var singleTerm : singleTermsByTermElementNum.get(element.getNum())) {
                if (singleTerm.getElementType() == element.getType()) {
                    singleTerm.setActive(enable);
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
                // Either the term is an additional single term that is related to specific equations (single term)
                } else if (term instanceof SingleEquationTerm<V, E> singleEquationTerm) {
                    if (singleEquationTerm.getEquation() != null) {
                        throw new PowsyblException("Equation term already added to another equation: "
                                + term.getEquation());
                    }
                    singleTermsByEquationElementNum.computeIfAbsent(elementNum, k -> new AdditionalSingleTermsByEquation())
                            .addSingleTerm(singleEquationTerm, this);
                    hasSingleEquationTerms[elementNum] = true;
                } else {
                    throw new IllegalArgumentException("Unsupported EquationTerm");
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
                if (singleTermsByEquationElementNum.containsKey(elementNum)) {
                    for (SingleEquationTerm<V, E> singleTerm : singleTermsByEquationElementNum.get(elementNum).terms) {
                        terms.add((T) singleTerm);
                    }
                }
                return terms;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T extends EquationTerm<V, E>> Map<Variable<V>, List<T>> getTermsByVariable() {
                Map<Variable<V>, List<T>> termsByVariable = new TreeMap<>();
                for (EquationTerm<V, E> term : this.getTerms()) {
                    for (Variable<V> v : term.getVariables()) {
                        termsByVariable.computeIfAbsent(v, k -> new ArrayList<>()).add((T) term);
                    }
                }
                return termsByVariable;
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

                if (hasSingleEquationTerms[elementNum]) {
                    for (SingleEquationTerm<V, E> singleTerm : singleTermsByEquationElementNum.get(elementNum).terms) {
                        if (singleTerm.isActive()) {
                            value += singleTerm.eval();
                        }
                    }
                }
                return value;
            }

            @Override
            public String toString() {
                return "EquationFromEquationArray(elementNum=" + elementNum +
                        ", type=" + type +
                        ", column=" + getColumn() + ")";
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
        for (Map.Entry<Integer, AdditionalSingleTermsByEquation> singleTermsEntry : singleTermsByEquationElementNum.entrySet()) {
            if (!elementActive[singleTermsEntry.getKey()]) {
                continue;
            }
            for (SingleEquationTerm<V, E> singleTerm : singleTermsEntry.getValue().terms) {
                if (singleTerm.isActive()) {
                    values[getElementNumToColumn(singleTermsEntry.getKey())] += singleTerm.eval();
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
                addEquationDerivativeVectorSortedTerms(elementNum, allTerms);
            }
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
                    terms.add(new EquationDerivativeElement<>(termArrayNum, termNum, elementNum, derivative));
                }
            }
        }
        // Terms are sorted with variable comparator
        terms.sort(Comparator.comparing(o -> o.derivative.getVariable()));

        allTerms.addAll(terms);
    }

    void invalidateEquationDerivativeVectors() {
        equationDerivativeVector = null;
        matrixElementIndexes.reset();
    }

    void updateAdditionalTerms(boolean recomputeRows) {
        if (recomputeRows) {
            additionalTermsRows = new int[elementCount][];
            additionalTermsDers = new double[elementCount][];
        }
        for (Map.Entry<Integer, AdditionalSingleTermsByEquation> entry : singleTermsByEquationElementNum.entrySet()) {
            int elementNum = entry.getKey();
            if (!elementActive[elementNum]) {
                continue;
            }
            AdditionalSingleTermsByEquation additionalTerms = entry.getValue();
            if (recomputeRows) {
                additionalTerms.computeRows();
                additionalTermsRows[elementNum] = additionalTerms.variableRows;
                additionalTermsDers[elementNum] = additionalTerms.derValues;
            }
            additionalTerms.computeDer();
        }
    }

    public void der(DerHandler handler, boolean recomputeRows) {
        Objects.requireNonNull(handler);

        updateEquationDerivativeVectors();
        equationDerivativeVector.update(recomputeRows);
        updateAdditionalTerms(recomputeRows);
        valueIndex = 0;
        // calculate all derivative values
        // process column by column so equation by equation of the array

        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            // skip inactive elements
            if (!elementActive[elementNum]) {
                continue;
            }

            int column = getElementNumToColumn(elementNum);
            // for each equation of the array we already have the list of terms to derive and its variable sorted
            // by variable row (required by solvers)
            double value;
            int row;

            if (!hasSingleEquationTerms[elementNum]) {
                // fast way without additional terms
                int cellIndexStart = equationDerivativeVector.cellIndexByEquationElementNum[elementNum];
                int cellIndexCount = equationDerivativeVector.nbCellsByEquationElementNum[elementNum];
                for (int cellIndex = cellIndexStart;
                     cellIndex < cellIndexStart + cellIndexCount;
                     cellIndex++) {
                    row = equationDerivativeVector.rowsByCell[cellIndex];
                    if (row != -1) {
                        value = equationDerivativeVector.valuesByCell[cellIndex];
                        onDer(handler, column, row, value);
                    }
                }
            } else {
                int[] computedRows = additionalTermsRows[elementNum].clone(); // cloning to erase each computed rows
                double[] computedDers = additionalTermsDers[elementNum];
                // Filling all the cells that have been computed in EquationDerivativeVector
                int cellIndexStart = equationDerivativeVector.cellIndexByEquationElementNum[elementNum];
                int cellIndexCount = equationDerivativeVector.nbCellsByEquationElementNum[elementNum];
                for (int cellIndex = cellIndexStart;
                     cellIndex < cellIndexStart + cellIndexCount;
                     cellIndex++) {
                    row = equationDerivativeVector.rowsByCell[cellIndex];
                    if (row != -1) {
                        value = equationDerivativeVector.valuesByCell[cellIndex];
                        for (int i = 0 ; i < computedRows.length ; i++) {
                            if (computedRows[i] == row) {
                                value += computedDers[i];
                                computedRows[i] = -1;
                            }
                        }
                        onDer(handler, column, row, value);
                    }
                }
                // Filling remaining cells that were not in the EquationDerivativeVector
                for (int i = 0 ; i < computedRows.length ; i++) {
                    if (computedRows[i] != -1) {
                        onDer(handler, column, computedRows[i], computedDers[i]);
                    }
                }
            }
        }
    }

    private void onDer(DerHandler handler, int column, int row, double value) {
        int matrixElementIndex = handler.onDer(column, row, value, matrixElementIndexes.get(valueIndex));
        matrixElementIndexes.set(valueIndex, matrixElementIndex);
        valueIndex++;
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
                if (hasSingleEquationTerms[elementNum]) {
                    List<SingleEquationTerm<V, E>> activeTerms = writeInactiveEquations ?
                        getSingleEquationTerms(elementNum) :
                        getSingleEquationTerms(elementNum).stream().filter(SingleEquationTerm::isActive).toList();
                    for (SingleEquationTerm<V, E> term : activeTerms) {
                        if (!first) {
                            writer.append(" + ");
                        }
                        if (!term.isActive()) {
                            writer.write("[ ");
                        }
                        term.write(writer);
                        if (!term.isActive()) {
                            writer.write(" ]");
                        }
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
