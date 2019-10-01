/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac;

import com.google.common.base.Stopwatch;
import com.powsybl.loadflow.simple.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.loadflow.simple.equations.EquationSystem;
import com.powsybl.math.matrix.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.powsybl.loadflow.simple.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowProfiler extends DefaultAcLoadFlowObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcLoadFlowProfiler.class);

    private final Stopwatch iterationStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch stopwatch = Stopwatch.createUnstarted();

    private static void restart(Stopwatch stopwatch) {
        stopwatch.reset();
        stopwatch.start();
    }

    @Override
    public void beforeEquationSystemCreation() {
        restart(stopwatch);
    }

    @Override
    public void afterEquationSystemCreation() {
        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "AC equation system created in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeVoltageInitializerPreparation(Class<?> voltageInitializerClass) {
        restart(stopwatch);
    }

    @Override
    public void afterVoltageInitializerPreparation() {
        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "Voltage initializer prepared in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beginIteration(int iteration) {
        restart(iterationStopwatch);
    }

    @Override
    public void beforeEquationsUpdate(int iteration) {
        restart(stopwatch);
    }

    @Override
    public void afterEquationsUpdate(EquationSystem equationSystem, int iteration) {
        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "Equations updated at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeEquationVectorUpdate(int iteration) {
        restart(stopwatch);
    }

    @Override
    public void afterEquationVectorUpdate(EquationSystem equationSystem, int iteration) {
        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "Equation vector updated at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeJacobianBuild(int iteration) {
        restart(stopwatch);
    }

    @Override
    public void afterJacobianBuild(Matrix j, EquationSystem equationSystem, int iteration) {
        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "Jacobian built at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeLuDecomposition(int iteration) {
        restart(stopwatch);
    }

    @Override
    public void afterLuDecomposition(int iteration) {
        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "LU decomposed at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeLuSolve(int iteration) {
        restart(stopwatch);
    }

    @Override
    public void afterLuSolve(int iteration) {
        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "LU solved at iteration {} in {} us", iteration, stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    @Override
    public void endIteration(int iteration) {
        iterationStopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "Iteration {} complete in {} ms", iteration, iterationStopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName) {
        restart(stopwatch);
    }

    @Override
    public void afterOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName, boolean stable) {
        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "Outer loop '{}' check done in {} ms", outerLoopName, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeNetworkUpdate() {
        restart(stopwatch);
    }

    @Override
    public void afterNetworkUpdate() {
        LOGGER.debug(PERFORMANCE_MARKER, "Network updated in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforePvBusesReactivePowerUpdate() {
        restart(stopwatch);
    }

    @Override
    public void afterPvBusesReactivePowerUpdate() {
        LOGGER.debug(PERFORMANCE_MARKER, "PV Buses reactive power updated in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }
}
