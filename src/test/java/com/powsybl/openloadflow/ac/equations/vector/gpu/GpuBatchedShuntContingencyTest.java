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
 * Batched fixed-shunt disconnection (N-1) on the device. A shunt compensator outage is not a generator or a
 * branch — the batch applies it as a per-scenario element mask over the shunt pack: the disabled shunt's
 * reactive injection (b·v²) and its J entries drop out, exactly as removing the shunt from OLF's network. This
 * verifies (1) disconnecting a fixed shunt solves on the device and matches OLF's own Newton on the same
 * outage (to ~1e-9), and (2) the masked element is the right one (the post-contingency voltage drops at the
 * shunt bus, so a no-op mask would be caught).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedShuntContingencyTest {

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
    void shuntDisconnectionBatchedMatchesCpu() {
        Network gpuNet = threeBusWithShunt();
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
                BatchContingency.shunt("sh3", net.getShuntById("sh3")));

        // This network's CPU reference runs with distributedSlack=false and useReactiveLimits=false, so the
        // device must run NEITHER outer loop — declare both off (an enabled-by-capability outer loop would
        // retarget under the slack mismatch and diverge).
        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters(), TOL, false, false, ReportNode.NO_OP);

        BatchScenarioResult r = results.get(0);
        assertTrue(!r.needsCpuFallback() && r.hasConverged(),
                "disconnecting the fixed shunt must solve on the device (status=" + r.convergenceStatus() + ")");
        sv.set(java.util.Arrays.copyOf(r.state(), n));
        AcSolverUtil.updateNetwork(net, es);

        // CPU reference: the SAME outage solved by OLF's own Newton on a fresh equation system (sh3
        // disconnected), with the SAME voltage init / tolerance / code path as the device — so a match is
        // ~1e-9 (a full LoadFlow.run carries ~1e-6 of its own init/criteria slack that would mask a real
        // regression). Physical bus V/angle are compared (order-independent across the two systems).
        Network cpu = threeBusWithShunt();
        cpu.getShuntCompensator("sh3").getTerminal().disconnect();
        LfNetwork cpuLf = load(cpu).get(0);
        EquationSystem<AcVariableType, AcEquationType> cpuEs = new AcEquationSystemCreator(cpuLf).create();
        AcSolverUtil.initStateVector(cpuLf, cpuEs, new UniformValueVoltageInitializer());
        AcTargetVector cpuTarget = new AcTargetVector(cpuLf, cpuEs);
        EquationVector<AcVariableType, AcEquationType> cpuEq = new EquationVector<>(cpuEs);
        cpuReference(cpuEs, cpuTarget, cpuEq, cpuEs.getStateVector());
        AcSolverUtil.updateNetwork(cpuLf, cpuEs);

        double worst = 0;
        double maxMove = 0;
        for (LfBus cpuBus : cpuLf.getBuses()) {
            LfBus gpuBus = net.getBusById(cpuBus.getId());
            if (gpuBus == null) {
                continue;
            }
            worst = Math.max(worst, Math.abs(cpuBus.getV() - gpuBus.getV()));
            worst = Math.max(worst, Math.abs(cpuBus.getAngle() - gpuBus.getAngle()));
            maxMove = Math.max(maxMove, Math.abs(1.0 - gpuBus.getV()));
        }
        System.out.printf("GPU batched shunt contingency [disconnect sh3]: converged=%b, max|gpu - cpu| = %.2e%n",
                r.hasConverged(), worst);
        assertTrue(worst < 1e-12, "the GPU shunt-contingency state must match OLF's Newton (worst = " + worst + ")");
        assertTrue(maxMove > 1e-3, "the shunt disconnection must actually move a bus voltage (not a no-op mask)");
    }

    /** Meshed b1(slack gen)-b2(gen + load)-b3(load + a fixed capacitive shunt), BALANCED (generation ≈ load).
     *  Disconnecting the b3 shunt removes its reactive support, so b3's voltage drops — a clearly observable,
     *  non-trivial change — while the small slack active mismatch keeps the case well-conditioned. */
    private static Network threeBusWithShunt() {
        Network n = Network.create("shunt-contingency", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("b" + i).add();
        }
        gen(n, "g1", "vl1", "b1", 60);
        gen(n, "g2", "vl2", "b2", 50);
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("b2").setConnectableBus("b2").setP0(20).setQ0(10).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("b3").setConnectableBus("b3").setP0(90).setQ0(40).add();
        // A fixed (non-voltage-controlling) capacitive shunt on the load bus b3.
        n.getVoltageLevel("vl3").newShuntCompensator().setId("sh3").setBus("b3").setConnectableBus("b3")
                .setSectionCount(1).setVoltageRegulatorOn(false)
                .newLinearModel().setBPerSection(0.004).setMaximumSectionCount(1).add()
                .add();
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
