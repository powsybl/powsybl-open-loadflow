/**
 * Copyright (c) 2021
 * , RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Writer;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationSubsystem<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationSystem<V, E> {

    private final EquationSystem<V, E> fullSystem;

    private final Predicate<Equation<V, E>> filter;

    private final EquationSystem<V, E> subSystem = new EquationSystemImpl<>();

    public EquationSubsystem(EquationSystem<V, E> fullSystem, Predicate<Equation<V, E>> filter) {
        this.fullSystem = Objects.requireNonNull(fullSystem);
        this.filter = Objects.requireNonNull(filter);
        for (Equation<V, E> equation : fullSystem.getEquations()) {

        }
    }

    public static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> EquationSystem<V, E> filter(EquationSystem<V, E> fullSystem, Predicate<Equation<V, E>> filter) {
        return new EquationSubsystem<>(fullSystem, filter);
    }

    public static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> EquationSystem<V, E> filterBuses(EquationSystem<V, E> fullSystem, Set<Integer> busesNum) {
        return filter(fullSystem, eq -> eq.getType().getElementType() == ElementType.BUS && busesNum.contains(eq.getNum()));
    }


    @Override
    public Equation<V, E> createEquation(int num, E type) {
        return null;
    }

    @Override
    public Collection<Equation<V, E>> getEquations() {
        return null;
    }

    @Override
    public List<Equation<V, E>> getEquations(ElementType elementType, int elementNum) {
        return null;
    }

    @Override
    public Optional<Equation<V, E>> getEquation(int num, E type) {
        return Optional.empty();
    }

    @Override
    public boolean hasEquation(int num, E type) {
        return false;
    }

    @Override
    public Equation<V, E> removeEquation(int num, E type) {
        return null;
    }

    @Override
    public void addEquationTerm(EquationTerm<V, E> equationTerm) {

    }

    @Override
    public List<EquationTerm<V, E>> getEquationTerms(ElementType elementType, int elementNum) {
        return null;
    }

    @Override
    public <T extends EquationTerm<V, E>> T getEquationTerm(ElementType elementType, int elementNum, Class<T> clazz) {
        return null;
    }

    @Override
    public SortedSet<Variable<V>> getSortedVariablesToFind() {
        return null;
    }

    @Override
    public NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve() {
        return null;
    }

    @Override
    public List<String> getRowNames(LfNetwork network) {
        return null;
    }

    @Override
    public List<String> getColumnNames(LfNetwork network) {
        return null;
    }

    @Override
    public double[] createEquationVector() {
        return new double[0];
    }

    @Override
    public void updateEquationVector(double[] fx) {

    }

    @Override
    public void updateEquations(double[] x) {

    }

    @Override
    public void updateEquations(double[] x, EquationUpdateType updateType) {

    }

    @Override
    public void addListener(EquationSystemListener<V, E> listener) {

    }

    @Override
    public void removeListener(EquationSystemListener<V, E> listener) {

    }

    @Override
    public void notifyEquationChange(Equation<V, E> equation, EquationEventType eventType) {

    }

    @Override
    public void notifyEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {

    }

    @Override
    public void write(Writer writer) {

    }

    @Override
    public List<Pair<Equation<V, E>, Double>> findLargestMismatches(double[] mismatch, int count) {
        return null;
    }
}
