/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.ac.AcTargetVector;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates the VALUES level of the GPU invalidation taxonomy: when only element PARAMETERS change
 * (a transformer tap / shunt-susceptance move) but the CSR pattern is unchanged, the cached context's
 * parameter packs are re-uploaded in place ({@link GpuAcNewtonSolver#refreshContextValues}) reusing the
 * cuDSS symbolic analysis — instead of a full rebuild.
 *
 * <p>The substantive property: a values-REFRESHED context must solve BYTE-IDENTICALLY to one built from
 * scratch on the new values. Driven via a transformer {@code r1} (ratio/tap) change on eurostag, which is
 * {@link GpuAcData#sameStructureAs} (idx packs unchanged) but not {@link GpuAcData#sameAs} (cbIn changed).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuValuesRefreshTest {

    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-10;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU full-NR native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    @Test
    void valuesRefreshMatchesRebuild() {
        LfNetwork network = Networks.load(EurostagFactory.fix(EurostagTutorialExample1Factory.create()),
                new FirstSlackBusSelector()).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(network).create();
        AcSolverUtil.initStateVector(network, es, new UniformValueVoltageInitializer());
        AcTargetVector target = new AcTargetVector(network, es);
        int n = es.getIndex().getSortedVariablesToFind().size();

        double[] x0 = es.getStateVector().get().clone();
        GpuAcData d1 = GpuAcDataExtractor.extract(network, es);
        GpuAcActivation act = GpuAcDataExtractor.extractActivation(network, es, n);
        int[] rowMode = act.rowMode();
        double[] targets = act.applyTargets(target.getArray());          // structure/activation unchanged by an r1 move

        long h1 = GpuAcNewtonSolver.createContext(n, d1.cbIn(), d1.cbIdx(), d1.obIn(), d1.obIdx(),
                d1.shIn(), d1.shIdx(), d1.hvIn(), d1.hvIdx(),
                d1.dqElem(), d1.dqSide(), d1.dqWeight(), d1.dqRow(), d1.dqKind(),
                d1.ziIn(), d1.ziIdx(),
                d1.drRow(), d1.drCol(), d1.drCoef());
        double[] out1 = GpuAcNewtonSolver.solveContext(h1, x0, targets, rowMode, MAX_ITER, TOL, 0, 0, 0.0, 0.0, 0.0, new int[n]);

        // Values-only change: move the NHV2_NLOAD transformer's ratio. Same topology -> same CSR pattern.
        LfBranch trafo = network.getBranchById("NHV2_NLOAD");
        trafo.getPiModel().setR1(trafo.getPiModel().getR1() * 1.1);
        GpuAcData d2 = GpuAcDataExtractor.extract(network, es);

        assertTrue(d1.sameStructureAs(d2), "an r1 (tap) move must keep the CSR pattern (sameStructureAs)");
        assertFalse(d1.sameAs(d2), "an r1 move must change the element parameter packs (not sameAs)");

        // VALUES path: re-upload the packs into the existing context (no rebuild, no re-analysis).
        GpuAcNewtonSolver.refreshContextValues(h1, d2.cbIn(), d2.obIn(), d2.shIn(), d2.hvIn());
        double[] outRefreshed = GpuAcNewtonSolver.solveContext(h1, x0, targets, rowMode, MAX_ITER, TOL, 0, 0, 0.0, 0.0, 0.0, new int[n]);

        // Reference: a context built FROM SCRATCH on the new values.
        long h2 = GpuAcNewtonSolver.createContext(n, d2.cbIn(), d2.cbIdx(), d2.obIn(), d2.obIdx(),
                d2.shIn(), d2.shIdx(), d2.hvIn(), d2.hvIdx(),
                d2.dqElem(), d2.dqSide(), d2.dqWeight(), d2.dqRow(), d2.dqKind(),
                d2.ziIn(), d2.ziIdx(),
                d2.drRow(), d2.drCol(), d2.drCoef());
        double[] outRebuilt = GpuAcNewtonSolver.solveContext(h2, x0, targets, rowMode, MAX_ITER, TOL, 0, 0, 0.0, 0.0, 0.0, new int[n]);

        double worst = 0;
        double moved = 0;
        for (int i = 0; i < n; i++) {
            worst = Math.max(worst, Math.abs(outRefreshed[i] - outRebuilt[i]));
            moved = Math.max(moved, Math.abs(outRefreshed[i] - out1[i]));
        }
        System.out.printf("GPU VALUES refresh [eurostag r1 move]: refresh-vs-rebuild max diff = %.2e, "
                + "solution moved by %.2e%n", worst, moved);
        GpuAcNewtonSolver.destroyContext(h1);
        GpuAcNewtonSolver.destroyContext(h2);

        assertTrue(outRefreshed[n + 1] < TOL, "the refreshed solve must converge");
        assertEquals(0.0, worst, 1e-12, "a VALUES refresh must solve identically to a from-scratch rebuild");
        assertTrue(moved > 1e-6, "the r1 change must actually move the solution (the refresh took effect)");
    }
}
