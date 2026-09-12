/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityAnalysisRunParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finite-difference / forward cross-check of the reverse-mode entry point
 * {@link AcSensitivityAnalysis#runAdjoint} on IEEE-14 (the VJP validation gate). runAdjoint reuses the
 * AC load flow retained in the network cache ({@code networkCacheEnabled}) and returns
 * {@code θ̄ = Sᵀ·ȳ} without materialising the sensitivity matrix {@code S}.
 *
 * <p>The factors are the canonical DC-like sensitivity {@code d P_branch1 / d P_injection}: with an
 * active-power function and an active-power variable the SPI unscale is unity (both in MW, base cancels),
 * so runAdjoint's raw per-unit output equals both the forward (unscaled) sensitivity and a central finite
 * difference in MW/MW with no scaling correction. This makes it a clean, self-contained gate:
 * <ul>
 *   <li>{@code θ̄_g} for {@code ȳ = e_branch} must equal the forward {@code S[branch, g]} (transpose
 *       identity, tight) and a re-solve central difference {@code dP_branch/dP_g} (ground truth);</li>
 *   <li>for a weighted multi-branch cotangent, {@code θ̄_g = Σ_b ȳ_b · S[b, g]} (linearity + the
 *       per-function {@code x̄} de-duplication).</li>
 * </ul>
 */
class AcSensitivityAnalysisAdjointTest {

    private static final List<String> GENS = List.of("B2-G", "B3-G", "B6-G");

    private static LoadFlowParameters cacheEnabledParameters() {
        LoadFlowParameters lfp = new LoadFlowParameters().setDistributedSlack(false);
        OpenLoadFlowParameters.create(lfp).setNetworkCacheEnabled(true);
        return lfp;
    }

    private static LoadFlowParameters distributedSlackCacheEnabledParameters() {
        LoadFlowParameters lfp = new LoadFlowParameters().setDistributedSlack(true);
        OpenLoadFlowParameters.create(lfp).setNetworkCacheEnabled(true);
        return lfp;
    }

    private static SensitivityFactor injectionToBranchFlow(String branch, String gen) {
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch,
                SensitivityVariableType.INJECTION_ACTIVE_POWER, gen, false, ContingencyContext.all());
    }

    // cotangent-map key for a BRANCH_ACTIVE_POWER_1 monitored function
    private static String powerKey(String branchId) {
        return AcSensitivityAnalysis.functionCotangentKey(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branchId);
    }

    private static double dBranchFlowPerGenFd(Network network, LoadFlowParameters lfp, String branch, String gen) {
        Generator g = network.getGenerator(gen);
        double p0 = g.getTargetP();
        double eps = 0.5; // MW
        g.setTargetP(p0 + eps);
        LoadFlow.find("OpenLoadFlow").run(network, lfp);
        double pPlus = network.getBranch(branch).getTerminal1().getP();
        g.setTargetP(p0 - eps);
        LoadFlow.find("OpenLoadFlow").run(network, lfp);
        double pMinus = network.getBranch(branch).getTerminal1().getP();
        g.setTargetP(p0);
        LoadFlow.find("OpenLoadFlow").run(network, lfp);
        return (pPlus - pMinus) / (2 * eps); // dP_branch_MW / dP_gen_MW == raw pu/pu
    }

    @Test
    void runAdjointMatchesForwardSensitivityAndFiniteDifferenceOnIeee14() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        String branch = "L1-2-1";

        // populate the cache (runAdjoint reuses this converged, factorized context)
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        List<SensitivityFactor> factors = new ArrayList<>();
        for (String g : GENS) {
            factors.add(injectionToBranchFlow(branch, g));
        }

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);

        // reverse mode: ȳ = e_branch -> θ̄_g = dP_branch1/dP_g
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        Map<String, Double> thetaBar = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                adjointBlocks(List.of(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), List.of(List.of(branch)),
                        SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS),
                Map.of(AcSensitivityAnalysis.functionCotangentKey(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch), 1.0));

        // forward sensitivity matrix S (its own no-cache load flow; unscaled == raw here)
        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));

        double maxAbs = 0;
        for (String g : GENS) {
            double s = fwd.getBranchFlow1SensitivityValue(g, branch, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double fd = dBranchFlowPerGenFd(network, lfp, branch, g); // last: mutates+restores the cache
            double theta = thetaBar.get(g);
            assertEquals(s, theta, 1e-12 * Math.abs(s) + 1e-13, "runAdjoint vs forward S for " + g);
            // FD floor here is the central-difference O(ε²) truncation (ε=0.5 MW, relative ~1e-2 → ~3e-4),
            // measured max ~3.4e-4; same residual the forward mode would show against this FD.
            assertEquals(fd, theta, 1e-3 * (Math.abs(fd) + 1e-2), "runAdjoint vs finite difference for " + g);
            maxAbs = Math.max(maxAbs, Math.abs(theta));
        }
        assertTrue(maxAbs > 0.1, "gradient must be non-trivial, got max |θ̄| = " + maxAbs);
    }

    @Test
    void runAdjointHandlesDistributedSlackOnIeee14() {
        // Distributed slack is an outer loop folded into the RHS via slackParticipationByBus
        // (getParticipatingElements) exactly as the forward does — no Schur/augmented Jacobian — so the
        // transpose picks it up on the shared λ. The gate is that runAdjoint reproduces the forward
        // sensitivity S with the slack DISTRIBUTED over the machines (S differs from the single-slack case
        // because the injection variable now carries the -participation columns).
        //
        // We compare against the forward S rather than a raw-targetP re-solve: perturbing a *participating*
        // generator's targetP has part of the change reabsorbed by the slack distribution, so the physical
        // FD's effective injection pattern is not the SPI INJECTION_ACTIVE_POWER variable (they disagree by
        // the participation, ~2x here). That is a property of the forward injection convention, which OLF's
        // own distributed-slack sensitivity tests validate against a convention-correct FD; here we assert
        // the adjoint inherits it exactly.
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = distributedSlackCacheEnabledParameters();
        String branch = "L1-2-1";

        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        List<SensitivityFactor> factors = new ArrayList<>();
        for (String g : GENS) {
            factors.add(injectionToBranchFlow(branch, g));
        }

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);

        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        Map<String, Double> thetaBar = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                adjointBlocks(List.of(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), List.of(List.of(branch)),
                        SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS),
                Map.of(AcSensitivityAnalysis.functionCotangentKey(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch), 1.0));

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));

        double maxAbs = 0;
        for (String g : GENS) {
            double s = fwd.getBranchFlow1SensitivityValue(g, branch, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double theta = thetaBar.get(g);
            assertEquals(s, theta, 1e-12 * Math.abs(s) + 1e-13, "runAdjoint vs forward S (distributed slack) for " + g);
            maxAbs = Math.max(maxAbs, Math.abs(theta));
        }
        assertTrue(maxAbs > 0.1, "gradient must be non-trivial, got max |θ̄| = " + maxAbs);
    }

    private static Network ieee14WithZone(double pilotTargetV) {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getGenerator("B8-G").newMinMaxReactiveLimits().setMinQ(-6).setMaxQ(200).add();
        network.newExtension(SecondaryVoltageControlAdder.class)
                .newControlZone().withName("z1")
                    .newPilotPoint().withTargetV(pilotTargetV).withBusbarSectionsOrBusesIds(List.of("B10")).add()
                    .newControlUnit().withId("B6-G").add()
                    .newControlUnit().withId("B8-G").add()
                    .add()
                .add();
        return network;
    }

    private static LoadFlowParameters svcCacheEnabledParameters() {
        LoadFlowParameters lfp = new LoadFlowParameters().setUseReactiveLimits(false);
        OpenLoadFlowParameters.create(lfp)
                .setSecondaryVoltageControl(true)
                .setMaxPlausibleTargetVoltage(1.6)
                .setNetworkCacheEnabled(true);
        return lfp;
    }

    private static double busVoltage(Network network, String busId) {
        return network.getBusBreakerView().getBus(busId).getV();
    }

    @Test
    void runAdjointHandlesSvcPilotLeverOnIeee14() {
        // TVC's RST lever: the SVC pilot-point target voltage (SVC_PILOT_POINT_TARGET_VOLTAGE, variableId =
        // the zone name). runAdjoint reuses fillSvcPilotFactorsRhs (the closed-loop coordination) on the
        // cached converged context. θ̄ is RAW per-unit dV_bus_pu / dV_pilot_pu, whereas the forward S and the
        // physical re-solve FD are kV/kV, so they relate by the nominal-voltage ratio Vnom(pilot)/Vnom(f0)
        // (which is 1 when the monitored bus shares the pilot's voltage level).
        String zone = "z1";
        String pilot = "B10";
        List<String> monitored = List.of("B10", "B6", "B4"); // pilot, a controller bus, a far bus on another VL
        double targetV = 13.0;
        double dV = 0.1; // kV central step on the pilot target

        LoadFlowParameters lfp = svcCacheEnabledParameters();
        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);

        Network network = ieee14WithZone(targetV);
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        List<SensitivityFactor> factors = new ArrayList<>();
        for (String bus : monitored) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, bus,
                    SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE, zone, false, ContingencyContext.all()));
        }
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);

        // forward closed-loop sensitivity S (kV/kV) and a per-bus re-solve central FD (kV/kV)
        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        Network nBefore = ieee14WithZone(targetV - dV);
        LoadFlow.find("OpenLoadFlow").run(nBefore, lfp);
        Network nAfter = ieee14WithZone(targetV + dV);
        LoadFlow.find("OpenLoadFlow").run(nAfter, lfp);

        List<AcSensitivityAnalysis.AdjointBlock> blocks = adjointBlocks(List.of(SensitivityFunctionType.BUS_VOLTAGE),
                List.of(monitored), SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE, List.of(zone));

        double pilotTheta = 0;
        for (String f0 : monitored) {
            // ȳ = e_{f0} -> θ̄[zone] = dV_f0 / dV_pilotTarget (closed loop), raw per-unit
            Map<String, Double> thetaBar = analysis.runAdjoint(network,
                    network.getVariantManager().getWorkingVariantId(), List.of(), blocks,
                    Map.of(AcSensitivityAnalysis.functionCotangentKey(SensitivityFunctionType.BUS_VOLTAGE, f0), 1.0));
            double theta = thetaBar.get(zone); // UNSCALED (kV/kV), the dual of get_sensitivity_matrix

            double sKv = fwd.getBusVoltageSensitivityValue(zone, f0, SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE);
            assertEquals(sKv, theta, 1e-12 * Math.abs(sKv) + 1e-13,
                    "runAdjoint vs forward closed-loop S for " + f0);

            double fdKv = (busVoltage(nAfter, f0) - busVoltage(nBefore, f0)) / (2 * dV);
            assertEquals(fdKv, theta, 1e-4 * (Math.abs(fdKv) + 1e-2),   // measured FD residual max ~1.6e-5
                    "runAdjoint vs re-solve FD for " + f0);

            if (f0.equals(pilot)) {
                pilotTheta = theta;
            }
        }
        // the closed loop makes the pilot bus voltage track its own target: a non-trivial, ≈1 gradient
        assertEquals(1.0, pilotTheta, 1e-9, "pilot bus voltage tracks its target");
    }

    @Test
    void runAdjointIsLinearInTheFunctionCotangents() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        String branchA = "L1-2-1";
        String branchB = "L1-5-1";
        double wA = 0.75;
        double wB = -1.5;

        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        List<SensitivityFactor> factors = new ArrayList<>();
        for (String g : GENS) {
            factors.add(injectionToBranchFlow(branchA, g));
            factors.add(injectionToBranchFlow(branchB, g));
        }

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);

        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        Map<String, Double> thetaBar = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                adjointBlocks(List.of(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), List.of(List.of(branchA, branchB)),
                        SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS),
                Map.of(powerKey(branchA), wA, powerKey(branchB), wB));

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));

        for (String g : GENS) {
            double sA = fwd.getBranchFlow1SensitivityValue(g, branchA, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double sB = fwd.getBranchFlow1SensitivityValue(g, branchB, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double expected = wA * sA + wB * sB;
            assertEquals(expected, thetaBar.get(g), 1e-12 * Math.abs(expected) + 1e-13,
                    "weighted Sᵀȳ for " + g);
        }
    }

    // y -> y + dY at constant ksi: scale R and X so the series admittance modulus shifts by dY; re-solve on a
    // fresh network and return [P1(variableLine), P1(crossBranch)] in MW.
    private static double[] admittancePerturbedFlows(LoadFlowParameters lfp, String variableLine, String crossBranch,
                                                     double rBase, double xBase, double yBase, double dY) {
        Network n = IeeeCdfNetworkFactory.create14();
        double scale = yBase / (yBase + dY);
        n.getLine(variableLine).setR(rBase * scale).setX(xBase * scale);
        LoadFlow.find("OpenLoadFlow").run(n, lfp);
        return new double[] {n.getBranch(variableLine).getTerminal1().getP(),
            n.getBranch(crossBranch).getTerminal1().getP()};
    }

    @Test
    void runAdjointHandlesLineAdmittanceLeverOnIeee14() {
        // TVC's line lever: BRANCH_ADMITTANCE = the series admittance modulus y = 1/hypot(R,X) at constant ksi.
        // The KEY case is a SELF-sensitivity (the monitored function is on the very branch whose admittance is
        // the variable): then AcSensitivityAnalysis.computeParameterDirectPartial contributes an explicit
        // direct term on top of the through-Jacobian term. analyseAdjoint adds yBar*computeParameterDirectPartial
        // exactly as the forward's calculateSensitivityValues adds sensi += computeParameterDirectPartial, so
        // this gate validates the adjoint direct-term handling (and its sign). A CROSS factor (function on a
        // different branch) has a zero direct term and checks the indirect term only.
        //
        // runAdjoint returns the UNSCALED sensitivity (funcBase(f)/varBase(v) applied inside analyseAdjoint),
        // i.e. the dual of get_sensitivity_matrix in physical units, so θ̄ equals the forward S and the
        // physical re-solve FD (MW per physical siemens) directly — no scaling correction.
        String variableLine = "L2-3-1"; // the controllable line -> its series admittance is the lever
        String crossBranch = "L1-5-1";  // a different monitored branch (direct term = 0)
        LoadFlowParameters lfp = cacheEnabledParameters();

        Network network = IeeeCdfNetworkFactory.create14();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        List<SensitivityFactor> factors = List.of(
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, variableLine,
                        SensitivityVariableType.BRANCH_ADMITTANCE, variableLine, false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, crossBranch,
                        SensitivityVariableType.BRANCH_ADMITTANCE, variableLine, false, ContingencyContext.all()));

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);

        String variantId = network.getVariantManager().getWorkingVariantId();
        List<AcSensitivityAnalysis.AdjointBlock> blocks = adjointBlocks(List.of(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1),
                List.of(List.of(variableLine, crossBranch)), SensitivityVariableType.BRANCH_ADMITTANCE, List.of(variableLine));
        double thetaSelf = analysis.runAdjoint(network, variantId, List.of(), blocks, Map.of(powerKey(variableLine), 1.0)).get(variableLine);
        double thetaCross = analysis.runAdjoint(network, variantId, List.of(), blocks, Map.of(powerKey(crossBranch), 1.0)).get(variableLine);

        // forward S (unscaled: physical MW per physical siemens)
        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        double sSelf = fwd.getBranchFlow1SensitivityValue(variableLine, variableLine, SensitivityVariableType.BRANCH_ADMITTANCE);
        double sCross = fwd.getBranchFlow1SensitivityValue(variableLine, crossBranch, SensitivityVariableType.BRANCH_ADMITTANCE);

        assertEquals(sSelf, thetaSelf, 1e-12 * Math.abs(sSelf) + 1e-13,
                "runAdjoint vs forward S, self (direct term)");
        assertEquals(sCross, thetaCross, 1e-12 * Math.abs(sCross) + 1e-13,
                "runAdjoint vs forward S, cross");

        // physical re-solve central finite difference on the admittance modulus
        double rBase = network.getLine(variableLine).getR();
        double xBase = network.getLine(variableLine).getX();
        double yBase = 1.0 / Math.hypot(rBase, xBase);
        double dY = 1e-4 * yBase;
        double[] pPlus = admittancePerturbedFlows(lfp, variableLine, crossBranch, rBase, xBase, yBase, dY);
        double[] pMinus = admittancePerturbedFlows(lfp, variableLine, crossBranch, rBase, xBase, yBase, -dY);
        double fdSelf = (pPlus[0] - pMinus[0]) / (2 * dY);
        double fdCross = (pPlus[1] - pMinus[1]) / (2 * dY);

        // tiny step (ε=1e-4·y) → O(ε²) truncation ~1e-8; measured residual ~3e-8
        assertEquals(fdSelf, thetaSelf, 1e-6 * (Math.abs(fdSelf) + 1e-6),
                "runAdjoint vs re-solve FD, self (direct term)");
        assertEquals(fdCross, thetaCross, 1e-6 * (Math.abs(fdCross) + 1e-6),
                "runAdjoint vs re-solve FD, cross");

        // the direct term makes the self-sensitivity substantial (and it must have the right sign to match S/FD)
        assertTrue(Math.abs(thetaSelf) > 1e-3, "self-sensitivity (with direct term) must be non-trivial, got " + thetaSelf);
    }

    @Test
    void runAdjointKeepsFunctionTypesDistinctOnSharedBranchId() {
        // A branch monitored by SEVERAL function types (here active power on side 1 AND side 2) shares one
        // functionId, so the cotangents must be keyed by (functionType, id), not the id alone — otherwise the
        // two function types' cotangents collide/sum. Distinct weights w1 != w2 expose the bug: θ̄ must be
        // w1·S[P1,g] + w2·S[P2,g], not (w1+w2)·(S[P1,g] + S[P2,g]).
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        String branch = "L1-2-1";
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        List<SensitivityFactor> factors = new ArrayList<>();
        for (String g : GENS) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch,
                    SensitivityVariableType.INJECTION_ACTIVE_POWER, g, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, branch,
                    SensitivityVariableType.INJECTION_ACTIVE_POWER, g, false, ContingencyContext.all()));
        }
        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);

        double w1 = 0.7;
        double w2 = -1.3;
        Map<String, Double> thetaBar = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                adjointBlocks(List.of(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2),
                        List.of(List.of(branch), List.of(branch)), SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS),
                Map.of(AcSensitivityAnalysis.functionCotangentKey(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch), w1,
                       AcSensitivityAnalysis.functionCotangentKey(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, branch), w2));

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        for (String g : GENS) {
            double s1 = fwd.getBranchFlow1SensitivityValue(g, branch, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double s2 = fwd.getBranchFlow2SensitivityValue(g, branch, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double expected = w1 * s1 + w2 * s2;
            assertEquals(expected, thetaBar.get(g), 1e-12 * Math.abs(expected) + 1e-13,
                    "runAdjoint must keep the two function types distinct on the shared branch id for " + g);
        }
    }

    // The blocks a caller states for one function type: the monitored functions, and the variables to
    // differentiate against. AcSensitivityAnalysis.buildAdjointFactors turns these into the minimal set —
    // the gates below call THAT method, so they validate the shipped construction rather than a copy of it.
    private static List<AcSensitivityAnalysis.AdjointBlock> adjointBlocks(List<SensitivityFunctionType> fts,
            List<List<String>> functionsPerType, SensitivityVariableType vt, List<String> variables) {
        List<AcSensitivityAnalysis.AdjointVariable> vars = variables.stream()
                .map(v -> new AcSensitivityAnalysis.AdjointVariable(v, vt, false)).toList();
        List<AcSensitivityAnalysis.AdjointBlock> blocks = new ArrayList<>();
        for (int t = 0; t < fts.size(); t++) {
            blocks.add(new AcSensitivityAnalysis.AdjointBlock(fts.get(t), functionsPerType.get(t), vars));
        }
        return blocks;
    }

    @Test
    void runAdjointMinimalFactorSetMatchesFullCrossProduct() {
        // The full adjoint would declare functions×variables factors; reverse mode only needs the O(F+V) set
        // buildAdjointFactors derives from the blocks. This gate gives that minimal set an INDEPENDENT oracle:
        // the FORWARD sensitivity matrix over the full cross product, contracted with the same cotangents —
        // θ̄(v) must equal Σ_f ȳ_f · S[f, v]. Branch admittance is the demanding case: a non-zero direct term
        // (self) AND cross sensitivities, which must come from the single adjoint solve rather than from
        // per-pair factors. Distinct cotangent weights make every cross term matter.
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityFunctionType ft = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
        SensitivityVariableType vt = SensitivityVariableType.BRANCH_ADMITTANCE;
        List<String> functions = List.of("L1-2-1", "L2-3-1", "L1-5-1", "L2-4-1", "L3-4-1");
        List<String> variables = List.of("L2-3-1", "L1-5-1"); // both monitored -> self direct term exercised
        double[] w = {0.7, -1.3, 0.4, 1.1, -0.6};
        Map<String, Double> cot = new HashMap<>();
        for (int i = 0; i < functions.size(); i++) {
            cot.put(AcSensitivityAnalysis.functionCotangentKey(ft, functions.get(i)), w[i]);
        }

        List<SensitivityFactor> full = new ArrayList<>();
        for (String v : variables) {
            for (String f : functions) {
                full.add(new SensitivityFactor(ft, f, vt, v, false, ContingencyContext.all()));
            }
        }
        List<AcSensitivityAnalysis.AdjointBlock> blocks = adjointBlocks(List.of(ft), List.of(functions), vt, variables);
        assertTrue(AcSensitivityAnalysis.buildAdjointFactors(network, blocks).size() < full.size(),
                "minimal set must be smaller than the cross product");

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();

        Map<String, Double> theta = analysis.runAdjoint(network, variantId, List.of(), blocks, cot);

        // oracle: the forward matrix over the FULL cross product, contracted with the same cotangents
        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, full,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        for (String v : variables) {
            double expected = 0;
            for (int i = 0; i < functions.size(); i++) {
                expected += w[i] * fwd.getBranchFlow1SensitivityValue(v, functions.get(i), vt);
            }
            assertEquals(expected, theta.get(v), 1e-12 * Math.abs(expected) + 1e-13,
                    "minimal O(F+V) adjoint must match the cotangent-weighted forward cross product for " + v);
        }
    }

    @Test
    void runAdjointMinimalFactorSetMatchesFullMultiFunctionType() {
        // Same gate as above with the structured multi-function-type request pypowsybl actually passes: two
        // function types over the same branches, and a variable (L2-4-1) that is NOT itself monitored — the
        // group guarantee. Oracle is again the cotangent-weighted forward cross product, per function type.
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        List<SensitivityFunctionType> fts = List.of(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                SensitivityFunctionType.BRANCH_CURRENT_1);
        List<String> branches = List.of("L1-2-1", "L2-3-1", "L1-5-1");
        List<List<String>> functionsPerType = List.of(branches, branches);
        SensitivityVariableType vt = SensitivityVariableType.BRANCH_ADMITTANCE;
        List<String> variables = List.of("L2-3-1", "L2-4-1"); // L2-3-1 monitored (self), L2-4-1 not (group-guarantee)

        Map<String, Double> cot = new HashMap<>();
        double[] wp = {0.7, -1.3, 0.4};
        double[] wi = {0.5, 0.9, -0.2};
        for (int i = 0; i < branches.size(); i++) {
            cot.put(AcSensitivityAnalysis.functionCotangentKey(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branches.get(i)), wp[i]);
            cot.put(AcSensitivityAnalysis.functionCotangentKey(SensitivityFunctionType.BRANCH_CURRENT_1, branches.get(i)), wi[i]);
        }

        List<SensitivityFactor> full = new ArrayList<>();
        for (String v : variables) {
            for (int t = 0; t < fts.size(); t++) {
                for (String f : functionsPerType.get(t)) {
                    full.add(new SensitivityFactor(fts.get(t), f, vt, v, false, ContingencyContext.all()));
                }
            }
        }
        List<AcSensitivityAnalysis.AdjointBlock> blocks = adjointBlocks(fts, functionsPerType, vt, variables);
        assertTrue(AcSensitivityAnalysis.buildAdjointFactors(network, blocks).size() < full.size(),
                "minimal set must be smaller than the cross product");

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();
        Map<String, Double> theta = analysis.runAdjoint(network, variantId, List.of(), blocks, cot);

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, full,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        for (String v : variables) {
            double expected = 0;
            for (int i = 0; i < branches.size(); i++) {
                expected += wp[i] * fwd.getBranchFlow1SensitivityValue(v, branches.get(i), vt)
                        + wi[i] * fwd.getBranchCurrent1SensitivityValue(v, branches.get(i), vt);
            }
            assertEquals(expected, theta.get(v), 1e-12 * Math.abs(expected) + 1e-13,
                    "minimal multi-type adjoint must match the cotangent-weighted forward cross product for " + v);
        }
    }

    @Test
    void runAdjointRejectsCotangentThatKeysNoBlockFunction() {
        // The mirror on the FUNCTION side of the boundary: a cotangent whose (type, id) key matches no
        // block function is silently dropped, so its term never reaches x-bar and theta-bar is wrong with
        // no error. This is the exact shape of the resolved-vs-caller id bug (a BUS_VOLTAGE cotangent keyed
        // by a bus id that no factor carried) -- here it must fail loudly instead. A block over "L1-2-1"
        // with a cotangent for the absent "L2-3-1" reproduces it.
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityFunctionType ft = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
        SensitivityVariableType vt = SensitivityVariableType.BRANCH_ADMITTANCE;
        List<AcSensitivityAnalysis.AdjointBlock> blocks =
                adjointBlocks(List.of(ft), List.of(List.of("L1-2-1")), vt, List.of("L1-2-1"));
        Map<String, Double> cot = Map.of(
                AcSensitivityAnalysis.functionCotangentKey(ft, "L1-2-1"), 1.0,   // in the block -> fine
                AcSensitivityAnalysis.functionCotangentKey(ft, "L2-3-1"), 1.0);  // in NO block -> must throw

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();

        PowsyblException e = assertThrows(PowsyblException.class,
            () -> analysis.runAdjoint(network, variantId, List.of(), blocks, cot));
        assertTrue(e.getMessage().contains("L2-3-1"), e.getMessage());
    }

    @Test
    void runAdjointGivesEveryVariableSetItsOwnGradient() {
        // Blocks may carry DIFFERENT variable sets, which is what lets one runAdjoint serve several lever
        // families at once: the cotangent is the same for all of them (it depends only on the monitored
        // functions), so N per-family calls repeat one identical transpose solve N times.
        //
        // The regression this pins: the group guarantee used to walk only the FIRST block's variables, on
        // the then-true assumption that every block shared one variable list. Every other set's variables
        // then got no θ̄ group and vanished from the returned map — and a caller rendering a missing
        // variable as 0.0 reads that as "these levers cannot help". Only the first variable of each set
        // survived (it anchors a group through the x-bar loop), which is what made it so easy to miss.
        //
        // Oracle: the same variables asked for ONE SET AT A TIME, which is the path every other gate here
        // already validates. Fused must equal per-set, variable for variable.
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityFunctionType ft = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
        List<String> functions = List.of("L1-2-1", "L2-3-1");
        // >1 variable per set, so a guarantee loop that covers only the first is caught; and the shunt set
        // is never a monitored function, so its variables can only be grouped by that loop.
        List<String> lines = List.of("L1-5-1", "L3-4-1", "L4-5-1");
        List<String> shunts = List.of("B9-SH");
        Map<String, Double> cot = Map.of(powerKey("L1-2-1"), 0.75, powerKey("L2-3-1"), -1.5);

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();

        AcSensitivityAnalysis.AdjointBlock lineBlock = new AcSensitivityAnalysis.AdjointBlock(ft, functions,
                adjointVariables(lines, SensitivityVariableType.BRANCH_ADMITTANCE));
        AcSensitivityAnalysis.AdjointBlock shuntBlock = new AcSensitivityAnalysis.AdjointBlock(ft, functions,
                adjointVariables(shunts, SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE));

        Map<String, Double> lineOnly = analysis.runAdjoint(network, variantId, List.of(),
                adjointBlocks(List.of(ft), List.of(functions), SensitivityVariableType.BRANCH_ADMITTANCE, lines), cot);
        Map<String, Double> shuntOnly = analysis.runAdjoint(network, variantId, List.of(),
                adjointBlocks(List.of(ft), List.of(functions), SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE, shunts), cot);

        // BOTH orders: whichever set comes second is the one a first-block-only guarantee loop drops, so
        // testing one order would leave the other half of the defect uncovered.
        for (List<AcSensitivityAnalysis.AdjointBlock> fused : List.of(List.of(shuntBlock, lineBlock),
                                                                     List.of(lineBlock, shuntBlock))) {
            Map<String, Double> theta = analysis.runAdjoint(network, variantId, List.of(), fused, cot);
            for (Map<String, Double> perSet : List.of(lineOnly, shuntOnly)) {
                for (Map.Entry<String, Double> e : perSet.entrySet()) {
                    assertTrue(theta.containsKey(e.getKey()),
                            "fused runAdjoint dropped variable " + e.getKey() + "; θ̄ held " + theta.keySet());
                    assertEquals(e.getValue(), theta.get(e.getKey()), 1e-12 * Math.abs(e.getValue()) + 1e-13,
                            "fused runAdjoint must match the per-variable-set answer for " + e.getKey());
                }
            }
            // and the comparison must not be vacuous: the variables a broken guarantee loop drops are the
            // non-first ones, so at least one of those has to be genuinely non-zero.
            assertTrue(Math.abs(theta.get(lines.get(2))) > 1e-6,
                    "expected a non-trivial θ̄ on a non-first variable, got " + theta.get(lines.get(2)));
        }
    }

    private static List<AcSensitivityAnalysis.AdjointVariable> adjointVariables(List<String> ids, SensitivityVariableType vt) {
        return ids.stream().map(v -> new AcSensitivityAnalysis.AdjointVariable(v, vt, false)).toList();
    }

    @Test
    void buildAdjointFactorsRejectsEmptyDeclarations() {
        // An empty declaration has no meaningful θ̄, and the failure must NAME what is empty: before this
        // guard each case died on an IndexOutOfBoundsException from inside buildAdjointFactors, and an
        // empty θ̄ returned quietly would have read as "none of your levers can help".
        Network network = IeeeCdfNetworkFactory.create14();
        SensitivityFunctionType ft = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
        SensitivityVariableType vt = SensitivityVariableType.BRANCH_ADMITTANCE;
        List<String> functions = List.of("L1-2-1");
        List<String> variables = List.of("L2-3-1");

        PowsyblException noBlocks = assertThrows(PowsyblException.class,
            () -> AcSensitivityAnalysis.buildAdjointFactors(network, List.of()));
        assertTrue(noBlocks.getMessage().contains("at least one AdjointBlock"), noBlocks.getMessage());

        PowsyblException noFunctions = assertThrows(PowsyblException.class,
            () -> AcSensitivityAnalysis.buildAdjointFactors(network,
                    adjointBlocks(List.of(ft), List.of(List.of()), vt, variables)));
        assertTrue(noFunctions.getMessage().contains("no monitored function"), noFunctions.getMessage());

        PowsyblException noVariables = assertThrows(PowsyblException.class,
            () -> AcSensitivityAnalysis.buildAdjointFactors(network,
                    adjointBlocks(List.of(ft), List.of(functions), vt, List.of())));
        assertTrue(noVariables.getMessage().contains("no variable"), noVariables.getMessage());
    }
}
