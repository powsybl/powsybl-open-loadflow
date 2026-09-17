/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.report.ReportNode;
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
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Batched generator contingency (N-1) on the device. A generator loss is not a branch mask — the batch
 * applies it as a per-scenario target override that mirrors {@code LfContingency.processLostGenerators}:
 * remove the generator's P from its bus's P target, and — when the bus loses its last voltage controller —
 * flip the bus PV→PQ (BUS_V row becomes the reactive-power equation, with a recomputed Q target).
 *
 * <p>Stage 1 scope: a generator at a NON distributed-slack-participant bus (the DS reweight is a follow-up),
 * not the slack/reference bus, not a remote/DISTR_Q controller — those fall through to the CPU. This verifies
 * (1) losing the non-slack voltage-controlling generator g2 solves on the device and matches a CPU
 * {@code LoadFlow.run} of the same outage, and (2) losing the slack generator g1 is rejected to the CPU.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedGeneratorContingencyTest {

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
    void generatorContingencyBatchedMatchesCpu() {
        Network gpuNet = meshed3Bus();
        LfNetwork net = load(gpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        cpuReference(es, target, eq, sv);
        double[] baseState = sv.get().clone();
        int n = baseState.length;

        List<BatchContingency> conts = List.of(
                BatchContingency.generator("g2", net.getGeneratorById("g2")),    // batchable: non-slack, non-DS
                BatchContingency.generator("g1", net.getGeneratorById("g1")));    // slack source: must reject

        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters(), TOL, false, false, ReportNode.NO_OP);

        BatchScenarioResult rG2 = results.get(0);
        BatchScenarioResult rG1 = results.get(1);
        assertTrue(rG1.needsCpuFallback(), "losing the slack generator g1 must be rejected to the CPU");
        assertTrue(!rG2.needsCpuFallback() && rG2.hasConverged(),
                "losing g2 must solve on the device (status=" + rG2.convergenceStatus() + ")");

        // CPU reference: OLF's own Newton on a FRESH equation system with g2 disconnected (b2 becomes PQ) — the
        // SAME voltage init, tolerance (TOL) and code path as the device, so cpu/gpu use identical parameters
        // and the only thing compared is the physics. Any disagreement is a real device bug.
        sv.set(java.util.Arrays.copyOf(rG2.state(), n));
        AcSolverUtil.updateNetwork(net, es);
        Network cpu = meshed3Bus();
        cpu.getGenerator("g2").getTerminal().disconnect();
        LfNetwork cpuLf = load(cpu).get(0);
        EquationSystem<AcVariableType, AcEquationType> cpuEs = new AcEquationSystemCreator(cpuLf).create();
        AcSolverUtil.initStateVector(cpuLf, cpuEs, new UniformValueVoltageInitializer());
        AcTargetVector cpuT = new AcTargetVector(cpuLf, cpuEs);
        EquationVector<AcVariableType, AcEquationType> cpuEq = new EquationVector<>(cpuEs);
        cpuReference(cpuEs, cpuT, cpuEq, cpuEs.getStateVector());
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
        System.out.printf("GPU batched generator contingency [lose g2, PV→PQ]: converged=%b, max|gpu - cpu| = %.2e%n",
                rG2.hasConverged(), worst);
        assertTrue(worst < 1e-12, "the GPU generator-contingency state must match OLF's Newton (worst = " + worst + ")");
    }

    /** Meshed b1(slack gen)-b2(gen, sole voltage controller)-b3(load). Losing g2 removes its P AND flips b2
     *  PV→PQ; both generators are non-participating so no bus is a distributed-slack participant. */
    private static Network meshed3Bus() {
        Network n = Network.create("gen-contingency", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("b" + i).add();
        }
        gen(n, "g1", "vl1", "b1", 60);
        gen(n, "g2", "vl2", "b2", 50);
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("b2").setConnectableBus("b2").setP0(20).setQ0(10).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("b3").setConnectableBus("b3").setP0(100).setQ0(40).add();
        line(n, "l12", "vl1", "b1", "vl2", "b2");
        line(n, "l23", "vl2", "b2", "vl3", "b3");
        line(n, "l13", "vl1", "b1", "vl3", "b3");
        return n;
    }

    private static void gen(Network n, String id, String vl, String bus, double targetP) {
        Generator g = n.getVoltageLevel(vl).newGenerator().setId(id).setBus(bus).setConnectableBus(bus)
                .setMinP(0).setMaxP(400).setTargetP(targetP).setTargetV(100).setVoltageRegulatorOn(true).add();
        g.newExtension(ActivePowerControlAdder.class).withParticipate(false).withDroop(4).add();
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
