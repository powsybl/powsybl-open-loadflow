/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.commons.report.ReportNode;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Islanding contingencies on the batched GPU N-1 — a capability a bare fixed-iteration N-1 batch lacks (it rejects
 * network-fragmenting outages). A meshed triangle {@code b1–b2–b3} (b1 slack+gen) carries a radial
 * leaf {@code b4} hung off b3. Outaging the radial branch {@code l34} islands b4: the GPU keeps the
 * fixed superset pattern, masks the branch, and FREEZES the islanded bus's rows to identity — held at a
 * clean flat {@code 1∠0} — in that scenario's per-scenario row mode, so the islanded sub-block stays
 * non-singular (it cannot poison the UBATCH factorization) and the main component re-solves correctly.
 *
 * <p>Two checks: the post-contingency main component IS the triangle alone (the radial leaf disconnected),
 * so the GPU's frozen-island solution must match a converged triangle-only network to machine precision on
 * b1/b2/b3; and the islanded leaf must report the clean flat {@code 1∠0} placeholder (no garbage voltage).
 *
 * <p>Skipped unless the native lib is available (built by native/build-gpu.sh with cuDSS).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedIslandingTest {

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
    void islandingContingencyFreezesIslandAndSolvesMainComponent() {
        // ---- full network: triangle + radial leaf; converge the base case, solve the radial outage ----
        LfNetworkParameters params = new LfNetworkParameters().setSlackBusSelector(new FirstSlackBusSelector());
        LfNetwork net = Networks.load(triangleNetwork(true), params).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);

        cpuReference(es, target, eq, sv);                    // converge the base case (hot start for the batch)
        double[] baseState = sv.get().clone();

        LfBranch radial = net.getBranchById("l34");
        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState,
                List.of(new BatchContingency("c-l34", radial)), es, target, new NewtonRaphsonParameters(), ReportNode.NO_OP);

        assertTrue(results.size() == 1, "one scenario");
        BatchScenarioResult r = results.get(0);
        System.out.printf("GPU islanding N-1 [radial outage l34]: converged=%b, %d iters, ||F||inf %.2e%n",
                r.hasConverged(), r.iterations(), r.finalMismatch());
        assertTrue(r.hasConverged(), "the islanding scenario must converge (the frozen island must not poison "
                + "the UBATCH factorization); status=" + r.convergenceStatus() + " ||F||=" + r.finalMismatch());

        // write the GPU's frozen-island solution back to the full network's buses
        sv.set(r.state());
        AcSolverUtil.updateNetwork(net, es);

        // ---- reference: the triangle alone (the post-contingency main component) ----
        LfNetwork triNet = Networks.load(triangleNetwork(false), params).get(0);
        EquationSystem<AcVariableType, AcEquationType> triEs = new AcEquationSystemCreator(triNet).create();
        AcSolverUtil.initStateVector(triNet, triEs, new UniformValueVoltageInitializer());
        AcTargetVector triTarget = new AcTargetVector(triNet, triEs);
        EquationVector<AcVariableType, AcEquationType> triEq = new EquationVector<>(triEs);
        cpuReference(triEs, triTarget, triEq, triEs.getStateVector());
        AcSolverUtil.updateNetwork(triNet, triEs);

        // each triangle bus must match between the GPU frozen-island solution and the triangle-only reference
        double worst = 0;
        int compared = 0;
        for (LfBus triBus : triNet.getBuses()) {
            LfBus fullBus = net.getBusById(triBus.getId());
            assertTrue(fullBus != null, "triangle bus " + triBus.getId() + " must exist in the full network");
            worst = Math.max(worst, Math.abs(fullBus.getV() - triBus.getV()));
            worst = Math.max(worst, Math.abs(fullBus.getAngle() - triBus.getAngle()));
            compared++;
        }
        System.out.printf("  main-component (%d triangle buses) max|v_gpu - v_ref| = %.2e%n", compared, worst);
        assertTrue(compared == 3, "must compare the 3 triangle buses");
        assertTrue(worst < 1e-11, "the GPU's frozen-island main component must match the triangle-only reference "
                + "(max diff = " + worst + ")");

        // the ISLANDED leaf bus (b4, disconnected from the main) must report a clean flat state — V=1 pu, angle 0
        // — NOT a garbage voltage/angle from freezing at the base equation target.
        LfBus island = net.getBuses().stream().filter(b -> triNet.getBusById(b.getId()) == null).findFirst().orElse(null);
        assertTrue(island != null, "the islanded bus must exist");
        System.out.printf("  islanded bus b4: V=%.6f pu, angle=%.6f rad%n", island.getV(), island.getAngle());
        assertTrue(Math.abs(island.getV() - 1.0) < 1e-9 && Math.abs(island.getAngle()) < 1e-9,
                "the islanded bus must be held at a clean flat 1∠0 (V=" + island.getV() + ", angle=" + island.getAngle() + ")");
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

    /**
     * Meshed triangle b1(slack+gen)-b2-b3, plus (when {@code withLeaf}) a radial leaf b4 off b3 via
     * branch {@code l34}, with a load on b4. With the leaf the radial outage islands b4; without it the
     * network IS the post-contingency main component.
     */
    private static Network triangleNetwork(boolean withLeaf) {
        Network n = Network.create("triangle-islanding", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= (withLeaf ? 4 : 3); i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("vl" + i + "_0").add();
        }
        n.getVoltageLevel("vl1").newGenerator().setId("g1").setBus("vl1_0").setConnectableBus("vl1_0")
                .setMinP(0).setMaxP(500).setTargetP(180).setTargetV(100).setVoltageRegulatorOn(true).add();
        addLoad(n, "vl2", 60, 30);
        addLoad(n, "vl3", 70, 25);
        line(n, "l12", "vl1", "vl2");
        line(n, "l23", "vl2", "vl3");
        line(n, "l13", "vl1", "vl3");
        if (withLeaf) {
            addLoad(n, "vl4", 40, 15);
            line(n, "l34", "vl3", "vl4");
        }
        return n;
    }

    private static void addLoad(Network n, String vl, double p, double q) {
        n.getVoltageLevel(vl).newLoad().setId("ld_" + vl).setBus(vl + "_0").setConnectableBus(vl + "_0")
                .setP0(p).setQ0(q).add();
    }

    private static void line(Network n, String id, String vlA, String vlB) {
        n.newLine().setId(id)
                .setVoltageLevel1(vlA).setBus1(vlA + "_0").setConnectableBus1(vlA + "_0")
                .setVoltageLevel2(vlB).setBus2(vlB + "_0").setConnectableBus2(vlB + "_0")
                .setR(1.0).setX(10.0).setG1(0).setB1(0).setG2(0).setB2(0)
                .add();
    }
}
