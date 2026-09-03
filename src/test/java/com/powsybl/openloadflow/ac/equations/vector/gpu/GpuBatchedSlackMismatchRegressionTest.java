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
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression for the batched slack-mismatch bug: with {@code distributedSlack=false}, a network whose generation
 * far exceeds its load (so the free slack bus absorbs a LARGE active mismatch) must batch-solve a contingency to
 * the SAME state as the CPU. The bug was that the device ran the distributed-slack outer loop unconditionally
 * (built from generator participation, not from the run's outer-loop set), so it retargeted a participating
 * bus's active power by its share of the −50 MW mismatch — diverging ~2.5e-2 in angle. It was latent on balanced
 * networks (the retarget is negligible when the mismatch is ~1 MW). With the outer loops correctly declared off
 * ({@code distributedSlack=false}, {@code reactiveLimits=false}), the device must match the CPU to ~1e-9.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedSlackMismatchRegressionTest {

    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-12;

    static {
        GpuTestPaths.init();
    }

    @Test
    void excessGenerationBranchContingencyMatchesCpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(), "GPU native lib not available");

        Network gpuNet = threeBusExcessGen();
        LfNetwork net = load(gpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        cpuBaseSolve(es, target, eq, sv);
        double[] baseState = sv.get().clone();
        int n = baseState.length;

        LfBranch outaged = net.getBranchById("l23");
        List<BatchContingency> conts = List.of(new BatchContingency("l23", outaged));
        // distributedSlack=false, reactiveLimits=false: the free slack absorbs the whole mismatch; the device
        // must run NEITHER outer loop (the regressed bug ran distributed slack from participation alone).
        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters(), TOL, false, false, ReportNode.NO_OP);
        BatchScenarioResult r = results.get(0);
        assertTrue(!r.needsCpuFallback() && r.hasConverged(),
                "the excess-generation branch contingency must solve on the device (status=" + r.convergenceStatus() + ")");
        sv.set(java.util.Arrays.copyOf(r.state(), n));
        AcSolverUtil.updateNetwork(net, es);

        // CPU reference: the SAME l23 outage solved by OLF's own Newton on a fresh equation system (l23
        // disconnected), with the SAME voltage init / tolerance / code path as the device — so a match is
        // ~1e-9 (a full LoadFlow.run carries ~1e-6 of its own init/criteria slack, which would mask a real
        // ~1e-6 regression). Physical bus V/angle are compared (order-independent across the two systems).
        Network cpuNet = threeBusExcessGen();
        cpuNet.getLine("l23").getTerminal1().disconnect();
        cpuNet.getLine("l23").getTerminal2().disconnect();
        LfNetwork cpuLf = load(cpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> cpuEs = new AcEquationSystemCreator(cpuLf).create();
        AcSolverUtil.initStateVector(cpuLf, cpuEs, new UniformValueVoltageInitializer());
        AcTargetVector cpuTarget = new AcTargetVector(cpuLf, cpuEs);
        EquationVector<AcVariableType, AcEquationType> cpuEq = new EquationVector<>(cpuEs);
        cpuBaseSolve(cpuEs, cpuTarget, cpuEq, cpuEs.getStateVector());
        AcSolverUtil.updateNetwork(cpuLf, cpuEs);

        double worst = 0;
        for (LfBus cpuBus : cpuLf.getBuses()) {
            LfBus gpuBus = net.getBusById(cpuBus.getId());
            if (gpuBus == null) {
                continue;
            }
            worst = Math.max(worst, Math.abs(cpuBus.getV() - gpuBus.getV()));
            worst = Math.max(worst, Math.abs(cpuBus.getAngle() - gpuBus.getAngle()));
        }
        System.out.printf("GPU batched excess-gen branch contingency: max|gpu - cpu| = %.2e%n", worst);
        assertTrue(worst < 1e-12, "the GPU state must match OLF's Newton under a large free-slack mismatch "
                + "(worst = " + worst + ")");
    }

    /** Meshed b1(slack gen 60)-b2(gen 50, load 20)-b3(load 40): generation 110, load 60 -> slack absorbs ~-50. */
    private static Network threeBusExcessGen() {
        Network n = Network.create("slack-mismatch-regression", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("b" + i).add();
        }
        gen(n, "g1", "vl1", "b1", 60);
        gen(n, "g2", "vl2", "b2", 50);
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("b2").setConnectableBus("b2").setP0(20).setQ0(10).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("b3").setConnectableBus("b3").setP0(40).setQ0(15).add();
        line(n, "l12", "vl1", "b1", "vl2", "b2");
        line(n, "l23", "vl2", "b2", "vl3", "b3");
        line(n, "l13", "vl1", "b1", "vl3", "b3");
        return n;
    }

    private static void gen(Network n, String id, String vl, String bus, double targetP) {
        n.getVoltageLevel(vl).newGenerator().setId(id).setBus(bus).setConnectableBus(bus)
                .setMinP(0).setMaxP(400).setTargetP(targetP).setTargetV(100).setVoltageRegulatorOn(true).add();
    }

    private static void line(Network n, String id, String vlA, String busA, String vlB, String busB) {
        n.newLine().setId(id)
                .setVoltageLevel1(vlA).setBus1(busA).setConnectableBus1(busA)
                .setVoltageLevel2(vlB).setBus2(busB).setConnectableBus2(busB)
                .setR(1.0).setX(10.0).setG1(0).setB1(0).setG2(0).setB2(0)
                .add();
    }

    private static List<LfNetwork> load(Network iidm) {
        return Networks.load(iidm, new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector()));
    }

    private static void cpuBaseSolve(EquationSystem<AcVariableType, AcEquationType> es, AcTargetVector target,
                                     EquationVector<AcVariableType, AcEquationType> eq, StateVector sv) {
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(es, new DenseMatrixFactory())) {
            eq.minus(target);
            for (int it = 0; it < MAX_ITER && maxAbs(eq.getArray()) >= TOL; it++) {
                double[] dx = eq.getArray().clone();
                j.solveTransposed(dx);
                sv.minus(dx);
                eq.minus(target);
            }
        }
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
