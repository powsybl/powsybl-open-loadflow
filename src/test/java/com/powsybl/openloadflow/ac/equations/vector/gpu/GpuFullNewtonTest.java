/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
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
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.ShuntNetworkFactory;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RUNG 2: the FULL Newton-Raphson runs on the GPU behind ONE JNI call
 * ({@link GpuAcNewtonSolver}) — fixed-pattern CSR Jacobian analyzed once by cuDSS, the
 * four generated fused kernels filling J and the mismatch on the device each iteration,
 * device-side state updates. Each test extracts the per-family element data from a real
 * OLF equation system ({@link GpuAcDataExtractor}), solves on the GPU, then runs OLF's
 * own {@link JacobianMatrix}-driven Newton on the SAME equation system as the reference:
 * if the GPU side missed or duplicated any equation term, the states diverge and the
 * test fails. Networks cover each family: closed branches (Eurostag), an open-ended
 * branch, a shunt compensator, and HVDC AC emulation.
 *
 * Skipped unless the native lib is available (classpath natives/&lt;arch&gt;/libolfgpu,
 * built by native/build-gpu.sh with cuDSS present, or -Dolf.gpu.lib).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuFullNewtonTest {

    private static final int MAX_ITER = 20;
    private static final double TOL = 1e-8;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU full-NR native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    /** OLF's own Newton (JacobianMatrix over the SAME equation system) — the reference. */
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

    private void check(Network iidm, String label) {
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
        System.out.printf("GPU full Newton [%s]: %d elements (%d cb, %d ob, %d sh, %d hvdc), n=%d, "
                        + "%d iters, ||F||inf %.2e, max|x_gpu - x_olf| = %.2e%n",
                label, d.cbIdx().length / 8 + d.obIdx().length / 6 + d.shIdx().length / 3 + d.hvIdx().length / 4,
                d.cbIdx().length / 8, d.obIdx().length / 6, d.shIdx().length / 3, d.hvIdx().length / 4,
                n, iters, norm, worst);
        assertTrue(norm < TOL, "GPU Newton must converge (||F||inf = " + norm + ")");
        assertTrue(worst < 1e-6, "GPU state must match OLF's Newton (max diff = " + worst + ")");
    }

    @Test
    void eurostagClosedBranches() {
        check(EurostagFactory.fix(EurostagTutorialExample1Factory.create()), "eurostag closed-branch");
    }

    @Test
    void eurostagWithOpenBranch() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getLine("NHV1_NHV2_1").getTerminal2().disconnect();   // side-2-open branch
        check(network, "eurostag + open branch");
    }

    @Test
    void shuntCompensator() {
        check(ShuntNetworkFactory.create(), "shunt compensator");
    }

    @Test
    void hvdcAcEmulation() {
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();
        check(network, "hvdc ac emulation");
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
