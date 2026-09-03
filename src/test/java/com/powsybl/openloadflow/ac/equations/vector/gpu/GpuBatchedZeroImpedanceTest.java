/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.AcTargetVector;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchContingency;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchScenarioResult;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonParameters;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Batched N-1 on a network with a zero-impedance branch (bus coupler) — the third family pass. The coupler's
 * DUMMY_P/Q variables couple the two buses' P/Q balances (and a spanning-tree edge adds the ZERO_V/ZERO_PHI
 * voltage/angle coupling), assembled per scenario by the {@code fillZeroImpBatch} kernel.
 *
 * <p>A contingency that outages the coupler itself is auto-rejected to the CPU (the element mapper does not
 * contain zero-impedance branches, so it is NOT_IN_GPU_MODEL — outaging a bus coupler is islanding-like). The
 * meshed lines around the coupler stay non-islanding when outaged, so they batch on-device and must agree with
 * a CPU {@code LoadFlow.run}. Skipped unless the native lib is available.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedZeroImpedanceTest {

    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-12;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU batched native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    @Test
    void zeroImpedanceBatchedN1RejectsCouplerOutageAndMatchesCpu() {
        Network gpuNet = meshedWithCoupler();
        LfNetwork net = load(gpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);

        GpuAcData d = GpuAcDataExtractor.extract(net, es);
        assertTrue(d.getZeroImpedanceCount() > 0, "the test network must exercise zero-impedance branches");

        cpuReference(es, target, eq, sv);                    // converge the base case as the hot start
        double[] baseState = sv.get().clone();
        int n = baseState.length;

        List<BatchContingency> conts = new ArrayList<>();
        for (LfBranch br : net.getBranches()) {
            if (br.getBus1() != null && br.getBus2() != null) {
                conts.add(new BatchContingency(br.getId(), br));
            }
        }

        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters(), TOL, false, false, ReportNode.NO_OP);

        double worst = 0;
        int checked = 0;
        int couplerRejected = 0;
        for (int i = 0; i < conts.size(); i++) {
            BatchContingency c = conts.get(i);
            BatchScenarioResult r = results.get(i);
            boolean isCoupler = "coupler".equals(c.contingencyId());
            if (isCoupler) {
                assertTrue(r.needsCpuFallback(), "the zero-impedance coupler outage must be rejected to the CPU");
                couplerRejected++;
                continue;
            }
            if (r.needsCpuFallback() || !r.hasConverged()) {
                continue;
            }
            sv.set(java.util.Arrays.copyOf(r.state(), n));
            AcSolverUtil.updateNetwork(net, es);

            // CPU reference: OLF's own Newton on a FRESH equation system with the branch disconnected — the SAME
            // voltage init, tolerance (TOL) and code path as the device (no engine-specific criteria), so any
            // disagreement is a real device bug rather than a parameter mismatch.
            Network cpu = meshedWithCoupler();
            Branch<?> br = cpu.getBranch(c.contingencyId());
            if (br == null) {
                continue;
            }
            br.getTerminal1().disconnect();
            br.getTerminal2().disconnect();
            LfNetwork cpuLf = load(cpu).get(0);
            EquationSystem<AcVariableType, AcEquationType> cpuEs = new AcEquationSystemCreator(cpuLf).create();
            AcSolverUtil.initStateVector(cpuLf, cpuEs, new UniformValueVoltageInitializer());
            AcTargetVector cpuT = new AcTargetVector(cpuLf, cpuEs);
            EquationVector<AcVariableType, AcEquationType> cpuEq = new EquationVector<>(cpuEs);
            cpuReference(cpuEs, cpuT, cpuEq, cpuEs.getStateVector());
            AcSolverUtil.updateNetwork(cpuLf, cpuEs);
            double w = 0;
            for (LfBus cpuBus : cpuLf.getBuses()) {
                LfBus gpuBus = net.getBusById(cpuBus.getId());
                if (gpuBus == null) {
                    continue;
                }
                w = Math.max(w, Math.abs(cpuBus.getV() - gpuBus.getV()));
                w = Math.max(w, Math.abs(cpuBus.getAngle() - gpuBus.getAngle()));
            }
            System.out.printf("  %-10s converged=%b, max|gpu - cpu| = %.2e%n", c.contingencyId(), r.hasConverged(), w);
            worst = Math.max(worst, w);
            checked++;
        }
        System.out.printf("GPU batched zero-impedance N-1: %d zero-imp branches, %d coupler outage rejected, "
                + "%d checked, worst max|gpu - cpu| = %.2e%n",
                d.getZeroImpedanceCount(), couplerRejected, checked, worst);
        assertTrue(couplerRejected >= 1, "must reject the coupler outage");
        assertTrue(checked >= 1, "must validate at least one non-coupler contingency on the device");
        assertTrue(worst < 1e-12, "every GPU zero-impedance post-contingency state must match OLF's Newton (worst = " + worst + ")");
    }

    /** Meshed triangle b1-b2-b3 plus a zero-impedance coupler b3-b4 feeding a load on b4. Outaging a triangle
     *  line stays non-islanding (so it batches and exercises the coupler's DUMMY_P/Q assembly per scenario);
     *  outaging the coupler is auto-rejected (bus-coupler / islanding-like). */
    private static Network meshedWithCoupler() {
        Network n = Network.create("zero-imp-meshed", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 4; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("b" + i).add();
        }
        Generator g1 = n.getVoltageLevel("vl1").newGenerator().setId("g1").setBus("b1").setConnectableBus("b1")
                .setMinP(0).setMaxP(400).setTargetP(160).setTargetV(100).setVoltageRegulatorOn(true).add();
        g1.newExtension(ActivePowerControlAdder.class).withParticipate(false).withDroop(4).add();
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("b2").setConnectableBus("b2").setP0(50).setQ0(20).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("b3").setConnectableBus("b3").setP0(50).setQ0(20).add();
        n.getVoltageLevel("vl4").newLoad().setId("l4").setBus("b4").setConnectableBus("b4").setP0(50).setQ0(20).add();
        line(n, "l12", "vl1", "b1", "vl2", "b2", 1.0, 10.0);
        line(n, "l23", "vl2", "b2", "vl3", "b3", 1.0, 10.0);
        line(n, "l13", "vl1", "b1", "vl3", "b3", 1.0, 10.0);
        line(n, "coupler", "vl3", "b3", "vl4", "b4", 0.0, 0.0);   // zero impedance -> DUMMY_P/Q coupler
        return n;
    }

    private static void line(Network n, String id, String vlA, String busA, String vlB, String busB,
                             double r, double x) {
        n.newLine().setId(id)
                .setVoltageLevel1(vlA).setBus1(busA).setConnectableBus1(busA)
                .setVoltageLevel2(vlB).setBus2(busB).setConnectableBus2(busB)
                .setR(r).setX(x).setG1(0).setB1(0).setG2(0).setB2(0)
                .add();
    }

    private static List<LfNetwork> load(Network iidm) {
        return Networks.load(iidm, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector()));
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

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
