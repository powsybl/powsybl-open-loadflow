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
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.GpuNewtonRaphsonFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GPU-vs-CPU benchmark on the large PEGASE cases (1354 / 2869 / 9241 / 13659 buses,
 * local gzipped XIIDM exports): the SAME plain {@code LoadFlow.run}, default CPU
 * Newton-Raphson (KLU sparse) vs the GPU solver selected through the AcSolver SPI.
 * Per case: one warm-up run + one timed run for each solver (fresh network each run —
 * a load flow mutates it); asserts both fully converge and the bus states agree, and
 * prints the wall-clock comparison.
 *
 * <p>Two GPU-scope parameters (applied to BOTH runs so the comparison is like-for-like):
 * low-impedance branches replaced by minimum impedance (the GPU path has no ZERO_PHI /
 * ZERO_V equations) and local-only generator voltage control (no DISTR_Q reactive-power
 * distribution equations).
 *
 * Skipped unless the GPU native lib is available AND the cases directory exists
 * (machine-local benchmark, not CI).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBenchmarkTest {

    private static final Path CASES = GpuTestPaths.caseDir();

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU full-NR native lib not available (build it with native/build-gpu.sh + cuDSS)");
        assumeTrue(Files.isDirectory(CASES), "benchmark cases not found at " + CASES);
    }

    private static LoadFlowParameters parameters(boolean gpu) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters)
                .setVoltageRemoteControl(false)
                .setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);
        if (gpu) {
            parametersExt.setAcSolverType(GpuNewtonRaphsonFactory.NAME);
        }
        return parameters;
    }

    private record Run(Network network, LoadFlowResult result, long millis) {
    }

    private static Run timedRun(Path file, LoadFlow.Runner runner, LoadFlowParameters parameters) {
        Network network = Network.read(file);
        long start = System.nanoTime();
        LoadFlowResult result = runner.run(network, parameters);
        return new Run(network, result, (System.nanoTime() - start) / 1_000_000);
    }

    private void bench(String caseFile) {
        Path file = CASES.resolve(caseFile);
        assumeTrue(Files.exists(file), caseFile + " not found");
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider());

        timedRun(file, runner, parameters(false));                  // CPU warm-up (JIT)
        Run cpu = timedRun(file, runner, parameters(false));
        timedRun(file, runner, parameters(true));                   // GPU warm-up (CUDA context)
        Run gpu = timedRun(file, runner, parameters(true));

        assertTrue(cpu.result().isFullyConverged(), "CPU load flow must converge");
        assertTrue(gpu.result().isFullyConverged(), "GPU load flow must converge");

        double worstV = 0;
        double worstA = 0;
        int compared = 0;
        for (Bus cpuBus : cpu.network().getBusView().getBuses()) {
            Bus gpuBus = gpu.network().getBusView().getBus(cpuBus.getId());
            if (gpuBus == null || Double.isNaN(cpuBus.getV()) || Double.isNaN(gpuBus.getV())) {
                continue;
            }
            worstV = Math.max(worstV, Math.abs(cpuBus.getV() - gpuBus.getV()));
            worstA = Math.max(worstA, Math.abs(cpuBus.getAngle() - gpuBus.getAngle()));
            compared++;
        }
        System.out.printf("BENCH %s: CPU %d ms (%d it) | GPU %d ms (%d it) | %d buses, "
                        + "max dV = %.2e kV, max dAngle = %.2e deg%n",
                caseFile.replace(".xiidm.gz", ""),
                cpu.millis(), cpu.result().getComponentResults().get(0).getIterationCount(),
                gpu.millis(), gpu.result().getComponentResults().get(0).getIterationCount(),
                compared, worstV, worstA);
        assertTrue(compared > 0, "must compare at least one bus");
        assertTrue(worstV < 1e-2, "bus voltages must match (max dV = " + worstV + " kV)");
        assertTrue(worstA < 1e-2, "bus angles must match (max dAngle = " + worstA + " deg)");
    }

    @Test
    void pegase1354() {
        bench("case1354pegase.xiidm.gz");
    }

    @Test
    void pegase2869() {
        bench("case2869pegase.xiidm.gz");
    }

    @Test
    void pegase9241() {
        bench("case9241pegase.xiidm.gz");
    }

    @Test
    void pegase13659() {
        bench("case13659pegase.xiidm.gz");
    }
}
