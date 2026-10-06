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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * BATCHED N-1: many post-contingency scenarios solved in one batched Newton-Raphson on
 * the GPU ({@link GpuAcNewtonSolver#solveBatch}), against ONE shared structure (CSR
 * pattern + cuDSS symbolic analysis built once, amortized over the whole batch). Each
 * scenario gets its own value/state/mismatch arrays and a per-scenario disabled-element
 * mask; converged scenarios retire from the batch.
 *
 * <p>Correctness reference: a batch of K identical scenarios (no element disabled) must
 * converge to the SAME state as OLF's own {@link JacobianMatrix}-driven Newton — this
 * exercises the entire batched machinery (per-scenario arrays, the batched fill kernels,
 * convergence retirement, cuDSS value repointing) without depending on modelling a
 * contingency in OLF. A second scenario with a non-empty disabled mask confirms the mask
 * plumbing reaches the kernels.
 *
 * <p>Skipped unless the native lib is available (classpath natives/&lt;arch&gt;/libolfgpu,
 * built by native/build-gpu.sh with cuDSS present, or -Dolf.gpu.lib).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedN1Test {

    private static final int MAX_ITER = 20;
    private static final double TOL = 1e-12;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU batched native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    private static double[] cpuReference(EquationSystem<AcVariableType, AcEquationType> es,
                                         AcTargetVector target, EquationVector<AcVariableType, AcEquationType> eq,
                                         StateVector sv) {
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(es, new DenseMatrixFactory())) {
            eq.minus(target);
            for (int it = 0; it < MAX_ITER && maxAbs(eq.getArray()) >= TOL; it++) {
                double[] dx = eq.getArray().clone();
                j.solveTransposed(dx);
                sv.minus(dx);
                eq.minus(target);
            }
        }
        return sv.get().clone();
    }

    @Test
    void batchReplicationMatchesSingleSolve() {
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
        double[] stableTargets = activation.applyTargets(target.getArray());

        // K identical scenarios, no element disabled — every scenario is the base case. The base
        // state/targets/row-mode are shared (tiled on-device); only the per-scenario mask is [k][].
        int k = 8;
        int nElements = d.getTotalElementCount();
        boolean[][] disabledMask = new boolean[k][nElements];
        int[][] rowModes = new int[k][];                  // per-scenario row mode; here every scenario = base
        java.util.Arrays.fill(rowModes, activation.rowMode());

        double[][] batch = GpuAcNewtonSolver.solveBatch(x0, stableTargets, rowModes, disabledMask,
                0, MAX_ITER, TOL, 1e-4, 1e-2, d.cbIn(), d.cbIdx(), d.obIn(), d.obIdx(), d.shIn(), d.shIdx(), d.hvIn(), d.hvIdx(),
                new int[0], new double[0], new double[0], new double[0], new double[0],
                new int[0], new double[0], -1, 0.0,
                new double[0], new double[0], new double[0],
                new int[0], new int[0], new double[0], new int[0], new int[0],
                new int[0], new int[0], new double[0], new double[0], new int[0]);

        double[] xRef = cpuReference(es, target, eq, sv);

        assertEquals(k, batch.length, "batch must return one result per scenario");
        for (int s = 0; s < k; s++) {
            double[] result = batch[s];
            double norm = result[n + 1];
            int status = (int) result[n + 2];
            double worst = 0;
            for (int i = 0; i < n; i++) {
                worst = Math.max(worst, Math.abs(result[i] - xRef[i]));
            }
            System.out.printf("GPU batched scenario %d: status=%d, ||F||inf=%.2e, max|x_gpu - x_olf|=%.2e%n",
                    s, status, norm, worst);
            assertEquals(0, status, "scenario " + s + " must converge");
            assertTrue(norm < TOL, "scenario " + s + " ||F||inf = " + norm);
            assertTrue(worst < 1e-12, "scenario " + s + " state must match OLF (max diff = " + worst + ")");
        }
    }

    @Test
    void disabledMaskReachesKernels() {
        // A scenario that disables a closed branch must produce a DIFFERENT result from the
        // base scenario, confirming the mask reaches the fill kernels. Convergence is not
        // asserted here: element 0 is the first closed branch in network order, which for
        // Eurostag is the NGEN_NHV1 transformer — a bridge whose outage islands a bus, so
        // undamped device Newton need not converge. Convergence on connectivity-preserving
        // contingencies is validated by GpuBatchedN1CrossCheckTest against OLF.
        Network iidm = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        LfNetwork network = Networks.load(iidm, new FirstSlackBusSelector()).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(network).create();
        AcSolverUtil.initStateVector(network, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(network, es);
        int n = es.getIndex().getSortedVariablesToFind().size();

        double[] x0 = sv.get().clone();
        GpuAcData d = GpuAcDataExtractor.extract(network, es);
        GpuAcActivation activation = GpuAcDataExtractor.extractActivation(network, es, n);
        double[] stableTargets = activation.applyTargets(target.getArray());

        int nElements = d.getTotalElementCount();
        // scenario 0: base case; scenario 1: disable the first closed branch (element index 0).
        // Base state/targets/row-mode are shared (tiled on-device); only the mask is per-scenario.
        boolean[][] disabledMask = {new boolean[nElements], new boolean[nElements]};
        assumeTrue(d.getClosedBranchCount() >= 1, "needs at least one closed branch");
        disabledMask[1][0] = true;
        int[][] rowModes = {activation.rowMode(), activation.rowMode()};   // per-scenario; both = base

        double[][] batch = GpuAcNewtonSolver.solveBatch(x0, stableTargets, rowModes, disabledMask,
                0, MAX_ITER, TOL, 1e-4, 1e-2, d.cbIn(), d.cbIdx(), d.obIn(), d.obIdx(), d.shIn(), d.shIdx(), d.hvIn(), d.hvIdx(),
                new int[0], new double[0], new double[0], new double[0], new double[0],
                new int[0], new double[0], -1, 0.0,
                new double[0], new double[0], new double[0],
                new int[0], new int[0], new double[0], new int[0], new int[0],
                new int[0], new int[0], new double[0], new double[0], new int[0]);

        int status0 = (int) batch[0][n + 2];
        int status1 = (int) batch[1][n + 2];
        System.out.printf("GPU batched disabled-mask: base status=%d, contingency status=%d%n", status0, status1);
        assertEquals(0, status0, "base scenario must converge");
        // The disabled element must change the fill: the contingency state differs from the base.
        double diff = 0;
        for (int i = 0; i < n; i++) {
            diff = Math.max(diff, Math.abs(batch[0][i] - batch[1][i]));
        }
        assertTrue(diff > 1e-6, "disabling an element must change the result (max diff = " + diff + ")");
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
