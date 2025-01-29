/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class EquationVector<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractVector<V, E>
        implements StateVectorListener, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquationVector.class);

    public EquationVector(EquationSystem<V, E> equationSystem) {
        super(equationSystem);
        equationSystem.getStateVector().addListener(this);
    }

    @Override
    public void onStateUpdate() {
        invalidateValues();
    }

    @Override
    protected double[] createArray() {
        double[] array = new double[equationSystem.getIndex().getColumnCount()];
        updateArray(array);
        return array;
    }

    @Override
    protected void updateArray(double[] array) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        var equations = equationSystem.getIndex().getSortedEquationsToSolve();

        if (array.length != equationSystem.getIndex().getColumnCount()) {
            throw new IllegalArgumentException("Bad equation vector length: " + array.length);
        }

        Arrays.fill(array, 0); // necessary?
        for (Equation<V, E> equation : equations) {
            array[equation.getColumn()] = equation.evalLhs();
        }
        for (EquationArray<V, E> equationArray : equationSystem.getEquationArrays()) {
            equationArray.eval(array);
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Equation vector updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    @Override
    public void close() {
        equationSystem.getStateVector().removeListener(this);
    }
}
