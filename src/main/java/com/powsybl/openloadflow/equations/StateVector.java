/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class StateVector {

    private double[] array;

    private final List<StateVectorListener> listeners = new ArrayList<>();

    public StateVector() {
        this(null);
    }

    public StateVector(double[] array) {
        this.array = array;
    }

    public void set(double[] array) {
        this.array = Objects.requireNonNull(array);
        notifyStateUpdate();
    }

    public double[] get() {
        return array;
    }

    public double get(int variableNum) {
        return array[variableNum];
    }

    public void set(int variableNum, double value) {
        array[variableNum] = value;
        notifyStateUpdate();
    }

    public void minus(double[] b) {
        Vectors.minus(array, b);
        notifyStateUpdate();
    }

    private void notifyStateUpdate() {
        for (StateVectorListener listener : listeners) {
            listener.onStateUpdate();
        }
    }

    public void addListener(StateVectorListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(StateVectorListener listener) {
        listeners.remove(Objects.requireNonNull(listener));
    }
}
