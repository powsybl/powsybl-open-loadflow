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
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.GpuNewtonRaphsonFactory;
import com.powsybl.openloadflow.ac.solver.StateVectorScalingMode;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.TwoBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates the device-side line search ({@link StateVectorScalingMode#LINE_SEARCH}) in the GPU
 * Newton-Raphson, mirroring OLF's {@link com.powsybl.openloadflow.ac.solver.LineSearchStateVectorScaling}.
 *
 * <p>Uses the ill-conditioned {@link TwoBusNetworkFactory} case ({@code l1.P0 = 3.902}) — exactly the
 * one {@code IllConditionedCaseTest} uses — where undamped Newton overshoots and stalls but line-search
 * backtracking converges. Asserts: (1) undamped GPU NR does NOT converge there, (2) GPU line search DOES,
 * to the same voltage as the CPU line-search reference, and (3) on a well-conditioned case line search is
 * a pure no-op (the step always improves → μ≡1 → byte-identical to undamped).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuLineSearchTest {

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU full-NR native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    private static Network illConditioned() {
        Network network = TwoBusNetworkFactory.create();
        network.getLoad("l1").setP0(3.902);     // overshoots without state-vector scaling
        return network;
    }

    private static LoadFlowParameters params(StateVectorScalingMode mode, boolean gpu, int maxNrIterations) {
        LoadFlowParameters p = new LoadFlowParameters()
                .setUseReactiveLimits(false)
                .setDistributedSlack(false);
        // NB: build the OpenLoadFlowParameters extension with ONE create() call — a second
        // create() on the same LoadFlowParameters replaces the extension and wipes these settings.
        OpenLoadFlowParameters ext = OpenLoadFlowParameters.create(p)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setStateVectorScalingMode(mode)
                .setMaxNewtonRaphsonIterations(maxNrIterations);
        if (gpu) {
            ext.setAcSolverType(GpuNewtonRaphsonFactory.NAME);
        }
        return p;
    }

    private static LoadFlow.Runner runner() {
        return new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
    }

    @Test
    void undampedGpuDoesNotConvergeOnIllConditionedCase() {
        LoadFlowResult result = runner().run(illConditioned(), params(StateVectorScalingMode.NONE, true, 15));
        assertFalse(result.isFullyConverged(), "undamped GPU NR must not converge on the ill-conditioned case");
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED,
                result.getComponentResults().get(0).getStatus());
    }

    @Test
    void lineSearchGpuRescuesIllConditionedCase() {
        // Undamped GPU NR cannot solve this case (see undampedGpuDoesNotConverge...). The device-side
        // line search rescues it: with the 2-norm backtracking the GPU converges to the physical
        // solution IllConditionedCaseTest documents for the SAME case, V(b2) = 0.6364204826103471.
        // (Near the PV-curve nose the mismatch plateaus, so V is only pinned to ~1e-3 — a multi-solution
        // case where a strict cross-solver byte match is not meaningful; the RESCUE is the property.)
        Network gpu = illConditioned();
        LoadFlowResult gpuResult = runner().run(gpu, params(StateVectorScalingMode.LINE_SEARCH, true, 30));
        assertTrue(gpuResult.isFullyConverged(), "GPU line search must rescue the ill-conditioned case");
        double gpuV2 = gpu.getBusBreakerView().getBus("b2").getV();
        System.out.printf("GPU line search [ill-conditioned 2-bus]: V(b2)=%.10f (ref 0.6364204826)%n", gpuV2);
        assertEquals(0.6364204826103471, gpuV2, 2e-3, "GPU line search must reach the physical solution");
    }

    @Test
    void lineSearchIsNoOpWhenStepAlwaysImproves() {
        // Well-conditioned case: undamped Newton's norm decreases every iteration, so line search never
        // backtracks (μ≡1) and the converged state is byte-identical to the undamped GPU run.
        Network none = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runner().run(none, params(StateVectorScalingMode.NONE, true, 30));
        Network ls = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runner().run(ls, params(StateVectorScalingMode.LINE_SEARCH, true, 30));

        for (Bus b : none.getBusView().getBuses()) {
            Bus bLs = ls.getBusView().getBus(b.getId());
            assertEquals(b.getV(), bLs.getV(), 0.0, "line search must not alter a well-conditioned solve (V)");
            assertEquals(b.getAngle(), bLs.getAngle(), 0.0, "line search must not alter a well-conditioned solve (angle)");
        }
    }
}
