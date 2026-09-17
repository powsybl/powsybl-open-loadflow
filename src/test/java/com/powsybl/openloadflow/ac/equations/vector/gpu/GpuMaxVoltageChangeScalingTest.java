/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.AcTargetVector;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.ac.solver.MaxVoltageChangeStateVectorScaling;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies the GPU MAX_VOLTAGE_CHANGE state-vector scaling (lsMode 2 in solveContext): each Newton
 * step is scaled by one global factor so the largest |Δv| / |Δφ| stays within maxDv / maxDphi. The
 * reference is OLF's own {@link MaxVoltageChangeStateVectorScaling} applied per iteration to a
 * JacobianMatrix Newton on the SAME equation system. maxDv / maxDphi are set small enough that the
 * clamp engages on the early steps (asserted: the clamped run takes strictly more iterations than the
 * undamped GPU run), so this exercises the scaling path rather than passing vacuously.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuMaxVoltageChangeScalingTest {

    private static final int MAX_ITER = 50;
    private static final double TOL = 1e-8;
    private static final double MAX_DV = 0.05;        // pu — tight, to force the clamp on the first steps
    private static final double MAX_DPHI = 0.05;      // rad

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU full-NR native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    /** OLF's Newton with the MAX_VOLTAGE_CHANGE clamp applied each iteration — the reference. */
    private static double[] cpuClampedReference(EquationSystem<AcVariableType, AcEquationType> es,
                                                AcTargetVector target, EquationVector<AcVariableType, AcEquationType> eq,
                                                StateVector sv) {
        var scaling = new MaxVoltageChangeStateVectorScaling(MAX_DV, MAX_DPHI);
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(es, new DenseMatrixFactory())) {
            eq.minus(target);
            for (int it = 0; it < MAX_ITER && maxAbs(eq.getArray()) >= TOL; it++) {
                double[] dx = eq.getArray().clone();
                j.solveTransposed(dx);
                scaling.apply(dx, es, null);
                sv.minus(dx);
                eq.minus(target);
            }
        }
        return sv.get().clone();
    }

    @Test
    void eurostagMaxVoltageChange() {
        Network iidm = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        LfNetwork network = Networks.load(iidm, new FirstSlackBusSelector()).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(network).create();
        AcSolverUtil.initStateVector(network, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(network, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        int n = es.getIndex().getSortedVariablesToFind().size();

        double[] x0 = sv.get().clone();
        GpuAcData d = GpuAcDataExtractor.extract(network, es);
        GpuAcActivation activation = GpuAcDataExtractor.extractActivation(network, es, n);
        int[] varType = GpuAcDataExtractor.extractVarType(es, n);
        double[] stableTargets = activation.applyTargets(target.getArray());

        long handle = GpuAcNewtonSolver.createContext(n, d.cbIn(), d.cbIdx(), d.obIn(), d.obIdx(),
                d.shIn(), d.shIdx(), d.hvIn(), d.hvIdx(),
                d.dqElem(), d.dqSide(), d.dqWeight(), d.dqRow(), d.dqKind(),
                d.ziIn(), d.ziIdx(),
                d.drRow(), d.drCol(), d.drCoef());
        int clampedIters;
        int undampedIters;
        double[] clamped;
        try {
            clamped = GpuAcNewtonSolver.solveContext(handle, x0, stableTargets, activation.rowMode(),
                    MAX_ITER, TOL, 2, 0, 0.0, MAX_DV, MAX_DPHI, varType);
            clampedIters = (int) clamped[n];
            // same start, no clamp (lsMode 0) — the clamp must cost extra iterations, else the test is vacuous
            double[] undamped = GpuAcNewtonSolver.solveContext(handle, x0, stableTargets, activation.rowMode(),
                    MAX_ITER, TOL, 0, 0, 0.0, 0.0, 0.0, varType);
            undampedIters = (int) undamped[n];
        } finally {
            GpuAcNewtonSolver.destroyContext(handle);
        }

        double[] xRef = cpuClampedReference(es, target, eq, sv);   // mutates sv AFTER the GPU ran on x0

        double worst = 0;
        for (int i = 0; i < n; i++) {
            worst = Math.max(worst, Math.abs(clamped[i] - xRef[i]));
        }
        System.out.printf("GPU MAX_VOLTAGE_CHANGE [eurostag]: n=%d, clamped %d iters vs undamped %d iters, "
                + "||F||inf %.2e, max|x_gpu - x_olf| = %.2e%n", n, clampedIters, undampedIters, clamped[n + 1], worst);
        assertTrue(clamped[n + 1] < TOL, "clamped GPU Newton must converge (||F||inf = " + clamped[n + 1] + ")");
        assertTrue(clampedIters > undampedIters, "the clamp must engage (clamped " + clampedIters
                + " vs undamped " + undampedIters + " iterations)");
        assertTrue(worst < 1e-6, "clamped GPU state must match OLF's clamped Newton (max diff = " + worst + ")");
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
