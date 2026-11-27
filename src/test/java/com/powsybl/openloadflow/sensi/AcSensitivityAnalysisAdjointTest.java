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
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensatorLinearModel;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivityFactory;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks {@link AcSensitivityAnalysis#runAdjoint}, {@code θ̄ = Sᵀ·ȳ}, against the forward sensitivity and against
 * load flow re-runs on IEEE-14.
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
        // θ̄ is per-unit dV_bus / dV_pilot, the forward S and the FD are kV/kV: they differ by Vnom(pilot)/Vnom(f0)
        String zone = "z1";
        String pilot = "B10";
        List<String> monitored = List.of("B10", "B6", "B4"); // pilot, a controller bus, a far bus on another VL
        double targetV = 13.0;
        double dV = 0.1; // kV central step on the pilot target

        // single slack: with a distributed slack the adjoint no longer equals the forward S
        LoadFlowParameters lfp = svcCacheEnabledParameters().setDistributedSlack(false);
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);

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
        // The outer loop only brings the pilot within its own tolerance of the target, so dividing by the target
        // step would carry that residual into the FD. In closed loop the pilot voltage IS the target, so the
        // achieved pilot voltage change measures the same slope without it.
        double dPilotKv = busVoltage(nAfter, pilot) - busVoltage(nBefore, pilot);
        assertEquals(2 * dV, dPilotKv, 5e-3, "the pilot must track its target in both perturbed load flows");
        double pilotTheta = 0;
        for (String f0 : monitored) {
            // ȳ = e_{f0} -> θ̄[zone] = dV_f0 / dV_pilotTarget (closed loop), raw per-unit
            Map<AcSensitivityAnalysis.VariableRef, Double> thetaBar = analysis.runAdjoint(network,
                    network.getVariantManager().getWorkingVariantId(), List.of(), weight(declaredBuses, SensitivityFunctionType.BUS_VOLTAGE, f0, 1.0), zoneLever);
            double theta = thetaBar.get(varKey(SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE, zone)); // UNSCALED (kV/kV)

            double sKv = fwd.getBusVoltageSensitivityValue(zone, f0, SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE);
            assertEquals(sKv, theta, 1e-12 * Math.abs(sKv) + 1e-13,
                    "runAdjoint vs forward closed-loop S for " + f0);

            double fdKv = (busVoltage(nAfter, f0) - busVoltage(nBefore, f0)) / dPilotKv;
            assertEquals(fdKv, theta, 1e-4 * (Math.abs(fdKv) + 1e-2),
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
        // BRANCH_ADMITTANCE: series admittance modulus at constant ksi. The self factor (function on the variable
        // branch) has a direct term on top of the state term, the cross factor has none.
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);

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
        // two function types on one branch id: the cotangents are keyed by (type, id), not summed
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);

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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
    }

    /**
     * A lever outside the main connected component reads 0, as in the forward, and is present in the result.
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
     * A lever that cannot be differentiated at all throws, naming it, where the forward writes NaN.
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
     * An unresolvable monitored function anchoring a lever's group must not drop that lever. Oracle: the same
     * request without that function.
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
     * The factor set must not depend on the iteration order of the cotangent map.
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
     * {@link OpenSensitivityAnalysisProvider#runAdjoint} delegates to the AC adjoint and rejects DC.
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
                new DTreeGraphConnectivityFactory<>(), sensiParams)
                .runAdjoint(network, variantId, List.of(), cot, variables);
        Map<AcSensitivityAnalysis.VariableRef, Double> viaProvider =
                new OpenSensitivityAnalysisProvider(new SparseMatrixFactory(),
                        new DTreeGraphConnectivityFactory<>())
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
                new DTreeGraphConnectivityFactory<>());
        PowsyblException dc = assertThrows(PowsyblException.class,
            () -> provider.runAdjoint(network, variantId, cot, variables, List.of(), dcParams));
        assertTrue(dc.getMessage().contains("only supported in AC"), dc.getMessage());
    }

    /**
     * A GLSK lever ({@link AcSensitivityAnalysis.AdjointVariable#ofVariableSet}): a dense rhs column. Oracle: the
     * forward sensitivity of the same GLSK.
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
     * A zero cotangent declares its function: an all-zero map gives a gradient of zeros, not an error.
     */
    @Test
    void runAdjointTreatsAZeroCotangentAsADeclaration() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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

    /** The monitored functions, all weighted 0. */
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
        // the minimal factor set against the forward over the full cross product: θ̄(v) = Σ_f ȳ_f · S[f, v]
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
        // blocks with different variable sets: each set gets its gradient. Oracle: one set at a time.
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        // >1 variable per set, so a guarantee loop that covers only the first is caught; and the shunt set
        // is never a monitored function, so its variables can only be grouped by that loop.
        List<String> lines = List.of("L1-5-1", "L3-4-1", "L4-5-1");
        List<String> shunts = List.of("B9-SH");
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = Map.of(powerKey("L1-2-1"), 0.75, powerKey("L2-3-1"), -1.5);

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
     * A variable gets its direct term from every monitored function, including those of a block that does not
     * declare it. Oracle: the forward cross product over both function types.
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
     * A variable is a (type, id) pair: one element declared under two types is two levers with two gradients.
     * Oracle: one type at a time, in both block orders.
     */
    @Test
    void runAdjointKeepsVariableTypesDistinctOnSharedElementId() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters lfp = cacheEnabledParameters();
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());

        String line = "L2-3-1"; // monitored too, so the self-pair / direct term collides as well
        Map<AcSensitivityAnalysis.FunctionRef, Double> cot = Map.of(powerKey("L1-2-1"), 0.75, powerKey("L2-3-1"), -1.5);

        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
     * A BUS_REACTIVE_POWER cotangent reaches x̄, and its direct term, on every branch incident to the bus,
     * reaches θ̄. Oracle: the forward cross product over target voltage and line admittance levers.
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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
                new DTreeGraphConnectivityFactory<>(), sensiParams);
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

    // the IEEE-14 generators the slack is distributed over (the condensers at zero stay at zero)
    private static final List<String> IEEE14_PARTICIPATING_GENERATORS = List.of("B1-G", "B2-G");

    private static final Map<AcSensitivityAnalysis.FunctionRef, Double> FLOW_COTANGENTS =
            Map.of(powerKey("L1-2-1"), 1.0, powerKey("L2-3-1"), 0.5);

    /** The objective FLOW_COTANGENTS is the gradient of. */
    private static double flowObjective(Network network) {
        return network.getBranch("L1-2-1").getTerminal1().getP() + 0.5 * network.getBranch("L2-3-1").getTerminal1().getP();
    }

    private static Map<AcSensitivityAnalysis.VariableRef, Double> runAdjointWithSlackDistribution(
            Network network, LoadFlowParameters lfp, List<AcSensitivityAnalysis.AdjointVariable> variables) {
        assertTrue(LoadFlow.find("OpenLoadFlow").run(network, lfp).isFullyConverged());
        SensitivityAnalysisParameters sensiParams = new SensitivityAnalysisParameters();
        sensiParams.setLoadFlowParameters(lfp);
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
                new DTreeGraphConnectivityFactory<>(), sensiParams);
        return analysis.runAdjoint(network, network.getVariantManager().getWorkingVariantId(), List.of(),
                FLOW_COTANGENTS, variables);
    }

    /**
     * The distributed slack load flow, run to an exact balance: the load flow's own distribution stops at
     * P_RESIDUE_EPS, which would bias a finite difference. Single slack re-runs, spreading the slack bus mismatch
     * over the participating generators by maximum power, until it is negligible.
     */
    private static void runToExactSlackBalance(Network network, LoadFlowParameters lfp,
                                               List<String> participatingGenerators) {
        LoadFlowParameters singleSlackParameters = lfp.copy().setDistributedSlack(false);
        double maxPSum = participatingGenerators.stream().mapToDouble(g -> network.getGenerator(g).getMaxP()).sum();
        for (int i = 0; i < 20; i++) {
            LoadFlowResult result = LoadFlow.find("OpenLoadFlow").run(network, singleSlackParameters);
            assertTrue(result.isFullyConverged());
            double residual = result.getComponentResults().getFirst().getSlackBusResults().getFirst().getActivePowerMismatch();
            if (Math.abs(residual) < 1e-9) {
                return;
            }
            for (String id : participatingGenerators) {
                Generator g = network.getGenerator(id);
                g.setTargetP(g.getTargetP() + residual * g.getMaxP() / maxPSum);
            }
        }
        throw new AssertionError("slack balance not reached");
    }

    /** d(objective)/d(perturbation) by a central difference of two exactly balanced load flow re-runs. */
    private static double reRunCentralDifference(Supplier<Network> base, LoadFlowParameters lfp,
                                                 BiConsumer<Network, Double> perturb, double step,
                                                 ToDoubleFunction<Network> objective) {
        double[] values = new double[2];
        for (int k = 0; k < 2; k++) {
            Network network = base.get();
            perturb.accept(network, k == 0 ? step : -step);
            runToExactSlackBalance(network, lfp, IEEE14_PARTICIPATING_GENERATORS);
            values[k] = objective.applyAsDouble(network);
        }
        return (values[0] - values[1]) / (2 * step);
    }

    private static void scaleImpedance(Network network, String lineId, double dY) {
        // series admittance modulus y + dY at constant ksi: R and X scaled by y / (y + dY)
        Line line = network.getLine(lineId);
        double y = 1.0 / Math.hypot(line.getR(), line.getX());
        double scale = y / (y + dY);
        line.setR(line.getR() * scale).setX(line.getX() * scale);
    }

    @Test
    void runAdjointWithSlackDistributionMatchesALoadFlowReRun() {
        // the adjoint differentiates through the slack distribution, losses included: it matches a re-run
        LoadFlowParameters lfp = distributedSlackCacheEnabledParameters();
        OpenLoadFlowParameters.get(lfp).setSlackBusPMaxMismatch(1e-6);
        Supplier<Network> ieee14 = IeeeCdfNetworkFactory::create14;
        Network network = ieee14.get();
        double shuntB = network.getShuntCompensator("B9-SH").getModel(ShuntCompensatorLinearModel.class).getBPerSection();
        double y23 = 1.0 / Math.hypot(network.getLine("L2-3-1").getR(), network.getLine("L2-3-1").getX());
        double y15 = 1.0 / Math.hypot(network.getLine("L1-5-1").getR(), network.getLine("L1-5-1").getX());

        record Lever(SensitivityVariableType type, String id, BiConsumer<Network, Double> perturb, double step) { }

        List<Lever> levers = List.of(
                new Lever(SensitivityVariableType.INJECTION_ACTIVE_POWER, "B1-G",
                        (n, d) -> n.getGenerator("B1-G").setTargetP(n.getGenerator("B1-G").getTargetP() + d), 0.5),
                new Lever(SensitivityVariableType.INJECTION_ACTIVE_POWER, "B2-G",
                        (n, d) -> n.getGenerator("B2-G").setTargetP(n.getGenerator("B2-G").getTargetP() + d), 0.5),
                new Lever(SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE, "B9-SH",
                        (n, d) -> {
                            ShuntCompensatorLinearModel model = n.getShuntCompensator("B9-SH").getModel(ShuntCompensatorLinearModel.class);
                            model.setBPerSection(model.getBPerSection() + d);
                        }, 1e-3 * Math.abs(shuntB)),
                // a line away from the slack bus and one connected to it (the slack bus rhs entry)
                new Lever(SensitivityVariableType.BRANCH_ADMITTANCE, "L2-3-1", (n, d) -> scaleImpedance(n, "L2-3-1", d), 1e-4 * y23),
                new Lever(SensitivityVariableType.BRANCH_ADMITTANCE, "L1-5-1", (n, d) -> scaleImpedance(n, "L1-5-1", d), 1e-4 * y15));

        List<AcSensitivityAnalysis.AdjointVariable> variables = levers.stream()
                .map(l -> AcSensitivityAnalysis.AdjointVariable.of(l.type(), l.id())).toList();
        Map<AcSensitivityAnalysis.VariableRef, Double> theta = runAdjointWithSlackDistribution(network, lfp, variables);

        for (Lever lever : levers) {
            AcSensitivityAnalysis.VariableRef key = varKey(lever.type(), lever.id());
            double fd = reRunCentralDifference(ieee14, lfp, lever.perturb(), lever.step(), AcSensitivityAnalysisAdjointTest::flowObjective);
            assertEquals(fd, theta.get(key), 1e-5 * (Math.abs(fd) + 1e-2), "adjoint vs re-run for " + key);
        }
    }

    @Test
    void runAdjointWithSlackDistributionMatchesALoadFlowReRunForAnSvcPilotLever() {
        // the remaining gap is in the SVC coordination weights, computed with the slack on the slack bus
        String zone = "z1";
        double targetV = 13.0;
        double dV = 0.01;
        LoadFlowParameters lfp = svcCacheEnabledParameters().setDistributedSlack(true);
        OpenLoadFlowParameters.get(lfp).setSlackBusPMaxMismatch(1e-6);

        Network network = ieee14WithZone(targetV);
        List<AcSensitivityAnalysis.AdjointVariable> zoneLever =
                levers(SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE, List.of(zone));
        double theta = runAdjointWithSlackDistribution(network, lfp, zoneLever)
                .get(varKey(SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE, zone));

        // divided by the achieved pilot voltage change
        double[] objective = new double[2];
        double[] pilotV = new double[2];
        for (int k = 0; k < 2; k++) {
            Network n = ieee14WithZone(targetV);
            n.getExtension(SecondaryVoltageControl.class).getControlZone(zone).orElseThrow().getPilotPoint()
                    .setTargetV(targetV + (k == 0 ? dV : -dV));
            runToExactSlackBalance(n, lfp, IEEE14_PARTICIPATING_GENERATORS);
            objective[k] = flowObjective(n);
            pilotV[k] = busVoltage(n, "B10");
        }
        double fd = (objective[0] - objective[1]) / (pilotV[0] - pilotV[1]);
        assertEquals(fd, theta, 3e-3 * Math.abs(fd), "adjoint vs re-run for the pilot target");
    }
}
