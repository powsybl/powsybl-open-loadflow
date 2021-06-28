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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationSystem {

    private final LfNetwork network;

    private final boolean indexTerms;

    private final Map<Pair<Integer, EquationType>, Equation> equations = new HashMap<>();

    private final Map<Pair<ElementType, Integer>, List<Equation>> equationsBySubject = new HashMap<>();

    private final Map<Pair<ElementType, Integer>, List<EquationTerm>> equationTermsBySubject = new HashMap<>();

    private class EquationCache implements EquationSystemListener {

        private final NavigableMap<Equation, NavigableMap<Variable, List<EquationTerm>>> sortedEquationsToSolve = new TreeMap<>();

        private final NavigableMap<Variable, Set<Equation>> sortedVariablesToFind = new TreeMap<>();

        private final Set<Equation> equationsToRemove = new HashSet<>();

        private final Set<Equation> equationsToAdd = new HashSet<>();

        private void update() {
            if (reIndex()) {
                int columnCount = 0;
                for (Equation equation : sortedEquationsToSolve.keySet()) {
                    equation.setColumn(columnCount++);
                }

                int rowCount = 0;
                for (Variable variable : sortedVariablesToFind.keySet()) {
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
            for (Equation equation : equationsToRemove) {
                sortedEquationsToSolve.remove(equation);
                for (EquationTerm equationTerm : equation.getTerms()) {
                    for (Variable variable : equationTerm.getVariables()) {
                        Set<Equation> equationsUsingThisVariable = sortedVariablesToFind.get(variable);
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
            for (Equation equation : equationsToAdd) {
                if (equation.isActive() && EquationUpdateType.DEFAULT == equation.getUpdateType()) {
                    for (EquationTerm equationTerm : equation.getTerms()) {
                        if (equationTerm.isActive()) {
                            for (Variable variable : equationTerm.getVariables()) {
                                sortedEquationsToSolve.computeIfAbsent(equation, k -> new TreeMap<>())
                                        .computeIfAbsent(variable, k -> new ArrayList<>())
                                        .add(equationTerm);
                                sortedVariablesToFind.computeIfAbsent(variable, k -> new TreeSet<>())
                                        .add(equation);
                            }
                        }
                    }
                }
            }

            equationsToRemove.clear();
            equationsToAdd.clear();

            return true;
        }

        @Override
        public void onEquationChange(Equation equation, EquationEventType eventType) {
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
        public void onEquationTermChange(EquationTerm term, EquationTermEventType eventType) {
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

        @Override
        public void onStateUpdate(double[] x) {
            // nothing to do
        }

        private NavigableMap<Equation, NavigableMap<Variable, List<EquationTerm>>> getSortedEquationsToSolve() {
            update();
            return sortedEquationsToSolve;
        }

        private NavigableSet<Variable> getSortedVariablesToFind() {
            update();
            return sortedVariablesToFind.navigableKeySet();
        }
    }

    private final EquationCache equationCache = new EquationCache();

    private final List<EquationSystemListener> listeners = new ArrayList<>();

    public enum EquationUpdateType {
        DEFAULT,
        AFTER_NR,
        NEVER
    }

    public EquationSystem(LfNetwork network) {
        this(network, false);
    }

    public EquationSystem(LfNetwork network, boolean indexTerms) {
        this.network = Objects.requireNonNull(network);
        this.indexTerms = indexTerms;
        addListener(equationCache);
    }

    LfNetwork getNetwork() {
        return network;
    }

    void addEquationTerm(EquationTerm equationTerm) {
        if (indexTerms) {
            Objects.requireNonNull(equationTerm);
            Pair<ElementType, Integer> subject = Pair.of(equationTerm.getElementType(), equationTerm.getElementNum());
            equationTermsBySubject.computeIfAbsent(subject, k -> new ArrayList<>())
                    .add(equationTerm);
        }
    }

    public List<EquationTerm> getEquationTerms(ElementType elementType, int elementNum) {
        if (!indexTerms) {
            throw new PowsyblException("Equations terms have not been indexed");
        }
        Objects.requireNonNull(elementType);
        Pair<ElementType, Integer> subject = Pair.of(elementType, elementNum);
        return equationTermsBySubject.getOrDefault(subject, Collections.emptyList());
    }

    public <T extends EquationTerm> T getEquationTerm(ElementType elementType, int elementNum, Class<T> clazz) {
        return getEquationTerms(elementType, elementNum)
                .stream()
                .filter(term -> clazz.isAssignableFrom(term.getClass()))
                .map(clazz::cast)
                .findFirst()
                .orElseThrow(() -> new PowsyblException("Equation term not found"));
    }

    public Equation createEquation(int num, EquationType type) {
        Pair<Integer, EquationType> p = Pair.of(num, type);
        Equation equation = equations.get(p);
        if (equation == null) {
            equation = addEquation(p);
        }
        return equation;
    }

    public Optional<Equation> getEquation(int num, EquationType type) {
        Pair<Integer, EquationType> p = Pair.of(num, type);
        return Optional.ofNullable(equations.get(p));
    }

    public boolean hasEquation(int num, EquationType type) {
        Pair<Integer, EquationType> p = Pair.of(num, type);
        return equations.containsKey(p);
    }

    public Equation removeEquation(int num, EquationType type) {
        Pair<Integer, EquationType> p = Pair.of(num, type);
        Equation equation = equations.remove(p);
        if (equation != null) {
            Pair<ElementType, Integer> subject = Pair.of(type.getElementType(), num);
            equationsBySubject.remove(subject);
            notifyEquationChange(equation, EquationEventType.EQUATION_REMOVED);
        }
        return equation;
    }

    private Equation addEquation(Pair<Integer, EquationType> p) {
        Equation equation = new Equation(p.getLeft(), p.getRight(), EquationSystem.this);
        equations.put(p, equation);
        Pair<ElementType, Integer> subject = Pair.of(p.getRight().getElementType(), p.getLeft());
        equationsBySubject.computeIfAbsent(subject, k -> new ArrayList<>())
                .add(equation);
        notifyEquationChange(equation, EquationEventType.EQUATION_CREATED);
        return equation;
    }

    public List<Equation> getEquations(ElementType elementType, int elementNum) {
        Objects.requireNonNull(elementType);
        Pair<ElementType, Integer> subject = Pair.of(elementType, elementNum);
        return equationsBySubject.getOrDefault(subject, Collections.emptyList());
    }

    public SortedSet<Variable> getSortedVariablesToFind() {
        return equationCache.getSortedVariablesToFind();
    }

    public NavigableMap<Equation, NavigableMap<Variable, List<EquationTerm>>> getSortedEquationsToSolve() {
        return equationCache.getSortedEquationsToSolve();
    }

    public List<String> getRowNames() {
        return getSortedVariablesToFind().stream()
                .map(eq -> network.getBus(eq.getNum()).getId() + "/" + eq.getType())
                .collect(Collectors.toList());
    }

    public List<String> getColumnNames() {
        return getSortedEquationsToSolve().navigableKeySet().stream()
                .map(v -> network.getBus(v.getNum()).getId() + "/" + v.getType())
                .collect(Collectors.toList());
    }

    public double[] createStateVector(VoltageInitializer initializer) {
        double[] x = new double[getSortedVariablesToFind().size()];
        for (Variable v : getSortedVariablesToFind()) {
            v.initState(initializer, network, x);
        }
        return x;
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
        for (Equation equation : equationCache.getSortedEquationsToSolve().keySet()) {
            fx[equation.getColumn()] = equation.eval();
        }
    }

    public void updateEquations(double[] x) {
        updateEquations(x, EquationUpdateType.DEFAULT);
    }

    public void updateEquations(double[] x, EquationUpdateType updateType) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(updateType);
        for (Equation equation : equations.values()) {
            if (updateType == equation.getUpdateType()) {
                equation.update(x);
            }
        }
        listeners.forEach(listener -> listener.onStateUpdate(x));
    }

    public void updateNetwork(double[] x) {
        // update state variable
        for (Variable v : getSortedVariablesToFind()) {
            v.updateState(network, x);
        }
    }

    public void addListener(EquationSystemListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    public void removeListener(EquationSystemListener listener) {
        listeners.remove(listener);
    }

    void notifyEquationChange(Equation equation, EquationEventType eventType) {
        Objects.requireNonNull(equation);
        Objects.requireNonNull(eventType);
        listeners.forEach(listener -> listener.onEquationChange(equation, eventType));
    }

    void notifyEquationTermChange(EquationTerm term, EquationTermEventType eventType) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(eventType);
        listeners.forEach(listener -> listener.onEquationTermChange(term, eventType));
    }

    public void write(Writer writer) {
        try {
            for (Equation equation : getSortedEquationsToSolve().navigableKeySet()) {
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

    public List<Pair<Equation, Double>> findLargestMismatches(double[] mismatch, int count) {
        return getSortedEquationsToSolve().keySet().stream()
                .map(equation -> Pair.of(equation, mismatch[equation.getColumn()]))
                .filter(e -> Math.abs(e.getValue()) > Math.pow(10, -7))
                .sorted(Comparator.comparingDouble((Map.Entry<Equation, Double> e) -> Math.abs(e.getValue())).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }
}
