/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Markers;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquationSystem.class);

    private final LfNetwork network;

    private final boolean indexTerms;

    private final Map<Pair<Integer, EquationType>, Equation> equations = new HashMap<>();

    private final Map<Pair<SubjectType, Integer>, List<Equation>> equationsBySubject = new HashMap<>();

    private final Map<Pair<SubjectType, Integer>, List<EquationTerm>> equationTermsBySubject = new HashMap<>();

    private class EquationCache implements EquationSystemListener {

        private boolean invalide = false;

        private final NavigableMap<Equation, NavigableMap<Variable, List<EquationTerm>>> sortedEquationsToSolve = new TreeMap<>();

        private final NavigableSet<Variable> sortedVariablesToFind = new TreeSet<>();

        private void update() {
            if (!invalide) {
                return;
            }

            Stopwatch stopwatch = Stopwatch.createStarted();

            // index derivatives per variable then per equation
            reIndex();

            int columnCount = 0;
            for (Equation equation : sortedEquationsToSolve.keySet()) {
                equation.setColumn(columnCount++);
            }

            int rowCount = 0;
            for (Variable variable : sortedVariablesToFind) {
                variable.setRow(rowCount++);
            }

            stopwatch.stop();
            LOGGER.debug(Markers.PERFORMANCE_MARKER, "Equation system ({} equations, {} variables) updated in {} ms",
                    columnCount, rowCount, stopwatch.elapsed(TimeUnit.MILLISECONDS));

            invalide = false;
        }

        private void reIndex() {
            sortedEquationsToSolve.clear();
            sortedVariablesToFind.clear();

            Set<Variable> variablesToFind = new HashSet<>();
            for (Equation equation : equations.values()) {
                if (equation.isActive()) {
                    NavigableMap<Variable, List<EquationTerm>> equationTermsByVariable = null;
                    // check we have at least one equation term active
                    boolean atLeastOneTermIsValid = false;
                    for (EquationTerm equationTerm : equation.getTerms()) {
                        if (equationTerm.isActive()) {
                            atLeastOneTermIsValid = true;
                            if (equationTermsByVariable == null) {
                                equationTermsByVariable = sortedEquationsToSolve.computeIfAbsent(equation, k -> new TreeMap<>());
                            }
                            for (Variable variable : equationTerm.getVariables()) {
                                if (variable.isActive()) {
                                    equationTermsByVariable.computeIfAbsent(variable, k -> new ArrayList<>())
                                            .add(equationTerm);
                                    variablesToFind.add(variable);
                                }
                            }
                        }
                    }
                    if (!atLeastOneTermIsValid) {
                        throw new IllegalStateException("Equation " + equation + " is active but all of its terms are inactive");
                    }
                }
            }
            sortedVariablesToFind.addAll(variablesToFind);
        }

        private void invalidate() {
            invalide = true;
        }

        @Override
        public void onEquationChange(Equation equation, EquationEventType eventType) {
            switch (eventType) {
                case EQUATION_CREATED:
                case EQUATION_REMOVED:
                case EQUATION_ACTIVATED:
                case EQUATION_DEACTIVATED:
                    invalidate();
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
                    invalidate();
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
            return sortedVariablesToFind;
        }
    }

    private final EquationCache equationCache = new EquationCache();

    private final List<EquationSystemListener> listeners = new ArrayList<>();

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
            Pair<SubjectType, Integer> subject = Pair.of(equationTerm.getSubjectType(), equationTerm.getSubjectNum());
            equationTermsBySubject.computeIfAbsent(subject, k -> new ArrayList<>())
                    .add(equationTerm);
        }
    }

    public List<EquationTerm> getEquationTerms(SubjectType subjectType, int subjectNum) {
        if (!indexTerms) {
            throw new PowsyblException("Equations terms have not been indexed");
        }
        Objects.requireNonNull(subjectType);
        Pair<SubjectType, Integer> subject = Pair.of(subjectType, subjectNum);
        return equationTermsBySubject.getOrDefault(subject, Collections.emptyList());
    }

    public <T extends EquationTerm> T getEquationTerm(SubjectType subjectType, int subjectNum, Class<T> clazz) {
        return getEquationTerms(subjectType, subjectNum)
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
            Pair<SubjectType, Integer> subject = Pair.of(type.getSubjectType(), num);
            equationsBySubject.remove(subject);
            notifyEquationChange(equation, EquationEventType.EQUATION_REMOVED);
        }
        return equation;
    }

    private Equation addEquation(Pair<Integer, EquationType> p) {
        Equation equation = new Equation(p.getLeft(), p.getRight(), EquationSystem.this);
        equations.put(p, equation);
        Pair<SubjectType, Integer> subject = Pair.of(p.getRight().getSubjectType(), p.getLeft());
        equationsBySubject.computeIfAbsent(subject, k -> new ArrayList<>())
                .add(equation);
        notifyEquationChange(equation, EquationEventType.EQUATION_CREATED);
        return equation;
    }

    public List<Equation> getEquations(SubjectType subjectType, int subjectNum) {
        Objects.requireNonNull(subjectType);
        Pair<SubjectType, Integer> subject = Pair.of(subjectType, subjectNum);
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

    public double[] createTargetVector() {
        double[] targets = new double[equationCache.getSortedEquationsToSolve().size()];
        for (Equation equation : equationCache.getSortedEquationsToSolve().keySet()) {
            equation.initTarget(network, targets);
        }
        return targets;
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
        Objects.requireNonNull(x);
        for (Equation equation : equations.values()) {
            equation.update(x);
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
}
