/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcTargetVector;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DOES the GPU's undamped Newton converge at the SAME per-iteration RATE as OLF's own
 * (undamped) CPU Newton, on the SAME equation system? Our other tests
 * ({@link GpuFullNewtonTest}, {@link GpuBatchedN1CrossCheckTest}) only prove the FINAL
 * converged state matches — not the trajectory. This isolates whether the slow early
 * convergence seen in the fixed-iteration benchmark (‖F‖∞ ≈ 1e-1 at K=4 on PEGASE-9241)
 * is INHERENT to OLF's undamped formulation (so the CPU would show it too) or a GPU
 * artifact.
 *
 * <p>Method: PEGASE-9241 base case, DC_VALUES warm start (as the fixed-iter benchmark and
 * the RTE defaults). For each fixed iteration count K, run BOTH from the same x0:
 * the GPU device Newton ({@code solve(..., maxIter=K, tol=0)} — no early stop) and OLF's
 * own {@code JacobianMatrix} undamped Newton (K steps, no scaling, the same reference
 * {@link GpuFullNewtonTest} uses), and print ‖F‖∞ side by side. If the two track at every
 * K, the rate is identical and the slow start is OLF-formulation-inherent (undamped), not
 * the GPU. A SPARSE matrix factory is used for the CPU reference (dense is infeasible at
 * n=18482).
 *
 * <p>Machine-local: skipped unless the GPU lib is available AND the case file exists.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuVsCpuFixedIterTest {

    private static final Path CASES = GpuTestPaths.caseDir();
    private static final String CASE = "case9241pegase.xiidm.gz";
    private static final int[] FIXED_ITERS = {1, 2, 3, 4, 5, 6, 8, 10, 12};

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void check() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(), "GPU native lib not available");
        assumeTrue(Files.exists(CASES.resolve(CASE)), CASE + " not found");
    }

    private record Problem(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                           AcTargetVector target, EquationVector<AcVariableType, AcEquationType> eq, StateVector sv) {
    }

    private static Problem loadGpuScope(Network iidm) {
        LoadFlowParameters lfp = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        OpenLoadFlowParameters olfp = OpenLoadFlowParameters.create(lfp)
                .setVoltageRemoteControl(false)
                .setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);
        AcLoadFlowParameters acp = OpenLoadFlowParameters.createAcParameters(iidm, lfp, olfp,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
        acp.getNetworkParameters().setMaxSlackBusCount(1);
        LfNetwork network = LfNetwork.load(iidm, new LfNetworkLoaderImpl(), acp.getNetworkParameters(), ReportNode.NO_OP).get(0);
        EquationSystem<AcVariableType, AcEquationType> es =
                new AcEquationSystemCreator(network, acp.getEquationSystemCreationParameters()).create();
        AcSolverUtil.initStateVector(network, es, acp.getVoltageInitializer());
        return new Problem(network, es, new AcTargetVector(network, es), new EquationVector<>(es), es.getStateVector());
    }

    /** OLF's own undamped Newton, EXACTLY k update steps from x0; returns ‖F‖∞ at x_k (the GPU's metric). */
    private static double cpuFixedK(Problem p, double[] x0, int k,
                                    JacobianMatrix<AcVariableType, AcEquationType> j) {
        p.sv().set(x0.clone());
        for (int it = 0; it < k; it++) {
            p.eq().minus(p.target());            // F at x_it
            double[] dx = p.eq().getArray().clone();
            j.solveTransposed(dx);               // refactors on changed values
            p.sv().minus(dx);                    // x_{it+1} = x_it - J^{-1} F
        }
        p.eq().minus(p.target());                // F at x_k
        return maxAbs(p.eq().getArray());
    }

    @Test
    void pegase9241SameRateAsCpu() {
        Problem p = loadGpuScope(Network.read(CASES.resolve(CASE)));
        int n = p.es().getIndex().getSortedVariablesToFind().size();
        double[] x0 = p.sv().get().clone();      // the DC-init state, common start

        GpuAcData d = GpuAcDataExtractor.extract(p.network(), p.es());
        GpuAcActivation activation = GpuAcDataExtractor.extractActivation(p.network(), p.es(), n);
        double[] stableTargets = activation.applyTargets(p.target().getArray());

        System.out.printf("GPU-vs-CPU undamped fixed-K on %s (n=%d), DC-init base case%n",
                CASE.replace(".xiidm.gz", ""), n);
        System.out.printf("  %3s | %12s | %12s | %s%n", "K", "GPU ||F||inf", "CPU ||F||inf", "ratio");

        boolean tracked = true;
        try (JacobianMatrix<AcVariableType, AcEquationType> j =
                     new JacobianMatrix<>(p.es(), new SparseMatrixFactory())) {
            for (int k : FIXED_ITERS) {
                double[] out = GpuAcNewtonSolver.solve(x0.clone(), stableTargets, activation.rowMode(), k, 0.0,
                        d.cbIn(), d.cbIdx(), d.obIn(), d.obIdx(), d.shIn(), d.shIdx(), d.hvIn(), d.hvIdx(),
                        d.dqElem(), d.dqSide(), d.dqWeight(), d.dqRow(), d.dqKind(),
                        d.ziIn(), d.ziIdx(),
                        d.drRow(), d.drCol(), d.drCoef());
                double gpuNorm = out[n + 1];
                double cpuNorm = cpuFixedK(p, x0, k, j);
                double ratio = cpuNorm > 0 ? gpuNorm / cpuNorm : Double.NaN;
                System.out.printf("  %3d | %12.4e | %12.4e | %.3f%n", k, gpuNorm, cpuNorm, ratio);
                // Same order of magnitude at every K → same rate. Allow generous slack for the
                // identity-row contribution to the GPU infnorm and FP reassociation.
                if (cpuNorm > 1e-9 && (ratio > 5.0 || ratio < 0.2)) {
                    tracked = false;
                }
            }
        }
        assertTrue(tracked, "GPU and CPU undamped Newton must converge at the same per-iteration rate");
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
