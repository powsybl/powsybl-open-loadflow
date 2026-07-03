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
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
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
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RUNG 2 for remote / distributed generator voltage control (DISTR_Q): several generators at distinct
 * buses regulate one pilot bus. The pilot's reactive balance moves onto the free controller's BUS_V row,
 * each other controller's BUS_V row carries a DISTR_Q reactive-distribution equation
 * ({@code qPct_k·Σ q − q_k}), and those rows are assembled on the GPU by the weighted DISTR_Q pass. The
 * full Newton runs on the GPU; OLF's own {@link JacobianMatrix} Newton on the SAME equation system is the
 * reference — a mis-weighted or mis-routed reactive contribution diverges.
 *
 * <p>Skipped unless the native lib is available (built by native/build-gpu.sh with cuDSS).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuGeneratorRemoteControlTest {

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
    void generatorRemoteControl() {
        // three generators at distinct buses regulate ONE shared pilot bus → active DISTR_Q rows
        checkReference(VoltageControlNetworkFactory.createWithGeneratorRemoteControl(), true);
    }

    @Test
    void generatorRemoteSingleControl() {
        // separating-impedance variant: each generator remotely controls its OWN distinct bus (single
        // controller per bus → no DISTR_Q), exercising the pilot-Q → controller-V row remap on its own.
        checkReference(VoltageControlNetworkFactory.createWithGeneratorRemoteControlAndSmallSeparatingImpedance(), false);
    }

    @Test
    void generatorRemoteControlWithShuntAtController() {
        // a fixed shunt on a DISTR_Q controller bus: its reactive joins the q sum, so the GPU must add a
        // shunt contribution to the DISTR_Q rows (not only the controllers' branch flows).
        Network iidm = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        iidm.getVoltageLevel("vl1").newShuntCompensator()
                .setId("SHUNT_B1").setBus("b1").setConnectableBus("b1").setSectionCount(1)
                .newLinearModel().setBPerSection(1e-3).setMaximumSectionCount(1).add()
                .add();
        checkReference(iidm, true);
    }

    @Test
    void generatorRemoteControlWithOpenBranchAtController() {
        // a half-open line incident on a DISTR_Q controller bus (b1): only its connected side is energized, so
        // OLF adds an OpenBranchSide2ReactiveFlowEquationTerm to b1's reactive sum. The GPU must mirror it with
        // an open-branch q contribution (dqKind 2) into the DISTR_Q rows — not throw on the non-closed branch.
        Network iidm = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        VoltageLevel vl5 = iidm.getSubstation("s").newVoltageLevel()
                .setId("vl5").setNominalV(20).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl5.getBusBreakerView().newBus().setId("b5").add();
        iidm.newLine().setId("openLine")
                .setVoltageLevel1("vl1").setBus1("b1").setConnectableBus1("b1")
                .setVoltageLevel2("vl5").setConnectableBus2("b5")   // side 2 left disconnected → half-open at b1
                .setR(1).setX(10).setG1(0).setB1(1e-3).setG2(0).setB2(1e-3)
                .add();
        checkReference(iidm, true);
    }

    @Test
    void generatorRemoteControlWithZeroImpedanceBranchAtController() {
        // a zero-impedance coupler incident on a DISTR_Q controller bus (b2), feeding a load on the far bus.
        // OLF adds the coupler's DUMMY_Q variable term to b2's reactive sum; the GPU must mirror it with a
        // linear DUMMY_Q contribution (dqKind 3) into the DISTR_Q rows — exercising the zero-impedance family
        // (P3, whose dummyQ coupling must be SKIPPED on the controller's suppressed Q row) and the DISTR_Q
        // assembly (P2) together.
        Network iidm = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        VoltageLevel vl6 = iidm.getSubstation("s").newVoltageLevel()
                .setId("vl6").setNominalV(20).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl6.getBusBreakerView().newBus().setId("b6").add();
        vl6.newLoad().setId("l6").setBus("b6").setConnectableBus("b6").setP0(10).setQ0(5).add();
        iidm.newLine().setId("zline")
                .setVoltageLevel1("vl2").setBus1("b2").setConnectableBus1("b2")
                .setVoltageLevel2("vl6").setBus2("b6").setConnectableBus2("b6")
                .setR(0).setX(0).setG1(0).setB1(0).setG2(0).setB2(0)   // zero impedance -> DUMMY_P/Q coupler
                .add();
        checkReference(iidm, true);
    }

    private void checkReference(Network iidm, boolean expectDistrQ) {
        LfNetworkParameters params = new LfNetworkParameters()
                .setSlackBusSelector(new FirstSlackBusSelector())
                .setGeneratorVoltageRemoteControl(true);
        LfNetwork network = Networks.load(iidm, params).get(0);
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
        System.out.printf("GPU full Newton [generator remote control / DISTR_Q]: n=%d, %d dq contributions, "
                + "%d iters, ||F||inf %.2e, max|x_gpu - x_olf| = %.2e%n",
                n, d.getDistrQContributionCount(), iters, norm, worst);
        assertTrue(d.getDistrQContributionCount() > 0 == expectDistrQ,
                "expected DISTR_Q contributions present=" + expectDistrQ + " but found "
                        + d.getDistrQContributionCount());
        assertTrue(norm < TOL, "GPU Newton must converge (||F||inf = " + norm + ")");
        assertTrue(worst < 1e-6, "GPU state must match OLF's Newton (max diff = " + worst + ")");
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }

    /**
     * RUNG 3 end-to-end: a plain {@code LoadFlow.run} with remote generator voltage control, CPU vs GPU,
     * exercising the cached {@link GpuNewtonRaphson} production path with the DISTR_Q assembly.
     */
    @Test
    void generatorRemoteControlLoadFlow() {
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        Network cpuNetwork = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        LoadFlowResult cpuResult = runner.run(cpuNetwork, new LoadFlowParameters());
        assertTrue(cpuResult.isFullyConverged(), "CPU reference load flow must converge");

        Network gpuNetwork = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
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
        System.out.printf("LoadFlow.run GPU vs CPU [generator remote control]: %d buses, max dV = %.2e kV, "
                + "max dAngle = %.2e deg%n", compared, worstV, worstA);
        assertTrue(compared > 0, "must compare at least one bus");
        assertTrue(worstV < 1e-2, "bus voltages must match (max dV = " + worstV + " kV)");
        assertTrue(worstA < 1e-2, "bus angles must match (max dAngle = " + worstA + " deg)");
    }
}
