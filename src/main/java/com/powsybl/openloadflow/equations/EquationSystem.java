/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationSystem<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    private final Map<Pair<Integer, E>, Equation<V, E>> equations = new HashMap<>();

    private final Map<Pair<ElementType, Integer>, List<Equation<V, E>>> equationsByElement = new HashMap<>();

    private Map<Pair<ElementType, Integer>, List<EquationTerm<V, E>>> equationTermsByElement;

    private final Map<E, EquationArray<V, E>> equationArrays;

    private final List<EquationSystemListener<V, E>> listeners = new ArrayList<>();

    private final LfNetwork network;

    private final VariableSet<V> variableSet;

    private final StateVector stateVector = new StateVector();

    private final EquationSystemIndex<V, E> index;

    public EquationSystem(Class<E> equationClass, LfNetwork network) {
        this(equationClass, network, new VariableSet<>());
    }

    public EquationSystem(Class<E> equationClass, LfNetwork network, VariableSet<V> variableSet) {
        this.network = Objects.requireNonNull(network);
        this.variableSet = Objects.requireNonNull(variableSet);
        index = new EquationSystemIndex<>(this);
        equationArrays = new EnumMap<>(equationClass);
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

    public EquationSystemIndex<V, E> getIndex() {
        return index;
    }

    public Collection<Equation<V, E>> getEquations() {
        return equations.values();
    }

    private void indexTerm(EquationTerm<V, E> equationTerm) {
        if (equationTermsByElement != null) {
            if (equationTerm.getElementType() != null && equationTerm.getElementNum() != -1) {
                Pair<ElementType, Integer> element = Pair.of(equationTerm.getElementType(), equationTerm.getElementNum());
                equationTermsByElement.computeIfAbsent(element, k -> new ArrayList<>())
                        .add(equationTerm);
            }
            for (EquationTerm<V, E> child : equationTerm.getChildren()) {
                indexTerm(child);
            }
        }
    }

    private void indexAllTerms() {
        if (equationTermsByElement == null) {
            equationTermsByElement = new HashMap<>();
            for (var equation : equations.values()) {
                for (var term : equation.getTerms()) {
                    indexTerm(term);
                }
            }
        }
    }

    void addEquationTerm(EquationTerm<V, E> equationTerm) {
        indexTerm(equationTerm);
        attach(equationTerm);
    }

    public List<EquationTerm<V, E>> getEquationTerms(ElementType elementType, int elementNum) {
        Objects.requireNonNull(elementType);
        indexAllTerms();
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

    public Equation<V, E> createEquation(LfElement element, E type) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(type);
        if (element.getType() != type.getElementType()) {
            throw new PowsyblException("Incorrect equation type: " + type);
        }
        Pair<Integer, E> p = Pair.of(element.getNum(), type);
        Equation<V, E> equation = equations.get(p);
        if (equation == null) {
            equation = addEquation(p)
                    .setActive(!element.isDisabled());
        }
        return equation;
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
            equationsByElement.get(element).remove(equation);
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

    public EquationArray<V, E> createEquationArray(E type) {
        Objects.requireNonNull(type);
        EquationArray<V, E> equationArray = equationArrays.get(type);
        if (equationArray == null) {
            equationArray = new EquationArray<>(type, network.getElementCount(type.getElementType()), this);
            equationArrays.put(type, equationArray);
        }
        return equationArray;
    }

    public Collection<EquationArray<V, E>> getEquationArrays() {
        return equationArrays.values();
    }

    public List<String> getRowNames() {
        return index.getSortedVariablesToFind().stream()
                .map(eq -> network.getBus(eq.getElementNum()).getId() + "/" + eq.getType())
                .collect(Collectors.toList());
    }

    public List<String> getColumnNames() {
        return index.getSortedEquationsToSolve().stream()
                .map(v -> network.getBus(v.getElementNum()).getId() + "/" + v.getType())
                .collect(Collectors.toList());
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
}
