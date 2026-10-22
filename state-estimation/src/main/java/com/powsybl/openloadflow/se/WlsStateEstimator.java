/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.se.equations.MeasuredQuantity;
import com.powsybl.openloadflow.se.equations.MeasurementEquation;
import com.powsybl.openloadflow.se.equations.MeasurementEquations;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An AC weighted-least-squares state estimator: it finds the network state that best
 * explains a set of noisy, redundant meter readings, trusting each reading in inverse
 * proportion to its variance.
 *
 * <p>Writing z for the readings, h(x) for what those meters would read in state x, and
 * W for the diagonal of inverse variances, the estimate minimizes
 * (z - h(x))' W (z - h(x)). Gauss-Newton solves the resulting normal equations
 * repeatedly, with H the Jacobian of h:</p>
 *
 * <pre>    (H' W H) dx = H' W (z - h(x)),    x &lt;- x + dx</pre>
 *
 * <p>State layout: the voltage magnitude of bus i occupies row i, its angle row n + i,
 * in radians. The angle of each reference bus is not an unknown. Its Jacobian column is
 * never assembled, which pins the angle origin of its synchronous component exactly.</p>
 *
 * <p>The gain matrix is dense, so this is usable at prototype size only.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public class WlsStateEstimator {

    private final LfNetwork network;

    private final List<Measurement> measurements;

    private final VariableSet<AcVariableType> variableSet = new VariableSet<>();

    private final StateVector stateVector;

    private final double[] state;

    private final List<MeasurementEquation> equations = new ArrayList<>();

    public WlsStateEstimator(LfNetwork network, List<Measurement> measurements) {
        this.network = Objects.requireNonNull(network);
        this.measurements = List.copyOf(Objects.requireNonNull(measurements));
        int busCount = network.getBuses().size();
        for (LfBus bus : network.getBuses()) {
            variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V).setRow(bus.getNum());
            variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI).setRow(busCount + bus.getNum());
        }
        this.state = new double[2 * busCount];
        this.stateVector = new StateVector(state);
        for (Measurement measurement : this.measurements) {
            equations.add(createEquation(measurement));
        }
    }

    private MeasurementEquation createEquation(Measurement measurement) {
        return switch (measurement.quantity()) {
            case BUS_V -> MeasurementEquations.busVoltage(measurement.bus(), variableSet, stateVector);
            case BUS_P -> MeasurementEquations.busInjection(measurement.bus(), true, variableSet, stateVector);
            case BUS_Q -> MeasurementEquations.busInjection(measurement.bus(), false, variableSet, stateVector);
            case BRANCH_P, BRANCH_Q, BRANCH_I -> MeasurementEquations.branchFlow(measurement.branch(),
                    measurement.quantity(), measurement.side(), variableSet, stateVector);
        };
    }

    /** Starts from a flat profile: 1 pu everywhere, every angle zero. */
    public void initFlat() {
        int busCount = network.getBuses().size();
        for (int i = 0; i < busCount; i++) {
            state[i] = 1.0;
            state[busCount + i] = 0.0;
        }
        stateVector.set(state);
    }

    /** Starts from an externally supplied state, for instance a previous estimate. */
    public void init(double[] busV, double[] busAngleRadian) {
        int busCount = network.getBuses().size();
        System.arraycopy(busV, 0, state, 0, busCount);
        System.arraycopy(busAngleRadian, 0, state, busCount, busCount);
        stateVector.set(state);
    }

    /** What the meters are predicted to read at the current state, in measurement order. */
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

    public WlsResult estimate(WlsParameters parameters) {
        Objects.requireNonNull(parameters);
        int busCount = network.getBuses().size();

        // Only angle differences are physical, so one angle per synchronous component is
        // held fixed. Those are exactly the buses the network marks as reference, the
        // same ones the AC load flow pins, so the estimator inherits its angle origin
        // rather than inventing one.
        boolean[] angleFixed = new boolean[busCount];
        int referenceCount = 0;
        for (LfBus bus : network.getBuses()) {
            if (bus.isReference()) {
                angleFixed[bus.getNum()] = true;
                referenceCount++;
            }
        }
        if (referenceCount == 0) {
            throw new IllegalStateException("No angle reference bus in network " + network
                    + ": the estimate would be determined only up to an arbitrary angle shift");
        }

        // state rows map onto Jacobian columns; a fixed angle is not an unknown, so it
        // has no column
        int[] rowToColumn = new int[2 * busCount];
        int columnCount = 0;
        for (int row = 0; row < 2 * busCount; row++) {
            boolean fixed = row >= busCount && angleFixed[row - busCount];
            rowToColumn[row] = fixed ? -1 : columnCount++;
        }

        boolean converged = false;
        int iteration = 0;
        while (iteration < parameters.maxIterations()) {
            iteration++;

            double[] increment = solveNormalEquations(rowToColumn, columnCount);

            double maxIncrement = 0;
            for (int row = 0; row < 2 * busCount; row++) {
                int column = rowToColumn[row];
                if (column >= 0) {
                    state[row] += increment[column];
                    maxIncrement = Math.max(maxIncrement, Math.abs(increment[column]));
                }
            }
            stateVector.set(state);

            if (maxIncrement < parameters.tolerance()) {
                converged = true;
                break;
            }
        }

        return buildResult(converged, iteration, busCount);
    }

    /**
     * The state is in per unit and radians indexed by internal bus number; the result is in the
     * units and identifiers IIDM uses.
     */
    private WlsResult buildResult(boolean converged, int iteration, int busCount) {
        List<WlsResult.BusResult> busResults = new ArrayList<>(busCount);
        for (LfBus bus : network.getBuses()) {
            busResults.add(new WlsResult.BusResult(bus.getId(), bus.getVoltageLevelId(),
                    state[bus.getNum()] * bus.getNominalV(),
                    Math.toDegrees(state[busCount + bus.getNum()])));
        }

        List<WlsResult.BranchResult> branchResults = new ArrayList<>();
        for (LfBranch branch : network.getBranches()) {
            if (branch.getBus1() != null && branch.getBus2() != null) {
                branchResults.add(new WlsResult.BranchResult(branch.getId(),
                        flow(branch, MeasuredQuantity.BRANCH_P, 1), flow(branch, MeasuredQuantity.BRANCH_Q, 1),
                        flow(branch, MeasuredQuantity.BRANCH_P, 2), flow(branch, MeasuredQuantity.BRANCH_Q, 2)));
            }
        }

        return new WlsResult(converged ? WlsResult.Status.ESTIMATED : WlsResult.Status.DID_NOT_CONVERGE,
                iteration, referenceBusIds(), objectiveFunctionValue(),
                busResults, branchResults);
    }

    /** One per synchronous component. */
    private List<String> referenceBusIds() {
        return network.getBuses().stream()
                .filter(LfBus::isReference)
                .map(LfBus::getId)
                .toList();
    }

    /** One branch flow at the estimated state, in MW or MVar. */
    private double flow(LfBranch branch, MeasuredQuantity quantity, int side) {
        return MeasurementEquations.branchFlow(branch, quantity, side, variableSet, stateVector).eval()
                * PerUnit.SB;
    }

    private double objectiveFunctionValue() {
        double objective = 0;
        for (int i = 0; i < equations.size(); i++) {
            Measurement measurement = measurements.get(i);
            double residual = measurement.valuePu() - equations.get(i).eval();
            objective += measurement.weight() * residual * residual;
        }
        return objective;
    }

    /**
     * Assembles G = H' W H and b = H' W (z - h(x)) one measurement row at a time, then
     * solves G dx = b. The gain matrix is accumulated directly: an explicit H is never
     * formed, since each measurement contributes an outer product of its own sparse row.
     */
    private double[] solveNormalEquations(int[] rowToColumn, int columnCount) {
        DenseMatrix gain = new DenseMatrix(columnCount, columnCount);
        double[] rhs = new double[columnCount];

        for (int i = 0; i < equations.size(); i++) {
            MeasurementEquation equation = equations.get(i);
            Measurement measurement = measurements.get(i);
            double weight = measurement.weight();
            double residual = measurement.valuePu() - equation.eval();

            int[] columns = new int[equation.variables().size()];
            double[] derivatives = new double[equation.variables().size()];
            int count = 0;
            for (Variable<AcVariableType> variable : equation.variables()) {
                int column = rowToColumn[variable.getRow()];
                if (column < 0) {
                    continue; // a fixed reference angle contributes no column
                }
                columns[count] = column;
                derivatives[count] = equation.der(variable);
                count++;
            }
            for (int a = 0; a < count; a++) {
                rhs[columns[a]] += weight * derivatives[a] * residual;
                for (int b = 0; b < count; b++) {
                    gain.add(columns[a], columns[b], weight * derivatives[a] * derivatives[b]);
                }
            }
        }

        try (LUDecomposition lu = gain.decomposeLU()) {
            lu.solve(rhs); // solved in place: rhs becomes dx
        }
        return rhs;
    }
}
