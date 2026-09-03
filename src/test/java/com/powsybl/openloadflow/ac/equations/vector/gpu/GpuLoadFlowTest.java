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
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.GpuNewtonRaphsonFactory;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.ShuntNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RUNG 3: the GPU Newton-Raphson behind a PLAIN {@code LoadFlow.run} — selected through
 * the AC solver SPI with
 * {@code OpenLoadFlowParameters.create(parameters).setAcSolverType(GpuNewtonRaphsonFactory.NAME)}.
 * Default load-flow parameters (distributed slack ON → the outer loop reruns the solver
 * with adjusted targets, exercising the per-run re-extraction). Each network runs twice
 * — default CPU Newton-Raphson vs GPU — and every bus voltage/angle must agree.
 *
 * Skipped unless the native lib is available (see {@link GpuFullNewtonTest}).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuLoadFlowTest {

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU full-NR native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    private void compare(Supplier<Network> networkSupplier, String label) {
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        Network cpuNetwork = networkSupplier.get();
        LoadFlowResult cpuResult = runner.run(cpuNetwork, new LoadFlowParameters());
        assertTrue(cpuResult.isFullyConverged(), "CPU reference load flow must converge");

        Network gpuNetwork = networkSupplier.get();
        LoadFlowParameters gpuParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(gpuParameters).setAcSolverType(GpuNewtonRaphsonFactory.NAME);
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
        System.out.printf("LoadFlow.run GPU vs CPU [%s]: %d buses, CPU %d iters / GPU %d iters, "
                        + "max dV = %.2e kV, max dAngle = %.2e deg%n",
                label, compared,
                cpuResult.getComponentResults().get(0).getIterationCount(),
                gpuResult.getComponentResults().get(0).getIterationCount(),
                worstV, worstA);
        // both runs converge, but to DIFFERENT points inside the stopping-criteria basin:
        // the CPU Newton stops at the configured criteria (1e-4 pu by default) while the
        // GPU iterates to a 1e-8 pu mismatch — so states agree to the criteria's slack,
        // not to machine precision (a missing/wrong term would diverge at the 1e-1 level;
        // the machine-precision equivalence is asserted by GpuFullNewtonTest).
        assertTrue(compared > 0, "must compare at least one bus");
        assertTrue(worstV < 1e-3, "bus voltages must match (max dV = " + worstV + " kV)");
        assertTrue(worstA < 1e-3, "bus angles must match (max dAngle = " + worstA + " deg)");
    }

    @Test
    void eurostagClosedBranches() {
        compare(() -> EurostagFactory.fix(EurostagTutorialExample1Factory.create()), "eurostag closed-branch");
    }

    @Test
    void eurostagWithOpenBranch() {
        compare(() -> {
            Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
            network.getLine("NHV1_NHV2_1").getTerminal2().disconnect();
            return network;
        }, "eurostag + open branch");
    }

    @Test
    void shuntCompensator() {
        compare(ShuntNetworkFactory::create, "shunt compensator");
    }

    @Test
    void hvdcAcEmulation() {
        compare(() -> {
            Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
            network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                    .withDroop(180)
                    .withP0(0.f)
                    .withEnabled(true)
                    .add();
            return network;
        }, "hvdc ac emulation");
    }
}
