/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.commons.reporter.Reporter;
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
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NewtonRaphson {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewtonRaphson.class);

    public static final List<AcEquationType> REPORTED_AC_EQUATION_TYPES = List.of(AcEquationType.BUS_TARGET_P, AcEquationType.BUS_TARGET_Q, AcEquationType.BUS_TARGET_V);

    private final LfNetwork network;

    private final NewtonRaphsonParameters parameters;

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private final JacobianMatrix<AcVariableType, AcEquationType> j;

    private final TargetVector<AcVariableType, AcEquationType> targetVector;

    private final EquationVector<AcVariableType, AcEquationType> equationVector;

    public NewtonRaphson(LfNetwork network, NewtonRaphsonParameters parameters,
                         EquationSystem<AcVariableType, AcEquationType> equationSystem,
                         JacobianMatrix<AcVariableType, AcEquationType> j,
                         TargetVector<AcVariableType, AcEquationType> targetVector,
                         EquationVector<AcVariableType, AcEquationType> equationVector) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.j = Objects.requireNonNull(j);
        this.targetVector = Objects.requireNonNull(targetVector);
        this.equationVector = Objects.requireNonNull(equationVector);
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

    public void reportAndLogLargestMismatchByAcEquationType(Reporter reporter, EquationSystem<AcVariableType, AcEquationType> equationSystem, double[] mismatch, double norm, int iteration) {
        Map<AcEquationType, Pair<Equation<AcVariableType, AcEquationType>, Double>> mismatchEquations = getLargestMismatchByAcEquationType(equationSystem, mismatch);

        // report largest mismatches in (P, Q, V) equations
        Reporter iterationMismatchReporter = parameters.isDetailedReport() ? Reports.createNewtonRaphsonMismatchReporter(reporter, iteration) : null;

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
                        LOGGER.trace("Mismatch `{}` for {}: {} (element={}) || Bus V /_ PHI = {} /_ {}", acEquationType, equation, equationMismatch, elementId, busV, busPhi);
                        if (iterationMismatchReporter != null) {
                            Reports.reportNewtonRaphsonMismatch(iterationMismatchReporter, getEquationTypeDescription(acEquationType), equationMismatch, elementId, busV, busPhi, iteration);
                        }
                    });
        }

        if (iterationMismatchReporter != null) {
            Reports.reportNewtonRaphsonNorm(iterationMismatchReporter, norm, iteration);
        }
    }

    private String getEquationTypeDescription(AcEquationType acEquationType) {
        switch (acEquationType) {
            case BUS_TARGET_P: return "TargetP";
            case BUS_TARGET_Q: return "TargetQ";
            case BUS_TARGET_V: return "TargetV";
            default: return null; // not implemented for other ac equation types
        }
    }

    private NewtonRaphsonStatus runIteration(StateVectorScaling svScaling, MutableInt iterations, Reporter reporter) {
        LOGGER.debug("Start iteration {}", iterations);

        try {
            // solve f(x) = j * dx
            try {
                j.solveTransposed(equationVector.getArray());
            } catch (MatrixException e) {
                LOGGER.error(e.toString(), e);
                return NewtonRaphsonStatus.SOLVER_FAILED;
            }
            // f(x) now contains dx

            svScaling.apply(equationVector.getArray(), equationSystem);

            // update x and f(x) will be automatically updated
            equationSystem.getStateVector().minus(equationVector.getArray());

            // substract targets from f(x)
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

            // test stopping criteria and log norm(fx)
            NewtonRaphsonStoppingCriteria.TestResult testResult = parameters.getStoppingCriteria().test(equationVector.getArray(), equationSystem);

            testResult = svScaling.applyAfter(equationSystem, equationVector, targetVector,
                                              parameters.getStoppingCriteria(), testResult);

            LOGGER.debug("|f(x)|={}", testResult.getNorm());

            if (parameters.isDetailedReport() || LOGGER.isTraceEnabled()) {
                reportAndLogLargestMismatchByAcEquationType(reporter, equationSystem, equationVector.getArray(), testResult.getNorm(), iterations.getValue());
            }

            if (testResult.isStop()) {
                return NewtonRaphsonStatus.CONVERGED;
            }

            return null;
        } finally {
            iterations.increment();
        }
    }

    public static void initStateVector(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem, VoltageInitializer initializer) {
        double[] x = new double[equationSystem.getIndex().getSortedVariablesToFind().size()];
        for (Variable<AcVariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_V:
                    x[v.getRow()] = initializer.getMagnitude(network.getBus(v.getElementNum()));
                    break;

                case BUS_PHI:
                    x[v.getRow()] = initializer.getAngle(network.getBus(v.getElementNum()));
                    break;

                case SHUNT_B:
                    x[v.getRow()] = network.getShunt(v.getElementNum()).getB();
                    break;

                case BRANCH_ALPHA1:
                    x[v.getRow()] = network.getBranch(v.getElementNum()).getPiModel().getA1();
                    break;

                case BRANCH_RHO1:
                    x[v.getRow()] = network.getBranch(v.getElementNum()).getPiModel().getR1();
                    break;

                case DUMMY_P:
                case DUMMY_Q:
                case BUS_PHI_ZERO:
                case BUS_PHI_NEGATIVE:
                    x[v.getRow()] = 0;
                    break;

                case BUS_V_ZERO:
                case BUS_V_NEGATIVE:
                    // when balanced, zero and negative sequence should be zero
                    // v_zero and v_negative initially set to zero will bring a singularity to the Jacobian
                    // We chose to set the initial value to a small one, but different from zero
                    // By construction if the system does not carry any asymmetry in its structure,
                    // the resolution of the system on the three sequences will bring a singularity
                    // Therefore, if the system is balanced by construction, we should run a balanced load flow only
                    x[v.getRow()] = 0.1;
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
        equationSystem.getStateVector().set(x);
    }

    public void updateNetwork() {
        // update state variable
        StateVector stateVector = equationSystem.getStateVector();
        for (Variable<AcVariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_V:
                    network.getBus(v.getElementNum()).setV(stateVector.get(v.getRow()));
                    break;

                case BUS_PHI:
                    network.getBus(v.getElementNum()).setAngle(stateVector.get(v.getRow()));
                    break;

                case BUS_V_ZERO:
                    network.getBus(v.getElementNum()).getAsym().setVz(stateVector.get(v.getRow()));
                    break;

                case BUS_PHI_ZERO:
                    network.getBus(v.getElementNum()).getAsym().setAngleZ(stateVector.get(v.getRow()));
                    break;

                case BUS_V_NEGATIVE:
                    network.getBus(v.getElementNum()).getAsym().setVn(stateVector.get(v.getRow()));
                    break;

                case BUS_PHI_NEGATIVE:
                    network.getBus(v.getElementNum()).getAsym().setAngleN(stateVector.get(v.getRow()));
                    break;

                case SHUNT_B:
                    network.getShunt(v.getElementNum()).setB(stateVector.get(v.getRow()));
                    break;

                case BRANCH_ALPHA1:
                    network.getBranch(v.getElementNum()).getPiModel().setA1(stateVector.get(v.getRow()));
                    break;

                case BRANCH_RHO1:
                    network.getBranch(v.getElementNum()).getPiModel().setR1(stateVector.get(v.getRow()));
                    break;

                case DUMMY_P:
                case DUMMY_Q:
                    // nothing to do
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
    }

    private boolean isStateUnrealistic() {
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
        }
        return !busesOutOfNormalVoltageRange.isEmpty();
    }

    public NewtonRaphsonResult run(VoltageInitializer voltageInitializer, Reporter reporter) {
        // initialize state vector
        initStateVector(network, equationSystem, voltageInitializer);

        Vectors.minus(equationVector.getArray(), targetVector.getArray());

        NewtonRaphsonStoppingCriteria.TestResult initialTestResult = parameters.getStoppingCriteria().test(equationVector.getArray(), equationSystem);
        StateVectorScaling svScaling = StateVectorScaling.create(parameters, initialTestResult);

        LOGGER.debug("|f(x0)|={}", initialTestResult.getNorm());

        if (parameters.isDetailedReport() || LOGGER.isTraceEnabled()) {
            reportAndLogLargestMismatchByAcEquationType(reporter, equationSystem, equationVector.getArray(), initialTestResult.getNorm(), -1);
        }

        // start iterations
        NewtonRaphsonStatus status = NewtonRaphsonStatus.NO_CALCULATION;
        MutableInt iterations = new MutableInt();
        while (iterations.getValue() <= parameters.getMaxIterations()) {
            NewtonRaphsonStatus newStatus = runIteration(svScaling, iterations, reporter);
            if (newStatus != null) {
                status = newStatus;
                break;
            }
        }

        if (iterations.getValue() >= parameters.getMaxIterations()) {
            status = NewtonRaphsonStatus.MAX_ITERATION_REACHED;
        }

        if (status == NewtonRaphsonStatus.CONVERGED || parameters.isAlwaysUpdateNetwork()) {
            updateNetwork();
        }

        // update network state variable
        if (status == NewtonRaphsonStatus.CONVERGED && isStateUnrealistic()) {
            status = NewtonRaphsonStatus.UNREALISTIC_STATE;
        }

        double slackBusActivePowerMismatch = network.getSlackBuses().stream().mapToDouble(LfBus::getMismatchP).sum();
        return new NewtonRaphsonResult(status, iterations.getValue(), slackBusActivePowerMismatch);
    }
}
