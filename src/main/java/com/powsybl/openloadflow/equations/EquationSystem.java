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

    private static class EquationCache implements EquationSystemListener {

        private final NavigableSet<Equation> sortedEquationsToSolve = new TreeSet<>();

        private final NavigableMap<Variable, NavigableMap<Equation, List<EquationTerm>>> sortedVariablesToFind = new TreeMap<>();

        private final Set<Equation> equationsToRemove = new HashSet<>();

        private final Set<Equation> equationsToAdd = new HashSet<>();

        private void update() {
            if (equationsToAdd.isEmpty() && equationsToRemove.isEmpty()) {
                return;
            }

            Stopwatch stopwatch = Stopwatch.createStarted();

            // equations to remove
            for (Equation equation : equationsToRemove) {
                sortedEquationsToSolve.remove(equation);
                for (EquationTerm equationTerm : equation.getTerms()) {
                    for (Variable variable : equationTerm.getVariables()) {
                        NavigableMap<Equation, List<EquationTerm>> equationTermsForThisVariable = sortedVariablesToFind.get(variable);
                        if (equationTermsForThisVariable != null) {
                            equationTermsForThisVariable.remove(equation);
                            if (equationTermsForThisVariable.isEmpty()) {
                                sortedVariablesToFind.remove(variable);
                            }
                        }
                    }
                }
            }

            // equations to add
            for (Equation equation : equationsToAdd) {
                sortedEquationsToSolve.add(equation);
                for (EquationTerm equationTerm : equation.getTerms()) {
                    for (Variable variable : equationTerm.getVariables()) {
                        sortedVariablesToFind.computeIfAbsent(variable, k -> new TreeMap<>())
                                .computeIfAbsent(equation, k -> new ArrayList<>())
                                .add(equationTerm);
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

            equationsToRemove.clear();
            equationsToAdd.clear();

            stopwatch.stop();
            LOGGER.debug(PERFORMANCE_MARKER, "Equation system indexed in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }

        @Override
        public void equationListChanged(Equation equation, EquationEventType eventType) {
            switch (eventType) {
                case EQUATION_CREATED:
                    break;
                case EQUATION_REMOVED:
                case EQUATION_DEACTIVATED:
                    if (!sortedEquationsToSolve.isEmpty()) { // not need to remove if not already indexed
                        equationsToRemove.add(equation);
                    }
                    equationsToAdd.remove(equation);
                    break;
                case EQUATION_UPDATED:
                    // no need to replace the equation if not yet activated
                    if (equation.isActive()) {
                        if (!sortedEquationsToSolve.isEmpty()) { // not need to remove if not already indexed
                            equationsToRemove.add(equation);
                        }
                        equationsToAdd.add(equation);
                    }
                    break;
                case EQUATION_ACTIVATED:
                    // no need to remove first because activated event means it was not already activated
                    equationsToAdd.add(equation);
                    break;
            }
        }

        private NavigableSet<Equation> getSortedEquationsToSolve() {
            update();
            return sortedEquationsToSolve;
        }

        private NavigableMap<Variable, NavigableMap<Equation, List<EquationTerm>>> getSortedVariablesToFind() {
            update();
            return sortedVariablesToFind;
        }
    }

    private final EquationCache equationCache = new EquationCache();

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
        if (!listeners.isEmpty()) {
            listeners.forEach(listener -> listener.equationListChanged(equation, eventType));
        }
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
