/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DefaultAcLoadFlowObserver implements AcLoadFlowObserver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAcLoadFlowObserver.class);

    @Override
    public void beforeNetworksCreation() {
        // empty
    }

    @Override
    public void afterNetworksCreation(List<LfNetwork> networks) {
        // empty
    }

    @Override
    public void beforeEquationSystemCreation() {
        // empty
    }

    @Override
    public void afterEquationSystemCreation() {
        // empty
    }

    @Override
    public void beforeOuterLoopBody(int outerLoopIteration, String outerLoopName) {
        // empty
    }

    @Override
    public void beforeVoltageInitializerPreparation(Class<?> voltageInitializerClass) {
        // empty
    }

    @Override
    public void afterVoltageInitializerPreparation() {
        // empty
    }

    @Override
    public void beforeStateVectorCreation(int iteration) {
        // empty
    }

    @Override
    public void afterStateVectorCreation(double[] x, int iteration) {
        // empty
    }

    @Override
    public void beginIteration(int iteration) {
        // empty
    }

    @Override
    public void beforeStoppingCriteriaEvaluation(double[] mismatch, EquationSystem equationSystem, int iteration) {
        // empty
    }

    @Override
    public void afterStoppingCriteriaEvaluation(double norm, int iteration) {
        // empty
    }

    @Override
    public void beforeEquationsUpdate(int iteration) {
        // empty
    }

    @Override
    public void afterEquationsUpdate(EquationSystem equationSystem, int iteration) {
        // empty
    }

    @Override
    public void beforeEquationVectorCreation(int iteration) {
        // empty
    }

    @Override
    public void afterEquationVectorCreation(double[] fx, EquationSystem equationSystem, int iteration) {
        if (LOGGER.isTraceEnabled()) {
            LfNetwork lfNetwork = equationSystem.getNetwork();
            NavigableMap<Equation, NavigableMap<Variable, List<EquationTerm>>> equationNavigableMapNavigableMap = equationSystem.getSortedEquationsToSolve();
            Map<LfBus, List<Equation>> equationsByBus = new LinkedHashMap<>();
            for (Equation equation : equationNavigableMapNavigableMap.keySet()) {
                equationsByBus.computeIfAbsent(lfNetwork.getBus(equation.getNum()), bus -> new ArrayList<>()).add(equation);
            }
            for (LfBus lfBus : equationsByBus.keySet()) {
                LOGGER.trace("Equations sur le bus {} :", lfBus.getId());
                for (Equation equation : equationsByBus.get(lfBus)) {
                    LOGGER.trace(" - equation de type {} ayant pour terme(s) :", equation.getType());
                    for (EquationTerm equationTerm : equation.getTerms()) {
                        if (equationTerm.isActive()) {
                            StringBuilder stringBuilder = new StringBuilder();
                            for (Variable variable : equationTerm.getVariables()) {
                                if (variable.isActive()) {
                                    stringBuilder.append((stringBuilder.length() != 0 ? ", " : "") + variable.getType() + " (bus " + lfNetwork.getBus(variable.getNum()).getId() + ")");
                                }
                            }
                            LOGGER.trace("   * terme {} de type {} avec pour variables : {}", equationTerm.getClass().getSimpleName(), equationTerm.getSubjectType(), stringBuilder.toString());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void beforeJacobianBuild(int iteration) {
        // empty
    }

    @Override
    public void afterJacobianBuild(Matrix j, EquationSystem equationSystem, int iteration) {
        if (LOGGER.isTraceEnabled()) {
            StringBuilder stringBuilder = new StringBuilder();
            j.print(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    stringBuilder.append((char) b);
                }
            }));
            LOGGER.trace("Jacobian matrix : {}{}", System.getProperty("line.separator"), stringBuilder.toString());
        }
    }

    @Override
    public void beforeLuDecomposition(int iteration) {
        // empty
    }

    @Override
    public void afterLuDecomposition(int iteration) {
        // empty
    }

    @Override
    public void beforeLuSolve(int iteration) {
        // empty
    }

    @Override
    public void afterLuSolve(int iteration) {
        // empty
    }

    @Override
    public void endIteration(int iteration) {
        // empty
    }

    @Override
    public void beforeOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName) {
        // empty
    }

    @Override
    public void afterOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName, boolean stable) {
        // empty
    }

    @Override
    public void afterOuterLoopBody(int outerLoopIteration, String outerLoopName) {
        // empty
    }

    @Override
    public void beforeNetworkUpdate() {
        // empty
    }

    @Override
    public void afterNetworkUpdate(LfNetwork network) {
        // empty
    }

    @Override
    public void beforePvBusesReactivePowerUpdate() {
        // empty
    }

    @Override
    public void afterPvBusesReactivePowerUpdate() {
        // empty
    }

    @Override
    public void beforeLoadFlow(LfNetwork network) {
        // empty
    }

    @Override
    public void afterLoadFlow(LfNetwork network) {
        // empty
    }
}
