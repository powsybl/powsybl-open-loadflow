/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class NewtonRaphson extends AbstractAcSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewtonRaphson.class);

    public static final List<AcEquationType> REPORTED_AC_EQUATION_TYPES = List.of(AcEquationType.BUS_TARGET_P, AcEquationType.BUS_TARGET_Q, AcEquationType.BUS_TARGET_V);

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
        return "Newton Raphson";
    }

    public static List<Pair<Equation<AcVariableType, AcEquationType>, Double>> findLargestMismatches(EquationSystem<AcVariableType, AcEquationType> equationSystem, double[] mismatch, int count) {
        return equationSystem.getIndex().getSortedEquationsToSolve().stream()
                .map(equation -> Pair.of(equation, mismatch[equation.getColumn()]))
                .filter(e -> Math.abs(e.getValue()) > Math.pow(10, -7))
                .sorted(Comparator.comparingDouble((Map.Entry<Equation<AcVariableType, AcEquationType>, Double> e) -> Math.abs(e.getValue())).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    public static Map<AcEquationType, Pair<Equation<AcVariableType, AcEquationType>, Double>> getLargestMismatchByAcEquationType(EquationSystem<AcVariableType, AcEquationType> equationSystem, double[] mismatch) {
        return equationSystem.getIndex().getSortedEquationsToSolve().stream()
                .map(equation -> Pair.of(equation, mismatch[equation.getColumn()]))
                .collect(Collectors.toMap(e -> e.getKey().getType(), Function.identity(), BinaryOperator.maxBy(Comparator.comparingDouble(e -> Math.abs(e.getValue())))));
    }

    public void reportAndLogLargestMismatchByAcEquationType(Reporter reporter, EquationSystem<AcVariableType, AcEquationType> equationSystem, double[] mismatch) {
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

                        LOGGER.trace("Mismatch {} on {}: {}", acEquationType, equation, equationMismatch);
                        LOGGER.trace("    Bus        Id       : {}", elementId);
                        LOGGER.trace("    Bus nominal V [  kV]: {}", busNominalV);
                        LOGGER.trace("    Bus         V [p.u.]: {}", busV);
                        LOGGER.trace("    Bus       Phi [ rad]: {}", busPhi);
                        LOGGER.trace("    Bus     sum P [  MW]: {}", busSumP);
                        LOGGER.trace("    Bus     sum Q [MVar]: {}", busSumQ);

                        if (reporter != null) {
                            Reports.reportNewtonRaphsonMismatch(reporter, getEquationTypeDescription(acEquationType), equationMismatch, elementId, busNominalV, busV, busPhi, busSumP, busSumQ);
                        }
                    });
        }
    }

    private String getEquationTypeDescription(AcEquationType acEquationType) {
        return switch (acEquationType) {
            case BUS_TARGET_P -> "TargetP";
            case BUS_TARGET_Q -> "TargetQ";
            case BUS_TARGET_V -> "TargetV";
            default -> null; // not implemented for other ac equation types
        };
    }

    private AcSolverStatus runIteration(StateVectorScaling svScaling, MutableInt iterations, Reporter reporter) {
        LOGGER.debug("Start iteration {}", iterations);

        try {
            // create iteration reporter
            // - add 1 to iteration so that it starts at 1 instead of 0
            Reporter iterationReporter = detailedReport ? Reports.createNewtonRaphsonMismatchReporter(reporter, iterations.getValue() + 1) : null;

            // solve f(x) = j * dx
            try {
                j.solveTransposed(equationVector.getArray());
            } catch (MatrixException e) {
                LOGGER.error(e.toString(), e);
                Reports.reportNewtonRaphsonError(reporter, e.toString());
                return AcSolverStatus.SOLVER_FAILED;
            }
            // f(x) now contains dx

            svScaling.apply(equationVector.getArray(), equationSystem, iterationReporter);

            // update x and f(x) will be automatically updated
            equationSystem.getStateVector().minus(equationVector.getArray());

            // subtract targets from f(x)
            equationVector.minus(targetVector);
            // f(x) now contains equation mismatches

            // test stopping criteria
            NewtonRaphsonStoppingCriteria.TestResult testResult = parameters.getStoppingCriteria().test(equationVector.getArray(), equationSystem);

            testResult = svScaling.applyAfter(equationSystem, equationVector, targetVector,
                                              parameters.getStoppingCriteria(), testResult,
                                              iterationReporter);

            LOGGER.debug("|f(x)|={}", testResult.getNorm());
            if (detailedReport) {
                Reports.reportNewtonRaphsonNorm(iterationReporter, testResult.getNorm(), iterations.getValue() + 1);
            }
            if (detailedReport || LOGGER.isTraceEnabled()) {
                reportAndLogLargestMismatchByAcEquationType(iterationReporter, equationSystem, equationVector.getArray());
            }
            if (testResult.isStop()) {
                return AcSolverStatus.CONVERGED;
            }

            return null;
        } finally {
            iterations.increment();
        }
    }

    private boolean isStateUnrealistic(Reporter reporter) {
        Map<String, Double> busesOutOfNormalVoltageRange = new LinkedHashMap<>();
        for (Variable<AcVariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            if (v.getType() == AcVariableType.BUS_V && !network.getBus(v.getElementNum()).isFictitious()) {
                double value = equationSystem.getStateVector().get(v.getRow());
                if (value < parameters.getMinRealisticVoltage() || value > parameters.getMaxRealisticVoltage()) {
                    busesOutOfNormalVoltageRange.put(network.getBus(v.getElementNum()).getId(), value);
                }
            }
        }
        if (!busesOutOfNormalVoltageRange.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                for (var e : busesOutOfNormalVoltageRange.entrySet()) {
                    LOGGER.trace("Bus '{}' has an unrealistic voltage magnitude: {} pu", e.getKey(), e.getValue());
                }
            }
            LOGGER.error("{} buses have a voltage magnitude out of range [{}, {}]: {}",
                    busesOutOfNormalVoltageRange.size(), parameters.getMinRealisticVoltage(), parameters.getMaxRealisticVoltage(), busesOutOfNormalVoltageRange);

            Reports.reportNewtonRaphsonBusesOutOfNormalVoltageRange(reporter, busesOutOfNormalVoltageRange, parameters.getMinRealisticVoltage(), parameters.getMaxRealisticVoltage());
        }
        return !busesOutOfNormalVoltageRange.isEmpty();
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, Reporter reporter) {
        // initialize state vector
        AcSolverUtil.initStateVector(network, equationSystem, voltageInitializer);

        Vectors.minus(equationVector.getArray(), targetVector.getArray());

        NewtonRaphsonStoppingCriteria.TestResult initialTestResult = parameters.getStoppingCriteria().test(equationVector.getArray(), equationSystem);
        StateVectorScaling svScaling = StateVectorScaling.fromMode(parameters, initialTestResult);

        LOGGER.debug("|f(x0)|={}", initialTestResult.getNorm());

        Reporter initialReporter = detailedReport ? Reports.createNewtonRaphsonMismatchReporter(reporter, 0) : null;
        if (detailedReport) {
            Reports.reportNewtonRaphsonNorm(initialReporter, initialTestResult.getNorm(), 0);
        }
        if (detailedReport || LOGGER.isTraceEnabled()) {
            reportAndLogLargestMismatchByAcEquationType(initialReporter, equationSystem, equationVector.getArray());
        }

        // start iterations
        AcSolverStatus status = AcSolverStatus.NO_CALCULATION;
        MutableInt iterations = new MutableInt();
        while (iterations.getValue() <= parameters.getMaxIterations()) {
            AcSolverStatus newStatus = runIteration(svScaling, iterations, reporter);
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

        // update network state variable
        if (status == AcSolverStatus.CONVERGED && isStateUnrealistic(reporter)) {
            status = AcSolverStatus.UNREALISTIC_STATE;
        }

        double slackBusActivePowerMismatch = network.getSlackBuses().stream().mapToDouble(LfBus::getMismatchP).sum();
        return new AcSolverResult(status, iterations.getValue(), slackBusActivePowerMismatch);
    }
}
