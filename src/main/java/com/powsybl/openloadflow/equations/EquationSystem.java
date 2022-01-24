/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationSystem<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquationSystem.class);

    private final boolean indexTerms;

    private final Map<Pair<Integer, E>, Equation<V, E>> equations = new HashMap<>();

    private final Map<Pair<ElementType, Integer>, List<Equation<V, E>>> equationsByElement = new HashMap<>();

    private final Map<Pair<ElementType, Integer>, List<EquationTerm<V, E>>> equationTermsByElement = new HashMap<>();

    public interface Index<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve();

        NavigableSet<Variable<V>> getSortedVariablesToFind();
    }

    private static class IncrementalIndex<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
            implements Index<V, E>, EquationSystemListener<V, E> {

        private final NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> sortedEquationsToSolve = new TreeMap<>();

        // variable reference counting in equation terms
        private final NavigableMap<Variable<V>, MutableInt> sortedVariablesRefCount = new TreeMap<>();

        private boolean equationIndexValid = false;

        private boolean variableIndexValid = false;

        private void update() {
            if (!equationIndexValid) {
                int columnCount = 0;
                for (Equation<V, E> equation : sortedEquationsToSolve.keySet()) {
                    equation.setColumn(columnCount++);
                }
                equationIndexValid = true;
                LOGGER.debug("Equations index updated ({} columns)", columnCount);
            }

            if (!variableIndexValid) {
                int rowCount = 0;
                for (Variable<V> variable : sortedVariablesRefCount.keySet()) {
                    variable.setRow(rowCount++);
                }
                variableIndexValid = true;
                LOGGER.debug("Variables index updated ({} rows)", rowCount);
            }
        }

        private void addTerm(EquationTerm<V, E> term) {
            for (Variable<V> variable : term.getVariables()) {
                sortedEquationsToSolve.computeIfAbsent(term.getEquation(), k -> new TreeMap<>())
                        .computeIfAbsent(variable, k -> new ArrayList<>())
                        .add(term);
                MutableInt refCount = sortedVariablesRefCount.get(variable);
                if (refCount == null) {
                    refCount = new MutableInt();
                    variableIndexValid = false;
                    sortedVariablesRefCount.put(variable, refCount);
                }
                refCount.increment();
            }
        }

        private void addEquation(Equation<V, E> equation) {
            for (EquationTerm<V, E> term : equation.getTerms()) {
                if (term.isActive()) {
                    addTerm(term);
                }
            }
            equationIndexValid = false;
        }

        private void removeTerm(EquationTerm<V, E> term) {
            NavigableMap<Variable<V>, List<EquationTerm<V, E>>> termsByVariable = sortedEquationsToSolve.get(term.getEquation());
            for (Variable<V> variable : term.getVariables()) {
                if (termsByVariable != null) {
                    List<EquationTerm<V, E>> terms = termsByVariable.get(variable);
                    if (terms != null) {
                        terms.remove(term);
                        if (terms.isEmpty()) {
                            termsByVariable.remove(variable);
                            if (termsByVariable.isEmpty()) {
                                sortedEquationsToSolve.remove(term.getEquation());
                            }
                        }
                    }
                }
                MutableInt variableRefCount = sortedVariablesRefCount.get(variable);
                if (variableRefCount != null) {
                    variableRefCount.decrement();
                    if (variableRefCount.intValue() == 0) {
                        sortedVariablesRefCount.remove(variable);
                        variableIndexValid = false;
                    }
                }
            }
        }

        private void removeEquation(Equation<V, E> equation) {
            sortedEquationsToSolve.remove(equation);
            for (EquationTerm<V, E> term : equation.getTerms()) {
                if (term.isActive()) {
                    removeTerm(term);
                }
            }
            equationIndexValid = false;
        }

        @Override
        public void onEquationChange(Equation<V, E> equation, EquationEventType eventType) {
            switch (eventType) {
                case EQUATION_REMOVED:
                    if (equation.isActive()) {
                        removeEquation(equation);
                    }
                    break;

                case EQUATION_DEACTIVATED:
                    removeEquation(equation);
                    break;

                case EQUATION_CREATED:
                    if (equation.isActive()) {
                        addEquation(equation);
                    }
                    break;

                case EQUATION_ACTIVATED:
                    addEquation(equation);
                    break;

                default:
                    throw new IllegalStateException("Event type not supported: " + eventType);
            }
        }

        @Override
        public void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
            if (term.getEquation().isActive()) {
                switch (eventType) {
                    case EQUATION_TERM_ADDED:
                        if (term.isActive()) {
                            addTerm(term);
                        }
                        break;

                    case EQUATION_TERM_ACTIVATED:
                        addTerm(term);
                        break;

                    case EQUATION_TERM_DEACTIVATED:
                        removeTerm(term);
                        break;

                    default:
                        throw new IllegalStateException("Event type not supported: " + eventType);
                }
            }
        }

        public NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve() {
            update();
            return sortedEquationsToSolve;
        }

        public NavigableSet<Variable<V>> getSortedVariablesToFind() {
            update();
            return sortedVariablesRefCount.navigableKeySet();
        }
    }

    /**
     * Used just for debugging as a reference
     */
    private class FullIndex implements Index<V, E>, EquationSystemListener<V, E> {

        private final TreeMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> sortedEquationsToSolve = new TreeMap<>();

        private final TreeSet<Variable<V>> sortedVariables = new TreeSet<>();

        private boolean valid = false;

        private void update() {
            if (!valid) {
                sortedEquationsToSolve.clear();
                sortedVariables.clear();
                for (var equation : equations.values()) {
                    if (equation.isActive()) {
                        for (var term : equation.getTerms()) {
                            if (term.isActive()) {
                                for (var v : term.getVariables()) {
                                    sortedEquationsToSolve.computeIfAbsent(equation, k -> new TreeMap<>())
                                            .computeIfAbsent(v, k -> new ArrayList<>())
                                            .add(term);
                                    sortedVariables.add(v);
                                }
                            }
                        }
                    }
                }

                int columnCount = 0;
                for (Equation<V, E> equation : sortedEquationsToSolve.keySet()) {
                    equation.setColumn(columnCount++);
                }
                LOGGER.debug("Equations index updated ({} columns)", columnCount);

                int rowCount = 0;
                for (Variable<V> variable : sortedVariables) {
                    variable.setRow(rowCount++);
                }
                LOGGER.debug("Variables index updated ({} rows)", rowCount);

                valid = true;
            }
        }

        @Override
        public void onEquationChange(Equation<V, E> equation, EquationEventType eventType) {
            valid = false;
        }

        @Override
        public void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
            valid = false;
        }

        public NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve() {
            update();
            return sortedEquationsToSolve;
        }

        public NavigableSet<Variable<V>> getSortedVariablesToFind() {
            update();
            return sortedVariables;
        }
    }

    private final IncrementalIndex<V, E> index = new IncrementalIndex<>();

    private final List<EquationSystemListener<V, E>> listeners = new ArrayList<>();

    private final VariableSet<V> variableSet;

    private final StateVector stateVector = new StateVector();

    public EquationSystem() {
        this(false);
    }

    public EquationSystem(boolean indexTerms) {
        this(new VariableSet<>(), indexTerms);
    }

    public EquationSystem(VariableSet<V> variableSet, boolean indexTerms) {
        this.variableSet = Objects.requireNonNull(variableSet);
        this.indexTerms = indexTerms;
        addListener(index);
    }

    public VariableSet<V> getVariableSet() {
        return variableSet;
    }

    public Variable<V> getVariable(int elementNum, V type) {
        return variableSet.getVariable(elementNum, type);
    }

    public StateVector getStateVector() {
        return stateVector;
    }

    public Index<V, E> getIndex() {
        return index;
    }

    void addEquationTerm(EquationTerm<V, E> equationTerm) {
        if (indexTerms) {
            Objects.requireNonNull(equationTerm);
            Pair<ElementType, Integer> element = Pair.of(equationTerm.getElementType(), equationTerm.getElementNum());
            equationTermsByElement.computeIfAbsent(element, k -> new ArrayList<>())
                    .add(equationTerm);
        }
        attach(equationTerm);
    }

    public List<EquationTerm<V, E>> getEquationTerms(ElementType elementType, int elementNum) {
        if (!indexTerms) {
            throw new PowsyblException("Equations terms have not been indexed");
        }
        Objects.requireNonNull(elementType);
        Pair<ElementType, Integer> element = Pair.of(elementType, elementNum);
        return equationTermsByElement.getOrDefault(element, Collections.emptyList());
    }

    public <T extends EquationTerm<V, E>> T getEquationTerm(ElementType elementType, int elementNum, Class<T> clazz) {
        return getEquationTerms(elementType, elementNum)
                .stream()
                .filter(term -> clazz.isAssignableFrom(term.getClass()))
                .map(clazz::cast)
                .findFirst()
                .orElseThrow(() -> new PowsyblException("Equation term not found"));
    }

    public Equation<V, E> createEquation(int num, E type) {
        Pair<Integer, E> p = Pair.of(num, type);
        Equation<V, E> equation = equations.get(p);
        if (equation == null) {
            equation = addEquation(p);
        }
        return equation;
    }

    public Optional<Equation<V, E>> getEquation(int num, E type) {
        Pair<Integer, E> p = Pair.of(num, type);
        return Optional.ofNullable(equations.get(p));
    }

    public boolean hasEquation(int num, E type) {
        Pair<Integer, E> p = Pair.of(num, type);
        return equations.containsKey(p);
    }

    public Equation<V, E> removeEquation(int num, E type) {
        Pair<Integer, E> p = Pair.of(num, type);
        Equation<V, E> equation = equations.remove(p);
        if (equation != null) {
            Pair<ElementType, Integer> element = Pair.of(type.getElementType(), num);
            equationsByElement.remove(element);
            notifyEquationChange(equation, EquationEventType.EQUATION_REMOVED);
        }
        return equation;
    }

    private Equation<V, E> addEquation(Pair<Integer, E> p) {
        Equation<V, E> equation = new Equation<>(p.getLeft(), p.getRight(), EquationSystem.this);
        equations.put(p, equation);
        Pair<ElementType, Integer> element = Pair.of(p.getRight().getElementType(), p.getLeft());
        equationsByElement.computeIfAbsent(element, k -> new ArrayList<>())
                .add(equation);
        notifyEquationChange(equation, EquationEventType.EQUATION_CREATED);
        return equation;
    }

    public List<Equation<V, E>> getEquations(ElementType elementType, int elementNum) {
        Objects.requireNonNull(elementType);
        Pair<ElementType, Integer> element = Pair.of(elementType, elementNum);
        return equationsByElement.getOrDefault(element, Collections.emptyList());
    }

    public void attach(EquationTerm<V, E> term) {
        Objects.requireNonNull(term);
        term.setStateVector(stateVector);
    }

    public List<String> getRowNames(LfNetwork network) {
        return index.getSortedVariablesToFind().stream()
                .map(eq -> network.getBus(eq.getElementNum()).getId() + "/" + eq.getType())
                .collect(Collectors.toList());
    }

    public List<String> getColumnNames(LfNetwork network) {
        return index.getSortedEquationsToSolve().navigableKeySet().stream()
                .map(v -> network.getBus(v.getElementNum()).getId() + "/" + v.getType())
                .collect(Collectors.toList());
    }

    public double[] createEquationVector() {
        double[] fx = new double[index.getSortedEquationsToSolve().size()];
        updateEquationVector(fx);
        return fx;
    }

    public void updateEquationVector(double[] fx) {
        if (fx.length != index.getSortedEquationsToSolve().size()) {
            throw new IllegalArgumentException("Bad equation vector length: " + fx.length);
        }

        Stopwatch stopwatch = Stopwatch.createStarted();

        Arrays.fill(fx, 0);
        for (Equation<V, E> equation : index.getSortedEquationsToSolve().keySet()) {
            fx[equation.getColumn()] = equation.eval();
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Equation vector updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    public void addListener(EquationSystemListener<V, E> listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    public void removeListener(EquationSystemListener<V, E> listener) {
        listeners.remove(listener);
    }

    void notifyEquationChange(Equation<V, E> equation, EquationEventType eventType) {
        Objects.requireNonNull(equation);
        Objects.requireNonNull(eventType);
        listeners.forEach(listener -> listener.onEquationChange(equation, eventType));
    }

    void notifyEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(eventType);
        listeners.forEach(listener -> listener.onEquationTermChange(term, eventType));
    }

    public void write(Writer writer, boolean writeInactiveEquations) {
        try {
            for (Equation<V, E> equation : equations.values().stream().sorted().collect(Collectors.toList())) {
                if (writeInactiveEquations || equation.isActive()) {
                    if (!equation.isActive()) {
                        writer.write("[ ");
                    }
                    equation.write(writer, writeInactiveEquations);
                    if (!equation.isActive()) {
                        writer.write(" ]");
                    }
                    writer.write(System.lineSeparator());
                }
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String writeToString(boolean writeInactiveEquations) {
        try (StringWriter writer = new StringWriter()) {
            write(writer, writeInactiveEquations);
            writer.flush();
            return writer.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String writeToString() {
        return writeToString(false);
    }

    public List<Pair<Equation<V, E>, Double>> findLargestMismatches(double[] mismatch, int count) {
        return index.getSortedEquationsToSolve().keySet().stream()
                .map(equation -> Pair.of(equation, mismatch[equation.getColumn()]))
                .filter(e -> Math.abs(e.getValue()) > Math.pow(10, -7))
                .sorted(Comparator.comparingDouble((Map.Entry<Equation<V, E>, Double> e) -> Math.abs(e.getValue())).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }
}
