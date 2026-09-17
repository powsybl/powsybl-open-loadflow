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
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DIAGNOSTIC (not a parity gate): isolate whether the ~3.55e-6 GPU-vs-CPU gap on losing the sole-voltage-
 * controller generator g2 is a GPU SOLVE bug or an OVERRIDE-vs-network-REMOVAL difference.
 *
 * <p>The GPU does NOT remove g2 from the equation system. It keeps the base {@code es} structurally identical
 * and applies a purely NUMERIC override: change b2's active-power target ({@code pTarget = busP - g2.P}), flip
 * b2's BUS_V row to mean the reactive-balance equation (rowMode), and set its reactive target
 * ({@code qTarget = remainingGenQ - loadQ}). OLF's CPU reference instead DISCONNECTS g2 and reloads a fresh
 * network (b2 becomes a pure PQ load bus).
 *
 * <p>This test builds reference systems and PROVES (with assertions) where the gap comes from:
 * <ol>
 *   <li>GPU vs in-process override (same {@code es}, b2 flipped PV→PQ by toggling its
 *       {@code BUS_TARGET_V}→{@code BUS_TARGET_Q} activation + the two override targets patched in) — ~2e-16:
 *       the GPU solves its OWN override system to machine precision;</li>
 *   <li>residual of the GENUINELY g2-removed equation system AT the GPU/override solution — ~3.5e-15: the
 *       GPU/override solution is a TRUE root of the removal system (override ≡ removal, same function);</li>
 *   <li>a TIGHT in-process Newton (1e-13) on the removal system vs the GPU/override — ~1e-16: same root.</li>
 * </ol>
 * Conclusion (asserted): the ~3.55e-6 seen against {@code LoadFlow.run} is that reference's OWN inner-Newton
 * convergence slack — {@code LoadFlow.run} stops ~3.5e-6 short of the true root, while the GPU sits exactly on
 * it. NOT a GPU solve bug and NOT an override-design difference. The lesson (already recorded for the other
 * batched parity tests): use a tight same-params in-process Newton as the reference, never {@code LoadFlow.run}.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedGenOverrideDiagnosticTest {

    private static final int MAX_ITER = 60;
    private static final double TOL = 1e-13;

    static {
        GpuTestPaths.init();
    }

    @Test
    void overrideVsRemovalIsolation() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(),
                "GPU batched native lib not available (build it with native/build-gpu.sh + cuDSS)");

        // ---- base case (no distribution), converged in-process as the hot start ----
        Network gpuNet = threeBus();
        LfNetwork net = load(gpuNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> es = new AcEquationSystemCreator(net).create();
        AcSolverUtil.initStateVector(net, es, new UniformValueVoltageInitializer());
        StateVector sv = es.getStateVector();
        AcTargetVector target = new AcTargetVector(net, es);
        EquationVector<AcVariableType, AcEquationType> eq = new EquationVector<>(es);
        newton(es, target, eq, sv);                              // converge base case
        double[] baseState = sv.get().clone();
        int n = baseState.length;

        LfGenerator g2 = net.getGeneratorById("g2");
        LfBus b2 = g2.getBus();
        int busNum = b2.getNum();
        int vRow = es.getVariableSet().getVariable(busNum, AcVariableType.BUS_V).getRow();
        int pRow = es.getVariableSet().getVariable(busNum, AcVariableType.BUS_PHI).getRow();

        // ---- (GPU) device solve of losing g2, no DS / no RL (the clean minimal repro) ----
        List<BatchContingency> conts = List.of(BatchContingency.generator("g2", g2));
        List<BatchScenarioResult> results = GpuBatchedSecurityAnalysisSolver.solveBatchN1(net, baseState, conts,
                es, target, new NewtonRaphsonParameters().setMaxIterations(MAX_ITER), 1e-12, false, false,
                ReportNode.NO_OP);
        BatchScenarioResult rG2 = results.get(0);
        assumeTrue(!rG2.needsCpuFallback() && rG2.hasConverged(),
                "GPU must solve losing g2 on device (status=" + rG2.convergenceStatus() + ")");
        double[] gpuState = rG2.state();

        // ---- (override) OLF's own Newton on the GPU's INTENDED system: same es, b2 flipped PV->PQ ----
        // The GPU override values (mirror GpuBatchedSecurityAnalysisSolver.computeGenOutage, no DS):
        double pTarget = b2.getTargetP() - g2.getTargetP();
        double remainingGenQ = b2.getGenerators().stream().filter(g -> g != g2).mapToDouble(LfGenerator::getTargetQ).sum();
        double qTarget = remainingGenQ - b2.getLoadTargetQ();
        System.out.printf("override targets: pTarget=%.12f qTarget=%.12f (remainingGenQ=%.3f loadQ=%.3f)%n",
                pTarget, qTarget, remainingGenQ, b2.getLoadTargetQ());

        Equation<AcVariableType, AcEquationType> vEq = es.getEquation(busNum, AcEquationType.BUS_TARGET_V).orElseThrow();
        Optional<Equation<AcVariableType, AcEquationType>> qEqOpt = es.getEquation(busNum, AcEquationType.BUS_TARGET_Q);
        System.out.printf("BUS_TARGET_Q present for b2=%b%n", qEqOpt.isPresent());
        Equation<AcVariableType, AcEquationType> qEq = qEqOpt.orElseThrow();

        sv.set(baseState.clone());                              // hot start from the base case, like the GPU
        vEq.setActive(false);                                  // PV -> PQ: drop V=targetV ...
        qEq.setActive(true);                                   // ... add the reactive-balance equation

        // fresh target/eq/jacobian on the toggled system; patch the two override entries in place
        AcTargetVector ovTarget = new AcTargetVector(net, es);
        double[] ovArr = ovTarget.getArray();                  // force creation + validate (caches; no further invalidation in a pure-state Newton)
        int pCol = es.getEquation(busNum, AcEquationType.BUS_TARGET_P).orElseThrow().getColumn();
        int qCol = qEq.getColumn();
        ovArr[pCol] = pTarget;
        ovArr[qCol] = qTarget;
        EquationVector<AcVariableType, AcEquationType> ovEq = new EquationVector<>(es);
        newton(es, ovTarget, ovEq, sv);
        double[] overrideState = sv.get().clone();

        // restore base activation (tidy; the network-removal reference uses a fresh network anyway)
        qEq.setActive(false);
        vEq.setActive(true);

        // ---- (removal) OLF network removal: disconnect g2, fresh LoadFlow.run, no DS / no RL ----
        Network cpu = threeBus();
        cpu.getGenerator("g2").getTerminal().disconnect();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters lfp = new LoadFlowParameters().setDistributedSlack(false).setUseReactiveLimits(false);
        OpenLoadFlowParameters.create(lfp).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        assumeTrue(runner.run(cpu, lfp).isFullyConverged(), "network-removal CPU reference must converge");

        // ---- comparisons ----
        double gpuVsOverride = maxDiff(gpuState, overrideState);
        System.out.printf("%n[1] GPU vs in-process OVERRIDE        : max|Δstate| = %.3e  (b2: dV=%.3e dPhi=%.3e)%n",
                gpuVsOverride, Math.abs(gpuState[vRow] - overrideState[vRow]),
                Math.abs(gpuState[pRow] - overrideState[pRow]));

        // map both device-order states back onto the network to compare against the removal LoadFlow per bus
        double ovVsRemoval = busDiff(cpu, net, es, sv, overrideState, n);
        double gpuVsRemoval = busDiff(cpu, net, es, sv, gpuState, n);
        System.out.printf("[2] in-process OVERRIDE vs REMOVAL    : max|Δ(V,phi)| = %.3e%n", ovVsRemoval);
        System.out.printf("[3] GPU vs REMOVAL (the original gap) : max|Δ(V,phi)| = %.3e%n", gpuVsRemoval);
        System.out.printf("%nVERDICT: %s%n", gpuVsOverride < 1e-9
                ? "GPU solves its override system correctly -> the gap is OVERRIDE(keep g2, flip) vs REMOVAL(drop g2)."
                : "GPU does NOT match its own override system -> a device SOLVE bug.");

        // ---- [4] LOCALIZER: push the converged override solution onto the GENUINE removal equation system and
        //          read which equation row has a non-zero residual. That row IS the functional override-vs-removal
        //          difference (the term the network removal changes but the numeric override does not). ----
        Network rmNet = threeBus();
        rmNet.getGenerator("g2").getTerminal().disconnect();
        LfNetwork netR = load(rmNet).get(0);
        EquationSystem<AcVariableType, AcEquationType> esR = new AcEquationSystemCreator(netR).create();
        AcSolverUtil.initStateVector(netR, esR, new UniformValueVoltageInitializer());
        StateVector svR = esR.getStateVector();
        AcTargetVector targetR = new AcTargetVector(netR, esR);
        EquationVector<AcVariableType, AcEquationType> eqR = new EquationVector<>(esR);

        // transfer the override solution (es ordering) into the removal state vector (esR ordering), by bus id + var type
        double[] stateR = svR.get().clone();
        for (Bus iidmBus : rmNet.getBusView().getBuses()) {
            LfBus rb = netR.getBusById(iidmBus.getId());
            LfBus ob = net.getBusById(iidmBus.getId());
            if (rb == null || ob == null) {
                continue;
            }
            for (AcVariableType vt : new AcVariableType[] {AcVariableType.BUS_V, AcVariableType.BUS_PHI}) {
                var rv = esR.getVariableSet().getVariable(rb.getNum(), vt);
                var ovv = es.getVariableSet().getVariable(ob.getNum(), vt);
                if (rv != null && rv.getRow() >= 0 && ovv != null && ovv.getRow() >= 0) {
                    stateR[rv.getRow()] = overrideState[ovv.getRow()];
                }
            }
        }
        svR.set(stateR);
        eqR.minus(targetR);                                    // F_removal(overrideSolution)
        double[] fR = eqR.getArray();
        // find the worst residual row and name its equation
        int worstRow = 0;
        for (int i = 1; i < fR.length; i++) {
            if (Math.abs(fR[i]) > Math.abs(fR[worstRow])) {
                worstRow = i;
            }
        }
        String worstEq = "(equation-array column, not a single bus equation)";
        for (var e : esR.getIndex().getSortedSingleEquationsToSolve()) {
            if (e.getColumn() == worstRow) {
                worstEq = e.toString();
                break;
            }
        }
        System.out.printf("%n[4] residual of the REMOVAL system at the OVERRIDE solution: max|F| = %.3e at row %d (%s)%n",
                Math.abs(fR[worstRow]), worstRow, worstEq);
        System.out.println("    (≈0 here means the override/GPU solution IS a true root of the removal system — "
                + "same function; the 3.55e-6 must be the reference not converging as tightly.)");

        // ---- [5] solve the REMOVAL system in-process to a TIGHT tolerance and compare to the override solution,
        //          and measure how far LoadFlow.run actually landed from that true root. ----
        AcSolverUtil.initStateVector(netR, esR, new UniformValueVoltageInitializer());   // fresh flat start
        newton(esR, targetR, eqR, svR);                                                  // converge removal system to TOL=1e-13
        AcSolverUtil.updateNetwork(netR, esR);
        sv.set(overrideState.clone());                                                    // put the override solution back on `net`
        AcSolverUtil.updateNetwork(net, es);
        double tightRemovalVsOverride = 0;
        double loadFlowVsTightRemoval = 0;
        for (Bus iidmBus : rmNet.getBusView().getBuses()) {
            LfBus rb = netR.getBusById(iidmBus.getId());
            LfBus ob = net.getBusById(iidmBus.getId());
            Bus lfBus = cpu.getBusView().getBus(iidmBus.getId());
            if (rb == null || ob == null) {
                continue;
            }
            tightRemovalVsOverride = Math.max(tightRemovalVsOverride, Math.abs(rb.getV() - ob.getV()));
            tightRemovalVsOverride = Math.max(tightRemovalVsOverride, Math.abs(rb.getAngle() - ob.getAngle()));
            if (lfBus != null && !Double.isNaN(lfBus.getV())) {
                double lfV = lfBus.getV() / lfBus.getVoltageLevel().getNominalV();
                loadFlowVsTightRemoval = Math.max(loadFlowVsTightRemoval, Math.abs(lfV - rb.getV()));
                loadFlowVsTightRemoval = Math.max(loadFlowVsTightRemoval, Math.abs(Math.toRadians(lfBus.getAngle()) - rb.getAngle()));
            }
        }
        System.out.printf("%n[5] tight in-process REMOVAL (TOL=1e-13) vs OVERRIDE/GPU : %.3e%n", tightRemovalVsOverride);
        System.out.printf("    LoadFlow.run removal vs the tight REMOVAL root          : %.3e%n", loadFlowVsTightRemoval);
        System.out.println("    => the 3.55e-6 is LoadFlow.run's own convergence slack; the GPU sits on the true "
                + "removal root. Use a tight in-process Newton as the parity reference, not LoadFlow.run.");

        // ---- reference-robust assertions (independent of LoadFlow.run's convergence slack) ----
        assertTrue(gpuVsOverride < 1e-9, "the GPU must solve its OWN gen-contingency override system to machine "
                + "precision (max|Δstate| = " + gpuVsOverride + ")");
        assertTrue(Math.abs(fR[worstRow]) < 1e-11, "the GPU/override solution must be a TRUE root of the genuinely "
                + "g2-removed equation system (residual = " + Math.abs(fR[worstRow]) + ")");
        assertTrue(tightRemovalVsOverride < 1e-11, "a TIGHT in-process Newton on the g2-removed system must land on "
                + "the same root as the GPU/override (Δ = " + tightRemovalVsOverride + ")");
        // sanity: the looser LoadFlow.run reference is the one that disagrees — that is the whole point.
        assertTrue(loadFlowVsTightRemoval > 1e-9, "the LoadFlow.run reference is expected to carry its convergence "
                + "slack vs the true root (Δ = " + loadFlowVsTightRemoval + ")");
    }

    /** Push a device-order state onto the network and return the worst |V|,|phi| vs the removal LoadFlow buses. */
    private static double busDiff(Network cpu, LfNetwork net, EquationSystem<AcVariableType, AcEquationType> es,
                                  StateVector sv, double[] state, int n) {
        sv.set(java.util.Arrays.copyOf(state, n));
        AcSolverUtil.updateNetwork(net, es);
        double worst = 0;
        for (Bus cpuBus : cpu.getBusView().getBuses()) {
            LfBus gpuBus = net.getBusById(cpuBus.getId());
            if (gpuBus == null || Double.isNaN(cpuBus.getV())) {
                continue;
            }
            worst = Math.max(worst, Math.abs(cpuBus.getV() / cpuBus.getVoltageLevel().getNominalV() - gpuBus.getV()));
            worst = Math.max(worst, Math.abs(Math.toRadians(cpuBus.getAngle()) - gpuBus.getAngle()));
        }
        return worst;
    }

    private static double maxDiff(double[] a, double[] b) {
        double m = 0;
        for (int i = 0; i < a.length; i++) {
            m = Math.max(m, Math.abs(a[i] - b[i]));
        }
        return m;
    }

    private static Network threeBus() {
        Network n = Network.create("gen-override-diag", "test");
        Substation s = n.newSubstation().setId("s").add();
        for (int i = 1; i <= 3; i++) {
            VoltageLevel vl = s.newVoltageLevel().setId("vl" + i).setNominalV(100)
                    .setTopologyKind(TopologyKind.BUS_BREAKER).add();
            vl.getBusBreakerView().newBus().setId("b" + i).add();
        }
        gen(n, "g1", "vl1", "b1", 20);                          // slack source, does NOT participate
        gen(n, "g2", "vl2", "b2", 40);                          // sole voltage controller of b2 (the lost gen)
        gen(n, "g3", "vl3", "b3", 40);
        n.getVoltageLevel("vl2").newLoad().setId("l2").setBus("b2").setConnectableBus("b2").setP0(90).setQ0(20).add();
        n.getVoltageLevel("vl3").newLoad().setId("l3").setBus("b3").setConnectableBus("b3").setP0(90).setQ0(20).add();
        line(n, "l12", "vl1", "b1", "vl2", "b2");
        line(n, "l23", "vl2", "b2", "vl3", "b3");
        line(n, "l13", "vl1", "b1", "vl3", "b3");
        return n;
    }

    private static void gen(Network n, String id, String vl, String bus, double targetP) {
        Generator g = n.getVoltageLevel(vl).newGenerator().setId(id).setBus(bus).setConnectableBus(bus)
                .setMinP(0).setMaxP(400).setTargetP(targetP).setTargetV(100).setVoltageRegulatorOn(true).add();
        g.newMinMaxReactiveLimits().setMinQ(-400).setMaxQ(400).add();
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

    private static void newton(EquationSystem<AcVariableType, AcEquationType> es,
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
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
