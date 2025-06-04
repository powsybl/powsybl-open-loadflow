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
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
public class FastDecoupled extends AbstractAcSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FastDecoupled.class);

    public static final List<AcEquationType> REPORTED_AC_EQUATION_TYPES = List.of(AcEquationType.BUS_TARGET_P, AcEquationType.BUS_TARGET_Q, AcEquationType.BUS_TARGET_V);

    protected final NewtonRaphsonParameters parameters;

    private JacobianMatrix<AcVariableType, AcEquationType> jPhi;
    private JacobianMatrix<AcVariableType, AcEquationType> jV;

    private enum PhiVEquationType {
        PHI_EQUATION_TYPE,
        V_EQUATION_TYPE;
    }

    private enum PhiVVariableType {
        PHI_VARIABLE_TYPE,
        V_VARIABLE_TYPE;
    }

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

    private PhiVEquationType getPhiVEquationType(AcEquationType acEquationType) {
        return switch (acEquationType) {
            case BUS_TARGET_P,
                 BUS_TARGET_PHI,
                 BRANCH_TARGET_P,
                 BRANCH_TARGET_ALPHA1,
                 ZERO_PHI,
                 DUMMY_TARGET_P,
                 BUS_DISTR_SLACK_P -> PhiVEquationType.PHI_EQUATION_TYPE;
            case BUS_TARGET_Q,
                 BUS_TARGET_V,
                 SHUNT_TARGET_B,
                 BRANCH_TARGET_Q,
                 BRANCH_TARGET_RHO1,
                 DISTR_Q,
                 ZERO_V,
                 DISTR_RHO,
                 DISTR_SHUNT_B,
                 DUMMY_TARGET_Q -> PhiVEquationType.V_EQUATION_TYPE;
            default -> null;
        };
    }

    private PhiVVariableType getPhiVVariableType(AcVariableType acVariableType) {
        return switch (acVariableType) {
            case BUS_PHI,
                 BRANCH_ALPHA1,
                 DUMMY_P -> PhiVVariableType.PHI_VARIABLE_TYPE;
            case BUS_V,
                 SHUNT_B,
                 BRANCH_RHO1,
                 DUMMY_Q -> PhiVVariableType.V_VARIABLE_TYPE;
            default -> null;
        };
    }

    Comparator<Equation<AcVariableType, AcEquationType>> phiVEquationComparator = (o1, o2) -> {
        PhiVEquationType equationType1 = getPhiVEquationType(o1.getType());
        PhiVEquationType equationType2 = getPhiVEquationType(o2.getType());
        if (equationType1 != equationType2) {
            return equationType1 == PhiVEquationType.PHI_EQUATION_TYPE ? -1 : 1;
        } else {
            return o1.compareTo(o2);
        }
    };

    Comparator<Variable<AcVariableType>> phiVVariableComparator = (o1, o2) -> {
        PhiVVariableType variableType1 = getPhiVVariableType(o1.getType());
        PhiVVariableType variableType2 = getPhiVVariableType(o2.getType());
        if (variableType1 != variableType2) {
            return variableType1 == PhiVVariableType.PHI_VARIABLE_TYPE ? -1 : 1;
        } else {
            return o1.compareTo(o2);
        }
    };

    private int getRangeForPhiSystemPart() {
        MutableInt index = new MutableInt();
        for (Variable<AcVariableType> var : equationSystem.getIndex().getSortedVariablesToFind()) {
            if (getPhiVVariableType(var.getType()) == PhiVVariableType.PHI_VARIABLE_TYPE) {
                index.increment();
            } else {
                break;
            }
        }
        return index.getValue();
    }

    private void runSingleSystemSolution(JacobianMatrix<AcVariableType, AcEquationType> j, double[] partialEquationVector, int rangeIndex, boolean isPhySystem,
                                         StateVectorScaling svScaling, ReportNode reportNode, ReportNode iterationReportNode) {
        int systemLength = partialEquationVector.length;
        int begin = isPhySystem ? 0 : rangeIndex;
        int end = isPhySystem ? rangeIndex : equationVector.getArray().length;

        // solve f(x) = j * dx
        // Extract the "Phi" or "V" part of the equation vector
        System.arraycopy(equationVector.getArray(), begin, partialEquationVector, 0, systemLength);
        j.solveTransposed(partialEquationVector);

        // copy the result on the right subset of equationVector
        System.arraycopy(partialEquationVector, 0, equationVector.getArray(), begin, systemLength);
        // f(x) now contains dx in its "Phi" or "V" part
        // TODO HG: Use the right subset of equationVector
        // TODO HG: should ban the using of lineSearch and replace it by none if selected with a warning
        svScaling.apply(equationVector.getArray(), equationSystem, iterationReportNode);

        // update x and f(x) will be automatically updated
        // TODO HG (OPTIM): Adapt the automatic update part to update only a subset of f(x) when Phi part is updated
        equationSystem.getStateVector().minusWithRange(equationVector.getArray(), begin, end);

        // subtract targets from f(x) for next iteration
        // we need to reverse begin in case of "Phi" to prepare for "V" one
        // for "V" system we recompute the whole mismatches as they are needed to assess convergence
        begin = isPhySystem ? rangeIndex : 0;
        end = equationVector.getArray().length;
        equationVector.minusWithRange(targetVector, begin, end);
        // f(x) now contains equation mismatches on either its "Phi" part or on the whole array
    }

    private AcSolverStatus runIteration(StateVectorScaling svScaling, MutableInt iterations, ReportNode reportNode, double[] phiEquationVector, double[] vEquationVector, int rangeIndex) {
        LOGGER.debug("Start iteration {}", iterations);
        try {
            // create iteration report
            // - add 1 to iteration so that it starts at 1 instead of 0
            ReportNode iterationReportNode = detailedReport ? Reports.createNewtonRaphsonMismatchReporter(reportNode, iterations.getValue() + 1) : null;

            try {
                // Solution on PHI
                runSingleSystemSolution(jPhi, phiEquationVector, rangeIndex, true, svScaling, reportNode, iterationReportNode);
                // Solution on V
                runSingleSystemSolution(jV, vEquationVector, rangeIndex, false, svScaling, reportNode, iterationReportNode);
            } catch (MatrixException e) {
                LOGGER.error(e.toString(), e);
                Reports.reportNewtonRaphsonError(reportNode, e.toString());
                return AcSolverStatus.SOLVER_FAILED;
            }

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

            LOGGER.debug("|f(x)|={}", testResult.getNorm());
            if (detailedReport) {
                Reports.reportNewtonRaphsonNorm(iterationReportNode, testResult.getNorm());
            }
            if (detailedReport || LOGGER.isTraceEnabled()) {
                reportAndLogLargestMismatchByAcEquationType(iterationReportNode, equationSystem, equationVector.getArray(), LOGGER);
            }
            if (testResult.isStop()) {
                return AcSolverStatus.CONVERGED;
            }

            return null;
        } finally {
            iterations.increment();
        }
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, ReportNode reportNode) {
        equationSystem.getIndex().updateWithComparators(phiVEquationComparator, phiVVariableComparator);
        int rangeIndex = getRangeForPhiSystemPart();

        // TODO HG: create Phi and V Jacobian matrices

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
        // prepare half-sized equation vector
        double[] phiEquationVector = new double[rangeIndex];
        double[] vEquationVector = new double[equationSystem.getIndex().getSortedEquationsToSolve().size() - rangeIndex];

        while (iterations.getValue() <= parameters.getMaxIterations()) {
            AcSolverStatus newStatus = runIteration(svScaling, iterations, reportNode, phiEquationVector, vEquationVector, rangeIndex);
            if (newStatus != null) {
                status = newStatus;
                break;
            }
        }

        // TODO HG: Close Phi and V matrices

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
