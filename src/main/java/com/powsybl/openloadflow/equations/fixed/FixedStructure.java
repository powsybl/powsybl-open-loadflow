/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations.fixed;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.Derivative;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationArray;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTermArray;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.equations.SingleEquation;
import com.powsybl.openloadflow.equations.SingleEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * An equation system compiled into arrays, with a Jacobian matrix structure that holds every equation where it may stand.
 * Nothing here depends on what is active.
 *
 * <ul>
 * <li><b>Positions.</b> One per variable that a term of the system can depend on, in the order of the variables. A position is
 *     a row (its variable) and a column (the equation that determines that variable).</li>
 * <li><b>Equations.</b> Numbered once, by type and element: the single equations, then the elements of each equation
 *     array. Each knows the positions of the variables it depends on (its rows). A single equation that is removed and
 *     created again keeps its number: it is the same one, with other terms.</li>
 * <li><b>Own position.</b> The position of the variable the {@link VariablePairing} pairs an equation with: a bus's reactive balance
 *     and its voltage target with its voltage, its active balance and the reference angle with its angle. The
 *     equations that share an own position are the <b>alternatives</b> of that position: OLF activates one of them at a time,
 *     and switching between them (a bus going from PV to PQ, a control switched on or off) is what makes its index
 *     change.</li>
 * <li><b>Controller positions.</b> The other positions an equation may take, given by the {@link VariablePairing} from the controls of the
 *     network: the reactive balance of a bus whose voltage a remote generator holds goes to the position of that
 *     generator's bus voltage, which the control frees.</li>
 * <li><b>Matrix structure.</b> For each position, the sorted rows of every equation that may stand there, plus the diagonal: the
 *     Jacobian by columns, one column per position. It is built from a list of pairings and only ever grows.</li>
 * <li><b>Matrix element indices.</b> For each derivative of each term, the index of its value in the column of the position its
 *     equation stands in, so that a fill writes each derivative without looking for its entry.</li>
 * </ul>
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class FixedStructure<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    /** The terms of one term array, indexed by term number, and where each of their outputs goes. */
    static final class ArrayTerms<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        final EquationTermArray<V, E> termArray;
        /** The equation of each term. */
        final int[] equation;
        /** The element of each term: its index in {@code eval()} and in each array of {@code evalDer()}. */
        final int[] element;
        /** For each derivative of each term: its array in {@code evalDer()}. */
        final int[][] localIndex;
        /** For each derivative of each term: the position of its variable. */
        final int[][] row;
        /** For each derivative of each term: its value index in the own column of the equation, -1 when the matrix structure lacks it. */
        final int[][] matrixElementIndex;

        ArrayTerms(EquationTermArray<V, E> termArray, int termCount) {
            this.termArray = termArray;
            equation = new int[termCount];
            element = new int[termCount];
            localIndex = new int[termCount][];
            row = new int[termCount][];
            matrixElementIndex = new int[termCount][];
        }
    }

    /** The terms an equation holds as objects: for each variable of each one, its position and where its derivative goes. */
    static final class ObjectTerms<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        final List<SingleEquationTerm<V, E>> terms;
        /** For each term, its variables. */
        final List<List<Variable<V>>> variables = new ArrayList<>();
        /** For each variable of each term: its position. */
        final int[][] row;
        /** For each variable of each term: the value index in the column of the position of the equation, -1 when the matrix structure lacks it. */
        final int[][] matrixElementIndex;

        ObjectTerms(List<SingleEquationTerm<V, E>> terms) {
            this.terms = terms;
            row = new int[terms.size()][];
            matrixElementIndex = new int[terms.size()][];
        }
    }

    private static final int[] NO_SLOT = new int[0];

    private final List<Variable<V>> variables;
    private final Map<Variable<V>, Integer> positions = new HashMap<>();

    private final List<SingleEquation<V, E>> singleEquations;
    private final Map<Long, Integer> singleEquationNumbers = new HashMap<>();
    /** False for a single equation that was removed from the system and not created again since: it holds nothing. */
    private final boolean[] bound;
    private final List<EquationArray<V, E>> arrays;
    private final int[] arrayFirstEquation;
    /** For each equation that is an element of an equation array: the index of its array; -1 for a single equation. */
    private final int[] arrayIndexOfEquation;
    /** For each equation that is an element of an equation array: its element number; -1 for a single equation. */
    private final int[] elementNumOfEquation;
    private final int equationCount;
    private final int[][] rows;
    private final int[] ownPosition;
    /** For each equation, the position its matrix element indices are for: its own at first, then the one the pairing gives it. */
    private final int[] pairedPosition;
    /** The equations that changed position since their matrix element indices were computed. */
    private final TIntArrayList staleMatrixElementIndices = new TIntArrayList();
    private final boolean[] fixesVariable;
    /** For each equation, the positions it may take besides its own, sorted: empty for most. */
    private final int[][] controllerPositions;
    /** For each equation, the terms it holds as objects: null when it has none. */
    private final List<ObjectTerms<V, E>> objectTerms;

    private final List<ArrayTerms<V, E>> arrayTerms = new ArrayList<>();
    /** For each equation array, the terms of its term arrays. */
    private final List<List<ArrayTerms<V, E>>> arrayTermsOfArray = new ArrayList<>();
    private final int[] arrayElementsWithSingleTerms;

    private int[] columnStart;
    private int[] rowIndices;
    private int[] diagonal;

    FixedStructure(EquationSystem<V, E> equationSystem, VariablePairing<V, E> pairing) {
        // numbered by type and element, not in the order of OLF's hash map, which changes from one run to the next:
        // where several pairings are possible the choice follows the numbers, and must be the same each time
        singleEquations = new ArrayList<>(equationSystem.getEquations());
        singleEquations.sort(Comparator.<SingleEquation<V, E>>comparingInt(equation -> equation.getType().ordinal())
                .thenComparingInt(SingleEquation::getElementNum));
        bound = new boolean[singleEquations.size()];
        Arrays.fill(bound, true);
        for (int equation = 0; equation < singleEquations.size(); equation++) {
            singleEquationNumbers.put(key(singleEquations.get(equation).getType(), singleEquations.get(equation).getElementNum()), equation);
        }
        arrays = List.copyOf(equationSystem.getEquationArrays());
        arrayFirstEquation = new int[arrays.size()];
        int count = singleEquations.size();
        for (int a = 0; a < arrays.size(); a++) {
            arrayFirstEquation[a] = count;
            count += arrays.get(a).getElementCount();
        }
        equationCount = count;
        arrayIndexOfEquation = new int[equationCount];
        elementNumOfEquation = new int[equationCount];
        Arrays.fill(arrayIndexOfEquation, 0, singleEquations.size(), -1);
        Arrays.fill(elementNumOfEquation, 0, singleEquations.size(), -1);
        for (int a = 0; a < arrays.size(); a++) {
            for (int element = 0; element < arrays.get(a).getElementCount(); element++) {
                arrayIndexOfEquation[arrayFirstEquation[a] + element] = a;
                elementNumOfEquation[arrayFirstEquation[a] + element] = element;
            }
        }

        SortedSet<Variable<V>> sorted = new TreeSet<>();
        for (int equation = 0; equation < equationCount; equation++) {
            forEachVariable(equation, sorted::add);
        }
        variables = List.copyOf(sorted);
        Map<Long, Integer> positionByTypeAndElement = new HashMap<>();
        for (int position = 0; position < variables.size(); position++) {
            Variable<V> variable = variables.get(position);
            positions.put(variable, position);
            positionByTypeAndElement.put(key(variable.getType(), variable.getElementNum()), position);
        }

        rows = new int[equationCount][];
        ownPosition = new int[equationCount];
        fixesVariable = new boolean[equationCount];
        TIntArrayList withSingleTerms = new TIntArrayList();
        TIntArrayList buffer = new TIntArrayList();
        for (int equation = 0; equation < equationCount; equation++) {
            buffer.resetQuick();
            forEachVariable(equation, variable -> buffer.add(positions.get(variable)));
            rows[equation] = sortedDistinct(buffer);
            E type = getType(equation);
            V variableType = pairing.variableType(type);
            ownPosition[equation] = variableType == null ? -1
                    : positionByTypeAndElement.getOrDefault(key(variableType, getElementNum(equation)), -1);
            fixesVariable[equation] = pairing.fixesVariable(type);
            if (equation >= singleEquations.size() && !getSingleTerms(equation).isEmpty()) {
                withSingleTerms.add(equation);
            }
        }
        arrayElementsWithSingleTerms = withSingleTerms.toArray();
        pairedPosition = ownPosition.clone();
        objectTerms = new ArrayList<>(equationCount);
        for (int equation = 0; equation < equationCount; equation++) {
            objectTerms.add(compileObjectTerms(equation));
        }

        controllerPositions = new int[equationCount][];
        Arrays.fill(controllerPositions, NO_SLOT);
        Map<Integer, TIntArrayList> controllerPositionLists = new HashMap<>();
        pairing.forEachControllerVariable((equationType, equationElementNum, variableType, variableElementNum) -> {
            int equation = getNumber(equationType, equationElementNum);
            Integer position = positionByTypeAndElement.get(key(variableType, variableElementNum));
            if (equation >= 0 && position != null && position != ownPosition[equation]) {
                controllerPositionLists.computeIfAbsent(equation, e -> new TIntArrayList()).add(position);
            }
        });
        controllerPositionLists.forEach((equation, positionList) -> controllerPositions[equation] = sortedDistinct(positionList));

        for (int a = 0; a < arrays.size(); a++) {
            List<ArrayTerms<V, E>> termsOfArray = new ArrayList<>();
            for (EquationTermArray<V, E> termArray : arrays.get(a).getTermArrays()) {
                termsOfArray.add(compile(termArray, a));
            }
            arrayTerms.addAll(termsOfArray);
            arrayTermsOfArray.add(termsOfArray);
        }
    }

    /** The object terms of an equation with the positions of their variables; null when it has none or when a variable has no position. */
    private ObjectTerms<V, E> compileObjectTerms(int equation) {
        List<SingleEquationTerm<V, E>> terms = List.copyOf(getSingleTerms(equation));
        if (terms.isEmpty()) {
            return null;
        }
        ObjectTerms<V, E> compiled = new ObjectTerms<>(terms);
        for (int t = 0; t < terms.size(); t++) {
            List<Variable<V>> termVariables = terms.get(t).getVariables();
            compiled.variables.add(termVariables);
            compiled.row[t] = new int[termVariables.size()];
            compiled.matrixElementIndex[t] = new int[termVariables.size()];
            for (int k = 0; k < termVariables.size(); k++) {
                Integer position = positions.get(termVariables.get(k));
                if (position == null) {
                    return null;
                }
                compiled.row[t][k] = position;
                compiled.matrixElementIndex[t][k] = rowIndices != null && pairedPosition[equation] >= 0 ? index(pairedPosition[equation], position) : -1;
            }
        }
        return compiled;
    }

    private ArrayTerms<V, E> compile(EquationTermArray<V, E> termArray, int a) {
        int elementCount = arrays.get(a).getElementCount();
        int termCount = 0;
        for (int element = 0; element < elementCount; element++) {
            termCount += termArray.getTermNumsForEquationElementNum(element).size();
        }
        ArrayTerms<V, E> terms = new ArrayTerms<>(termArray, termCount);
        for (int element = 0; element < elementCount; element++) {
            TIntArrayList termNums = termArray.getTermNumsForEquationElementNum(element);
            int equation = arrayFirstEquation[a] + element;
            if (!termNums.isEmpty() && ownPosition[equation] < 0) {
                throw new PowsyblException("No variable to pair the element " + element + " of the equation array "
                        + arrays.get(a).getType() + " with");
            }
            for (int i = 0; i < termNums.size(); i++) {
                int term = termNums.getQuick(i);
                List<Derivative<V>> derivatives = termArray.getTermDerivatives(term);
                terms.equation[term] = equation;
                terms.element[term] = termArray.getTermElementNum(term);
                terms.localIndex[term] = new int[derivatives.size()];
                terms.row[term] = new int[derivatives.size()];
                terms.matrixElementIndex[term] = new int[derivatives.size()];
                for (int k = 0; k < derivatives.size(); k++) {
                    terms.localIndex[term][k] = derivatives.get(k).getLocalIndex();
                    terms.row[term][k] = positions.get(derivatives.get(k).getVariable());
                }
            }
        }
        return terms;
    }

    /**
     * Builds the matrix structure that holds the given pairings: each equation's rows in the column of its position, and the
     * diagonal of every position. Then the matrix element indices of the array terms.
     */
    void buildMatrixStructure(TIntArrayList pairedEquations, TIntArrayList pairedPositions) {
        int variableCount = variables.size();
        int[] start = new int[variableCount + 1];
        for (int position = 0; position < variableCount; position++) {
            start[position + 1] = 1;
        }
        for (int i = 0; i < pairedEquations.size(); i++) {
            start[pairedPositions.getQuick(i) + 1] += rows[pairedEquations.getQuick(i)].length;
        }
        for (int position = 0; position < variableCount; position++) {
            start[position + 1] += start[position];
        }
        int[] raw = new int[start[variableCount]];
        int[] next = Arrays.copyOf(start, variableCount);
        for (int position = 0; position < variableCount; position++) {
            raw[next[position]++] = position;
        }
        for (int i = 0; i < pairedEquations.size(); i++) {
            int position = pairedPositions.getQuick(i);
            for (int row : rows[pairedEquations.getQuick(i)]) {
                raw[next[position]++] = row;
            }
        }
        columnStart = new int[variableCount + 1];
        int size = 0;
        for (int position = 0; position < variableCount; position++) {
            Arrays.sort(raw, start[position], start[position + 1]);
            columnStart[position] = size;
            for (int i = start[position]; i < start[position + 1]; i++) {
                if (size == columnStart[position] || raw[size - 1] != raw[i]) {
                    raw[size++] = raw[i];
                }
            }
        }
        columnStart[variableCount] = size;
        rowIndices = Arrays.copyOf(raw, size);
        diagonal = new int[variableCount];
        for (int position = 0; position < variableCount; position++) {
            diagonal[position] = index(position, position);
        }
        setMatrixElementIndices();
    }

    /**
     * Takes the matrix structure of the structure this one replaces, when both have the same positions: the system was compiled
     * again, for an equation it had not seen, and its matrix can stay as long as the matrix structure holds what is paired.
     */
    boolean adoptMatrixStructure(FixedStructure<V, E> previous) {
        if (previous.rowIndices == null || !variables.equals(previous.variables)) {
            return false;
        }
        columnStart = previous.columnStart;
        rowIndices = previous.rowIndices;
        diagonal = previous.diagonal;
        setMatrixElementIndices();
        return true;
    }

    private void setMatrixElementIndices() {
        staleMatrixElementIndices.resetQuick();
        for (int equation = 0; equation < equationCount; equation++) {
            setObjectTermMatrixElementIndices(equation);
        }
        for (ArrayTerms<V, E> terms : arrayTerms) {
            for (int term = 0; term < terms.equation.length; term++) {
                setMatrixElementIndices(terms, term);
            }
        }
    }

    private void setObjectTermMatrixElementIndices(int equation) {
        ObjectTerms<V, E> compiled = objectTerms.get(equation);
        if (compiled != null) {
            for (int t = 0; t < compiled.row.length; t++) {
                for (int k = 0; k < compiled.row[t].length; k++) {
                    compiled.matrixElementIndex[t][k] = pairedPosition[equation] >= 0 ? index(pairedPosition[equation], compiled.row[t][k]) : -1;
                }
            }
        }
    }

    private void setMatrixElementIndices(ArrayTerms<V, E> terms, int term) {
        for (int k = 0; k < terms.row[term].length; k++) {
            terms.matrixElementIndex[term][k] = index(pairedPosition[terms.equation[term]], terms.row[term][k]);
        }
    }

    /**
     * The position an equation holds now. Its matrix element indices are for that position, so that a fill writes each derivative
     * without looking for its entry, wherever the equation is: {@link #updateMatrixElementIndices()} computes them again.
     */
    void setPairedPosition(int equation, int position) {
        if (pairedPosition[equation] != position) {
            pairedPosition[equation] = position;
            staleMatrixElementIndices.add(equation);
        }
    }

    /** Computes again the matrix element indices of the equations that changed position. The matrix structure must hold their entries. */
    void updateMatrixElementIndices() {
        for (int i = 0; i < staleMatrixElementIndices.size(); i++) {
            int equation = staleMatrixElementIndices.getQuick(i);
            setObjectTermMatrixElementIndices(equation);
            if (equation >= singleEquations.size()) {
                int a = getArrayIndex(equation);
                for (ArrayTerms<V, E> terms : arrayTermsOfArray.get(a)) {
                    TIntArrayList termNums = terms.termArray.getTermNumsForEquationElementNum(equation - arrayFirstEquation[a]);
                    for (int j = 0; j < termNums.size(); j++) {
                        setMatrixElementIndices(terms, termNums.getQuick(j));
                    }
                }
            }
        }
        staleMatrixElementIndices.resetQuick();
    }

    /** The number of the equation of a type and an element, single or element of an array; -1 when there is none. */
    private int getNumber(E type, int elementNum) {
        Integer number = singleEquationNumbers.get(key(type, elementNum));
        if (number != null) {
            return number;
        }
        for (int a = 0; a < arrays.size(); a++) {
            if (arrays.get(a).getType() == type && elementNum < arrays.get(a).getElementCount()) {
                return arrayFirstEquation[a] + elementNum;
            }
        }
        return -1;
    }

    /**
     * The number of a single equation of this structure. It is -1 for any other equation: one created since the
     * compilation and not bound, or the element of an equation array.
     */
    int getNumber(Equation<V, E> equation) {
        Integer number = singleEquationNumbers.get(key(equation.getType(), equation.getElementNum()));
        return number != null && bound[number] && singleEquations.get(number) == equation ? number : -1;
    }

    /** A single equation was removed from the system: it keeps its number and its rows, and holds nothing. */
    boolean unbind(SingleEquation<V, E> equation) {
        int number = getNumber(equation);
        if (number >= 0) {
            bound[number] = false;
            objectTerms.set(number, null);
        }
        return number >= 0;
    }

    /**
     * A single equation was created in the system: it takes the number of the removed one of the same type and
     * element, and so the same position. Returns that number, or -1 when there is none.
     */
    int bind(SingleEquation<V, E> equation) {
        Integer number = singleEquationNumbers.get(key(equation.getType(), equation.getElementNum()));
        if (number == null || bound[number]) {
            return -1;
        }
        singleEquations.set(number, equation);
        bound[number] = true;
        return number;
    }

    /**
     * Adds the variables of the terms a single equation has now to its rows, which only grow, like the matrix structure.
     * Returns false when one of these variables has no position.
     */
    boolean addRows(int equation) {
        TIntArrayList all = new TIntArrayList(rows[equation]);
        for (SingleEquationTerm<V, E> term : getSingleTerms(equation)) {
            for (Variable<V> variable : term.getVariables()) {
                Integer position = positions.get(variable);
                if (position == null) {
                    return false;
                }
                all.add(position);
            }
        }
        rows[equation] = sortedDistinct(all);
        objectTerms.set(equation, compileObjectTerms(equation));
        return true;
    }

    /** The index of the value at (row, column), or -1 when the matrix structure does not hold it. */
    public int index(int column, int row) {
        int index = Arrays.binarySearch(rowIndices, columnStart[column], columnStart[column + 1], row);
        return index >= 0 ? index : -1;
    }

    /** True when a column of the matrix structure already holds the rows of an equation. */
    boolean holds(int column, int equation) {
        for (int row : rows[equation]) {
            if (index(column, row) < 0) {
                return false;
            }
        }
        return true;
    }

    private void forEachVariable(int equation, Consumer<Variable<V>> consumer) {
        for (SingleEquationTerm<V, E> term : getSingleTerms(equation)) {
            term.getVariables().forEach(consumer);
        }
        if (equation >= singleEquations.size()) {
            int element = getElementNum(equation);
            for (EquationTermArray<V, E> termArray : getArray(equation).getTermArrays()) {
                TIntArrayList termNums = termArray.getTermNumsForEquationElementNum(element);
                for (int i = 0; i < termNums.size(); i++) {
                    for (Derivative<V> derivative : termArray.getTermDerivatives(termNums.getQuick(i))) {
                        consumer.accept(derivative.getVariable());
                    }
                }
            }
        }
    }

    private static int[] sortedDistinct(TIntArrayList values) {
        values.sort();
        int size = 0;
        for (int i = 0; i < values.size(); i++) {
            if (size == 0 || values.getQuick(size - 1) != values.getQuick(i)) {
                values.setQuick(size++, values.getQuick(i));
            }
        }
        return values.toArray(0, size);
    }

    private static long key(Enum<?> type, int elementNum) {
        return ((long) type.ordinal() << 32) | elementNum;
    }

    private int getArrayIndex(int equation) {
        return arrayIndexOfEquation[equation];
    }

    private EquationArray<V, E> getArray(int equation) {
        return arrays.get(getArrayIndex(equation));
    }

    public int getVariableCount() {
        return variables.size();
    }

    public Variable<V> getVariable(int position) {
        return variables.get(position);
    }

    /** The position of a variable, or -1 when no term of the system depends on it. */
    public int getPosition(Variable<V> variable) {
        return positions.getOrDefault(variable, -1);
    }

    public int getEquationCount() {
        return equationCount;
    }

    /** The single equations are the first equations; the elements of the equation arrays follow. */
    public int getSingleEquationCount() {
        return singleEquations.size();
    }

    public E getType(int equation) {
        return equation < singleEquations.size() ? singleEquations.get(equation).getType() : getArray(equation).getType();
    }

    public int getElementNum(int equation) {
        return equation < singleEquations.size() ? singleEquations.get(equation).getElementNum()
                : equation - arrayFirstEquation[getArrayIndex(equation)];
    }

    public boolean isArrayElement(int equation) {
        return equation >= singleEquations.size();
    }

    public boolean isActive(int equation) {
        return equation < singleEquations.size() ? bound[equation] && singleEquations.get(equation).isActive()
                : getArray(equation).isElementActive(getElementNum(equation));
    }

    /** The column of the equation in OLF's own index: -1 when it is not active. */
    public int getColumnInEquationSystem(int equation) {
        if (equation < singleEquations.size()) {
            return bound[equation] ? singleEquations.get(equation).getColumn() : -1;
        }
        return arrays.get(arrayIndexOfEquation[equation]).getElementNumToColumn(elementNumOfEquation[equation]);
    }

    /** The terms held as objects: all the terms of a single equation, the additional terms of an array element. */
    public List<SingleEquationTerm<V, E>> getSingleTerms(int equation) {
        if (equation < singleEquations.size()) {
            return bound[equation] ? singleEquations.get(equation).getTerms() : List.of();
        }
        return getArray(equation).getSingleEquationTerms(getElementNum(equation));
    }

    /** The position the equation is paired with, or -1 when it has none of its own. */
    public int getOwnPosition(int equation) {
        return ownPosition[equation];
    }

    boolean fixesVariable(int equation) {
        return fixesVariable[equation];
    }

    /** The terms the equation holds as objects, compiled; null when it has none. */
    ObjectTerms<V, E> getObjectTerms(int equation) {
        return objectTerms.get(equation);
    }

    /** The positions the equation may take besides its own: those its {@link VariablePairing} gives, sorted. */
    int[] getControllerPositions(int equation) {
        return controllerPositions[equation];
    }

    /** The positions of the variables the equation depends on, sorted. */
    int[] getRows(int equation) {
        return rows[equation];
    }

    List<ArrayTerms<V, E>> getArrayTerms() {
        return arrayTerms;
    }

    int[] getArrayElementsWithSingleTerms() {
        return arrayElementsWithSingleTerms;
    }

    public int[] getColumnStart() {
        return columnStart;
    }

    public int[] getRowIndices() {
        return rowIndices;
    }

    public int[] getDiagonal() {
        return diagonal;
    }

    public int getValueCount() {
        return rowIndices.length;
    }
}
