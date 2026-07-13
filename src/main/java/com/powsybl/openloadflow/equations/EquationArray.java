/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.util.Evaluable;
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

    private final int[] equationDerivativeVectorStartIndices;
    private EquationDerivativeVector equationDerivativeVector;

    private final class AdditionalSingleTermsByEquation {
        private final List<SingleEquationTerm<V, E>> terms = new ArrayList<>();
        private final TreeMap<Variable<V>, List<SingleEquationTerm<V, E>>> termsByVariable = new TreeMap<>();

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
            invalidateAdditionalTermsVector();
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

    private final MatrixElementIndexes matrixElementIndexes = new MatrixElementIndexes();

    /**
     * An alternative body of an element equation of the array, alternating with the default body (the array terms
     * plus untagged single terms, e.g. a bus power balance). See {@link AlternativeEquation} for the concept: the
     * matrix structure is the union of all alternatives, switching preserves it.
     */
    public final class ElementAlternative implements Evaluable {

        private final E type;

        private final int elementNum; // element for target evaluation (e.g. remotely controlled bus)

        private final List<SingleEquationTerm<V, E>> terms = new ArrayList<>();

        // single variable body with coefficient one (x[variable]), fully vectorized: no term object, the
        // derivative is constant one and the evaluation a state vector read; null for term-based bodies
        private final Variable<V> variable;

        private ElementAlternative(E type, int elementNum) {
            this(type, elementNum, null);
        }

        private ElementAlternative(E type, int elementNum, Variable<V> variable) {
            this.type = Objects.requireNonNull(type);
            this.elementNum = elementNum;
            this.variable = variable;
        }

        public Variable<V> getVariable() {
            return variable;
        }

        public E getType() {
            return type;
        }

        public int getElementNum() {
            return elementNum;
        }

        public List<SingleEquationTerm<V, E>> getTerms() {
            return Collections.unmodifiableList(terms);
        }

        @Override
        public double eval() {
            if (variable != null) {
                return equationSystem.getStateVector().get()[variable.getRow()];
            }
            double value = 0;
            for (SingleEquationTerm<V, E> term : terms) {
                if (term.isActive()) {
                    value += term.eval();
                }
            }
            return value;
        }
    }

    /**
     * The default body of an element equation having alternatives (the array terms plus untagged single terms, e.g.
     * a bus power balance), evaluable whatever the active alternative is with the usual term activity semantic.
     */
    public final class ElementBalance implements Evaluable {

        private final int elementNum;

        private ElementBalance(int elementNum) {
            this.elementNum = elementNum;
        }

        public EquationArray<V, E> getEquationArray() {
            return EquationArray.this;
        }

        public E getType() {
            return type;
        }

        public int getElementNum() {
            return elementNum;
        }

        @Override
        public double eval() {
            double value = 0;
            for (EquationTermArray<V, E> termArray : termArrays) {
                int[] termNumsConcatenatedStartIndices = termArray.getTermNumsConcatenatedStartIndices();
                var termNums = termArray.getTermNumsConcatenated();
                for (int i = termNumsConcatenatedStartIndices[elementNum]; i < termNumsConcatenatedStartIndices[elementNum + 1]; i++) {
                    int termNum = termNums.getQuick(i);
                    if (termArray.isTermActive(termNum)) {
                        value += termArray.eval(termArray.getTermElementNum(termNum));
                    }
                }
            }
            if (hasSingleEquationTerms[elementNum]) {
                for (SingleEquationTerm<V, E> singleTerm : singleTermsByEquationElementNum.get(elementNum).terms) {
                    if (singleTerm.isActive() && singleTermAlternativeNums.get(singleTerm) == null) {
                        value += singleTerm.eval();
                    }
                }
            }
            return value;
        }
    }

    // per element alternatives (empty list when the element has none); the default body (array terms + untagged
    // single terms) is denoted by alternative num -1
    private final Map<Integer, List<ElementAlternative>> elementAlternatives = new TreeMap<>();

    private final Map<SingleEquationTerm<V, E>, Integer> singleTermAlternativeNums = new IdentityHashMap<>();

    private int[] elementActiveAlternativeNum;

    private int[] elementRequestedAlternativeNum;

    private int[] elementDisabledAlternativeNum;

    private boolean[] elementDisabled;

    private void ensureAlternativeArrays() {
        if (elementActiveAlternativeNum == null) {
            elementActiveAlternativeNum = new int[elementCount];
            Arrays.fill(elementActiveAlternativeNum, -1);
            elementRequestedAlternativeNum = new int[elementCount];
            Arrays.fill(elementRequestedAlternativeNum, -1);
            elementDisabledAlternativeNum = new int[elementCount];
            Arrays.fill(elementDisabledAlternativeNum, -2);
            elementDisabled = new boolean[elementCount];
        }
    }

    public boolean hasElementAlternatives(int elementNum) {
        return elementActiveAlternativeNum != null && !getElementAlternatives(elementNum).isEmpty();
    }

    private List<ElementAlternative> getElementAlternatives(int elementNum) {
        return elementAlternatives.getOrDefault(elementNum, Collections.emptyList());
    }

    public int addElementAlternative(int elementNum, E alternativeType, int alternativeElementNum,
                                     List<? extends EquationTerm<V, E>> terms) {
        ensureAlternativeArrays();
        ElementAlternative alternative = new ElementAlternative(alternativeType, alternativeElementNum);
        List<ElementAlternative> alternatives = elementAlternatives.computeIfAbsent(elementNum, k -> new ArrayList<>());
        int alternativeNum = alternatives.size();
        alternatives.add(alternative);
        Equation<V, E> element = getElement(elementNum);
        for (EquationTerm<V, E> term : terms) {
            element.addTerm(term);
            SingleEquationTerm<V, E> singleTerm = (SingleEquationTerm<V, E>) term;
            alternative.terms.add(singleTerm);
            singleTermAlternativeNums.put(singleTerm, alternativeNum);
        }
        return alternativeNum;
    }

    /**
     * Add a vectorized alternative whose body is a single variable with coefficient one (e.g. a voltage or phase
     * target): no term object is created, the body is stored as flat data and the alternative variable is
     * permanently registered in the index (the union matrix structure covers all alternatives by design).
     */
    public int addElementVariableAlternative(int elementNum, E alternativeType, int alternativeElementNum,
                                             Variable<V> variable) {
        ensureAlternativeArrays();
        ElementAlternative alternative = new ElementAlternative(alternativeType, alternativeElementNum,
                Objects.requireNonNull(variable));
        List<ElementAlternative> alternatives = elementAlternatives.computeIfAbsent(elementNum, k -> new ArrayList<>());
        int alternativeNum = alternatives.size();
        alternatives.add(alternative);
        equationSystem.getIndex().addAlternativeVariable(variable);
        invalidateAdditionalTermsVector();
        matrixElementIndexes.reset();
        return alternativeNum;
    }

    /**
     * Add the alternative to activate when the element of this equation is disabled, see
     * {@link AlternativeEquation#addDisabledAlternative}.
     */
    public int addElementDisabledAlternative(int elementNum, E alternativeType, List<? extends EquationTerm<V, E>> terms) {
        if (hasElementDisabledAlternative(elementNum)) {
            throw new PowsyblException("Element " + elementNum + " of " + type + " equation array already has a disabled alternative");
        }
        int alternativeNum = addElementAlternative(elementNum, alternativeType, elementNum, terms);
        elementDisabledAlternativeNum[elementNum] = alternativeNum;
        return alternativeNum;
    }

    /**
     * Vectorized variant of {@link #addElementDisabledAlternative}: the trivial body is the given variable with
     * coefficient one (e.g. v = target or phi = target), stored as flat data without any term object.
     */
    public int addElementVariableDisabledAlternative(int elementNum, E alternativeType, Variable<V> variable) {
        if (hasElementDisabledAlternative(elementNum)) {
            throw new PowsyblException("Element " + elementNum + " of " + type + " equation array already has a disabled alternative");
        }
        int alternativeNum = addElementVariableAlternative(elementNum, alternativeType, elementNum, variable);
        elementDisabledAlternativeNum[elementNum] = alternativeNum;
        return alternativeNum;
    }

    public boolean hasElementDisabledAlternative(int elementNum) {
        return elementDisabledAlternativeNum != null && elementDisabledAlternativeNum[elementNum] != -2;
    }

    public boolean hasElementAlternative(int elementNum, E alternativeType) {
        for (ElementAlternative alternative : getElementAlternatives(elementNum)) {
            if (alternative.type == alternativeType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Switch the active body of the element equation: either an alternative type, or the array type for the default
     * body. The matrix structure is preserved, only values and targets are invalidated. While the element is
     * disabled, the switch is recorded and applied at re-enabling.
     */
    public void setElementActiveAlternative(int elementNum, E alternativeType) {
        int alternativeNum;
        if (alternativeType == type) {
            alternativeNum = -1; // the default body
        } else {
            alternativeNum = -2;
            List<ElementAlternative> alternatives = getElementAlternatives(elementNum);
            for (int i = 0; i < alternatives.size(); i++) {
                if (alternatives.get(i).type == alternativeType) {
                    alternativeNum = i;
                    break;
                }
            }
            if (alternativeNum == -2) {
                throw new PowsyblException("No alternative of type " + alternativeType + " for element " + elementNum
                        + " of " + type + " equation array");
            }
        }
        if (elementDisabled[elementNum]) {
            // remember the alternative to restore at re-enabling
            elementRequestedAlternativeNum[elementNum] = alternativeNum;
            return;
        }
        doSetElementActiveAlternative(elementNum, alternativeNum);
    }

    private void doSetElementActiveAlternative(int elementNum, int alternativeNum) {
        if (elementActiveAlternativeNum[elementNum] != alternativeNum) {
            elementActiveAlternativeNum[elementNum] = alternativeNum;
            equationSystem.notifyEquationArrayChange(this, elementNum, EquationEventType.EQUATION_ALTERNATIVE_CHANGED);
        }
    }

    /**
     * See {@link AlternativeEquation#setElementDisabled(boolean)}: the element equation stays active, disabling is
     * fully represented by the switch to the disabled alternative, preserving the matrix structure.
     */
    public void setElementDisabled(int elementNum, boolean disabled) {
        if (!hasElementDisabledAlternative(elementNum)) {
            throw new PowsyblException("Element " + elementNum + " of " + type + " equation array has no disabled alternative");
        }
        if (elementDisabled[elementNum] != disabled) {
            elementDisabled[elementNum] = disabled;
            if (disabled) {
                elementRequestedAlternativeNum[elementNum] = elementActiveAlternativeNum[elementNum];
                doSetElementActiveAlternative(elementNum, elementDisabledAlternativeNum[elementNum]);
            } else {
                doSetElementActiveAlternative(elementNum, elementRequestedAlternativeNum[elementNum]);
            }
        }
    }

    public E getElementActiveType(int elementNum) {
        int alternativeNum = elementActiveAlternativeNum == null ? -1 : elementActiveAlternativeNum[elementNum];
        return alternativeNum == -1 ? type : getElementAlternatives(elementNum).get(alternativeNum).type;
    }

    public int getElementActiveElementNum(int elementNum) {
        int alternativeNum = elementActiveAlternativeNum == null ? -1 : elementActiveAlternativeNum[elementNum];
        return alternativeNum == -1 ? elementNum : getElementAlternatives(elementNum).get(alternativeNum).elementNum;
    }

    public ElementBalance getElementBalance(int elementNum) {
        return new ElementBalance(elementNum);
    }

    /**
     * Whether the default body (array terms + untagged single terms) of the element equation contributes to the
     * matrix and to the equation evaluation (used by the vectorized derivative computation).
     */
    public boolean isElementBalanceContributing(int elementNum) {
        return elementActiveAlternativeNum == null || elementActiveAlternativeNum[elementNum] == -1;
    }

    /**
     * Whether the given single term contributes: untagged terms follow the default body, tagged terms contribute
     * when their alternative is the active one (the disabled alternative whatever the term activity is, as disabling
     * an element deactivates all its terms but the trivial equation must keep holding its variables).
     */
    private boolean isSingleTermContributing(SingleEquationTerm<V, E> term, int elementNum) {
        Integer alternativeNum = singleTermAlternativeNums.get(term);
        if (alternativeNum == null) {
            return isElementBalanceContributing(elementNum) && term.isActive();
        }
        if (alternativeNum != elementActiveAlternativeNum[elementNum]) {
            return false;
        }
        return alternativeNum == elementDisabledAlternativeNum[elementNum] || term.isActive();
    }

    public EquationArray(E type, int elementCount, EquationSystem<V, E> equationSystem) {
        this.type = Objects.requireNonNull(type);
        this.elementCount = elementCount;
        this.equationSystem = Objects.requireNonNull(equationSystem);
        elementActive = new boolean[elementCount];
        Arrays.fill(elementActive, true);
        hasSingleEquationTerms = new boolean[elementCount];
        Arrays.fill(hasSingleEquationTerms, false);
        this.length = elementCount; // all activated initially
        this.equationDerivativeVectorStartIndices = new int[elementCount + 1];
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

    public List<SingleEquationTerm<V, E>> getAllSingleEquationTerms() {
        List<SingleEquationTerm<V, E>> terms = new ArrayList<>();
        for (AdditionalSingleTermsByEquation additionalSingleTerms : singleTermsByEquationElementNum.values()) {
            terms.addAll(additionalSingleTerms.terms);
        }
        return terms;
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
            if (hasElementDisabledAlternative(element.getNum())) {
                // the element equation stays active, disabling is represented by switching to the disabled (trivial)
                // alternative, preserving the matrix structure
                setElementDisabled(element.getNum(), !enable);
            } else {
                setElementActive(element.getNum(), enable);
            }
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
                if (isElementBalanceContributing(elementNum)) {
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
                }

                if (hasSingleEquationTerms[elementNum]) {
                    for (SingleEquationTerm<V, E> singleTerm : singleTermsByEquationElementNum.get(elementNum).terms) {
                        if (isSingleTermContributing(singleTerm, elementNum)) {
                            value += singleTerm.eval();
                        }
                    }
                }
                return value;
            }

            @Override
            public E getActiveType() {
                return getElementActiveType(elementNum);
            }

            @Override
            public int getActiveElementNum() {
                return getElementActiveElementNum(elementNum);
            }

            @Override
            public boolean keepsInactiveTermVariables() {
                // elements with alternatives keep the variables of all alternatives registered so that switching
                // never changes the matrix structure
                return hasElementAlternatives(elementNum);
            }

            @Override
            public boolean hasDisabledAlternative() {
                return hasElementDisabledAlternative(elementNum);
            }

            @Override
            public void setElementDisabled(boolean disabled) {
                EquationArray.this.setElementDisabled(elementNum, disabled);
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
                // skip inactive equations and equations whose active alternative is not the default body
                if (!elementActive[elementNum] || !isElementBalanceContributing(elementNum)) {
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
        AdditionalTermsVector<V, E> atv = getAdditionalTermsVector();
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            int termStart = atv.elementUniqueTermStart[elementNum];
            int termEnd = atv.elementUniqueTermStart[elementNum + 1];
            if (termStart == termEnd || !elementActive[elementNum]) {
                continue;
            }
            boolean balanceContributing = isElementBalanceContributing(elementNum);
            if (balanceContributing && atv.elementUntaggedTermCount[elementNum] == 0) {
                // the default body is active and the element has no untagged term: no additional term contributes
                continue;
            }
            int column = getElementNumToColumn(elementNum);
            int activeAlternativeNum = elementActiveAlternativeNum == null ? -1 : elementActiveAlternativeNum[elementNum];
            int disabledAlternativeNum = elementDisabledAlternativeNum == null ? -2 : elementDisabledAlternativeNum[elementNum];
            double[] x = equationSystem.getStateVector().get();
            for (int t = termStart; t < termEnd; t++) {
                SingleEquationTerm<V, E> singleTerm = atv.uniqueTerms.get(t);
                int alternativeNum = atv.uniqueTermAlternativeNums[t];
                if (singleTerm == null) {
                    // vectorized variable alternative body: x[variable] when its alternative is the active one
                    if (alternativeNum == activeAlternativeNum) {
                        values[column] += x[atv.uniqueTermVariables.get(t).getRow()];
                    }
                    continue;
                }
                boolean contributing = alternativeNum == -1
                        ? balanceContributing && singleTerm.isActive()
                        : alternativeNum == activeAlternativeNum && (alternativeNum == disabledAlternativeNum || singleTerm.isActive());
                if (contributing) {
                    values[column] += singleTerm.eval();
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
        // Terms are sorted with variable comparator
        terms.sort(Comparator.comparing(o -> o.derivative.getVariable()));

        allTerms.addAll(terms);
    }

    void invalidateEquationDerivativeVectors() {
        equationDerivativeVector = null;
        matrixElementIndexes.reset();
        partialDerReady = false;
    }

    /**
     * Flat view of the additional single terms of all elements (mirroring what {@link EquationDerivativeVector} does
     * for array terms): per element a range of variable groups, per group a variable and a range of terms with their
     * alternative nums baked in, so that the evaluation and derivation hot loops are pure array walks.
     */
    private static final class AdditionalTermsVector<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        private final int[] elementStart; // element num -> first variable group

        private final List<Variable<V>> groupVariables;

        private final int[] groupTermStart; // variable group -> first term

        // a null term denotes a vectorized variable alternative body (coefficient one): derivative is constant
        // one with respect to the group variable, evaluation is a state vector read
        private final List<SingleEquationTerm<V, E>> terms;

        private final int[] termAlternativeNums; // -1 for terms common to all alternatives (default body)

        // evaluation view: each term once per element (a term appears in one group per variable it depends on)
        private final int[] elementUniqueTermStart;

        private final List<SingleEquationTerm<V, E>> uniqueTerms;

        private final List<Variable<V>> uniqueTermVariables; // only set (non null) for variable alternative bodies

        private final int[] uniqueTermAlternativeNums;

        // per element, the count of terms common to all alternatives (untagged): when zero and the default body is
        // the active one, no additional term of the element can contribute (the dominant case: plain buses whose
        // only additional terms are their trivial disabled alternatives), allowing fast paths
        private final int[] elementUntaggedTermCount;

        private AdditionalTermsVector(int[] elementStart, List<Variable<V>> groupVariables, int[] groupTermStart,
                                      List<SingleEquationTerm<V, E>> terms, int[] termAlternativeNums,
                                      int[] elementUniqueTermStart, List<SingleEquationTerm<V, E>> uniqueTerms,
                                      List<Variable<V>> uniqueTermVariables, int[] uniqueTermAlternativeNums,
                                      int[] elementUntaggedTermCount) {
            this.elementStart = elementStart;
            this.groupVariables = groupVariables;
            this.groupTermStart = groupTermStart;
            this.terms = terms;
            this.termAlternativeNums = termAlternativeNums;
            this.elementUniqueTermStart = elementUniqueTermStart;
            this.uniqueTerms = uniqueTerms;
            this.uniqueTermVariables = uniqueTermVariables;
            this.uniqueTermAlternativeNums = uniqueTermAlternativeNums;
            this.elementUntaggedTermCount = elementUntaggedTermCount;
        }
    }

    private AdditionalTermsVector<V, E> additionalTermsVector;

    private void invalidateAdditionalTermsVector() {
        additionalTermsVector = null;
        partialDerReady = false;
    }

    private AdditionalTermsVector<V, E> getAdditionalTermsVector() {
        if (additionalTermsVector == null) {
            int[] elementStart = new int[elementCount + 1];
            List<Variable<V>> groupVariables = new ArrayList<>();
            TIntArrayList groupTermStart = new TIntArrayList();
            List<SingleEquationTerm<V, E>> terms = new ArrayList<>();
            TIntArrayList termAlternativeNums = new TIntArrayList();
            int[] elementUniqueTermStart = new int[elementCount + 1];
            List<SingleEquationTerm<V, E>> uniqueTerms = new ArrayList<>();
            TIntArrayList uniqueTermAlternativeNums = new TIntArrayList();
            List<Variable<V>> uniqueTermVariables = new ArrayList<>();
            int[] elementUntaggedTermCount = new int[elementCount];
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                elementStart[elementNum] = groupVariables.size();
                elementUniqueTermStart[elementNum] = uniqueTerms.size();
                // merge term groups and vectorized variable alternative bodies (null term slots) per variable,
                // so each variable of the element yields exactly one matrix entry
                Map<Variable<V>, List<SingleEquationTerm<V, E>>> termsByVariable = new LinkedHashMap<>();
                Map<Variable<V>, TIntArrayList> variableAlternativeNumsByVariable = new LinkedHashMap<>();
                AdditionalSingleTermsByEquation additionalTerms = singleTermsByEquationElementNum.get(elementNum);
                if (additionalTerms != null) {
                    termsByVariable.putAll(additionalTerms.termsByVariable);
                }
                List<ElementAlternative> alternatives = getElementAlternatives(elementNum);
                for (int alternativeNum = 0; alternativeNum < alternatives.size(); alternativeNum++) {
                    ElementAlternative alternative = alternatives.get(alternativeNum);
                    if (alternative.variable != null) {
                        termsByVariable.computeIfAbsent(alternative.variable, k -> Collections.emptyList());
                        variableAlternativeNumsByVariable.computeIfAbsent(alternative.variable, k -> new TIntArrayList())
                                .add(alternativeNum);
                    }
                }
                for (Map.Entry<Variable<V>, List<SingleEquationTerm<V, E>>> e : termsByVariable.entrySet()) {
                    groupVariables.add(e.getKey());
                    groupTermStart.add(terms.size());
                    for (SingleEquationTerm<V, E> term : e.getValue()) {
                        terms.add(term);
                        Integer alternativeNum = singleTermAlternativeNums.get(term);
                        termAlternativeNums.add(alternativeNum == null ? -1 : alternativeNum);
                    }
                    TIntArrayList variableAlternativeNums = variableAlternativeNumsByVariable.get(e.getKey());
                    if (variableAlternativeNums != null) {
                        for (int i = 0; i < variableAlternativeNums.size(); i++) {
                            terms.add(null);
                            termAlternativeNums.add(variableAlternativeNums.getQuick(i));
                            // unique (evaluation) view entry for the variable body
                            uniqueTerms.add(null);
                            uniqueTermVariables.add(e.getKey());
                            uniqueTermAlternativeNums.add(variableAlternativeNums.getQuick(i));
                        }
                    }
                }
                if (additionalTerms != null) {
                    for (SingleEquationTerm<V, E> term : additionalTerms.terms) {
                        uniqueTerms.add(term);
                        uniqueTermVariables.add(null);
                        Integer alternativeNum = singleTermAlternativeNums.get(term);
                        uniqueTermAlternativeNums.add(alternativeNum == null ? -1 : alternativeNum);
                        if (alternativeNum == null) {
                            elementUntaggedTermCount[elementNum]++;
                        }
                    }
                }
            }
            elementStart[elementCount] = groupVariables.size();
            groupTermStart.add(terms.size());
            elementUniqueTermStart[elementCount] = uniqueTerms.size();
            additionalTermsVector = new AdditionalTermsVector<>(elementStart, groupVariables, groupTermStart.toArray(),
                    terms, termAlternativeNums.toArray(), elementUniqueTermStart, uniqueTerms, uniqueTermVariables,
                    uniqueTermAlternativeNums.toArray(), elementUntaggedTermCount);
        }
        return additionalTermsVector;
    }

    // scratch buffers (grown on demand) for the additional single term derivatives of the element being processed,
    // snapshotted once per element instead of scanning the terms for each completed (row, column) matrix element
    private int[] additionalTermRows = new int[0];
    private double[] additionalTermValues = new double[0];

    // per element start of the derivative value index sequence, captured during the full derivation walk so that
    // a single element can be re-derived in place (partial value update); only valid while partialDerReady
    private int[] elementValueIndexStart;

    private boolean partialDerReady;

    boolean isPartialDerReady() {
        return partialDerReady;
    }

    /**
     * Re-derive a single element equation in place, reusing the matrix element indexes captured during the last
     * full derivation walk (see {@link JacobianMatrix} partial value update). Only callable while
     * {@link #isPartialDerReady()}.
     */
    public void derElementPartial(DerHandler handler, int elementNum) {
        if (!partialDerReady) {
            throw new PowsyblException("Partial derivation not ready for " + type + " equation array");
        }
        equationDerivativeVector.updateRange(this, equationDerivativeVectorStartIndices[elementNum],
                equationDerivativeVectorStartIndices[elementNum + 1]);
        derElement(handler, elementNum, elementValueIndexStart[elementNum]);
    }

    public void der(DerHandler handler) {
        Objects.requireNonNull(handler);

        updateEquationDerivativeVectors();
        equationDerivativeVector.update(this);

        if (elementValueIndexStart == null) {
            elementValueIndexStart = new int[elementCount];
        }

        // calculate all derivative values
        // process column by column so equation by equation of the array
        int valueIndex = 0;
        for (int elementNum = 0; elementNum < elementCount; elementNum++) {
            elementValueIndexStart[elementNum] = valueIndex;
            // skip inactive elements
            if (!elementActive[elementNum]) {
                continue;
            }
            valueIndex = derElement(handler, elementNum, valueIndex);
        }
        partialDerReady = true;
    }

    private int derElement(DerHandler handler, int elementNum, int valueIndexStart) {
        int valueIndex = valueIndexStart;
        int column = getElementNumToColumn(elementNum);
        // for each equation of the array we already have the list of terms to derive and its variable sorted
        // by variable row (required by solvers)

        // snapshot the additional single term derivatives of this element: one entry per variable having a row,
        // contributing zeros when masked (inactive term or non active alternative) to preserve the matrix
        // structure
        int additionalTermCount = 0;
        AdditionalTermsVector<V, E> atv = getAdditionalTermsVector();
        int groupStart = atv.elementStart[elementNum];
        int groupEnd = atv.elementStart[elementNum + 1];
        if (groupStart < groupEnd) {
            if (additionalTermRows.length < groupEnd - groupStart) {
                additionalTermRows = new int[groupEnd - groupStart];
                additionalTermValues = new double[groupEnd - groupStart];
            }
            boolean balanceContributing = isElementBalanceContributing(elementNum);
            int activeAlternativeNum = elementActiveAlternativeNum == null ? -1 : elementActiveAlternativeNum[elementNum];
            int disabledAlternativeNum = elementDisabledAlternativeNum == null ? -2 : elementDisabledAlternativeNum[elementNum];
            // when the default body is active and the element has no untagged term, no additional term
            // contributes: the union pattern entries (explicit zeros) must still be emitted, but the term
            // derivations can be skipped
            boolean anyTermCanContribute = !balanceContributing || atv.elementUntaggedTermCount[elementNum] > 0;
            for (int g = groupStart; g < groupEnd; g++) {
                Variable<V> v = atv.groupVariables.get(g);
                int additionalTermRow = v.getRow();
                if (additionalTermRow == -1) {
                    continue;
                }
                double additionalTermValue = 0;
                if (anyTermCanContribute) {
                    for (int t = atv.groupTermStart[g]; t < atv.groupTermStart[g + 1]; t++) {
                        SingleEquationTerm<V, E> term = atv.terms.get(t);
                        int alternativeNum = atv.termAlternativeNums[t];
                        if (term == null) {
                            // vectorized variable alternative body: derivative is constant one
                            if (alternativeNum == activeAlternativeNum) {
                                additionalTermValue += 1;
                            }
                            continue;
                        }
                        boolean contributing = alternativeNum == -1
                                ? balanceContributing && term.isActive()
                                : alternativeNum == activeAlternativeNum && (alternativeNum == disabledAlternativeNum || term.isActive());
                        if (contributing) {
                            additionalTermValue += term.der(v);
                        }
                    }
                }
                additionalTermRows[additionalTermCount] = additionalTermRow;
                additionalTermValues[additionalTermCount] = additionalTermValue;
                additionalTermCount++;
            }
        }

        // process term by term
        double value = 0;
        int row;

        int prevRow = -1;
        int iStart = this.equationDerivativeVectorStartIndices[elementNum];
        int iEnd = this.equationDerivativeVectorStartIndices[elementNum + 1];
        for (int i = iStart; i < iEnd; i++) {

            // the derivative variable row
            row = equationDerivativeVector.rows[i];

            // if an element at (row, column) is complete (we switch to another row), notify
            if (prevRow != -1 && row != prevRow) {
                value += consumeAdditionalTermValue(prevRow, additionalTermCount);
                onDer(handler, column, prevRow, value, valueIndex);
                valueIndex++;
                value = 0;
            }
            prevRow = row;
            value += equationDerivativeVector.values[i];
        }

        // remaining notif
        if (prevRow != -1) {
            value += consumeAdditionalTermValue(prevRow, additionalTermCount);
            onDer(handler, column, prevRow, value, valueIndex);
            valueIndex++;
        }

        // remaining additional term entries, on rows not present in the derivative vector of this element
        for (int a = 0; a < additionalTermCount; a++) {
            if (additionalTermRows[a] != -1) {
                onDer(handler, column, additionalTermRows[a], additionalTermValues[a], valueIndex);
                valueIndex++;
            }
        }
        return valueIndex;
    }

    private double consumeAdditionalTermValue(int row, int additionalTermCount) {
        for (int a = 0; a < additionalTermCount; a++) {
            if (additionalTermRows[a] == row) {
                additionalTermRows[a] = -1; // consumed
                return additionalTermValues[a];
            }
        }
        return 0;
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
