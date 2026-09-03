/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
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
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Batched generator contingency at a DISTRIBUTED-SLACK participant bus (Stage 2). Losing a participating
 * generator is a per-scenario target override (remove its P, PV→PQ-flip the bus) PLUS a per-scenario
 * distributed-slack reweight: the lost generator drops out of the slack participation, so its bus's
 * participation factor / base P / headroom / footroom shrink by its share AND the per-scenario factor sum
 * shrinks by the same factor delta, so the other participating buses absorb its share — exactly OLF's
 * {@code DistributedSlackOuterLoop} after {@code LfContingency.processLostGenerators}.
 *
 * <p>The slack generator does not participate, so the on-device retain stays zero (the reweight math holds).
 * Verifies the device solution of losing the participating, sole-voltage-controller g2 matches a CPU
 * {@code LoadFlow.run} (distributed slack on) of the same outage.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedGeneratorContingencyDsTest {

    private static final int MAX_ITER = 30;
    private static final double TOL = 1e-8;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void checkGpu() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU batched native lib not available (build it with native/build-gpu.sh + cuDSS)");
    }

    @Test
    void generatorAtDistributedSlackBusBatchedMatchesCpu() {
        Network gpuNet = threeBusDs();
        LfNetwork net = load(gpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        cpuReference(es, target, eq, sv);                    // converge the base case (no distribution) as the hot start
        double[] baseState = sv.get().clone();
        int n = baseState.length;

        assertTrue(GpuAcDataExtractor.extract(net, es).getDistributedSlackBusCount() > 0,
                "the network must have participating generators (a DS reweight to exercise)");

        // g2 participates in the distributed slack AND is its bus's sole voltage controller: losing it removes
        // its P, flips b2 PV→PQ, and reweights the slack distribution onto g3.
        List<BatchContingency> conts = List.of(BatchContingency.generator("g2", net.getGeneratorById("g2")));

        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters(), 1e-10, true, false, ReportNode.NO_OP);

        BatchScenarioResult rG2 = results.get(0);
        assertTrue(!rG2.needsCpuFallback() && rG2.hasConverged(),
                "losing the DS-participant g2 must solve on the device (status=" + rG2.convergenceStatus() + ")");

        // CPU reference: disconnect g2, full LoadFlow.run with distributed slack on (same scope).
        sv.set(java.util.Arrays.copyOf(rG2.state(), n));
        AcSolverUtil.updateNetwork(net, es);
        Network cpu = threeBusDs();
        cpu.getGenerator("g2").getTerminal().disconnect();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters lfp = new LoadFlowParameters().setDistributedSlack(true).setUseReactiveLimits(false);
        // TIGHT inner-Newton reference: OLF's default UNIFORM_CRITERIA (convEpsPerEq=1e-4) stops ~e-4 short of the
        // true root, which used to read as a ~1.6e-6 GPU "gap". Drive it to 1e-12 so the reference lands on the
        // true root the device already sits on (see GpuBatchedGenOverrideDiagnosticTest / GpuBatchedL1L2DiagnosticTest).
        OpenLoadFlowParameters.create(lfp).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setNewtonRaphsonConvEpsPerEq(1e-12);
        assertTrue(runner.run(cpu, lfp).isFullyConverged(), "CPU reference for losing g2 must converge");

        double worst = 0;
        for (Bus cpuBus : cpu.getBusView().getBuses()) {
            LfBus gpuBus = net.getBusById(cpuBus.getId());
            if (gpuBus == null || Double.isNaN(cpuBus.getV())) {
                continue;
            }
            worst = Math.max(worst, Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - gpuBus.getV()));
            worst = Math.max(worst, Math.abs(Math.toRadians(cpuBus.getAngle()) - gpuBus.getAngle()));
        }
        System.out.printf("GPU batched gen contingency at DS bus [lose g2, PV→PQ + reweight]: converged=%b, "
                + "max|gpu - cpu| = %.2e%n", rG2.hasConverged(), worst);
        assertTrue(worst < 1e-10, "the GPU DS-reweight generator-contingency state must match the CPU LoadFlow "
                + "(worst = " + worst + ")");
    }

    /** Meshed b1(non-participating slack gen)-b2(participating gen, sole controller)-b3(participating gen);
     *  b2/b3 carry loads exceeding the scheduled generation, so the slack absorbs a deficit distributed across
     *  b2/b3. Losing g2 removes its P, flips b2 PV→PQ AND reweights the distribution onto g3. */
    private static Network threeBusDs() {
        Network n = Network.create("gen-contingency-ds", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("b" + i).add();
        }
        gen(n, "g1", "vl1", "b1", 20, false);                // slack source, does NOT participate
        gen(n, "g2", "vl2", "b2", 40, true);                 // participating + sole voltage controller of b2
        gen(n, "g3", "vl3", "b3", 40, true);                 // participating: absorbs g2's lost share
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("b2").setConnectableBus("b2").setP0(90).setQ0(20).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("b3").setConnectableBus("b3").setP0(90).setQ0(20).add();
        line(n, "l12", "vl1", "b1", "vl2", "b2");
        line(n, "l23", "vl2", "b2", "vl3", "b3");
        line(n, "l13", "vl1", "b1", "vl3", "b3");
        return n;
    }

    private static void gen(Network n, String id, String vl, String bus, double targetP, boolean participate) {
        Generator g = n.getVoltageLevel(vl).newGenerator().setId(id).setBus(bus).setConnectableBus(bus)
                .setMinP(0).setMaxP(400).setTargetP(targetP).setTargetV(100).setVoltageRegulatorOn(true).add();
        g.newMinMaxReactiveLimits().setMinQ(-400).setMaxQ(400).add();   // wide: reactive limits never bind here
        g.newExtension(ActivePowerControlAdder.class).withParticipate(participate).withDroop(4).add();
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
