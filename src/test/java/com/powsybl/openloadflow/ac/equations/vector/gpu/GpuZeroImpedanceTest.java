/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.AcTargetVector;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.ac.solver.GpuNewtonRaphsonFactory;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.ZeroImpedanceNetworkFactory;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RUNG 2 for zero-impedance branches (bus couplers): each adds DUMMY_P/DUMMY_Q variables coupling both
 * buses' P/Q balances, and a spanning-tree edge carries the ZERO_V (v1−rho·v2) / ZERO_PHI (ph1−ph2)
 * voltage/angle coupling on its dummy rows; non-tree edges hold the dummies at zero. The GPU assembles the
 * linear zero-impedance family; OLF's own {@link JacobianMatrix} Newton on the SAME equation system is the
 * reference — a mis-placed dummy column or coupling row diverges.
 *
 * <p>Skipped unless the native lib is available (built by native/build-gpu.sh with cuDSS).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuZeroImpedanceTest {

    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-8;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU full-NR native lib not available (build it with native/build-gpu.sh + cuDSS)");
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
    void zeroImpedanceSubNetwork() {
        checkReference(ZeroImpedanceNetworkFactory.createWith3BusesNonImpedantSubNetwork());
    }

    private void checkReference(Network iidm) {
        LfNetwork network = Networks.load(iidm, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector())).get(0);
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

        double[] out = GpuAcNewtonSolver.solve(x0, stableTargets, activation.rowMode(), MAX_ITER, TOL,
                d.cbIn(), d.cbIdx(), d.obIn(), d.obIdx(), d.shIn(), d.shIdx(), d.hvIn(), d.hvIdx(),
                d.dqElem(), d.dqSide(), d.dqWeight(), d.dqRow(), d.dqKind(),
                d.ziIn(), d.ziIdx(),
                d.drRow(), d.drCol(), d.drCoef());
        int iters = (int) out[n];
        double norm = out[n + 1];

        double[] xRef = cpuReference(es, target, eq, sv);    // mutates sv AFTER the GPU ran on x0

        double worst = 0;
        for (int i = 0; i < n; i++) {
            worst = Math.max(worst, Math.abs(out[i] - xRef[i]));
        }
        System.out.printf("GPU full Newton [zero-impedance]: n=%d, %d zero-imp branches, %d iters, "
                + "||F||inf %.2e, max|x_gpu - x_olf| = %.2e%n",
                n, d.getZeroImpedanceCount(), iters, norm, worst);
        assertTrue(d.getZeroImpedanceCount() > 0, "the test must exercise zero-impedance branches");
        assertTrue(norm < TOL, "GPU Newton must converge (||F||inf = " + norm + ")");
        assertTrue(worst < 1e-6, "GPU state must match OLF's Newton (max diff = " + worst + ")");
    }

    @Test
    void zeroImpedanceLoadFlow() {
        Supplier<Network> supplier = ZeroImpedanceNetworkFactory::createWith3BusesNonImpedantSubNetwork;
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        // this sub-network sits near voltage collapse (~0.8 pu), so its Jacobian is ill-conditioned and
        // the last bits of Newton convergence move the state visibly. Converge BOTH tightly (the GPU always
        // iterates to 1e-8) so they reach the same operating point rather than differing by criteria slack.
        Network cpuNetwork = supplier.get();
        LoadFlowParameters cpuParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(cpuParameters).setNewtonRaphsonConvEpsPerEq(1e-7);
        LoadFlowResult cpuResult = runner.run(cpuNetwork, cpuParameters);
        assertTrue(cpuResult.isFullyConverged(), "CPU reference load flow must converge");

        Network gpuNetwork = supplier.get();
        LoadFlowParameters gpuParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(gpuParameters)
                .setNewtonRaphsonConvEpsPerEq(1e-7)
                .setAcSolverType(GpuNewtonRaphsonFactory.NAME);
        LoadFlowResult gpuResult = runner.run(gpuNetwork, gpuParameters);
        assertTrue(gpuResult.isFullyConverged(), "GPU load flow must converge");

        double worstV = 0;
        double worstA = 0;
        int compared = 0;
        for (Bus cpuBus : cpuNetwork.getBusView().getBuses()) {
            Bus gpuBus = gpuNetwork.getBusView().getBus(cpuBus.getId());
            if (gpuBus == null || Double.isNaN(cpuBus.getV()) || Double.isNaN(gpuBus.getV())) {
                continue;
            }
            worstV = Math.max(worstV, Math.abs(cpuBus.getV() - gpuBus.getV()));
            worstA = Math.max(worstA, Math.abs(cpuBus.getAngle() - gpuBus.getAngle()));
            compared++;
        }
        System.out.printf("LoadFlow.run GPU vs CPU [zero-impedance]: %d buses, max dV = %.2e kV, "
                + "max dAngle = %.2e deg%n", compared, worstV, worstA);
        assertTrue(compared > 0, "must compare at least one bus");
        assertTrue(worstV < 1e-2, "bus voltages must match (max dV = " + worstV + " kV)");
        assertTrue(worstA < 1e-2, "bus angles must match (max dAngle = " + worstA + " deg)");
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
