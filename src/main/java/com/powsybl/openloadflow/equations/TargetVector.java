/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.*;

import java.util.NavigableSet;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class TargetVector<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractLfNetworkListener
        implements EquationSystemIndexListener<V, E> {

    @FunctionalInterface
    public interface Initializer<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        void initialize(Equation<V, E> equation, LfNetwork network, double[] targets);
    }

    private final LfNetwork network;

    private final EquationSystem<V, E> equationSystem;

    private final Initializer<V, E> initializer;

    private double[] array;

    private enum Status {
        VALID,
        VECTOR_INVALID, // structure has changed
        VALUES_INVALID // same structure but values have to be updated
    }

    private Status status = Status.VECTOR_INVALID;

    public TargetVector(LfNetwork network, EquationSystem<V, E> equationSystem, Initializer<V, E> initializer) {
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.initializer = Objects.requireNonNull(initializer);
        network.addListener(this);
        equationSystem.getIndex().addListener(this);
    }

    private void invalidateValues() {
        if (status == Status.VALID) {
            status = Status.VALUES_INVALID;
        }
    }

    @Override
    public void onLoadActivePowerTargetChange(LfBus bus, double oldLoadTargetP, double newLoadTargetP) {
        invalidateValues();
    }

    @Override
    public void onLoadReactivePowerTargetChange(LfBus bus, double oldLoadTargetQ, double newLoadTargetQ) {
        invalidateValues();
    }

    @Override
    public void onGenerationActivePowerTargetChange(LfGenerator generator, double oldGenerationTargetP, double newGenerationTargetP) {
        invalidateValues();
    }

    @Override
    public void onGenerationReactivePowerTargetChange(LfBus bus, double oldGenerationTargetQ, double newGenerationTargetQ) {
        invalidateValues();
    }

    @Override
    public void onDiscretePhaseControlTapPositionChange(LfBranch controllerBranch, int oldPosition, int newPosition) {
        invalidateValues();
    }

    @Override
    public void onEquationChange(Equation<V, E> equation, ChangeType changeType) {
        status = Status.VECTOR_INVALID;
    }

    @Override
    public void onVariableChange(Variable<V> variable, ChangeType changeType) {
        // nothing to do
    }

    @Override
    public void onEquationTermChange(EquationTerm<V, E> term) {
        // nothing to do
    }

    public double[] toArray() {
        switch (status) {
            case VECTOR_INVALID:
                createArray();
                break;

            case VALUES_INVALID:
                updateArray();
                break;

            default:
                // nothing to do
                break;
        }
        return array;
    }

    public static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> double[] createArray(LfNetwork network, EquationSystem<V, E> equationSystem, Initializer<V, E> initializer) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(equationSystem);
        Objects.requireNonNull(initializer);
        NavigableSet<Equation<V, E>> sortedEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve();
        double[] array = new double[sortedEquationsToSolve.size()];
        for (Equation<V, E> equation : sortedEquationsToSolve) {
            initializer.initialize(equation, network, array);
        }
        return array;
    }

    private void createArray() {
        array = createArray(network, equationSystem, initializer);
        status = Status.VALID;
    }

    private void updateArray() {
        NavigableSet<Equation<V, E>> sortedEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve();
        for (Equation<V, E> equation : sortedEquationsToSolve) {
            initializer.initialize(equation, network, array);
        }
        status = Status.VALID;
    }
}
