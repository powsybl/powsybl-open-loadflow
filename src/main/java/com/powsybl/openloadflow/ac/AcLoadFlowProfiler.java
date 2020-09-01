/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.io.table.AsciiTableFormatter;
import com.powsybl.commons.io.table.Column;
import com.powsybl.openloadflow.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Markers;
import com.powsybl.math.matrix.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowProfiler extends DefaultAcLoadFlowObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcLoadFlowProfiler.class);

    private final Stopwatch stopwatch = Stopwatch.createUnstarted();

    private final Stopwatch iterationStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch loadFlowStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch equationSystemCreationStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch stateVectorCreationTotalStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch equationVectorCreationTotalStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch equationUpdateTotalStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch jacobianBuildTotalStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch luDecompositionTotalStopwatch = Stopwatch.createUnstarted();

    private final Stopwatch luSolveTotalStopwatch = Stopwatch.createUnstarted();

    private static void restart(Stopwatch stopwatch) {
        stopwatch.reset();
        stopwatch.start();
    }

    @Override
    public void beforeEquationSystemCreation() {
        equationSystemCreationStopwatch.start();
    }

    @Override
    public void afterEquationSystemCreation() {
        equationSystemCreationStopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "AC equation system created in {} ms", equationSystemCreationStopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeVoltageInitializerPreparation(Class<?> voltageInitializerClass) {
        restart(stopwatch);
    }

    @Override
    public void afterVoltageInitializerPreparation() {
        stopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Voltage initializer prepared in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beginIteration(int iteration) {
        restart(iterationStopwatch);
    }

    @Override
    public void beforeStateVectorCreation(int iteration) {
        restart(stopwatch);
        stateVectorCreationTotalStopwatch.start();
    }

    @Override
    public void afterStateVectorCreation(double[] x, int iteration) {
        stopwatch.stop();
        stateVectorCreationTotalStopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "State vector created at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeEquationsUpdate(int iteration) {
        restart(stopwatch);
        equationUpdateTotalStopwatch.start();
    }

    @Override
    public void afterEquationsUpdate(EquationSystem equationSystem, int iteration) {
        stopwatch.stop();
        equationUpdateTotalStopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Equations updated at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeEquationVectorCreation(int iteration) {
        restart(stopwatch);
        equationVectorCreationTotalStopwatch.start();
    }

    @Override
    public void afterEquationVectorCreation(double[] fx, EquationSystem equationSystem, int iteration) {
        stopwatch.stop();
        equationVectorCreationTotalStopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Equation vector created at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeJacobianBuild(int iteration) {
        restart(stopwatch);
        jacobianBuildTotalStopwatch.start();
    }

    @Override
    public void afterJacobianBuild(Matrix j, EquationSystem equationSystem, int iteration) {
        stopwatch.stop();
        jacobianBuildTotalStopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Jacobian built at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeLuDecomposition(int iteration) {
        restart(stopwatch);
        luDecompositionTotalStopwatch.start();
    }

    @Override
    public void afterLuDecomposition(int iteration) {
        stopwatch.stop();
        luDecompositionTotalStopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "LU decomposed at iteration {} in {} ms", iteration, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeLuSolve(int iteration) {
        restart(stopwatch);
        luSolveTotalStopwatch.start();
    }

    @Override
    public void afterLuSolve(int iteration) {
        stopwatch.stop();
        luSolveTotalStopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "LU solved at iteration {} in {} us", iteration, stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    @Override
    public void endIteration(int iteration) {
        iterationStopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Iteration {} complete in {} ms", iteration, iterationStopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName) {
        restart(stopwatch);
    }

    @Override
    public void afterOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName, boolean stable) {
        stopwatch.stop();
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Outer loop '{}' check done in {} ms", outerLoopName, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeNetworkUpdate() {
        restart(stopwatch);
    }

    @Override
    public void afterNetworkUpdate(LfNetwork network) {
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Network updated in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforePvBusesReactivePowerUpdate() {
        restart(stopwatch);
    }

    @Override
    public void afterPvBusesReactivePowerUpdate() {
        LOGGER.debug(Markers.PERFORMANCE_MARKER, "PV Buses reactive power updated in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void beforeLoadFlow(LfNetwork network) {
        equationSystemCreationStopwatch.reset();
        stateVectorCreationTotalStopwatch.reset();
        equationVectorCreationTotalStopwatch.reset();
        equationUpdateTotalStopwatch.reset();
        jacobianBuildTotalStopwatch.reset();
        luDecompositionTotalStopwatch.reset();
        luSolveTotalStopwatch.reset();
        loadFlowStopwatch.start();
    }

    @Override
    public void afterLoadFlow(LfNetwork network) {
        loadFlowStopwatch.stop();

        if (LOGGER.isDebugEnabled()) {
            long loadFlowTime = loadFlowStopwatch.elapsed(TimeUnit.MILLISECONDS);
            LOGGER.debug(Markers.PERFORMANCE_MARKER, "Ac loadflow ran on network {} in {} ms", network.getNum(), loadFlowTime);

            long equationSystemCreationTime = equationSystemCreationStopwatch.elapsed(TimeUnit.MILLISECONDS);
            long stateVectorCreationTotalTime = stateVectorCreationTotalStopwatch.elapsed(TimeUnit.MILLISECONDS);
            long equationVectorCreationTotalTime = equationVectorCreationTotalStopwatch.elapsed(TimeUnit.MILLISECONDS);
            long equationUpdateTotalTime = equationUpdateTotalStopwatch.elapsed(TimeUnit.MILLISECONDS);
            long jacobianBuildTotalTime = jacobianBuildTotalStopwatch.elapsed(TimeUnit.MILLISECONDS);
            long luDecompositionTotalTime = luDecompositionTotalStopwatch.elapsed(TimeUnit.MILLISECONDS);
            long luSolveTotalTime = luSolveTotalStopwatch.elapsed(TimeUnit.MILLISECONDS);
            long otherTime = loadFlowTime
                    - equationSystemCreationTime
                    - stateVectorCreationTotalTime
                    - equationVectorCreationTotalTime
                    - equationUpdateTotalTime
                    - jacobianBuildTotalTime
                    - luDecompositionTotalTime
                    - luSolveTotalTime;
            StringWriter writer = new StringWriter();
            try (AsciiTableFormatter formatter = new AsciiTableFormatter(writer,
                    "Detailed profiling on network " + network.getNum(),
                    new Column("Component"),
                    new Column("Time (ms)"))) {
                formatter.writeCell("Equation system creation").writeCell((int) equationSystemCreationTime)
                        .writeCell("State vector creation").writeCell((int) stateVectorCreationTotalTime)
                        .writeCell("Equation vector creation").writeCell((int) equationVectorCreationTotalTime)
                        .writeCell("Equation update").writeCell((int) equationUpdateTotalTime)
                        .writeCell("Jacobian build").writeCell((int) jacobianBuildTotalTime)
                        .writeCell("LU decomposition").writeCell((int) luDecompositionTotalTime)
                        .writeCell("LU solve").writeCell((int) luSolveTotalTime)
                        .writeCell("Other").writeCell((int) otherTime);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            String tableStr = writer.toString();
            LOGGER.debug(Markers.PERFORMANCE_MARKER, "{}", tableStr.substring(0, tableStr.length() - System.lineSeparator().length()));
        }
    }
}
