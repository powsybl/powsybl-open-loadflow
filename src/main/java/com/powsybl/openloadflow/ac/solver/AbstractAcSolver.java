/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractAcSolver implements AcSolver {

    protected final LfNetwork network;

    protected final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    protected final JacobianMatrix<AcVariableType, AcEquationType> j;

    protected final TargetVector<AcVariableType, AcEquationType> targetVector;

    protected final EquationVector<AcVariableType, AcEquationType> equationVector;

    protected boolean detailedReport;

    public static final List<AcEquationType> REPORTED_AC_EQUATION_TYPES = List.of(AcEquationType.BUS_TARGET_P, AcEquationType.BUS_TARGET_Q, AcEquationType.BUS_TARGET_V);

    protected AbstractAcSolver(LfNetwork network,
                               EquationSystem<AcVariableType, AcEquationType> equationSystem,
                               JacobianMatrix<AcVariableType, AcEquationType> j,
                               TargetVector<AcVariableType, AcEquationType> targetVector,
                               EquationVector<AcVariableType, AcEquationType> equationVector,
                               boolean detailedReport) {
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.j = Objects.requireNonNull(j);
        this.targetVector = Objects.requireNonNull(targetVector);
        this.equationVector = Objects.requireNonNull(equationVector);
        this.detailedReport = detailedReport;
    }

    public static List<Pair<Equation<AcVariableType, AcEquationType>, Double>> findLargestMismatches(EquationSystem<AcVariableType, AcEquationType> equationSystem, double[] mismatch, int count) {
        return equationSystem.getIndex().getSortedEquationsToSolve().stream()
                .map(equation -> Pair.of(equation, mismatch[equation.getColumn()]))
                .filter(e -> Math.abs(e.getValue()) > Math.pow(10, -7))
                .sorted(Comparator.comparingDouble((Map.Entry<Equation<AcVariableType, AcEquationType>, Double> e) -> Math.abs(e.getValue())).reversed())
                .limit(count)
                .toList();
    }

    public static Map<AcEquationType, Pair<Equation<AcVariableType, AcEquationType>, Double>> getLargestMismatchByAcEquationType(EquationSystem<AcVariableType, AcEquationType> equationSystem, double[] mismatch) {
        return equationSystem.getIndex().getSortedEquationsToSolve().stream()
                .map(equation -> Pair.of(equation, mismatch[equation.getColumn()]))
                .collect(Collectors.toMap(e -> e.getKey().getType(), Function.identity(), BinaryOperator.maxBy(Comparator.comparingDouble(e -> Math.abs(e.getValue())))));
    }

    protected String getEquationTypeDescription(AcEquationType acEquationType) {
        return switch (acEquationType) {
            case BUS_TARGET_P -> "P";
            case BUS_TARGET_Q -> "Q";
            case BUS_TARGET_V -> "V";
            default -> null; // not implemented for other ac equation types
        };
    }

    public void reportAndLogLargestMismatchByAcEquationType(ReportNode reportNode, EquationSystem<AcVariableType, AcEquationType> equationSystem, double[] mismatch, Logger logger) {
        Map<AcEquationType, Pair<Equation<AcVariableType, AcEquationType>, Double>> mismatchEquations = getLargestMismatchByAcEquationType(equationSystem, mismatch);

        // report largest mismatches in (P, Q, V) equations
        for (AcEquationType acEquationType : REPORTED_AC_EQUATION_TYPES) {
            Optional.ofNullable(mismatchEquations.get(acEquationType))
                    .ifPresent(equationPair -> {
                        Equation<AcVariableType, AcEquationType> equation = equationPair.getKey();
                        double equationMismatch = equationPair.getValue();
                        int elementNum = equation.getElementNum();
                        String elementId = equation.getElement(network).map(LfElement::getId).orElse("?");
                        int busVRow = equationSystem.getVariable(elementNum, AcVariableType.BUS_V).getRow();
                        int busPhiRow = equationSystem.getVariable(elementNum, AcVariableType.BUS_PHI).getRow();
                        double busV = equationSystem.getStateVector().get(busVRow);
                        double busPhi = equationSystem.getStateVector().get(busPhiRow);
                        LfBus bus = network.getBus(elementNum);
                        double busNominalV = bus.getNominalV();
                        double busSumP = bus.getP().eval() * PerUnit.SB;
                        double busSumQ = bus.getQ().eval() * PerUnit.SB;

                        if (logger.isTraceEnabled()) {
                            logger.trace("Largest mismatch on {}: {}", getEquationTypeDescription(acEquationType), equationMismatch);
                            logger.trace("    Bus Id: {} (nominalVoltage={})", elementId, busNominalV);
                            logger.trace("    Bus  V: {} pu, {} rad", busV, busPhi);
                        }

                        if (reportNode != null) {
                            Reports.BusReport busReport = new Reports.BusReport(elementId, equationMismatch, busNominalV, busV, busPhi, busSumP, busSumQ);
                            Reports.reportNewtonRaphsonLargestMismatches(reportNode, getEquationTypeDescription(acEquationType), busReport);
                        }
                    });
        }
    }

    protected AcSolverStatus reportAndReturnStatus(Logger logger, NewtonRaphsonStoppingCriteria.TestResult testResult, ReportNode iterationReportNode) {
        logger.debug("|f(x)|={}", testResult.getNorm());
        if (detailedReport) {
            Reports.reportSolverNorm(iterationReportNode, testResult.getNorm());
        }
        if (detailedReport || logger.isTraceEnabled()) {
            reportAndLogLargestMismatchByAcEquationType(iterationReportNode, equationSystem, equationVector.getArray(), logger);
        }
        if (testResult.isStop()) {
            return AcSolverStatus.CONVERGED;
        }

        return null;
    }
}
