/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.*;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class TargetVector<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractVector<V, E> implements AutoCloseable {

    public interface Initializer<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        void initialize(ScalarEquation<V, E> equation, LfNetwork network, double[] targets);

        void initialize(EquationArray<V, E> equationArray, LfNetwork network, double[] targets);
    }

    private final LfNetwork network;

    private final Initializer<V, E> initializer;

    private final LfNetworkListener networkListener = new AbstractLfNetworkListener() {

        @Override
        public void onGeneratorVoltageControlTargetChange(GeneratorVoltageControl control, double newTargetVoltage) {
            invalidateValues();
        }

        @Override
        public void onTransformerVoltageControlTargetChange(TransformerVoltageControl transformerVoltageControl, double newTargetVoltage) {
            invalidateValues();
        }

        @Override
        public void onLoadActivePowerTargetChange(LfLoad load, double oldTargetP, double newTargetP) {
            invalidateValues();
        }

        @Override
        public void onLoadReactivePowerTargetChange(LfLoad load, double oldTargetQ, double newTargetQ) {
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
        public void onTapPositionChange(LfBranch branch, int oldPosition, int newPosition) {
            invalidateValues();
        }

        @Override
        public void onShuntSusceptanceChange(LfShunt shunt, double b) {
            invalidateValues();
        }

        @Override
        public void onDisableChange(LfElement element, boolean disabled) {
            for (var equationTerm : equationSystem.getEquationTerms(element.getType(), element.getNum())) {
                if (equationTerm.hasRhs()) {
                    invalidateValues();
                }
            }
        }
    };

    public TargetVector(LfNetwork network, EquationSystem<V, E> equationSystem, Initializer<V, E> initializer) {
        super(equationSystem);
        this.network = Objects.requireNonNull(network);
        this.initializer = Objects.requireNonNull(initializer);
        network.addListener(networkListener);
    }

    public static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> double[] createArray(LfNetwork network, EquationSystem<V, E> equationSystem, Initializer<V, E> initializer) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(equationSystem);
        Objects.requireNonNull(initializer);
        List<ScalarEquation<V, E>> sortedEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve();
        double[] array = new double[equationSystem.getIndex().getColumnCount()];
        for (ScalarEquation<V, E> equation : sortedEquationsToSolve) {
            initializer.initialize(equation, network, array);
        }
        for (EquationArray<V, E> equationArray : equationSystem.getEquationArrays()) {
            initializer.initialize(equationArray, network, array);
        }
        return array;
    }

    @Override
    protected double[] createArray() {
        return createArray(network, equationSystem, initializer);
    }

    @Override
    protected void updateArray(double[] array) {
        List<ScalarEquation<V, E>> sortedEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve();
        for (ScalarEquation<V, E> equation : sortedEquationsToSolve) {
            initializer.initialize(equation, network, array);
        }
        for (EquationArray<V, E> equationArray : equationSystem.getEquationArrays()) {
            initializer.initialize(equationArray, network, array);
        }
    }

    @Override
    public void close() {
        network.removeListener(networkListener);
    }
}
