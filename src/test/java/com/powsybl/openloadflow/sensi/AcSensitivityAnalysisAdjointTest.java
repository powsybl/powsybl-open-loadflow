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
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static AcSensitivityAnalysis.FunctionRef powerKey(String branchId) {
        return new AcSensitivityAnalysis.FunctionRef(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branchId);
    }

    // gradient-map key: a variable is the (type, id) pair, never the id alone
    private static AcSensitivityAnalysis.VariableRef varKey(SensitivityVariableType variableType, String variableId) {
        return new AcSensitivityAnalysis.VariableRef(variableType, variableId);
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
        Map<AcSensitivityAnalysis.VariableRef, Double> thetaBar = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                Map.of(new AcSensitivityAnalysis.FunctionRef(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch), 1.0),
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS));

        // forward sensitivity matrix S (its own no-cache load flow; unscaled == raw here)
        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));

        double maxAbs = 0;
        for (String g : GENS) {
            double s = fwd.getBranchFlow1SensitivityValue(g, branch, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double fd = dBranchFlowPerGenFd(network, lfp, branch, g); // last: mutates+restores the cache
            double theta = thetaBar.get(varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, g));
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
        Map<AcSensitivityAnalysis.VariableRef, Double> thetaBar = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                Map.of(new AcSensitivityAnalysis.FunctionRef(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch), 1.0),
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS));

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));

        double maxAbs = 0;
        for (String g : GENS) {
            double s = fwd.getBranchFlow1SensitivityValue(g, branch, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double theta = thetaBar.get(varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, g));
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
        // the zone name). runAdjoint reuses describeSvcPilotFactorsRhs (the closed-loop coordination) on the
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

        List<AcSensitivityAnalysis.AdjointVariable> zoneLever =
                levers(SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE, List.of(zone));

        // every monitored bus is declared with weight 0; the loop re-weights one of them to 1 at a time
        Map<AcSensitivityAnalysis.FunctionRef, Double> declaredBuses =
                declared(List.of(SensitivityFunctionType.BUS_VOLTAGE), List.of(monitored));
        double pilotTheta = 0;
        for (String f0 : monitored) {
            // ȳ = e_{f0} -> θ̄[zone] = dV_f0 / dV_pilotTarget (closed loop), raw per-unit
            Map<AcSensitivityAnalysis.VariableRef, Double> thetaBar = analysis.runAdjoint(network,
                    network.getVariantManager().getWorkingVariantId(), List.of(), weight(declaredBuses, SensitivityFunctionType.BUS_VOLTAGE, f0, 1.0), zoneLever);
            double theta = thetaBar.get(varKey(SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE, zone)); // UNSCALED (kV/kV)

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
        Map<AcSensitivityAnalysis.VariableRef, Double> thetaBar = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                Map.of(powerKey(branchA), wA, powerKey(branchB), wB),
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS));

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));

        for (String g : GENS) {
            double sA = fwd.getBranchFlow1SensitivityValue(g, branchA, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double sB = fwd.getBranchFlow1SensitivityValue(g, branchB, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double expected = wA * sA + wB * sB;
            assertEquals(expected, thetaBar.get(varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, g)), 1e-12 * Math.abs(expected) + 1e-13,
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
        List<AcSensitivityAnalysis.AdjointVariable> lineLever =
                levers(SensitivityVariableType.BRANCH_ADMITTANCE, List.of(variableLine));
        AcSensitivityAnalysis.VariableRef lineKey = varKey(SensitivityVariableType.BRANCH_ADMITTANCE, variableLine);
        double thetaSelf = analysis.runAdjoint(network, variantId, List.of(), Map.of(powerKey(variableLine), 1.0, powerKey(crossBranch), 0.0), lineLever).get(lineKey);
        double thetaCross = analysis.runAdjoint(network, variantId, List.of(), Map.of(powerKey(crossBranch), 1.0, powerKey(variableLine), 0.0), lineLever).get(lineKey);

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
        Map<AcSensitivityAnalysis.VariableRef, Double> thetaBar = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                Map.of(new AcSensitivityAnalysis.FunctionRef(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch), w1,
                       new AcSensitivityAnalysis.FunctionRef(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, branch), w2),
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS));

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, factors,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        for (String g : GENS) {
            double s1 = fwd.getBranchFlow1SensitivityValue(g, branch, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double s2 = fwd.getBranchFlow2SensitivityValue(g, branch, SensitivityVariableType.INJECTION_ACTIVE_POWER);
            double expected = w1 * s1 + w2 * s2;
            assertEquals(expected, thetaBar.get(varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, g)), 1e-12 * Math.abs(expected) + 1e-13,
                    "runAdjoint must keep the two function types distinct on the shared branch id for " + g);
        }
    }

    // The blocks a caller states for one function type: the monitored functions, and the variables to
    // differentiate against. AcSensitivityAnalysis.buildAdjointFactors turns these into the minimal set —
    // the gates below call THAT method, so they validate the shipped construction rather than a copy of it.
    /** the declared buses, with {@code f0} weighted 1 and every other left at its declared 0 */
    private static Map<AcSensitivityAnalysis.FunctionRef, Double> weight(
            Map<AcSensitivityAnalysis.FunctionRef, Double> declaredFunctions,
            SensitivityFunctionType type, String id, double w) {
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = new LinkedHashMap<>(declaredFunctions);
        cot.put(new AcSensitivityAnalysis.FunctionRef(type, id), w);
        return cot;
    }

    private static List<AcSensitivityAnalysis.AdjointVariable> concat(List<AcSensitivityAnalysis.AdjointVariable> a,
                                                                      List<AcSensitivityAnalysis.AdjointVariable> b) {
        return Stream.concat(a.stream(), b.stream()).toList();
    }

    private static List<AcSensitivityAnalysis.AdjointVariable> levers(SensitivityVariableType vt, List<String> ids) {
        return ids.stream().map(v -> AcSensitivityAnalysis.AdjointVariable.of(vt, v)).toList();
    }

    private static AcSensitivityAnalysis newAnalysis(LoadFlowParameters lfp) {
        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        return new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
    }

    /**
     * A lever outside the main connected component reads 0, and is PRESENT.
     *
     * <p>The forward path already decided this: {@code calculateSensitivityValues} writes 0 for every
     * VALID_ONLY_FOR_FUNCTION factor, with the comment that the sensitivity is known to value 0 — a variable
     * outside the component moves nothing, so zero is not a fallback, it is the answer. The adjoint used to
     * keep only VALID factors and drop such a lever from the returned map entirely, which a caller reading a
     * missing lever as zero could not tell apart from a real zero, and which said "this lever cannot help"
     * by accident rather than on purpose.</p>
     */
    @Test
    void runAdjointAnswersZeroForALeverOutsideTheComponent() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getGenerator("B6-G").getTerminal().disconnect();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        Map<AcSensitivityAnalysis.VariableRef, Double> theta = newAnalysis(lfp).runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(),
                Map.of(powerKey("L1-2-1"), 0.75),
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("B2-G", "B6-G")));

        AcSensitivityAnalysis.VariableRef live = varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, "B2-G");
        AcSensitivityAnalysis.VariableRef gone = varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, "B6-G");
        assertTrue(theta.containsKey(gone), "the disconnected lever vanished; θ̄ held " + theta.keySet());
        assertEquals(0.0, theta.get(gone), 0, "a lever outside the component reads zero");
        assertTrue(Math.abs(theta.get(live)) > 0.1,
                "the connected lever is trivially zero, so presence proves nothing: " + theta.get(live));
    }

    /**
     * A lever that cannot be differentiated AT ALL throws, naming it.
     *
     * <p>When neither the lever nor any monitored function resolves, the forward writes NaN and records the
     * id in a separate skipped-variable list. The adjoint has no second channel — it returns one number per
     * lever — and a NaN loose in a gradient poisons an optimiser's step with no clue where it came from. So
     * it says the same thing where it can be acted on. This is the one case the adjoint deliberately does
     * NOT mirror.</p>
     */
    @Test
    void runAdjointRefusesALeverItCannotDifferentiateAtAll() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getLine("L4-5-1").getTerminal1().disconnect();
        network.getLine("L4-5-1").getTerminal2().disconnect();
        network.getGenerator("B6-G").getTerminal().disconnect();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        AcSensitivityAnalysis analysis = newAnalysis(lfp);
        String variantId = network.getVariantManager().getWorkingVariantId();
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = Map.of(powerKey("L4-5-1"), 1.0);
        List<AcSensitivityAnalysis.AdjointVariable> vars =
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("B6-G"));

        PowsyblException e = assertThrows(PowsyblException.class,
            () -> analysis.runAdjoint(network, variantId, List.of(), cot, vars));
        assertTrue(e.getMessage().contains("B6-G"), e.getMessage());
    }

    /**
     * One unresolvable monitored function must not strip a perfectly good lever of its gradient.
     *
     * <p>A θ̄ group needs the VARIABLE's element and nothing from the function, yet groups used to be built
     * from VALID factors alone, so a lever anchored on a function outside the component lost its group and
     * its answer. With the anchor chosen by a total order that became systematic rather than occasional: one
     * disconnected branch sorting first would silence every lever in the request.</p>
     *
     * <p>Here L1-5-1 is disconnected and sorts before the live L2-3-1, so it anchors. The first lever is
     * safe either way, since the x̄ pairs already reach it; the SECOND is the one that used to disappear.
     * Oracle: the same request without the disconnected function declared at all.</p>
     */
    @Test
    void runAdjointSurvivesAnUnresolvableAnchorFunction() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getLine("L1-5-1").getTerminal1().disconnect();
        network.getLine("L1-5-1").getTerminal2().disconnect();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        AcSensitivityAnalysis analysis = newAnalysis(lfp);
        String variantId = network.getVariantManager().getWorkingVariantId();
        List<AcSensitivityAnalysis.AdjointVariable> vars =
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("B2-G", "B3-G"));

        Map<AcSensitivityAnalysis.FunctionRef, Double> liveOnly = Map.of(powerKey("L2-3-1"), 0.75);
        Map<AcSensitivityAnalysis.FunctionRef, Double> withDeadAnchor = new LinkedHashMap<>(liveOnly);
        withDeadAnchor.put(powerKey("L1-5-1"), 0.0); // sorts first, so it anchors every lever's group

        Map<AcSensitivityAnalysis.VariableRef, Double> expected =
                analysis.runAdjoint(network, variantId, List.of(), liveOnly, vars);
        Map<AcSensitivityAnalysis.VariableRef, Double> actual =
                analysis.runAdjoint(network, variantId, List.of(), withDeadAnchor, vars);

        assertEquals(expected.keySet(), actual.keySet(), "a dead anchor changed the lever set");
        double second = Math.abs(expected.get(varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, "B3-G")));
        assertTrue(second > 0.1, "the second lever is trivially zero, so this proves nothing: " + second);
        for (var e : expected.entrySet()) {
            assertEquals(e.getValue(), actual.get(e.getKey()), 1e-12 * Math.abs(e.getValue()) + 1e-13,
                    "a dead anchor moved the gradient of " + e.getKey());
        }
    }

    /** The emitted factors as a comparable set, since SensitivityFactor has no value equality. */
    private static Set<String> factorKeys(List<SensitivityFactor> factors) {
        return factors.stream()
                .map(f -> f.getFunctionType() + "|" + f.getFunctionId() + "|"
                        + f.getVariableType() + "|" + f.getVariableId())
                .collect(Collectors.toSet());
    }

    /**
     * The factor set must not depend on the iteration order of the caller's cotangent map.
     *
     * <p>Any monitored function can anchor a lever's θ̄ group, so the choice looks free. It is not: the
     * anchor's own validity decides the lever's fate, because a function outside the main connected
     * component yields a dropped factor, no group, and a lever missing from the answer, where another
     * function would have anchored it fine. Picking whichever function the map happened to yield first tied
     * that to hash order — and {@code Map.of} salts its iteration per JVM run, so one request could answer
     * with different levers on different runs of the same program. Reproducibility is the floor here.</p>
     *
     * <p>The levers are injections rather than branch parameters on purpose: a branch parameter pairs with
     * its direct-term carriers anyway, which would mask the anchor. Only the first lever is covered by the
     * x̄ pairs, so the rest expose the anchor directly.</p>
     */
    @Test
    void buildAdjointFactorsDoesNotDependOnTheCotangentMapOrder() {
        Network network = IeeeCdfNetworkFactory.create14();
        List<String> branches = List.of("L1-2-1", "L2-3-1", "L1-5-1");
        List<AcSensitivityAnalysis.AdjointVariable> variables =
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS);

        List<AcSensitivityAnalysis.FunctionRef> forward = branches.stream().map(AcSensitivityAnalysisAdjointTest::powerKey).toList();
        List<AcSensitivityAnalysis.FunctionRef> backward = new ArrayList<>(forward);
        Collections.reverse(backward);

        Set<String> fromForward = factorKeys(
                AcSensitivityAnalysis.buildAdjointFactors(network, new LinkedHashSet<>(forward), variables));
        Set<String> fromBackward = factorKeys(
                AcSensitivityAnalysis.buildAdjointFactors(network, new LinkedHashSet<>(backward), variables));

        assertTrue(fromForward.size() > branches.size(),
                "the levers must add factors beyond the x̄ pairs, or the anchor is not exercised: " + fromForward);
        assertEquals(fromForward, fromBackward,
                "the emitted factor set changed with the order of the cotangent map");
    }

    /**
     * The SPI-level entry point, {@link OpenSensitivityAnalysisProvider#runAdjoint}, which is what a caller
     * outside OpenLoadFlow actually holds.
     *
     * <p>Two things live only there and are reachable no other way. It builds the analysis from the
     * provider's own matrix and connectivity factories, so a delegation that dropped an argument or
     * reordered the two maps would be invisible to every gate above; and it rejects a DC request, since the
     * adjoint is AC-only — the sensitivity matrix it transposes does not exist in the DC path. The
     * signature carries a cotangent map and a lever list of different generic types, exactly the pair most
     * easily transposed by mistake.</p>
     */
    @Test
    void providerRunAdjointDelegatesAndRejectsDc() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        String variantId = network.getVariantManager().getWorkingVariantId();
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = Map.of(powerKey("L1-2-1"), 0.75);
        List<AcSensitivityAnalysis.AdjointVariable> variables =
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS);

        Map<AcSensitivityAnalysis.VariableRef, Double> direct = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams)
                .runAdjoint(network, variantId, List.of(), cot, variables);
        Map<AcSensitivityAnalysis.VariableRef, Double> viaProvider =
                new OpenSensitivityAnalysisProvider(new SparseMatrixFactory(),
                        new EvenShiloachGraphDecrementalConnectivityFactory<>())
                        .runAdjoint(network, variantId, cot, variables, List.of(), sensiParams);

        assertEquals(direct.keySet(), viaProvider.keySet(), "the provider returned a different lever set");
        double maxAbs = 0;
        for (var e : direct.entrySet()) {
            assertEquals(e.getValue(), viaProvider.get(e.getKey()), 0,
                    "the provider must delegate unchanged, for " + e.getKey());
            maxAbs = Math.max(maxAbs, Math.abs(e.getValue()));
        }
        assertTrue(maxAbs > 0.1, "the gradients are trivially zero, so delegation proves nothing: " + maxAbs);

        SensitivityAnalysisParameters dcParams = new SensitivityAnalysisParameters();
        dcParams.setLoadFlowParameters(new LoadFlowParameters().setDc(true));
        OpenSensitivityAnalysisProvider provider = new OpenSensitivityAnalysisProvider(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>());
        PowsyblException dc = assertThrows(PowsyblException.class,
            () -> provider.runAdjoint(network, variantId, cot, variables, List.of(), dcParams));
        assertTrue(dc.getMessage().contains("only supported in AC"), dc.getMessage());
    }

    /**
     * A GLSK lever, declared through {@link AcSensitivityAnalysis.AdjointVariable#ofVariableSet}.
     *
     * <p>This is the only request that reaches a {@code MultiVariablesFactorGroup}, and so the only one
     * that produces a genuinely DENSE RHS column: a GLSK spreads its injection over every weighted bus,
     * where every other lever gated here moves one residual row or four. The column description carries
     * that case as {@code Entries} rather than {@code OneHot} and the adjoint contracts it over its
     * non-zeros, a path no other gate in this class exercises.</p>
     *
     * <p>It is also the only thing a {@link AcSensitivityAnalysis.VariableRef} cannot express on its own:
     * a GLSK is typed INJECTION_ACTIVE_POWER exactly like the injection levers beside it, so the
     * variable-set flag is what separates a zone id from a network element id. Declaring it wrong yields
     * no error, just a lever resolved against the wrong thing.</p>
     *
     * <p>Oracle: the forward sensitivity for the same GLSK over the same monitored branches, contracted
     * with the same cotangents.</p>
     */
    @Test
    void runAdjointHandlesAGlskLever() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        String glsk = "glsk";
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet(glsk,
                List.of(new WeightedSensitivityVariable("B2-G", 0.25f),
                        new WeightedSensitivityVariable("B3-G", 0.25f),
                        new WeightedSensitivityVariable("B6-G", 0.5f))));
        List<String> branches = List.of("L1-2-1", "L2-3-1");
        double[] w = {0.75, -1.5};
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = new LinkedHashMap<>();
        for (int i = 0; i < branches.size(); i++) {
            cot.put(powerKey(branches.get(i)), w[i]);
        }

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        Map<AcSensitivityAnalysis.VariableRef, Double> theta = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), variableSets, cot,
                List.of(AcSensitivityAnalysis.AdjointVariable.ofVariableSet(
                        SensitivityVariableType.INJECTION_ACTIVE_POWER, glsk)));

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network,
                branches.stream().map(b -> new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, b,
                        SensitivityVariableType.INJECTION_ACTIVE_POWER, glsk, true, ContingencyContext.all())).toList(),
                new SensitivityAnalysisRunParameters().setVariableSets(variableSets).setParameters(sensiParams));

        double expected = 0;
        for (int i = 0; i < branches.size(); i++) {
            expected += w[i] * fwd.getBranchFlow1SensitivityValue(glsk, branches.get(i),
                    SensitivityVariableType.INJECTION_ACTIVE_POWER);
        }
        assertTrue(Math.abs(expected) > 1e-3, "the oracle is trivially zero: " + expected);
        AcSensitivityAnalysis.VariableRef glskRef =
                varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, glsk);
        assertTrue(theta.containsKey(glskRef), "the GLSK lever is missing from θ̄; it held " + theta.keySet());
        assertEquals(expected, theta.get(glskRef), 1e-12 * Math.abs(expected) + 1e-13,
                "a GLSK lever must contract its dense RHS column to the forward answer");
    }

    /**
     * A cotangent of zero DECLARES its function without weighting it.
     *
     * <p>The cotangent map is the whole declaration of what is monitored, so the value and the membership
     * carry different meanings: the key says "this function is part of the request", the value says how
     * much of it reaches the loss. A caller sweeping weights, or zeroing one objective term, must be able
     * to set a weight to zero without the function silently leaving the request — and an all-zero map is a
     * legitimate request whose answer is a gradient of zeros, not an error and not an empty map. Treating
     * a zero value as an absent key would make the second case throw "needs at least one monitored
     * function" on a request that names one.</p>
     */
    @Test
    void runAdjointTreatsAZeroCotangentAsADeclaration() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();
        List<AcSensitivityAnalysis.AdjointVariable> variables =
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, GENS);

        // a request that names one function and weights it zero: valid, and every lever still answers
        Map<AcSensitivityAnalysis.VariableRef, Double> allZero = analysis.runAdjoint(network, variantId,
                List.of(), Map.of(powerKey("L1-2-1"), 0.0), variables);
        for (String g : GENS) {
            AcSensitivityAnalysis.VariableRef ref =
                    varKey(SensitivityVariableType.INJECTION_ACTIVE_POWER, g);
            assertTrue(allZero.containsKey(ref),
                    "an unweighted request still declares its levers; θ̄ held " + allZero.keySet());
            assertEquals(0.0, allZero.get(ref), 1e-13, "an unweighted request has a zero gradient for " + g);
        }

        // and adding an unweighted function to a weighted request leaves every gradient where it was
        Map<AcSensitivityAnalysis.FunctionRef, Double> weighted = Map.of(powerKey("L1-2-1"), 0.75);
        Map<AcSensitivityAnalysis.FunctionRef, Double> weightedPlusZero = new LinkedHashMap<>(weighted);
        weightedPlusZero.put(powerKey("L2-3-1"), 0.0);
        Map<AcSensitivityAnalysis.VariableRef, Double> before =
                analysis.runAdjoint(network, variantId, List.of(), weighted, variables);
        Map<AcSensitivityAnalysis.VariableRef, Double> after =
                analysis.runAdjoint(network, variantId, List.of(), weightedPlusZero, variables);
        assertEquals(before.keySet(), after.keySet(), "declaring an unweighted function changed the lever set");
        double maxAbs = 0;
        for (var e : before.entrySet()) {
            assertEquals(e.getValue(), after.get(e.getKey()), 1e-12 * Math.abs(e.getValue()) + 1e-13,
                    "declaring an unweighted function moved the gradient of " + e.getKey());
            maxAbs = Math.max(maxAbs, Math.abs(e.getValue()));
        }
        assertTrue(maxAbs > 0.1, "the weighted request is trivially zero, so this proves nothing: " + maxAbs);
    }

    /**
     * The monitored functions a caller declares, all weighted 0 — a zero entry still DECLARES its function,
     * so a test can state the whole monitored set and then override only the weights it cares about.
     */
    private static Map<AcSensitivityAnalysis.FunctionRef, Double> declared(List<SensitivityFunctionType> fts,
                                                                           List<List<String>> functionsPerType) {
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = new LinkedHashMap<>();
        for (int t = 0; t < fts.size(); t++) {
            for (String f : functionsPerType.get(t)) {
                cot.put(new AcSensitivityAnalysis.FunctionRef(fts.get(t), f), 0.0);
            }
        }
        return cot;
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
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = new HashMap<>();
        for (int i = 0; i < functions.size(); i++) {
            cot.put(new AcSensitivityAnalysis.FunctionRef(ft, functions.get(i)), w[i]);
        }

        List<SensitivityFactor> full = new ArrayList<>();
        for (String v : variables) {
            for (String f : functions) {
                full.add(new SensitivityFactor(ft, f, vt, v, false, ContingencyContext.all()));
            }
        }
        List<AcSensitivityAnalysis.AdjointVariable> vars = levers(vt, variables);
        assertTrue(AcSensitivityAnalysis.buildAdjointFactors(network, cot.keySet(), vars).size() < full.size(),
                "minimal set must be smaller than the cross product");

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();

        Map<AcSensitivityAnalysis.VariableRef, Double> theta = analysis.runAdjoint(network, variantId, List.of(), cot, vars);

        // oracle: the forward matrix over the FULL cross product, contracted with the same cotangents
        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, full,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        for (String v : variables) {
            double expected = 0;
            for (int i = 0; i < functions.size(); i++) {
                expected += w[i] * fwd.getBranchFlow1SensitivityValue(v, functions.get(i), vt);
            }
            assertEquals(expected, theta.get(varKey(vt, v)), 1e-12 * Math.abs(expected) + 1e-13,
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

        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = new HashMap<>();
        double[] wp = {0.7, -1.3, 0.4};
        double[] wi = {0.5, 0.9, -0.2};
        for (int i = 0; i < branches.size(); i++) {
            cot.put(new AcSensitivityAnalysis.FunctionRef(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branches.get(i)), wp[i]);
            cot.put(new AcSensitivityAnalysis.FunctionRef(SensitivityFunctionType.BRANCH_CURRENT_1, branches.get(i)), wi[i]);
        }

        List<SensitivityFactor> full = new ArrayList<>();
        for (String v : variables) {
            for (int t = 0; t < fts.size(); t++) {
                for (String f : functionsPerType.get(t)) {
                    full.add(new SensitivityFactor(fts.get(t), f, vt, v, false, ContingencyContext.all()));
                }
            }
        }
        List<AcSensitivityAnalysis.AdjointVariable> vars = levers(vt, variables);
        assertTrue(AcSensitivityAnalysis.buildAdjointFactors(network, cot.keySet(), vars).size() < full.size(),
                "minimal set must be smaller than the cross product");

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();
        Map<AcSensitivityAnalysis.VariableRef, Double> theta = analysis.runAdjoint(network, variantId, List.of(), cot, vars);

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, full,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        for (String v : variables) {
            double expected = 0;
            for (int i = 0; i < branches.size(); i++) {
                expected += wp[i] * fwd.getBranchFlow1SensitivityValue(v, branches.get(i), vt)
                        + wi[i] * fwd.getBranchCurrent1SensitivityValue(v, branches.get(i), vt);
            }
            assertEquals(expected, theta.get(varKey(vt, v)), 1e-12 * Math.abs(expected) + 1e-13,
                    "minimal multi-type adjoint must match the cotangent-weighted forward cross product for " + v);
        }
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
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = Map.of(powerKey("L1-2-1"), 0.75, powerKey("L2-3-1"), -1.5);

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();

        List<AcSensitivityAnalysis.AdjointVariable> lineLevers = levers(SensitivityVariableType.BRANCH_ADMITTANCE, lines);
        List<AcSensitivityAnalysis.AdjointVariable> shuntLevers =
                levers(SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE, shunts);

        Map<AcSensitivityAnalysis.VariableRef, Double> lineOnly =
                analysis.runAdjoint(network, variantId, List.of(), cot, lineLevers);
        Map<AcSensitivityAnalysis.VariableRef, Double> shuntOnly =
                analysis.runAdjoint(network, variantId, List.of(), cot, shuntLevers);

        // BOTH orders: whichever family comes second is the one a first-family-only pass would drop, so
        // testing one order would leave the other half of the defect uncovered.
        for (List<AcSensitivityAnalysis.AdjointVariable> fused : List.of(concat(shuntLevers, lineLevers),
                                                                        concat(lineLevers, shuntLevers))) {
            Map<AcSensitivityAnalysis.VariableRef, Double> theta = analysis.runAdjoint(network, variantId, List.of(), cot, fused);
            for (Map<AcSensitivityAnalysis.VariableRef, Double> perSet : List.of(lineOnly, shuntOnly)) {
                for (Map.Entry<AcSensitivityAnalysis.VariableRef, Double> e : perSet.entrySet()) {
                    assertTrue(theta.containsKey(e.getKey()),
                            "fused runAdjoint dropped variable " + e.getKey() + "; θ̄ held " + theta.keySet());
                    assertEquals(e.getValue(), theta.get(e.getKey()), 1e-12 * Math.abs(e.getValue()) + 1e-13,
                            "fused runAdjoint must match the per-variable-set answer for " + e.getKey());
                }
            }
            // and the comparison must not be vacuous: the variables a broken guarantee loop drops are the
            // non-first ones, so at least one of those has to be genuinely non-zero.
            AcSensitivityAnalysis.VariableRef lastLineKey = varKey(SensitivityVariableType.BRANCH_ADMITTANCE, lines.get(2));
            assertTrue(Math.abs(theta.get(lastLineKey)) > 1e-6,
                    "expected a non-trivial θ̄ on a non-first variable, got " + theta.get(lastLineKey));
        }
    }

    /**
     * A variable owes its direct term {@code ∂f/∂p} to EVERY monitored function of the call, including the
     * functions of a block that does not declare it.
     *
     * <p>x̄ and λ are shared by the whole call, so θ̄ for a lever sums over every declared function, whatever
     * block it sits in. The factor set has to follow: a pair is the only thing that can carry a direct term,
     * and the builder used to look for carriers only among the functions of the block that declared the
     * variable. A line admittance declared beside branch flows therefore lost its term with a bus reactive
     * injection declared in the next block, and the loss was silent — the lever still had a group, still had
     * an RHS column, and still returned a plausible number.</p>
     *
     * <p>Here the line is incident to the monitored generator bus, so the missing term is a real one, and
     * the line is declared ONLY in the branch-flow block. The oracle is the cotangent-weighted forward cross product over
     * BOTH function types. The bus term is asserted to be a material share of the total first, so that a
     * build which drops it cannot pass on rounding.</p>
     */
    @Test
    void runAdjointCarriesADirectTermFromAnotherBlocksFunction() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityFunctionType flowType = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
        SensitivityFunctionType busQType = SensitivityFunctionType.BUS_REACTIVE_POWER;
        SensitivityVariableType vtLine = SensitivityVariableType.BRANCH_ADMITTANCE;
        String flowFunction = "L1-2-1";
        // A GENERATOR bus, whose reactive injection is free to move: at a PQ bus the injection is held
        // constant, the through-state and direct terms cancel exactly, and the whole term would be zero.
        String busFunction = "B2";
        String line = "L2-4-1";          // incident to that bus, and declared ONLY in the branch-flow block
        double wFlow = 0.75;
        double wBusQ = -1.5;

        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = Map.of(
                new AcSensitivityAnalysis.FunctionRef(flowType, flowFunction), wFlow,
                new AcSensitivityAnalysis.FunctionRef(busQType, busFunction), wBusQ);
        List<AcSensitivityAnalysis.AdjointVariable> vars = concat(levers(vtLine, List.of(line)),
                levers(SensitivityVariableType.BUS_TARGET_VOLTAGE, List.of("B3-G")));

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        Map<AcSensitivityAnalysis.VariableRef, Double> theta = analysis.runAdjoint(network,
                network.getVariantManager().getWorkingVariantId(), List.of(), cot, vars);

        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, List.of(
                new SensitivityFactor(flowType, flowFunction, vtLine, line, false, ContingencyContext.all()),
                new SensitivityFactor(busQType, busFunction, vtLine, line, false, ContingencyContext.all())),
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));
        double flowTerm = wFlow * fwd.getSensitivityValue(line, flowFunction, flowType, vtLine);
        double busQTerm = wBusQ * fwd.getSensitivityValue(line, busFunction, busQType, vtLine);
        double expected = flowTerm + busQTerm;

        assertTrue(Math.abs(busQTerm) > 1e-2 * Math.abs(flowTerm),
                "the other block's term is negligible next to the one that survives, so dropping it would "
                        + "pass: " + busQTerm + " vs " + flowTerm);
        assertEquals(expected, theta.get(varKey(vtLine, line)), 1e-9 * Math.abs(expected) + 1e-10,
                "θ̄ must carry the direct term of a function declared in another block");
    }

    /**
     * A differentiation variable is the (variableType, variableId) PAIR, so one element declared under two
     * types in a single call is two levers with two gradients.
     *
     * <p>The regression this pins, found from a pypowsybl session: variable identity was the id STRING
     * alone, on both sides of the build. The dedup key of the emitted factors was
     * (functionType, functionId, variableId), so the second type's factors collided with the first type's
     * and were deduplicated away — that type got no factor, hence no group from
     * {@code createFactorGroups}, hence no gradient; and the returned map was keyed by the id alone, so
     * had a group survived, the second value would have overwritten the first. Either way one type was
     * silently gone, or reported under the other's value. The function side already knew a monitored
     * function is a (type, id) pair ({@code FunctionRef}); the variable side did not.</p>
     *
     * <p>The element chosen is itself a monitored function, so the v0 factors, the self-pair carrying the
     * direct term, and the group guarantee all collide under the old key, not just one of them. The
     * oracle is the same lever asked for ONE TYPE AT A TIME. Both block orders are exercised because the
     * dedup is order-dependent: whichever type comes second is the one that used to disappear. The two
     * answers are asserted genuinely DIFFERENT first, since a test comparing two equal numbers would pass
     * just as well when one is reported under the other's key.</p>
     */
    @Test
    void runAdjointKeepsVariableTypesDistinctOnSharedElementId() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityFunctionType ft = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
        List<String> functions = List.of("L1-2-1", "L2-3-1");
        String line = "L2-3-1"; // monitored too, so the self-pair / direct term collides as well
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = Map.of(powerKey("L1-2-1"), 0.75, powerKey("L2-3-1"), -1.5);

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();

        List<AcSensitivityAnalysis.AdjointVariable> admittanceLever =
                levers(SensitivityVariableType.BRANCH_ADMITTANCE, List.of(line));
        List<AcSensitivityAnalysis.AdjointVariable> reactanceLever =
                levers(SensitivityVariableType.BRANCH_REACTANCE, List.of(line));
        AcSensitivityAnalysis.VariableRef admittanceKey = varKey(SensitivityVariableType.BRANCH_ADMITTANCE, line);
        AcSensitivityAnalysis.VariableRef reactanceKey = varKey(SensitivityVariableType.BRANCH_REACTANCE, line);

        double admittanceAlone = analysis.runAdjoint(network, variantId, List.of(),
                cot, admittanceLever).get(admittanceKey);
        double reactanceAlone = analysis.runAdjoint(network, variantId, List.of(),
                cot, reactanceLever).get(reactanceKey);

        assertTrue(Math.abs(admittanceAlone) > 1e-6, "the admittance lever is trivially zero: " + admittanceAlone);
        assertTrue(Math.abs(reactanceAlone) > 1e-6, "the reactance lever is trivially zero: " + reactanceAlone);
        assertTrue(Math.abs(admittanceAlone - reactanceAlone) > 1e-3 * Math.abs(admittanceAlone),
                "the two types answer the same number, so this gate could not tell them apart: "
                        + admittanceAlone + " vs " + reactanceAlone);

        for (List<AcSensitivityAnalysis.AdjointVariable> fused : List.of(concat(admittanceLever, reactanceLever),
                                                                         concat(reactanceLever, admittanceLever))) {
            Map<AcSensitivityAnalysis.VariableRef, Double> theta = analysis.runAdjoint(network, variantId, List.of(), cot, fused);
            assertTrue(theta.containsKey(admittanceKey),
                    "fused runAdjoint dropped the admittance lever; θ̄ held " + theta.keySet());
            assertTrue(theta.containsKey(reactanceKey),
                    "fused runAdjoint dropped the reactance lever; θ̄ held " + theta.keySet());
            assertEquals(admittanceAlone, theta.get(admittanceKey), 1e-12 * Math.abs(admittanceAlone) + 1e-13,
                    "fused runAdjoint must match the single-type answer for the admittance lever");
            assertEquals(reactanceAlone, theta.get(reactanceKey), 1e-12 * Math.abs(reactanceAlone) + 1e-13,
                    "fused runAdjoint must match the single-type answer for the reactance lever");
        }
    }

    /**
     * A BUS_REACTIVE_POWER cotangent must reach x̄, and its direct term must reach θ̄.
     *
     * <p>That function is not a single equation term: a bus's reactive injection is the
     * {@code InjectionDerivable} behind its BUS_TARGET_Q equation, a signed sum of the incident branch terms.
     * The first adjoint scattered only {@code EquationTerm}s and skipped everything else with a
     * {@code continue}, so a reactive-power cotangent produced a clean 0.0 (IEEE-14, bus B2 against the B3-G
     * target voltage: forward 5.437, adjoint 0.0) while the forward path, which goes through
     * {@code calculateSensi}, was right. The scatter now goes through the {@code Derivable} contract for every
     * function type, with no fall-through.</p>
     *
     * <p>The second facet is the direct term. For a branch flow it lives on the self-pair (the monitored
     * branch is the variable branch); for a bus reactive injection it lives on every branch INCIDENT to the
     * bus, and a bus is never a branch, so the minimal factor set had no pair to carry it. The block below
     * puts the incident line L4-5-1 neither first among the variables nor with the first function, so the
     * only way its direct term with B5 can be served is the (bus, branch) cross product the builder now
     * emits for this function type. B5 is a PQ bus: its injection is constant, so the through-state and the
     * direct term cancel exactly, and an adjoint that dropped the direct term would return the non-zero
     * through-state part alone.</p>
     *
     * <p>Oracle: the cotangent-weighted forward cross product, per variable, over a target-voltage family
     * (through the state only) and a line-admittance family (state plus direct term).</p>
     */
    @Test
    void runAdjointScattersABusReactivePowerCotangent() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityFunctionType ft = SensitivityFunctionType.BUS_REACTIVE_POWER;
        List<String> buses = List.of("B2", "B5");          // a PV bus, whose injection moves, and a PQ bus
        double[] w = {0.7, -1.3};
        List<String> gens = List.of("B3-G", "B8-G");        // BUS_TARGET_VOLTAGE levers
        List<String> lines = List.of("L1-2-1", "L4-5-1");   // BRANCH_ADMITTANCE levers, both incident to a monitored bus
        SensitivityVariableType vtGen = SensitivityVariableType.BUS_TARGET_VOLTAGE;
        SensitivityVariableType vtLine = SensitivityVariableType.BRANCH_ADMITTANCE;

        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = new HashMap<>();
        for (int i = 0; i < buses.size(); i++) {
            cot.put(new AcSensitivityAnalysis.FunctionRef(ft, buses.get(i)), w[i]);
        }
        List<AcSensitivityAnalysis.AdjointVariable> vars = concat(levers(vtGen, gens), levers(vtLine, lines));

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        Map<AcSensitivityAnalysis.VariableRef, Double> theta = analysis.runAdjoint(network, network.getVariantManager().getWorkingVariantId(),
                List.of(), cot, vars);

        List<SensitivityFactor> full = new ArrayList<>();
        for (String bus : buses) {
            for (String g : gens) {
                full.add(new SensitivityFactor(ft, bus, vtGen, g, false, ContingencyContext.all()));
            }
            for (String line : lines) {
                full.add(new SensitivityFactor(ft, bus, vtLine, line, false, ContingencyContext.all()));
            }
        }
        SensitivityAnalysisResult fwd = SensitivityAnalysis.find().run(network, full,
                new SensitivityAnalysisRunParameters().setParameters(sensiParams));

        double maxAbs = 0;
        for (String g : gens) {
            double expected = 0;
            for (int i = 0; i < buses.size(); i++) {
                expected += w[i] * fwd.getSensitivityValue(g, buses.get(i), ft, vtGen);
            }
            assertEquals(expected, theta.get(varKey(vtGen, g)), 1e-12 * Math.abs(expected) + 1e-10,
                    "bus reactive power cotangent through the state, for " + g);
            maxAbs = Math.max(maxAbs, Math.abs(expected));
        }
        assertTrue(maxAbs > 1e-3, "the oracle is trivially zero, so the old silent-zero behaviour would pass: " + maxAbs);
        for (String line : lines) {
            double expected = 0;
            for (int i = 0; i < buses.size(); i++) {
                expected += w[i] * fwd.getSensitivityValue(line, buses.get(i), ft, vtLine);
            }
            assertEquals(expected, theta.get(varKey(vtLine, line)), 1e-12 * Math.abs(expected) + 1e-10,
                    "bus reactive power cotangent with the incident-branch direct term, for " + line);
        }
    }

    @Test
    void runAdjointRefusesToRunWithoutACachedLoadFlow() {
        // runAdjoint reuses the factorized Jacobian the network cache retains. With no cached load flow there
        // is nothing to solve against, and the failure must say so rather than dereference an empty Optional.
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = new LoadFlowParameters().setDistributedSlack(false); // networkCacheEnabled OFF
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), sensiParams);
        String variantId = network.getVariantManager().getWorkingVariantId();
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = Map.of(powerKey("L1-2-1"), 1.0);
        List<AcSensitivityAnalysis.AdjointVariable> vars =
                levers(SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("B2-G"));

        PowsyblException e = assertThrows(PowsyblException.class,
            () -> analysis.runAdjoint(network, variantId, List.of(), cot, vars));
        assertTrue(e.getMessage().contains("networkCacheEnabled"), e.getMessage());
    }

    @Test
    void buildAdjointFactorsRejectsEmptyDeclarations() {
        // An empty declaration has no meaningful θ̄, and the failure must NAME which side is empty: the
        // alternative is an IndexOutOfBoundsException from inside buildAdjointFactors, and an empty θ̄
        // returned quietly would have read as "none of your levers can help".
        Network network = IeeeCdfNetworkFactory.create14();
        SensitivityFunctionType ft = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
        SensitivityVariableType vt = SensitivityVariableType.BRANCH_ADMITTANCE;
        List<AcSensitivityAnalysis.FunctionRef> functions = List.of(new AcSensitivityAnalysis.FunctionRef(ft, "L1-2-1"));
        List<AcSensitivityAnalysis.AdjointVariable> variables = levers(vt, List.of("L2-3-1"));

        PowsyblException noFunctions = assertThrows(PowsyblException.class,
            () -> AcSensitivityAnalysis.buildAdjointFactors(network, List.of(), variables));
        assertTrue(noFunctions.getMessage().contains("at least one monitored function"), noFunctions.getMessage());

        PowsyblException noVariables = assertThrows(PowsyblException.class,
            () -> AcSensitivityAnalysis.buildAdjointFactors(network, functions, List.of()));
        assertTrue(noVariables.getMessage().contains("at least one variable"), noVariables.getMessage());
    }
}
