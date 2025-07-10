/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.json.JsonUtil;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.networktest.LfDcNode;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class NewtonRaphson extends AbstractAcSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewtonRaphson.class);

    protected final NewtonRaphsonParameters parameters;

    public NewtonRaphson(LfNetwork network, NewtonRaphsonParameters parameters,
                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                         JacobianMatrix<AcVariableType, AcEquationType> j,
                         TargetVector<AcVariableType, AcEquationType> targetVector,
                         EquationVector<AcVariableType, AcEquationType> equationVector,
                         boolean detailedReport) {
        super(network, equationSystem, j, targetVector, equationVector, detailedReport);
        this.parameters = Objects.requireNonNull(parameters);
    }

    @Override
    public String getName() {
        return "Newton-Raphson";
    }

    private AcSolverStatus runIteration(StateVectorScaling svScaling, MutableInt iterations, ReportNode reportNode) {
        LOGGER.debug("Start iteration {}", iterations);
        try {
            // create iteration report
            // - add 1 to iteration so that it starts at 1 instead of 0
            ReportNode iterationReportNode = detailedReport ? Reports.createAcMismatchReporter(reportNode, iterations.intValue() + 1) : null;

            // solve f(x) = j * dx
            try {
                System.out.println("##############################_____Equation Vector 4_____##############################");
                System.out.println(Arrays.toString(equationVector.getArray()));
                System.out.println(Arrays.toString(equationSystem.getStateVector().get()));
                j.solveTransposed(equationVector.getArray());
            } catch (MatrixException e) {
                LOGGER.error(e.toString(), e);
                Reports.reportAcSolverError(reportNode, getName(), e.toString());
                return AcSolverStatus.SOLVER_FAILED;
            }
            // f(x) now contains dx
            svScaling.apply(equationVector.getArray(), equationSystem, iterationReportNode);

            // update x and f(x) will be automatically updated
            equationSystem.getStateVector().minus(equationVector.getArray());

            // subtract targets from f(x)
            equationVector.minus(targetVector);
            // f(x) now contains equation mismatches

            if (LOGGER.isTraceEnabled()) {
                findLargestMismatches(equationSystem, equationVector.getArray(), 5)
                        .forEach(e -> {
                            Equation<AcVariableType, AcEquationType> equation = e.getKey();
                            String elementId = equation.getElement(network).map(LfElement::getId).orElse("?");
                            LOGGER.trace("Mismatch for {}: {} (element={})", equation, e.getValue(), elementId);
                        });
            }

            // test stopping criteria
            NewtonRaphsonStoppingCriteria.TestResult testResult = parameters.getStoppingCriteria().test(equationVector.getArray(), equationSystem);

            testResult = svScaling.applyAfter(equationSystem, equationVector, targetVector,
                                              parameters.getStoppingCriteria(), testResult,
                                              iterationReportNode);

            return reportAndReturnStatus(LOGGER, testResult, iterationReportNode);
        } finally {

//            // add this to print the Jacobian
//            System.out.println("\n\n##############################_____Newton-Raphson iteration_____##############################");
//            j.getMatrix().print(System.out);
//            System.out.println("\n");
//            //

            iterations.increment();
        }
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, ReportNode reportNode) {
        // initialize state vector

        AcSolverUtil.initStateVector(network, equationSystem, voltageInitializer);

        Vectors.minus(equationVector.getArray(), targetVector.getArray());

        System.out.println("##############################_____Target Vector_____##############################");
        System.out.println(Arrays.toString(targetVector.getArray()));
        System.out.println(Arrays.toString(equationVector.getArray()));

        NewtonRaphsonStoppingCriteria.TestResult initialTestResult = parameters.getStoppingCriteria().test(equationVector.getArray(), equationSystem);
        StateVectorScaling svScaling = StateVectorScaling.fromMode(parameters, initialTestResult);

        LOGGER.debug("|f(x0)|={}", initialTestResult.getNorm());

        ReportNode initialReportNode = detailedReport ? Reports.createAcMismatchReporter(reportNode, 0) : null;
        if (detailedReport) {
            Reports.reportSolverNorm(initialReportNode, initialTestResult.getNorm());
        }
        if (detailedReport || LOGGER.isTraceEnabled()) {
            reportAndLogLargestMismatchByAcEquationType(initialReportNode, equationSystem, equationVector.getArray(), LOGGER);
        }

        // start iterations
        AcSolverStatus status = AcSolverStatus.NO_CALCULATION;
        MutableInt iterations = new MutableInt();
        while (iterations.getValue() <= parameters.getMaxIterations()) {
            AcSolverStatus newStatus = runIteration(svScaling, iterations, reportNode);
            if (newStatus != null) {
                status = newStatus;
                break;
            }
        }

        if (iterations.getValue() >= parameters.getMaxIterations()) {
            status = AcSolverStatus.MAX_ITERATION_REACHED;
        }

        if (status == AcSolverStatus.CONVERGED || parameters.isAlwaysUpdateNetwork()) {
            AcSolverUtil.updateNetwork(network, equationSystem);
            System.out.println("##############################_____Variables Values_____##############################");
            StateVector stateVector = equationSystem.getStateVector();

            for (Variable<AcVariableType> variable : equationSystem.getIndex().getSortedVariablesToFind()) {
                int row = variable.getRow();
                double value = stateVector.get(row);

                System.out.println(variable.getType().getSymbol() + variable.getElementNum() + " = " + value);
            }
            System.out.println("\n");
            for (LfBus bus : network.getBuses()) {
                System.out.println("Bus " + bus.getId() + ":");
                System.out.println("  P = " + bus.getP().eval());
                System.out.println("  Q = " + bus.getQ().eval());
                System.out.println("  V = " + bus.getV());
                System.out.println("  Angle = " + bus.getAngle());
            }
        }

        double slackBusActivePowerMismatch = network.getSlackBuses().stream().mapToDouble(LfBus::getMismatchP).sum();
        return new AcSolverResult(status, iterations.getValue(), slackBusActivePowerMismatch);
    }
}
