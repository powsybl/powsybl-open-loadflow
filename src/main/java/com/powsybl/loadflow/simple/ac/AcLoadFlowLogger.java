/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac;

import com.powsybl.loadflow.simple.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.loadflow.simple.equations.Equation;
import com.powsybl.loadflow.simple.equations.EquationSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowLogger extends DefaultAcLoadFlowObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcLoadFlowLogger.class);

    @Override
    public void beforeOuterLoopBody(int outerLoopIteration, String outerLoopName) {
        LOGGER.debug("Start outer loop iteration {} (name='{}')", outerLoopIteration, outerLoopName);
    }

    @Override
    public void beforeVoltageInitializerPreparation(Class<?> voltageInitializerClass) {
        LOGGER.info("Voltage level initializer: {}", voltageInitializerClass.getSimpleName());
    }

    @Override
    public void beginIteration(int iteration) {
        LOGGER.debug("Start iteration {}", iteration);
    }

    @Override
    public void norm(double norm) {
        LOGGER.debug("|f(x)|={}", norm);
    }

    public void logLargestMismatches(double[] fx, EquationSystem equationSystem, int count) {
        Map<Equation, Double> mismatches = new HashMap<>(equationSystem.getSortedEquationsToSolve().size());
        for (Equation equation : equationSystem.getSortedEquationsToSolve()) {
            mismatches.put(equation, fx[equation.getRow()]);
        }
        mismatches.entrySet().stream()
                .filter(e -> Math.abs(e.getValue()) > Math.pow(10, -7))
                .sorted(Comparator.comparingDouble((Map.Entry<Equation, Double> e) -> Math.abs(e.getValue())).reversed())
                .limit(count)
                .forEach(e -> LOGGER.trace("Mismatch for {}: {}", e.getKey(), e.getValue()));
    }
}
