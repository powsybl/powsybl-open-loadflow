/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationVector<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractVector<V, E>
        implements StateVectorListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquationVector.class);

    private final EquationEvaluator evaluator;

    public EquationVector(EquationSystem<V, E> equationSystem, EquationEvaluator evaluator) {
        super(equationSystem);
        equationSystem.getStateVector().addListener(this);
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    @Override
    public void onStateUpdate() {
        invalidateValues();
    }

    @Override
    protected double[] createArray() {
        double[] array = new double[equationSystem.getIndex().getSortedEquationsToSolve().size()];
        updateArray(array);
        return array;
    }

    @Override
    protected void updateArray(double[] array) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        int columnCount = equationSystem.getIndex().getColumnCount();

        if (array.length != columnCount) {
            throw new IllegalArgumentException("Bad equation vector length: " + array.length);
        }

        Arrays.fill(array, 0); // necessary?
        for (int column = 0; column < columnCount; column++) {
            array[column] = evaluator.eval(column);
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Equation vector updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }
}
