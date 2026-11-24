/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.LUDecomposition;
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
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * AC weighted-least-squares state estimator. With z the readings, h(x) the measurement model, W the
 * inverse variances and H the Jacobian of h, it minimizes (z - h(x))^T W (z - h(x)) by Gauss-Newton:
 *
 * <pre>    (H^T W H) dx = H^T W (z - h(x)),    x &lt;- x + dx</pre>
 *
 * <p>h lives in the load flow's {@code EquationSystem}, keyed on {@code AcEquationType}, and H comes
 * from its {@code JacobianMatrix}. Two readings of the same quantity share an equation and a row of
 * H but keep their own residuals. The angle of each reference bus is not an unknown.</p>
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

    /**
     * Relative growth of the objective (absolute below 1) that counts as a worse iteration, so
     * round-off near the minimum is not taken for divergence.
     */
    private static final double OBJECTIVE_GROWTH_THRESHOLD = 1e-6;

    private final LfNetwork network;

    private final List<LfMeasurement> measurements;

    private final MatrixFactory matrixFactory;

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    /** The equation of each measurement, in measurement order; two readings of one quantity share it. */
    private final List<Equation<AcVariableType, AcEquationType>> equations = new ArrayList<>();

    /** Per equation, the square root of the summed weight of every reading of that quantity. */
    private double[] sqrtWeight;

    /** The state row of each variable mapped onto its gain column, -1 where it is not an unknown. */
    private int[] rowToColumn;

    private int unknownCount;

    /** H scaled by the square root of the weights, rebuilt on each iteration. */
    private Matrix scaledJacobian;

    private Matrix gain;

    public WlsStateEstimator(LfNetwork network, List<LfMeasurement> measurements) {
        this(network, measurements, new SparseMatrixFactory());
    }

    public WlsStateEstimator(LfNetwork network, List<LfMeasurement> measurements, MatrixFactory matrixFactory) {
        this.network = Objects.requireNonNull(network);
        this.measurements = List.copyOf(Objects.requireNonNull(measurements));
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.equationSystem = new EquationSystem<>(AcEquationType.class, network);
        for (LfMeasurement measurement : this.measurements) {
            LfElement element = measurement.element();
            AcEquationType type = measurement.type();
            equations.add(equationSystem.getEquation(element.getNum(), type).orElseGet(
                    () -> equationSystem.createEquation(element, type)
                            .addTerms(MeasurementEquations.terms(type, element, equationSystem.getVariableSet()))));
        }
    }

    /** The measurement model, for anything that needs the equations themselves. */
    public EquationSystem<AcVariableType, AcEquationType> getEquationSystem() {
        return equationSystem;
    }

    /** Starts from a flat profile: 1 pu everywhere, every angle zero. */
    public void initFlat() {
        double[] x = new double[equationSystem.getIndex().getRowCount()];
        for (LfBus bus : network.getBuses()) {
            int row = row(bus, AcVariableType.BUS_V);
            if (row >= 0) {
                x[row] = 1.0;
            }
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
        double[] h = new double[equations.size()];
        for (int i = 0; i < h.length; i++) {
            h[i] = equations.get(i).eval();
        }
        return h;
    }

    public WlsResult estimate() {
        return estimate(new WlsParameters());
    }

    /** Starts from a flat profile unless {@link #initFlat} or {@link #init} set another start. */
    public WlsResult estimate(WlsParameters parameters) {
        Objects.requireNonNull(parameters);
        if (equationSystem.getStateVector().get() == null) {
            initFlat();
        }
        indexUnknowns();
        sumWeightsPerQuantity();
        if (unknownCount == 0) {
            return buildResult(WlsResult.Status.UNOBSERVABLE,
                    "No reading depends on the state of the network, so there is nothing to estimate", 0, null);
        }
        LfBus unpinned = findBusWithUnpinnedAngle();
        if (unpinned != null) {
            return buildResult(WlsResult.Status.UNOBSERVABLE, "No reading ties the voltage angle at bus "
                    + unpinned.getId() + " to an angle reference bus, so it is determined only up to an"
                    + " arbitrary shift", 0, null);
        }

        WlsResult.Status status = WlsResult.Status.DID_NOT_CONVERGE;
        String statusText = "";
        int iteration = 0; // state updates applied, so a run stopped before its first one counts none
        double previousObjective = Double.NaN;
        int consecutiveGrowth = 0;

        try (JacobianMatrix<AcVariableType, AcEquationType> jacobian =
                     new JacobianMatrix<>(equationSystem, matrixFactory)) {
            while (iteration < parameters.maxIterations()) {
                boolean finiteJacobian = scaleJacobian(jacobian.getMatrix());
                double[] residuals = residuals();
                double objective = objectiveFunctionValue(residuals);
                if (!finiteJacobian || !Double.isFinite(objective)) {
                    status = WlsResult.Status.FAILED;
                    String culprit = finiteJacobian
                            ? "The weighted sum of squared residuals"
                            : "A derivative of the measurement model";
                    statusText = culprit + " is not a finite number, so the iteration cannot continue";
                    break;
                }

                // one worse iteration can be an overshoot Gauss-Newton recovers from; two in a row is divergence
                if (objective - previousObjective > OBJECTIVE_GROWTH_THRESHOLD * Math.max(previousObjective, 1)) {
                    consecutiveGrowth++;
                    if (consecutiveGrowth >= 2) {
                        status = WlsResult.Status.FAILED;
                        statusText = "The weighted sum of squared residuals grew in two successive"
                                + " iterations, to " + objective + ", so the iteration is diverging"
                                + " rather than closing in";
                        break;
                    }
                } else {
                    consecutiveGrowth = 0;
                }
                previousObjective = objective;

                double[] increment;
                try {
                    increment = solveNormalEquations(residuals);
                } catch (MatrixException e) {
                    status = WlsResult.Status.UNOBSERVABLE;
                    statusText = "The gain matrix could not be decomposed, which is what a measurement"
                            + " set that does not determine the state produces: " + e.getMessage();
                    break;
                }

                double[] x = equationSystem.getStateVector().get().clone();
                double maxIncrement = 0;
                for (int row = 0; row < rowToColumn.length; row++) {
                    int column = rowToColumn[row];
                    if (column >= 0) {
                        x[row] += increment[column];
                        maxIncrement = Math.max(maxIncrement, Math.abs(increment[column]));
                    }
                }
                if (!Double.isFinite(maxIncrement)) {
                    status = WlsResult.Status.FAILED;
                    statusText = "The state update is not a finite number, so the iteration cannot continue";
                    break;
                }
                equationSystem.getStateVector().set(x);
                iteration++;

                if (maxIncrement < parameters.tolerance()) {
                    status = WlsResult.Status.ESTIMATED;
                    break;
                }
            }

            // the last Jacobian is one step behind the final state
            boolean finiteJacobian = scaleJacobian(jacobian.getMatrix());
            return buildResultWithAccuracy(status, statusText, iteration, finiteJacobian);
        }
    }

    /** Accuracies are {@code NaN} and residuals untestable when the gain matrix cannot be decomposed. */
    private WlsResult buildResultWithAccuracy(WlsResult.Status status, String statusText, int iteration,
                                              boolean finiteJacobian) {
        gain = scaledJacobian.times(scaledJacobian.transpose());
        if (!finiteJacobian) {
            return buildResult(status, statusText, iteration, null);
        }
        // a dense LU decomposes lazily, so a singular gain can also throw from the solves inside
        try (LUDecomposition lu = gain.decomposeLU()) {
            return buildResult(status, statusText, iteration, lu);
        } catch (MatrixException e) {
            LOGGER.debug("Gain matrix of network {} could not be decomposed, so the estimate carries"
                    + " no accuracy: {}", network, e.getMessage());
            return buildResult(status, statusText, iteration, null);
        }
    }

    /** Assembles the gain matrix at the current state and solves the normal equations for dx. */
    private double[] solveNormalEquations(double[] residuals) {
        double[] increment = projectResiduals(residuals);
        gain = scaledJacobian.times(scaledJacobian.transpose());
        try (LUDecomposition lu = gain.decomposeLU()) {
            lu.solve(increment); // solved in place: the projection becomes dx
        }
        return increment;
    }

    /** What each meter reads less what the current state predicts, per unit, in measurement order. */
    private double[] residuals() {
        double[] residuals = new double[measurements.size()];
        for (int i = 0; i < residuals.length; i++) {
            residuals[i] = measurements.get(i).valuePu() - equations.get(i).eval();
        }
        return residuals;
    }

    /** The weighted sum of squared residuals. */
    private double objectiveFunctionValue(double[] residuals) {
        double objective = 0;
        for (int i = 0; i < residuals.length; i++) {
            objective += measurements.get(i).weight() * residuals[i] * residuals[i];
        }
        return objective;
    }

    /**
     * Gives every unknown a gain column. Only angle differences are physical, so one angle per
     * synchronous component is held fixed. Those are exactly the buses the network marks as
     * reference, the same ones the AC load flow pins, so the estimator inherits its angle origin
     * rather than inventing one.
     */
    private void indexUnknowns() {
        int rowCount = equationSystem.getIndex().getRowCount();
        boolean[] fixed = new boolean[rowCount];
        int referenceCount = 0;
        for (LfBus bus : network.getBuses()) {
            if (bus.isReference()) {
                referenceCount++;
                int row = row(bus, AcVariableType.BUS_PHI);
                if (row >= 0) {
                    fixed[row] = true;
                }
            }
        }
        if (referenceCount == 0) {
            throw new PowsyblException("No angle reference bus in network " + network
                    + ": the estimate would be determined only up to an arbitrary angle shift");
        }
        rowToColumn = new int[rowCount];
        unknownCount = 0;
        for (int row = 0; row < rowCount; row++) {
            rowToColumn[row] = fixed[row] ? -1 : unknownCount++;
        }
    }

    /**
     * A bus whose angle is an unknown that no reading ties to a reference bus, or null if there is
     * none. Readings couple the buses whose variables they share. A coupled group without a
     * reference bus can shift all its angles together and change no reading, and a numerical
     * decomposition does not reliably refuse the singular gain matrix that makes.
     */
    private LfBus findBusWithUnpinnedAngle() {
        int[] group = new int[network.getBuses().size()];
        Arrays.setAll(group, i -> i);
        for (Equation<AcVariableType, AcEquationType> equation : equations) {
            int first = -1;
            for (Variable<AcVariableType> variable : equation.getTermsByVariable().keySet()) {
                int bus = groupOf(group, variable.getElementNum());
                if (first < 0) {
                    first = bus;
                } else {
                    group[bus] = first;
                }
            }
        }
        boolean[] pinned = new boolean[group.length];
        for (LfBus bus : network.getBuses()) {
            if (bus.isReference() && row(bus, AcVariableType.BUS_PHI) >= 0) {
                pinned[groupOf(group, bus.getNum())] = true;
            }
        }
        for (LfBus bus : network.getBuses()) {
            if (row(bus, AcVariableType.BUS_PHI) >= 0 && !pinned[groupOf(group, bus.getNum())]) {
                return bus;
            }
        }
        return null;
    }

    private static int groupOf(int[] group, int bus) {
        int root = bus;
        while (group[root] != root) {
            root = group[root];
        }
        return root;
    }

    /**
     * Readings sharing a row of H add their weights. The square root is kept, since H is scaled by it.
     */
    private void sumWeightsPerQuantity() {
        double[] weight = new double[equationSystem.getIndex().getColumnCount()];
        for (int i = 0; i < measurements.size(); i++) {
            weight[equations.get(i).getColumn()] += measurements.get(i).weight();
        }
        sqrtWeight = new double[weight.length];
        for (int column = 0; column < weight.length; column++) {
            sqrtWeight[column] = Math.sqrt(weight[column]);
        }
    }

    /**
     * Builds W^(1/2) H^T over the unknowns only, so the gain matrix is this times its transpose.
     * Rebuilt each iteration: a dense matrix iterates non-zero values only, so its element indices
     * shift as derivatives that were zero at a flat start become non-zero.
     *
     * @param transposedJacobian H^T, as {@code JacobianMatrix} holds it
     * @return whether every derivative was a finite number
     */
    private boolean scaleJacobian(Matrix transposedJacobian) {
        // a sparse matrix must be filled column by column, which is the order this iterates in
        Matrix scaled = matrixFactory.create(unknownCount, sqrtWeight.length, 3 * unknownCount);
        boolean[] finite = {true};
        transposedJacobian.iterateNonZeroValue((row, column, value) -> {
            int gainColumn = rowToColumn[row];
            if (gainColumn >= 0) {
                finite[0] &= Double.isFinite(value);
                // set, not add: only set fills in the start of a skipped (empty) column
                scaled.set(gainColumn, column, value * sqrtWeight[column]);
            }
        });
        scaledJacobian = scaled;
        return finite[0];
    }

    /**
     * H^T W (z - h(x)), formed against the scaled Jacobian, whose columns already carry sqrt(w).
     */
    private double[] projectResiduals(double[] residuals) {
        double[] residualPerQuantity = new double[sqrtWeight.length];
        for (int i = 0; i < residuals.length; i++) {
            residualPerQuantity[equations.get(i).getColumn()] += measurements.get(i).weight() * residuals[i];
        }
        for (int column = 0; column < residualPerQuantity.length; column++) {
            residualPerQuantity[column] /= sqrtWeight[column];
        }
        double[] projection = new double[unknownCount];
        scaledJacobian.iterateNonZeroValue((row, column, value) ->
                projection[row] += value * residualPerQuantity[column]);
        return projection;
    }

    /**
     * The gain matrix H^T W H at the final state, over the unknowns only. Null before {@link #estimate}.
     */
    public Matrix getGainMatrix() {
        return gain;
    }

    /**
     * The diagonal of G^-1, one solve per unknown, without forming the inverse.
     */
    private double[] estimateVariances(LUDecomposition lu) {
        double[] variance = new double[unknownCount];
        if (lu == null) {
            Arrays.fill(variance, Double.NaN);
            return variance;
        }
        for (int column = 0; column < unknownCount; column++) {
            double[] unit = new double[unknownCount];
            unit[column] = 1;
            lu.solve(unit);
            variance[column] = unit[column];
        }
        return variance;
    }

    /**
     * The diagonal of {@code H G^-1 H^T}, per equation.
     */
    private double[] propagatedVariances(LUDecomposition lu) {
        double[] propagated = new double[sqrtWeight.length];
        if (lu == null) {
            Arrays.fill(propagated, Double.NaN);
            return propagated;
        }
        for (int column = 0; column < propagated.length; column++) {
            double[] row = new double[unknownCount];
            scaledJacobian.iterateNonZeroValueOfColumn(column, (r, ignored, value) -> row[r] = value);
            double[] solved = row.clone();
            lu.solve(solved);
            double quadraticForm = 0;
            for (int i = 0; i < unknownCount; i++) {
                quadraticForm += row[i] * solved[i];
            }
            // the column carries the scaling of its own weight, which this takes back out
            propagated[column] = quadraticForm / (sqrtWeight[column] * sqrtWeight[column]);
        }
        return propagated;
    }

    /**
     * The state is in per unit and radians indexed by the equation system; the result is in the
     * units and identifiers IIDM uses.
     */
    private WlsResult buildResult(WlsResult.Status status, String statusText, int iteration, LUDecomposition lu) {
        double[] x = equationSystem.getStateVector().get();
        double[] variance = estimateVariances(lu);

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

        List<WlsResult.MeasurementResult> measurementResults = buildMeasurementResults(lu);
        double objective = 0;
        for (WlsResult.MeasurementResult measurementResult : measurementResults) {
            objective += measurementResult.weightedSquaredResidual();
        }

        return new WlsResult(status, statusText, iteration, referenceBusIds(), objective,
                measurements.size() - unknownCount, busResults, branchResults, measurementResults);
    }

    private static double value(double[] x, int row) {
        return row >= 0 ? x[row] : Double.NaN;
    }

    /** {@code NaN} where the quantity is not an unknown, which is what a fixed angle is. */
    private double standardDeviation(double[] variance, int stateRow) {
        int column = stateRow >= 0 && stateRow < rowToColumn.length ? rowToColumn[stateRow] : -1;
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
    private List<WlsResult.MeasurementResult> buildMeasurementResults(LUDecomposition lu) {
        double[] propagated = propagatedVariances(lu);
        List<WlsResult.MeasurementResult> results = new ArrayList<>(measurements.size());
        for (int i = 0; i < measurements.size(); i++) {
            LfMeasurement measurement = measurements.get(i);
            double variance = measurement.sigmaPu() * measurement.sigmaPu();
            double residualVariance = variance - propagated[equations.get(i).getColumn()];
            double redundancy = residualVariance / variance;
            boolean testable = redundancy >= MINIMUM_TESTABLE_REDUNDANCY;
            // without a decomposed gain matrix the redundancy is unknown, which is not critical
            boolean unknown = Double.isNaN(redundancy);
            double unit = unitFactor(measurement);
            double estimatedPu = equations.get(i).eval();
            double residualPu = measurement.valuePu() - estimatedPu;
            results.add(new WlsResult.MeasurementResult(
                    measurement.element().getId(), measurement.type(), measurement.sourceMeasurementIds(),
                    measurement.valuePu() * unit,
                    estimatedPu * unit,
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
