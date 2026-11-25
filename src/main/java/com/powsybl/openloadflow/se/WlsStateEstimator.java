/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.SingleEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.se.equations.MeasurementEquations;
import com.powsybl.openloadflow.se.solver.GainFactorization;
import com.powsybl.openloadflow.se.solver.GaussNewtonFactory;
import com.powsybl.openloadflow.se.solver.WlsParameters;
import com.powsybl.openloadflow.se.solver.WlsProblem;
import com.powsybl.openloadflow.se.solver.WlsSolver;
import com.powsybl.openloadflow.se.solver.WlsSolverFactory;
import com.powsybl.openloadflow.se.solver.WlsSolverResult;
import com.powsybl.openloadflow.se.solver.WlsSolverStatus;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AC weighted-least-squares state estimator. It builds the measurement model of a set of readings
 * in the load flow's {@code EquationSystem}, hands the minimization to a {@link WlsSolver}, and
 * reports the state it ended on with the accuracy the readings allow.
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public class WlsStateEstimator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WlsStateEstimator.class);

    /**
     * Redundancy below which a measurement counts as critical. Round-off keeps a critical
     * measurement's redundancy slightly above zero, and its normalized residual would be noise.
     */
    public static final double MINIMUM_TESTABLE_REDUNDANCY = 1e-9;

    private final LfNetwork network;

    private final List<LfMeasurement> measurements;

    private final MatrixFactory matrixFactory;

    private final WlsSolverFactory solverFactory;

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private final WlsProblem problem;

    private Matrix gain;

    public WlsStateEstimator(LfNetwork network, List<LfMeasurement> measurements) {
        this(network, measurements, new SparseMatrixFactory());
    }

    public WlsStateEstimator(LfNetwork network, List<LfMeasurement> measurements, MatrixFactory matrixFactory) {
        this(network, measurements, matrixFactory, new GaussNewtonFactory());
    }

    public WlsStateEstimator(LfNetwork network, List<LfMeasurement> measurements, MatrixFactory matrixFactory,
                             WlsSolverFactory solverFactory) {
        this.network = Objects.requireNonNull(network);
        this.measurements = List.copyOf(Objects.requireNonNull(measurements));
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.solverFactory = Objects.requireNonNull(solverFactory);
        this.equationSystem = new EquationSystem<>(AcEquationType.class, network);

        List<Equation<AcVariableType, AcEquationType>> equations = new ArrayList<>(this.measurements.size());
        double[] values = new double[this.measurements.size()];
        double[] standardDeviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            LfMeasurement measurement = this.measurements.get(i);
            LfElement element = measurement.element();
            AcEquationType type = measurement.type();
            equations.add(equationSystem.getEquation(element.getNum(), type).orElseGet(
                    () -> equationSystem.createEquation(element, type)
                            .addTerms(MeasurementEquations.terms(type, element, equationSystem.getVariableSet()))));
            values[i] = measurement.valuePu();
            standardDeviations[i] = measurement.sigmaPu();
        }
        this.problem = new WlsProblem(network, equationSystem, equations, values, standardDeviations, matrixFactory);
    }

    /** The measurement model, for anything that needs the equations themselves. */
    public EquationSystem<AcVariableType, AcEquationType> getEquationSystem() {
        return equationSystem;
    }

    /** Starts from a flat profile: 1 pu everywhere, every angle zero. */
    public void initFlat() {
        double[] x = new double[equationSystem.getIndex().getRowCount()];
        for (LfBus bus : network.getBuses()) {
            set(x, row(bus, AcVariableType.BUS_V), 1.0);
        }
        equationSystem.getStateVector().set(x);
    }

    /**
     * Starts from an externally supplied state, for instance a previous estimate.
     *
     * @param busV           the voltage magnitude of each bus, per unit, indexed by bus number
     * @param busAngleRadian the voltage angle of each bus, in radians, indexed by bus number
     */
    public void init(double[] busV, double[] busAngleRadian) {
        Objects.requireNonNull(busV);
        Objects.requireNonNull(busAngleRadian);
        double[] x = new double[equationSystem.getIndex().getRowCount()];
        for (LfBus bus : network.getBuses()) {
            set(x, row(bus, AcVariableType.BUS_V), busV[bus.getNum()]);
            set(x, row(bus, AcVariableType.BUS_PHI), busAngleRadian[bus.getNum()]);
        }
        equationSystem.getStateVector().set(x);
    }

    private static void set(double[] x, int row, double value) {
        if (row >= 0) {
            x[row] = value;
        }
    }

    private int row(LfBus bus, AcVariableType type) {
        return equationSystem.getVariableSet().getVariable(bus.getNum(), type).getRow();
    }

    /** What the meters are predicted to read at the current state, per unit, in measurement order. */
    public double[] evaluate() {
        return problem.evaluate();
    }

    public WlsResult estimate() {
        return estimate(new WlsParameters());
    }

    /** Starts from a flat profile unless {@link #initFlat} or {@link #init} set another start. */
    public WlsResult estimate(WlsParameters parameters) {
        Objects.requireNonNull(parameters);
        gain = null;
        if (equationSystem.getStateVector().get() == null) {
            initFlat();
        }
        if (problem.getUnknownCount() == 0) {
            return buildResult(WlsResult.Status.UNOBSERVABLE,
                    "No reading depends on the state of the network, so there is nothing to estimate", 0, null, null);
        }
        Optional<LfBus> unpinned = problem.findBusWithUnpinnedAngle();
        if (unpinned.isPresent()) {
            return buildResult(WlsResult.Status.UNOBSERVABLE, "No reading ties the voltage angle at bus "
                    + unpinned.get().getId() + " to an angle reference bus, so it is determined only up to an"
                    + " arbitrary shift", 0, null, null);
        }

        try (JacobianMatrix<AcVariableType, AcEquationType> jacobian =
                     new JacobianMatrix<>(equationSystem, matrixFactory)) {
            WlsSolver solver = solverFactory.create(problem, jacobian, parameters);
            WlsSolverResult solved = solver.run();
            // the solver's last linearization is one step behind the final state and may have left readings out
            WlsProblem.ScaledJacobian linearization = problem.scaleJacobian(jacobian.getMatrix(), true);
            return buildResultWithAccuracy(solver, solved, linearization);
        }
    }

    private static WlsResult.Status statusOf(WlsSolverStatus status) {
        return switch (status) {
            case CONVERGED -> WlsResult.Status.ESTIMATED;
            case MAX_ITERATION_REACHED -> WlsResult.Status.DID_NOT_CONVERGE;
            case SINGULAR_GAIN_MATRIX -> WlsResult.Status.UNOBSERVABLE;
            case SOLVER_FAILED -> WlsResult.Status.FAILED;
        };
    }

    /** Accuracies are {@code NaN} and residuals untestable when the gain matrix cannot be factorized. */
    private WlsResult buildResultWithAccuracy(WlsSolver solver, WlsSolverResult solved,
                                              WlsProblem.ScaledJacobian linearization) {
        WlsResult.Status status = statusOf(solved.status());
        if (!linearization.finite()) {
            return buildResult(status, solved.statusText(), solved.iterations(), null, null);
        }
        // a dense LU decomposes lazily, so a singular gain can also throw from the solves inside
        try (GainFactorization factorization = solver.factorizeGain(linearization.matrix())) {
            gain = factorization.getGainMatrix();
            return buildResult(status, solved.statusText(), solved.iterations(), factorization, linearization.matrix());
        } catch (MatrixException e) {
            LOGGER.debug("Gain matrix of network {} could not be factorized, so the estimate carries"
                    + " no accuracy: {}", network, e.getMessage());
            return buildResult(status, solved.statusText(), solved.iterations(), null, null);
        }
    }

    /**
     * The gain matrix H^T W H at the final state, over the unknowns only. Null before
     * {@link #estimate} and where it could not be factorized.
     */
    public Matrix getGainMatrix() {
        return gain;
    }

    /**
     * The diagonal of G^-1, one solve per unknown, without forming the inverse.
     */
    private double[] estimateVariances(GainFactorization factorization) {
        int unknownCount = problem.getUnknownCount();
        double[] variance = new double[unknownCount];
        if (factorization == null) {
            Arrays.fill(variance, Double.NaN);
            return variance;
        }
        for (int column = 0; column < unknownCount; column++) {
            double[] unit = new double[unknownCount];
            unit[column] = 1;
            factorization.solve(unit);
            variance[column] = unit[column];
        }
        return variance;
    }

    /**
     * The diagonal of {@code H G^-1 H^T}, per equation.
     */
    private double[] propagatedVariances(GainFactorization factorization, Matrix scaledJacobian) {
        double[] propagated = new double[problem.getEquationCount()];
        if (factorization == null) {
            Arrays.fill(propagated, Double.NaN);
            return propagated;
        }
        int unknownCount = problem.getUnknownCount();
        for (int column = 0; column < propagated.length; column++) {
            double[] row = new double[unknownCount];
            scaledJacobian.iterateNonZeroValueOfColumn(column, (r, ignored, value) -> row[r] = value);
            double[] solved = row.clone();
            factorization.solve(solved);
            double quadraticForm = 0;
            for (int i = 0; i < unknownCount; i++) {
                quadraticForm += row[i] * solved[i];
            }
            // the column carries the scaling of its own weight, which this takes back out
            propagated[column] = quadraticForm / problem.getEquationWeight(column);
        }
        return propagated;
    }

    /**
     * The state is in per unit and radians indexed by the equation system; the result is in the
     * units and identifiers IIDM uses.
     */
    private WlsResult buildResult(WlsResult.Status status, String statusText, int iteration,
                                  GainFactorization factorization, Matrix scaledJacobian) {
        double[] x = equationSystem.getStateVector().get();
        double[] variance = estimateVariances(factorization);

        List<WlsResult.BusResult> busResults = new ArrayList<>(network.getBuses().size());
        for (LfBus bus : network.getBuses()) {
            int v = row(bus, AcVariableType.BUS_V);
            int phi = row(bus, AcVariableType.BUS_PHI);
            busResults.add(new WlsResult.BusResult(bus.getId(), bus.getVoltageLevelId(),
                    value(x, v) * bus.getNominalV(),
                    Math.toDegrees(value(x, phi)),
                    standardDeviation(variance, v) * bus.getNominalV(),
                    Math.toDegrees(standardDeviation(variance, phi))));
        }

        List<WlsResult.BranchResult> branchResults = new ArrayList<>();
        for (LfBranch branch : network.getBranches()) {
            if (branch.getBus1() != null && branch.getBus2() != null) {
                branchResults.add(new WlsResult.BranchResult(branch.getId(),
                        flow(branch, AcEquationType.BRANCH_TARGET_P1), flow(branch, AcEquationType.BRANCH_TARGET_Q1),
                        flow(branch, AcEquationType.BRANCH_TARGET_P2), flow(branch, AcEquationType.BRANCH_TARGET_Q2)));
            }
        }

        List<WlsResult.MeasurementResult> measurementResults = buildMeasurementResults(factorization, scaledJacobian);
        double objective = 0;
        for (WlsResult.MeasurementResult measurementResult : measurementResults) {
            objective += measurementResult.weightedSquaredResidual();
        }

        return new WlsResult(status, statusText, iteration, referenceBusIds(), objective,
                measurements.size() - problem.getUnknownCount(), busResults, branchResults, measurementResults);
    }

    private static double value(double[] x, int row) {
        return row >= 0 ? x[row] : Double.NaN;
    }

    /** {@code NaN} where the quantity is not an unknown, which is what a fixed angle is. */
    private double standardDeviation(double[] variance, int stateRow) {
        int column = problem.getGainColumn(stateRow);
        return column < 0 ? Double.NaN : Math.sqrt(variance[column]);
    }

    /** One per synchronous component. */
    private List<String> referenceBusIds() {
        return network.getBuses().stream()
                .filter(LfBus::isReference)
                .map(LfBus::getId)
                .toList();
    }

    /** One branch flow at the estimated state, in MW or MVar. */
    private double flow(LfBranch branch, AcEquationType type) {
        SingleEquationTerm<AcVariableType, AcEquationType> term = MeasurementEquations.branchTerm(
                branch, type, equationSystem.getVariableSet());
        for (Variable<AcVariableType> variable : term.getVariables()) {
            if (variable.getRow() < 0) {
                return Double.NaN; // a state the measurements left undetermined
            }
        }
        term.setStateVector(equationSystem.getStateVector());
        return term.eval() * PerUnit.SB;
    }

    /**
     * Residuals normalized by {@code Omega = R - H G^-1 H^T}. {@code Omega_ii = 0} marks a critical
     * measurement, whose residual cannot be tested.
     */
    private List<WlsResult.MeasurementResult> buildMeasurementResults(GainFactorization factorization,
                                                                      Matrix scaledJacobian) {
        double[] propagated = propagatedVariances(factorization, scaledJacobian);
        double[] estimated = problem.evaluate();
        List<WlsResult.MeasurementResult> results = new ArrayList<>(measurements.size());
        for (int i = 0; i < measurements.size(); i++) {
            LfMeasurement measurement = measurements.get(i);
            double variance = measurement.sigmaPu() * measurement.sigmaPu();
            double residualVariance = variance - propagated[problem.getEquationColumn(i)];
            double redundancy = residualVariance / variance;
            boolean testable = redundancy >= MINIMUM_TESTABLE_REDUNDANCY;
            // without a factorized gain matrix the redundancy is unknown, which is not critical
            boolean unknown = Double.isNaN(redundancy);
            double unit = unitFactor(measurement);
            double residualPu = measurement.valuePu() - estimated[i];
            results.add(new WlsResult.MeasurementResult(
                    measurement.element().getId(), measurement.type(), measurement.sourceMeasurementIds(),
                    measurement.valuePu() * unit,
                    estimated[i] * unit,
                    residualPu * unit,
                    measurement.sigmaPu() * unit,
                    testable ? residualPu / Math.sqrt(residualVariance) : Double.NaN,
                    testable || unknown ? redundancy : 0));
        }
        return results;
    }

    /** What a per unit value of this quantity is multiplied by to reach the unit IIDM uses. */
    private static double unitFactor(LfMeasurement measurement) {
        return switch (measurement.type()) {
            case BUS_TARGET_V -> ((LfBus) measurement.element()).getNominalV();
            case BRANCH_TARGET_I1 -> PerUnit.ib(((LfBranch) measurement.element()).getBus1().getNominalV());
            case BRANCH_TARGET_I2 -> PerUnit.ib(((LfBranch) measurement.element()).getBus2().getNominalV());
            default -> PerUnit.SB;
        };
    }
}
