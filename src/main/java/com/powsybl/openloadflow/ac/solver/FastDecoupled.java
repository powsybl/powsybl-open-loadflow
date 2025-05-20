/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
public class FastDecoupled extends AbstractAcSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FastDecoupled.class);

    public static final List<AcEquationType> REPORTED_AC_EQUATION_TYPES = List.of(AcEquationType.BUS_TARGET_P, AcEquationType.BUS_TARGET_Q, AcEquationType.BUS_TARGET_V);

    protected final NewtonRaphsonParameters parameters;

    public FastDecoupled(LfNetwork network, NewtonRaphsonParameters parameters, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                         JacobianMatrix<AcVariableType, AcEquationType> j, TargetVector<AcVariableType, AcEquationType> targetVector,
                         EquationVector<AcVariableType, AcEquationType> equationVector, boolean detailedReport) {
        super(network, equationSystem, j, targetVector, equationVector, detailedReport);
        this.parameters = Objects.requireNonNull(parameters);
    }

    @Override
    public String getName() {
        return "Fast Decoupled";
    }

    private AcSolverStatus runIteration(StateVectorScaling svScaling, MutableInt iterations, ReportNode reportNode) {
        LOGGER.debug("Start iteration {}", iterations);
    try {
        // create iteration report
        // - add 1 to iteration so that it starts at 1 instead of 0
        ReportNode iterationReportNode = detailedReport ? Reports.createNewtonRaphsonMismatchReporter(reportNode, iterations.getValue() + 1) : null;

        // Phi state vector update
        // TODO HG: Call method to activate phi equations and variables and deactivate v equations and variables
        // solve f(x) = j * dx
        try {
            j.solveTransposed(equationVector.getArray());
        } catch (MatrixException e) {
            LOGGER.error(e.toString(), e);
            Reports.reportNewtonRaphsonError(reportNode, e.toString());
            return AcSolverStatus.SOLVER_FAILED;
        }
        // f(x) now contains dx

        svScaling.apply(equationVector.getArray(), equationSystem, iterationReportNode);

        // update x and f(x) will be automatically updated
        equationSystem.getStateVector().minus(equationVector.getArray());

        // V state vector update
        // TODO HG: Call method to activate v equations and variables and deactivate phi equations and variables

        return null;
    } finally {
        iterations.increment();
    }
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, ReportNode reportNode) {
        // initialize state vector
        AcSolverUtil.initStateVector(network, equationSystem, voltageInitializer);

        Vectors.minus(equationVector.getArray(), targetVector.getArray());

        NewtonRaphsonStoppingCriteria.TestResult initialTestResult = parameters.getStoppingCriteria().test(equationVector.getArray(), equationSystem);
        StateVectorScaling svScaling = StateVectorScaling.fromMode(parameters, initialTestResult);

        LOGGER.debug("|f(x0)|={}", initialTestResult.getNorm());

        ReportNode initialReportNode = detailedReport ? Reports.createNewtonRaphsonMismatchReporter(reportNode, 0) : null;
        if (detailedReport) {
            Reports.reportNewtonRaphsonNorm(initialReportNode, initialTestResult.getNorm());
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
        }

        double slackBusActivePowerMismatch = network.getSlackBuses().stream().mapToDouble(LfBus::getMismatchP).sum();
        return new AcSolverResult(status, iterations.getValue(), slackBusActivePowerMismatch);
    }
}
