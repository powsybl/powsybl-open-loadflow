/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquationSystem.class);

    private final LfNetwork network;

    private final Map<Pair<Integer, EquationType>, Equation> equations = new HashMap<>();

    private interface EquationCache extends EquationSystemListener {

        NavigableSet<Equation> getSortedEquationsToSolve();

        NavigableMap<Variable, NavigableMap<Equation, List<EquationTerm>>> getSortedVariablesToFind();
    }

    private class SimpleEquationCache implements EquationCache {

        private boolean invalide = false;

        private final NavigableSet<Equation> sortedEquationsToSolve = new TreeSet<>();

        private final NavigableMap<Variable, NavigableMap<Equation, List<EquationTerm>>> sortedVariablesToFind = new TreeMap<>();

        private void update() {
            if (!invalide) {
                return;
            }

            Stopwatch stopwatch = Stopwatch.createStarted();

            sortedEquationsToSolve.clear();
            sortedVariablesToFind.clear();

            // index derivatives per variable then per equation
            for (Equation equation : equations.values()) {
                if (equation.isActive()) {
                    sortedEquationsToSolve.add(equation);
                    for (EquationTerm equationTerm : equation.getTerms()) {
                        for (Variable variable : equationTerm.getVariables()) {
                            sortedVariablesToFind.computeIfAbsent(variable, k -> new TreeMap<>())
                                    .computeIfAbsent(equation, k -> new ArrayList<>())
                                    .add(equationTerm);
                        }
                    }
                }
            }

            int rowCount = 0;
            for (Equation equation : sortedEquationsToSolve) {
                equation.setRow(rowCount++);
            }

            int columnCount = 0;
            for (Variable variable : sortedVariablesToFind.keySet()) {
                variable.setColumn(columnCount++);
            }

            invalide = false;

            stopwatch.stop();
            LOGGER.debug(PERFORMANCE_MARKER, "Equation system indexed in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }

        @Override
        public void equationListChanged(Equation equation, EquationEventType eventType) {
            invalide = true;
        }

        @Override
        public NavigableSet<Equation> getSortedEquationsToSolve() {
            update();
            return sortedEquationsToSolve;
        }

        @Override
        public NavigableMap<Variable, NavigableMap<Equation, List<EquationTerm>>> getSortedVariablesToFind() {
            update();
            return sortedVariablesToFind;
        }
    }

    private class IncrementalEquationCache implements EquationCache {

        private final NavigableSet<Equation> sortedEquationsToSolve = new TreeSet<>();

        private final NavigableMap<Variable, NavigableMap<Equation, List<EquationTerm>>> sortedVariablesToFind = new TreeMap<>();

        private boolean invalide = false;

        @Override
        public void equationListChanged(Equation equation, EquationEventType eventType) {
            switch (eventType) {
                case EQUATION_CREATED:
                    if (equation.isActive()) {
                        addEquation(equation);
                    }
                    break;
                case EQUATION_REMOVED:
                    break;
                case EQUATION_ACTIVATED:
                    addEquation(equation);
                    break;
                case EQUATION_DEACTIVATED:
                    break;
            }
        }

        private void addEquation(Equation equation) {
            System.out.println("ADD " + equation);
            sortedEquationsToSolve.add(equation);
            for (EquationTerm equationTerm : equation.getTerms()) {
                for (Variable variable : equationTerm.getVariables()) {
                    sortedVariablesToFind.computeIfAbsent(variable, k -> new TreeMap<>())
                            .computeIfAbsent(equation, k -> new ArrayList<>())
                            .add(equationTerm);
                }
            }
        }

        private void update() {
            if (!invalide) {
                return;
            }

            int rowCount = 0;
            for (Equation equation : sortedEquationsToSolve) {
                equation.setRow(rowCount++);
            }

            int columnCount = 0;
            for (Variable variable : sortedVariablesToFind.keySet()) {
                variable.setColumn(columnCount++);
            }

            invalide = false;
        }

        @Override
        public NavigableSet<Equation> getSortedEquationsToSolve() {
            update();
            return sortedEquationsToSolve;
        }

        @Override
        public NavigableMap<Variable, NavigableMap<Equation, List<EquationTerm>>> getSortedVariablesToFind() {
            update();
            return sortedVariablesToFind;
        }
    }

    private final EquationCache equationCache = new IncrementalEquationCache();

    private final List<EquationSystemListener> listeners = new ArrayList<>();

    public EquationSystem(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
        addListener(equationCache);
    }

    LfNetwork getNetwork() {
        return network;
    }

    public Equation createEquation(int num, EquationType type) {
        Pair<Integer, EquationType> p = Pair.of(num, type);
        Equation equation = equations.get(p);
        if (equation == null) {
            equation = addEquation(p);
        }
        return equation;
    }

    public boolean hasEquation(int num, EquationType type) {
        Pair<Integer, EquationType> p = Pair.of(num, type);
        return equations.containsKey(p);
    }

    public Equation removeEquation(int num, EquationType type) {
        Pair<Integer, EquationType> p = Pair.of(num, type);
        Equation equation = equations.remove(p);
        if (equation != null) {
            notifyListeners(equation, EquationEventType.EQUATION_REMOVED);
        }
        return equation;
    }

    private Equation addEquation(Pair<Integer, EquationType> p) {
        Equation equation = new Equation(p.getLeft(), p.getRight(), EquationSystem.this);
        equations.put(p, equation);
        notifyListeners(equation, EquationEventType.EQUATION_CREATED);
        return equation;
    }

    public SortedSet<Equation> getSortedEquationsToSolve() {
        return equationCache.getSortedEquationsToSolve();
    }

    public NavigableMap<Variable, NavigableMap<Equation, List<EquationTerm>>> getSortedVariablesToFind() {
        return equationCache.getSortedVariablesToFind();
    }

    public List<String> getRowNames() {
        return getSortedEquationsToSolve().stream()
                .map(eq -> network.getBus(eq.getNum()).getId() + "/" + eq.getType())
                .collect(Collectors.toList());
    }

    public List<String> getColumnNames() {
        return getSortedVariablesToFind().navigableKeySet().stream()
                .map(v -> network.getBus(v.getNum()).getId() + "/" + v.getType())
                .collect(Collectors.toList());
    }

    public double[] createStateVector(VoltageInitializer initializer) {
        double[] x = new double[getSortedVariablesToFind().size()];
        for (Variable v : getSortedVariablesToFind().navigableKeySet()) {
            v.initState(initializer, network, x);
        }
        return x;
    }

    public double[] createTargetVector() {
        double[] targets = new double[equationCache.getSortedEquationsToSolve().size()];
        for (Equation equation : equationCache.getSortedEquationsToSolve()) {
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
        for (Equation equation : equationCache.getSortedEquationsToSolve()) {
            fx[equation.getRow()] = equation.eval();
        }
    }

    public void updateEquations(double[] x) {
        for (Equation equation : equations.values()) {
            equation.update(x);
        }
    }

    public void updateNetwork(double[] x) {
        // update state variable
        for (Variable v : getSortedVariablesToFind().navigableKeySet()) {
            v.updateState(network, x);
        }
    }

    public void addListener(EquationSystemListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    void notifyListeners(Equation equation, EquationEventType eventType) {
        Objects.requireNonNull(equation);
        Objects.requireNonNull(eventType);
        listeners.forEach(listener -> listener.equationListChanged(equation, eventType));
    }

    public void write(Writer writer) {
        try {
            for (Equation equation : getSortedEquationsToSolve()) {
                equation.write(writer);
                writer.write(System.lineSeparator());
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
