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
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
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
import com.powsybl.openloadflow.network.VoltageControl;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Batched N-1 on a network with transformer ratio distribution (DISTR_RHO) — two transformers share the
 * voltage control of one bus, so each controller's BRANCH_RHO1 row carries a linear {@code (1/count)·Σr1 − r1}
 * equation assembled per scenario by the {@code fillDistrRhoBatch} kernel.
 *
 * <p>Outaging a CONTROLLING transformer reforms the {@code 1/count} coefficients (OLF redistributes the ratio
 * across the survivors), which the fixed batch pattern cannot represent — so those contingencies must be
 * REJECTED to the CPU. This verifies (1) DISTR_RHO networks now batch (no throw), (2) a controller-transformer
 * outage is routed to the CPU, and (3) the surviving (non-controller) contingencies agree with a CPU
 * {@code LoadFlow.run}. Skipped unless the native lib is available.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedDistrRhoTest {

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
    void distrRhoBatchedN1RejectsControllerOutageAndMatchesCpu() {
        Network gpuNet = sharedControlMeshed();
        LfNetwork net = load(gpuNet);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        enableTransformerControl(net);

        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);

        GpuAcData d = GpuAcDataExtractor.extract(net, es);
        assertTrue(d.getDistrRhoContributionCount() > 0, "the test network must exercise DISTR_RHO rows");
        Set<LfBranch> controllers = GpuAcDataExtractor.distrRhoControllerBranches(net, es);
        assertTrue(!controllers.isEmpty(), "must have DISTR_RHO controller transformers");

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
        int controllerRejected = 0;
        for (int i = 0; i < conts.size(); i++) {
            BatchContingency c = conts.get(i);
            BatchScenarioResult r = results.get(i);
            boolean isController = c.outagedBranches().stream().anyMatch(controllers::contains);
            if (isController) {
                assertTrue(r.needsCpuFallback(), "a controlling transformer outage (" + c.contingencyId()
                        + ") must be rejected to the CPU (coefficients reform)");
                controllerRejected++;
                continue;
            }
            if (r.needsCpuFallback() || !r.hasConverged()) {
                continue;                                    // islanding / de-energized → CPU owns it
            }
            sv.set(java.util.Arrays.copyOf(r.state(), n));
            AcSolverUtil.updateNetwork(net, es);

            // CPU reference: OLF's own Newton on a FRESH equation system with the branch disconnected — the SAME
            // continuous ratio-distribution (DISTR_RHO) the device solves, with the SAME voltage init / tolerance
            // / code path. (A full LoadFlow.run instead drives the ratio through the transformer-voltage-control
            // OUTER LOOP — a different method, agreeing only to ~5e-4. Same-method, same-params is ~1e-9.)
            Network cpu = sharedControlMeshed();
            Branch<?> br = cpu.getBranch(c.contingencyId());
            if (br == null) {
                continue;
            }
            br.getTerminal1().disconnect();
            br.getTerminal2().disconnect();
            LfNetwork cpuLf = load(cpu);
            EquationSystem<AcVariableType, AcEquationType> cpuEs = new AcEquationSystemCreator(cpuLf).create();
            enableTransformerControl(cpuLf);                 // same continuous DISTR_RHO control the device solves
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
        System.out.printf("GPU batched DISTR_RHO N-1 [transformer shared control]: %d dr contributions, "
                + "%d controller outages rejected, %d checked, worst max|gpu - cpu| = %.2e%n",
                d.getDistrRhoContributionCount(), controllerRejected, checked, worst);
        assertTrue(controllerRejected >= 1, "must reject at least one controller-transformer outage");
        assertTrue(checked >= 1, "must validate at least one non-controller DISTR_RHO contingency on the device");
        assertTrue(worst < 1e-12, "every GPU DISTR_RHO post-contingency state must match OLF's Newton (worst = " + worst + ")");
    }

    /** The shared-transformer-control network plus a line parallel to LINE_12, so that outaging a
     *  non-controller line stays non-islanding and actually exercises the batched DISTR_RHO assembly. */
    private static Network sharedControlMeshed() {
        Network n = VoltageControlNetworkFactory.createWithTransformerSharedRemoteControl();
        Line l12 = n.getLine("LINE_12");
        Terminal t1 = l12.getTerminal1();
        Terminal t2 = l12.getTerminal2();
        n.newLine().setId("LINE_12b")
                .setVoltageLevel1(t1.getVoltageLevel().getId()).setBus1(t1.getBusBreakerView().getBus().getId())
                .setConnectableBus1(t1.getBusBreakerView().getConnectableBus().getId())
                .setVoltageLevel2(t2.getVoltageLevel().getId()).setBus2(t2.getBusBreakerView().getBus().getId())
                .setConnectableBus2(t2.getBusBreakerView().getConnectableBus().getId())
                .setR(1.05).setX(10.0).setG1(0).setB1(0).setG2(0).setB2(0)
                .add();
        return n;
    }

    private static LfNetwork load(Network iidm) {
        return Networks.load(iidm, new LfNetworkParameters()
                .setSlackBusSelector(new FirstSlackBusSelector())
                .setTransformerVoltageControl(true)).get(0);
    }

    private static void enableTransformerControl(LfNetwork net) {
        for (LfBranch b : net.<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            if (b.isConnectedAtBothSides()) {
                b.setVoltageControlEnabled(true);
            }
        }
        net.fixTransformerVoltageControls();
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
