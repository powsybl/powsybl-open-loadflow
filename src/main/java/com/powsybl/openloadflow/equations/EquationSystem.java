/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationSystem<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquationSystem.class);

    private final boolean indexTerms;

    private final Map<Pair<Integer, E>, Equation<V, E>> equations = new HashMap<>();

    private final Map<Pair<ElementType, Integer>, List<Equation<V, E>>> equationsBySubject = new HashMap<>();

    private final Map<Pair<ElementType, Integer>, List<EquationTerm<V, E>>> equationTermsBySubject = new HashMap<>();

    private class EquationCache implements EquationSystemListener<V, E> {

        private final NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> sortedEquationsToSolve = new TreeMap<>();

        private final NavigableMap<Variable<V>, Set<Equation<V, E>>> sortedVariablesToFind = new TreeMap<>();

        private final Set<Equation<V, E>> equationsToRemove = new HashSet<>();

        private final Set<Equation<V, E>> equationsToAdd = new HashSet<>();

        private void update() {
            if (reIndex()) {
                LOGGER.debug("Reindex equation system");

                int columnCount = 0;
                for (Equation<V, E> equation : sortedEquationsToSolve.keySet()) {
                    equation.setColumn(columnCount++);
                }

                int rowCount = 0;
                for (Variable<V> variable : sortedVariablesToFind.keySet()) {
                    variable.setRow(rowCount++);
                }
            }
        }

        private boolean reIndex() {
            if (equationsToAdd.isEmpty() && equationsToRemove.isEmpty()) {
                return false;
            }

            // index derivatives per variable then per equation

            // equations to remove
            for (Equation<V, E> equation : equationsToRemove) {
                sortedEquationsToSolve.remove(equation);
                for (EquationTerm<V, E> equationTerm : equation.getTerms()) {
                    for (Variable<V> variable : equationTerm.getVariables()) {
                        Set<Equation<V, E>> equationsUsingThisVariable = sortedVariablesToFind.get(variable);
                        if (equationsUsingThisVariable != null) {
                            equationsUsingThisVariable.remove(equation);
                            if (equationsUsingThisVariable.isEmpty()) {
                                sortedVariablesToFind.remove(variable);
                            }
                        }
                    }
                }
            }

            // equations to add
            for (Equation<V, E> equation : equationsToAdd) {
                // do not use equations that would be updated only after NR
                if (equation.isActive()) {
                    // check we have at least one equation term active
                    boolean atLeastOneTermIsValid = false;
                    for (EquationTerm<V, E> equationTerm : equation.getTerms()) {
                        if (equationTerm.isActive()) {
                            atLeastOneTermIsValid = true;
                            for (Variable<V> variable : equationTerm.getVariables()) {
                                sortedEquationsToSolve.computeIfAbsent(equation, k -> new TreeMap<>())
                                        .computeIfAbsent(variable, k -> new ArrayList<>())
                                        .add(equationTerm);
                                sortedVariablesToFind.computeIfAbsent(variable, k -> new TreeSet<>())
                                        .add(equation);
                            }
                        }
                    }
                    if (!atLeastOneTermIsValid) {
                        throw new IllegalStateException("Equation " + equation + " is active but all of its terms are inactive");
                    }
                }
            }

            equationsToRemove.clear();
            equationsToAdd.clear();

            return true;
        }

        @Override
        public void onEquationChange(Equation<V, E> equation, EquationEventType eventType) {
            switch (eventType) {
                case EQUATION_REMOVED:
                case EQUATION_DEACTIVATED:
                    if (!sortedEquationsToSolve.isEmpty()) { // not need to remove if not already indexed
                        equationsToRemove.add(equation);
                    }
                    equationsToAdd.remove(equation);
                    break;

                case EQUATION_CREATED:
                case EQUATION_ACTIVATED:
                    // no need to remove first because activated event means it was not already activated
                    equationsToAdd.add(equation);
                    break;

                default:
                    throw new IllegalStateException("Event type not supported: " + eventType);
            }
        }

        @Override
        public void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
            switch (eventType) {
                case EQUATION_TERM_ADDED:
                case EQUATION_TERM_ACTIVATED:
                case EQUATION_TERM_DEACTIVATED:
                    if (!sortedEquationsToSolve.isEmpty()) { // not need to remove if not already indexed
                        equationsToRemove.add(term.getEquation());
                    }
                    equationsToAdd.add(term.getEquation());
                    break;

                default:
                    throw new IllegalStateException("Event type not supported: " + eventType);
            }
        }

        private NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve() {
            update();
            return sortedEquationsToSolve;
        }

        private NavigableSet<Variable<V>> getSortedVariablesToFind() {
            update();
            return sortedVariablesToFind.navigableKeySet();
        }
    }

    private final EquationCache equationCache = new EquationCache();

    private final List<EquationSystemListener<V, E>> listeners = new ArrayList<>();

    private final StateVector stateVector = new StateVector();

    public EquationSystem() {
        this(false);
    }

    public EquationSystem(boolean indexTerms) {
        this.indexTerms = indexTerms;
        addListener(equationCache);
    }

    public StateVector getStateVector() {
        return stateVector;
    }

    void addEquationTerm(EquationTerm<V, E> equationTerm) {
        if (indexTerms) {
            Objects.requireNonNull(equationTerm);
            Pair<ElementType, Integer> subject = Pair.of(equationTerm.getElementType(), equationTerm.getElementNum());
            equationTermsBySubject.computeIfAbsent(subject, k -> new ArrayList<>())
                    .add(equationTerm);
        }
    }

    public List<EquationTerm<V, E>> getEquationTerms(ElementType elementType, int elementNum) {
        if (!indexTerms) {
            throw new PowsyblException("Equations terms have not been indexed");
        }
        Objects.requireNonNull(elementType);
        Pair<ElementType, Integer> subject = Pair.of(elementType, elementNum);
        return equationTermsBySubject.getOrDefault(subject, Collections.emptyList());
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
            Pair<ElementType, Integer> subject = Pair.of(type.getElementType(), num);
            equationsBySubject.remove(subject);
            notifyEquationChange(equation, EquationEventType.EQUATION_REMOVED);
        }
        return equation;
    }

    private Equation<V, E> addEquation(Pair<Integer, E> p) {
        Equation<V, E> equation = new Equation<>(p.getLeft(), p.getRight(), EquationSystem.this);
        equations.put(p, equation);
        Pair<ElementType, Integer> subject = Pair.of(p.getRight().getElementType(), p.getLeft());
        equationsBySubject.computeIfAbsent(subject, k -> new ArrayList<>())
                .add(equation);
        notifyEquationChange(equation, EquationEventType.EQUATION_CREATED);
        return equation;
    }

    public List<Equation<V, E>> getEquations(ElementType elementType, int elementNum) {
        Objects.requireNonNull(elementType);
        Pair<ElementType, Integer> subject = Pair.of(elementType, elementNum);
        return equationsBySubject.getOrDefault(subject, Collections.emptyList());
    }

    public SortedSet<Variable<V>> getSortedVariablesToFind() {
        return equationCache.getSortedVariablesToFind();
    }

    public NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve() {
        return equationCache.getSortedEquationsToSolve();
    }

    public List<String> getRowNames(LfNetwork network) {
        return getSortedVariablesToFind().stream()
                .map(eq -> network.getBus(eq.getNum()).getId() + "/" + eq.getType())
                .collect(Collectors.toList());
    }

    public List<String> getColumnNames(LfNetwork network) {
        return getSortedEquationsToSolve().navigableKeySet().stream()
                .map(v -> network.getBus(v.getNum()).getId() + "/" + v.getType())
                .collect(Collectors.toList());
    }

    public double[] createEquationVector() {
        double[] fx = new double[equationCache.getSortedEquationsToSolve().size()];
        updateEquationVector(fx);
        return fx;
    }

    public void updateEquationVector(double[] fx) {
        if (fx.length != equationCache.getSortedEquationsToSolve().size()) {
            throw new IllegalArgumentException("Bad equation vector length: " + fx.length);
        }
        Arrays.fill(fx, 0);
        for (Equation<V, E> equation : equationCache.getSortedEquationsToSolve().keySet()) {
            fx[equation.getColumn()] = equation.eval();
        }
    }

    public void updateEquations() {
        updateEquations(EquationUpdateType.DEFAULT);
    }

    public void updateEquations(EquationUpdateType updateType) {
        Objects.requireNonNull(updateType);
        for (Equation<V, E> equation : equations.values()) {
            equation.update(stateVector);
        }
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

    public void write(Writer writer) {
        try {
            for (Equation<V, E> equation : getSortedEquationsToSolve().navigableKeySet()) {
                if (equation.isActive()) {
                    equation.write(writer);
                    writer.write(System.lineSeparator());
                }
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Pair<Equation<V, E>, Double>> findLargestMismatches(double[] mismatch, int count) {
        return getSortedEquationsToSolve().keySet().stream()
                .map(equation -> Pair.of(equation, mismatch[equation.getColumn()]))
                .filter(e -> Math.abs(e.getValue()) > Math.pow(10, -7))
                .sorted(Comparator.comparingDouble((Map.Entry<Equation<V, E>, Double> e) -> Math.abs(e.getValue())).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }
}
