/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.solver;

import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.JacobianMatrix;

import java.util.Objects;

/**
 * Gauss-Newton on the normal equations. With z the readings, h(x) the measurement model, W the
 * inverse variances and H the Jacobian of h, each iteration solves
 *
 * <pre>    (H^T W H) dx = H^T W (z - h(x)),    x &lt;- x + dx</pre>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public class GaussNewton implements WlsSolver {

    /**
     * Relative growth of the objective (absolute below 1) that counts as a worse iteration, so
     * round-off near the minimum is not taken for divergence.
     */
    private static final double OBJECTIVE_GROWTH_THRESHOLD = 1e-6;

    private final WlsProblem problem;

    private final JacobianMatrix<AcVariableType, AcEquationType> jacobian;

    private final WlsParameters parameters;

    private final GainFactorization.Factory gainFactorizationFactory;

    public GaussNewton(WlsProblem problem, JacobianMatrix<AcVariableType, AcEquationType> jacobian,
                       WlsParameters parameters, GainFactorization.Factory gainFactorizationFactory) {
        this.problem = Objects.requireNonNull(problem);
        this.jacobian = Objects.requireNonNull(jacobian);
        this.parameters = Objects.requireNonNull(parameters);
        this.gainFactorizationFactory = Objects.requireNonNull(gainFactorizationFactory);
    }

    @Override
    public String getName() {
        return GaussNewtonFactory.NAME;
    }

    @Override
    public GainFactorization factorizeGain(Matrix scaledJacobian) {
        return gainFactorizationFactory.factorize(scaledJacobian);
    }

    @Override
    public WlsSolverResult run() {
        int iteration = 0; // state updates applied, so a run stopped before its first one counts none
        double previousObjective = Double.NaN;
        int consecutiveGrowth = 0;

        while (iteration < parameters.maxIterations()) {
            boolean withCurrents = iteration > 0 || !parameters.excludeCurrentsOnFirstIteration();
            WlsProblem.ScaledJacobian scaledJacobian = problem.scaleJacobian(jacobian.getMatrix(), withCurrents);
            double[] residuals = problem.residuals();
            double objective = problem.objective(residuals);
            if (!scaledJacobian.finite() || !Double.isFinite(objective)) {
                String culprit = scaledJacobian.finite()
                        ? "The weighted sum of squared residuals"
                        : "A derivative of the measurement model";
                return failed(culprit + " is not a finite number, so the iteration cannot continue", iteration);
            }

            // one worse iteration can be an overshoot Gauss-Newton recovers from; two in a row is divergence
            if (objective - previousObjective > OBJECTIVE_GROWTH_THRESHOLD * Math.max(previousObjective, 1)) {
                consecutiveGrowth++;
                if (consecutiveGrowth >= 2) {
                    return failed("The weighted sum of squared residuals grew in two successive iterations, to "
                            + objective + ", so the iteration is diverging rather than closing in", iteration);
                }
            } else {
                consecutiveGrowth = 0;
            }
            previousObjective = objective;

            double[] increment = problem.projectResiduals(scaledJacobian.matrix(), residuals);
            try (GainFactorization gain = gainFactorizationFactory.factorize(scaledJacobian.matrix())) {
                gain.solve(increment); // solved in place: the projection becomes dx
            } catch (MatrixException e) {
                return new WlsSolverResult(WlsSolverStatus.SINGULAR_GAIN_MATRIX, "The gain matrix could not be"
                        + " decomposed, which is what a measurement set that does not determine the state"
                        + " produces: " + e.getMessage(), iteration);
            }

            double maxIncrement = 0;
            for (double dx : increment) {
                maxIncrement = Math.max(maxIncrement, Math.abs(dx));
            }
            if (!Double.isFinite(maxIncrement)) {
                return failed("The state update is not a finite number, so the iteration cannot continue", iteration);
            }
            if (maxIncrement > parameters.maxStep()) {
                // keeps the direction, so a step too long for the linearization still heads the right way
                double scale = parameters.maxStep() / maxIncrement;
                for (int i = 0; i < increment.length; i++) {
                    increment[i] *= scale;
                }
            }
            problem.addToState(increment);
            iteration++;

            if (maxIncrement < parameters.tolerance()) {
                return new WlsSolverResult(WlsSolverStatus.CONVERGED, "", iteration);
            }
        }
        return new WlsSolverResult(WlsSolverStatus.MAX_ITERATION_REACHED, "", iteration);
    }

    private static WlsSolverResult failed(String statusText, int iteration) {
        return new WlsSolverResult(WlsSolverStatus.SOLVER_FAILED, statusText, iteration);
    }
}
