/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.solver;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The weighted least squares problem a {@link WlsSolver} works on: the measurement model h(x),
 * what each reading says, how far it is trusted and which state variables are unknowns.
 *
 * <p>h lives in the load flow's {@code EquationSystem}, keyed on {@code AcEquationType}. Two
 * readings of the same quantity share an equation and a row of H but keep their own residuals.
 * The angle of each reference bus is not an unknown.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public final class WlsProblem {

    /**
     * @param matrix {@code W^(1/2) H^T} over the unknowns only
     * @param finite whether every derivative in it is a finite number
     */
    public record ScaledJacobian(Matrix matrix, boolean finite) {
    }

    private final LfNetwork network;

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    /** The equation of each reading, in reading order; two readings of one quantity share it. */
    private final List<Equation<AcVariableType, AcEquationType>> equations;

    private final double[] values;

    private final double[] weights;

    private final MatrixFactory matrixFactory;

    /** Per equation, the square root of the summed weight of every reading of that quantity. */
    private final double[] sqrtWeight;

    /** Per equation, whether it is a current magnitude. */
    private final boolean[] current;

    /** The state row of each variable mapped onto its gain column, -1 where it is not an unknown. */
    private final int[] rowToColumn;

    private final int unknownCount;

    /**
     * @param network            the network the equations are written on
     * @param equationSystem     the measurement model
     * @param equations          the equation of each reading, in reading order
     * @param values             what each reading says, per unit
     * @param standardDeviations the standard deviation of each reading, per unit
     * @param matrixFactory      creates the matrices handed to a solver
     */
    public WlsProblem(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                      List<Equation<AcVariableType, AcEquationType>> equations, double[] values,
                      double[] standardDeviations, MatrixFactory matrixFactory) {
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.equations = List.copyOf(equations);
        this.values = values.clone();
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        if (values.length != equations.size() || standardDeviations.length != equations.size()) {
            throw new IllegalArgumentException("Expected one value and one standard deviation per reading");
        }
        weights = new double[values.length];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = 1 / (standardDeviations[i] * standardDeviations[i]);
        }

        // readings sharing a row of H add their weights
        int equationCount = equationSystem.getIndex().getColumnCount();
        sqrtWeight = new double[equationCount];
        current = new boolean[equationCount];
        for (int i = 0; i < weights.length; i++) {
            Equation<AcVariableType, AcEquationType> equation = this.equations.get(i);
            sqrtWeight[equation.getColumn()] += weights[i];
            current[equation.getColumn()] = equation.getType() == AcEquationType.BRANCH_TARGET_I1
                    || equation.getType() == AcEquationType.BRANCH_TARGET_I2;
        }
        for (int column = 0; column < equationCount; column++) {
            sqrtWeight[column] = Math.sqrt(sqrtWeight[column]);
        }

        rowToColumn = indexUnknowns();
        unknownCount = (int) Arrays.stream(rowToColumn).filter(column -> column >= 0).count();
    }

    /**
     * Gives every unknown a gain column. Only angle differences are physical, so one angle per
     * synchronous component is held fixed. Those are exactly the buses the network marks as
     * reference, the same ones the AC load flow pins, so the estimator inherits its angle origin
     * rather than inventing one.
     */
    private int[] indexUnknowns() {
        int rowCount = equationSystem.getIndex().getRowCount();
        boolean[] fixed = new boolean[rowCount];
        int referenceCount = 0;
        for (LfBus bus : network.getBuses()) {
            if (bus.isReference()) {
                referenceCount++;
                int row = angleRow(bus);
                if (row >= 0) {
                    fixed[row] = true;
                }
            }
        }
        if (referenceCount == 0) {
            throw new PowsyblException("No angle reference bus in network " + network
                    + ": the estimate would be determined only up to an arbitrary angle shift");
        }
        int[] columns = new int[rowCount];
        int next = 0;
        for (int row = 0; row < rowCount; row++) {
            columns[row] = fixed[row] ? -1 : next++;
        }
        return columns;
    }

    private int angleRow(LfBus bus) {
        return equationSystem.getVariableSet().getVariable(bus.getNum(), AcVariableType.BUS_PHI).getRow();
    }

    public int getReadingCount() {
        return values.length;
    }

    /** The measured quantities, which are fewer than the readings where two readings share one. */
    public int getEquationCount() {
        return sqrtWeight.length;
    }

    public int getUnknownCount() {
        return unknownCount;
    }

    /** The column a reading's quantity has in H^T, and so in a scaled Jacobian. */
    public int getEquationColumn(int reading) {
        return equations.get(reading).getColumn();
    }

    /** The summed weight of every reading of the quantity in that column. */
    public double getEquationWeight(int equationColumn) {
        return sqrtWeight[equationColumn] * sqrtWeight[equationColumn];
    }

    /** The gain matrix column of the variable at a state row, or -1 if it is not an unknown. */
    public int getGainColumn(int stateRow) {
        return stateRow >= 0 && stateRow < rowToColumn.length ? rowToColumn[stateRow] : -1;
    }

    /** What the meters are predicted to read at the current state, per unit, in reading order. */
    public double[] evaluate() {
        double[] h = new double[equations.size()];
        for (int i = 0; i < h.length; i++) {
            h[i] = equations.get(i).eval();
        }
        return h;
    }

    /** What each meter reads less what the current state predicts, per unit, in reading order. */
    public double[] residuals() {
        double[] residuals = evaluate();
        for (int i = 0; i < residuals.length; i++) {
            residuals[i] = values[i] - residuals[i];
        }
        return residuals;
    }

    /** The weighted sum of squared residuals. */
    public double objective(double[] residuals) {
        double objective = 0;
        for (int i = 0; i < residuals.length; i++) {
            objective += weights[i] * residuals[i] * residuals[i];
        }
        return objective;
    }

    /**
     * Builds W^(1/2) H^T over the unknowns only, so the gain matrix is this times its transpose.
     * A new matrix each time: a dense matrix iterates non-zero values only, so its element indices
     * shift as derivatives that were zero at a flat start become non-zero.
     *
     * @param transposedJacobian H^T, as {@code JacobianMatrix} holds it
     * @param withCurrents       whether current magnitude readings take part; their columns are
     *                           left empty otherwise
     * @return the scaled matrix and whether every derivative in it was a finite number
     */
    public ScaledJacobian scaleJacobian(Matrix transposedJacobian, boolean withCurrents) {
        // a sparse matrix must be filled column by column, which is the order this iterates in
        Matrix scaled = matrixFactory.create(unknownCount, sqrtWeight.length, 3 * unknownCount);
        boolean[] finite = {true};
        transposedJacobian.iterateNonZeroValue((row, column, value) -> {
            int gainColumn = rowToColumn[row];
            if (gainColumn >= 0 && (withCurrents || !current[column])) {
                finite[0] &= Double.isFinite(value);
                // set, not add: only set fills in the start of a skipped (empty) column
                scaled.set(gainColumn, column, value * sqrtWeight[column]);
            }
        });
        return new ScaledJacobian(scaled, finite[0]);
    }

    /**
     * H^T W (z - h(x)), formed against a scaled Jacobian, whose columns already carry sqrt(w).
     * A reading whose column the scaled Jacobian leaves empty contributes nothing.
     */
    public double[] projectResiduals(Matrix scaledJacobian, double[] residuals) {
        double[] residualPerQuantity = new double[sqrtWeight.length];
        for (int i = 0; i < residuals.length; i++) {
            residualPerQuantity[equations.get(i).getColumn()] += weights[i] * residuals[i];
        }
        for (int column = 0; column < residualPerQuantity.length; column++) {
            residualPerQuantity[column] /= sqrtWeight[column];
        }
        double[] projection = new double[unknownCount];
        scaledJacobian.iterateNonZeroValue((row, column, value) ->
                projection[row] += value * residualPerQuantity[column]);
        return projection;
    }

    /** Moves the state by an increment indexed by gain column. */
    public void addToState(double[] increment) {
        double[] x = equationSystem.getStateVector().get().clone();
        for (int row = 0; row < rowToColumn.length; row++) {
            if (rowToColumn[row] >= 0) {
                x[row] += increment[rowToColumn[row]];
            }
        }
        equationSystem.getStateVector().set(x);
    }

    /**
     * A bus whose angle is an unknown that no reading ties to a reference bus. Readings couple the
     * buses whose variables they share. A coupled group without a reference bus can shift all its
     * angles together and change no reading, and a numerical decomposition does not reliably
     * refuse the singular gain matrix that makes.
     */
    public Optional<LfBus> findBusWithUnpinnedAngle() {
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
            if (bus.isReference() && angleRow(bus) >= 0) {
                pinned[groupOf(group, bus.getNum())] = true;
            }
        }
        return network.getBuses().stream()
                .filter(bus -> angleRow(bus) >= 0 && !pinned[groupOf(group, bus.getNum())])
                .findFirst();
    }

    private static int groupOf(int[] group, int bus) {
        int root = bus;
        while (group[root] != root) {
            root = group[root];
        }
        return root;
    }
}
