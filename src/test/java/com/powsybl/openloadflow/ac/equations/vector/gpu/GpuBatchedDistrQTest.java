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
import com.powsybl.iidm.network.Load;
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
 * Batched N-1 on a network with remote/distributed generator voltage control (DISTR_Q) — the first of the
 * three "family" passes carried into the batched GPU path (Phase 3). Several generators at distinct buses
 * regulate one pilot bus, so each controller's BUS_V row carries a weighted reactive-distribution equation;
 * the batch assembles those rows per scenario with the {@code fillDistrQBatch} kernel, dropping a
 * contributor's term when its closed/open branch is outaged in that scenario.
 *
 * <p>Each converged post-contingency state is compared bus-by-bus to a CPU {@code LoadFlow.run} of the same
 * outage (remote control on, FIRST slack). Where this network previously THREW in the batched path, it now
 * solves on the device and agrees with the CPU. Skipped unless the native lib is available.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedDistrQTest {

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
    void distrQBatchedN1MatchesCpu() {
        Network gpuNet = meshed3BusRemoteControl();
        LfNetwork net = load(gpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);

        GpuAcData d = GpuAcDataExtractor.extract(net, es);
        assertTrue(d.getDistrQContributionCount() > 0, "the test network must exercise DISTR_Q rows");

        cpuReference(es, target, eq, sv);                    // converge the base case as the hot start
        double[] baseState = sv.get().clone();
        int n = baseState.length;

        List<BatchContingency> conts = new ArrayList<>();
        for (var line : gpuNet.getLines()) {
            LfBranch br = net.getBranchById(line.getId());
            if (br != null && br.getBus1() != null && br.getBus2() != null) {
                conts.add(new BatchContingency(line.getId(), br));
            }
        }
        assertTrue(conts.size() >= 2, "must have several branch contingencies");

        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters(), TOL, false, false, ReportNode.NO_OP);

        double worst = 0;
        int checked = 0;
        for (int i = 0; i < conts.size(); i++) {
            String id = conts.get(i).contingencyId();
            BatchScenarioResult r = results.get(i);
            if (r.needsCpuFallback()) {
                continue;
            }
            if (!r.hasConverged()) {
                continue;                                    // some outages de-energize a controller (CPU owns those)
            }
            sv.set(java.util.Arrays.copyOf(r.state(), n));
            AcSolverUtil.updateNetwork(net, es);

            // CPU reference: OLF's own Newton on a FRESH equation system with the branch disconnected — the
            // SAME voltage init, tolerance (TOL) and code path as the device, so the only thing compared is the
            // physics. The device keeps a static masked DISTR_Q pattern; this fresh system has the true
            // post-contingency control topology, so any disagreement is a real device bug (not engine slack).
            Network cpu = meshed3BusRemoteControl();
            Branch<?> br = cpu.getBranch(id);
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
            System.out.printf("  %-10s converged=%b, max|gpu - cpu| = %.2e%n", id, r.hasConverged(), w);
            worst = Math.max(worst, w);
            checked++;
        }
        System.out.printf("GPU batched DISTR_Q N-1 [generator remote control]: %d dq contributions, %d checked, "
                + "worst max|gpu - cpu| = %.2e%n", d.getDistrQContributionCount(), checked, worst);
        assertTrue(checked >= 1, "must validate at least one DISTR_Q contingency on the device");
        assertTrue(worst < 1e-12, "every GPU DISTR_Q post-contingency state must match OLF's Newton (worst = " + worst + ")");
    }

    /** Meshed triangle b1-b2-b3: generators g1@b1 and g2@b2 BOTH remotely regulate pilot b3 (which carries the
     *  load) → a DISTR_Q reactive-distribution row. The triangle keeps every single-line outage non-islanding,
     *  so each scenario exercises the batched DISTR_Q assembly (and the mask-skip when an outaged line's q
     *  would otherwise enter the sum). Generators are non-participating so distributed slack stays off. */
    private static Network meshed3BusRemoteControl() {
        Network n = Network.create("distrq-meshed", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("b" + i).add();
        }
        Load l3 = n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("b3").setConnectableBus("b3")
                .setP0(150).setQ0(80).add();
        remoteGen(n, "g1", "vl1", "b1", l3);
        remoteGen(n, "g2", "vl2", "b2", l3);
        line(n, "l12", "vl1", "b1", "vl2", "b2");
        line(n, "l23", "vl2", "b2", "vl3", "b3");
        line(n, "l13", "vl1", "b1", "vl3", "b3");
        return n;
    }

    private static void remoteGen(Network n, String id, String vl, String bus, Load pilot) {
        Generator g = n.getVoltageLevel(vl).newGenerator().setId(id).setBus(bus).setConnectableBus(bus)
                .setMinP(0).setMaxP(400).setTargetP(80).setTargetV(100).setVoltageRegulatorOn(true)
                .setRegulatingTerminal(pilot.getTerminal()).add();     // regulate the PILOT bus (remote)
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
        return Networks.load(iidm, new LfNetworkParameters()
                .setSlackBusSelector(new FirstSlackBusSelector())
                .setGeneratorVoltageRemoteControl(true));
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
