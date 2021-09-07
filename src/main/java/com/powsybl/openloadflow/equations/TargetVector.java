/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.*;

import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class TargetVector extends AbstractLfNetworkListener implements EquationSystemListener {

    private final LfNetwork network;

    private final EquationSystem equationSystem;

    private double[] array;

    private enum Status {
        VALID,
        VECTOR_INVALID, // structure has changed
        VALUES_INVALID // same structure but values have to be updated
    }

    private Status status = Status.VECTOR_INVALID;

    public TargetVector(LfNetwork network, EquationSystem equationSystem) {
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        network.addListener(this);
        equationSystem.addListener(this);
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
    public void onPhaseControlTapPositionChange(PiModel piModel, int oldPosition, int newPosition) {
        invalidateValues();
    }

    @Override
    public void onEquationChange(Equation equation, EquationEventType eventType) {
        status = Status.VECTOR_INVALID;
    }

    @Override
    public void onEquationTermChange(EquationTerm term, EquationTermEventType eventType) {
        // nothing to do
    }

    @Override
    public void onStateUpdate(double[] x) {
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

    public static double[] createArray(LfNetwork network, EquationSystem equationSystem) {
        NavigableMap<Equation, NavigableMap<Variable, List<EquationTerm>>> sortedEquationsToSolve = equationSystem.getSortedEquationsToSolve();
        double[] array = new double[sortedEquationsToSolve.size()];
        for (Equation equation : sortedEquationsToSolve.keySet()) {
            equation.initTarget(network, array);
        }
        return array;
    }

    private void createArray() {
        array = createArray(network, equationSystem);
        status = Status.VALID;
    }

    private void updateArray() {
        NavigableMap<Equation, NavigableMap<Variable, List<EquationTerm>>> sortedEquationsToSolve = equationSystem.getSortedEquationsToSolve();
        for (Equation equation : sortedEquationsToSolve.keySet()) {
            equation.initTarget(network, array);
        }
        status = Status.VALID;
    }
}
