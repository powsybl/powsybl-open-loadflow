/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControlAdder;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityAnalysisRunParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sensitivity to the pilot-point target voltage of a secondary voltage control (SVC) zone
 * ({@link SensitivityVariableType#SVC_PILOT_POINT_TARGET_VOLTAGE}, computed by
 * {@link SvcPilotPointClosedLoopSensitivity}).
 *
 * <p>All SVC pilot-point sensitivity tests live here. The closed-loop tests follow the finite-difference
 * cross-check pattern : perturb the pilot target by a small ΔV, re-solve the
 * load flow with the SVC outer loop, and check that, for every monitored quantity F, the analytic
 * {@code dF/dV_pilot* · ΔV} matches the actual change measured by the re-solve.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class AcSvcPilotPointSensitivityTest extends AbstractSensitivityAnalysisTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcSvcPilotPointSensitivityTest.class);

    private static final List<String> FLOW_LABELS = List.of("P1", "P2", "Q1", "Q2", "I1", "I2");

    AcSvcPilotPointSensitivityTest(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------

    /** IEEE-14 with a single SVC zone z1: pilot B10, controllers B6-G and B8-G (B8-G limits widened). */
    private static Network ieee14WithZone(double pilotTargetV) {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getGenerator("B8-G").newMinMaxReactiveLimits().setMinQ(-6).setMaxQ(200).add();
        network.newExtension(SecondaryVoltageControlAdder.class)
                .newControlZone()
                    .withName("z1")
                    .newPilotPoint().withTargetV(pilotTargetV).withBusbarSectionsOrBusesIds(List.of("B10")).add()
                    .newControlUnit().withId("B6-G").add()
                    .newControlUnit().withId("B8-G").add()
                    .add()
                .add();
        return network;
    }

    /** IEEE-118 with two SVC zones: z1 (pilot B60, controllers B59/B61) and z2 (pilot B67, controllers B65/B66). */
    private static void addTwoSvcZones(Network network, double pilotTargetZ1, double pilotTargetZ2) {
        network.newExtension(SecondaryVoltageControlAdder.class)
                .newControlZone()
                    .withName("z1")
                    .newPilotPoint().withTargetV(pilotTargetZ1).withBusbarSectionsOrBusesIds(List.of("B60")).add()
                    .newControlUnit().withId("B59-G").add()
                    .newControlUnit().withId("B61-G").add()
                    .add()
                .newControlZone()
                    .withName("z2")
                    .newPilotPoint().withTargetV(pilotTargetZ2).withBusbarSectionsOrBusesIds(List.of("B67")).add()
                    .newControlUnit().withId("B65-G").add()
                    .newControlUnit().withId("B66-G").add()
                    .add()
                .add();
    }

    private static SensitivityAnalysisParameters svcSensiParameters() {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        sensiParameters.getLoadFlowParameters().setUseReactiveLimits(false);
        OpenLoadFlowParameters.create(sensiParameters.getLoadFlowParameters())
                .setSecondaryVoltageControl(true)
                .setMaxPlausibleTargetVoltage(1.6);
        return sensiParameters;
    }

    /** [P1, P2, Q1, Q2, I1, I2] of a branch at the current operating point. */
    private static double[] captureFlows(Network network, String branchId) {
        Branch<?> branch = network.getBranch(branchId);
        Terminal t1 = branch.getTerminal1();
        Terminal t2 = branch.getTerminal2();
        return new double[] {t1.getP(), t2.getP(), t1.getQ(), t2.getQ(), t1.getI(), t2.getI()};
    }

    private static double[] readFlowSensitivities(SensitivityAnalysisResult result, String zone, String branchId) {
        SensitivityVariableType svc = SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE;
        return new double[] {
                result.getBranchFlow1SensitivityValue(zone, branchId, svc),
                result.getBranchFlow2SensitivityValue(zone, branchId, svc),
                result.getSensitivityValue(zone, branchId, SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, svc),
                result.getSensitivityValue(zone, branchId, SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, svc),
                result.getBranchCurrent1SensitivityValue(zone, branchId, svc),
                result.getBranchCurrent2SensitivityValue(zone, branchId, svc)};
    }

    // ------------------------------------------------------------------------------------------------------------
    // Open-loop sensitivities (BUS_TARGET_VOLTAGE variable) — sanity that the SVC controllers influence the zone
    // ------------------------------------------------------------------------------------------------------------

    @Test
    void testSvcOpenLoopSensitivityIeee14() {
        Network network = ieee14WithZone(13.0);

        // Open-loop SVC factors: pilot voltage and controller reactive power w.r.t. each controller's target voltage.
        List<String> controlUnits = List.of("B6-G", "B8-G");
        List<SensitivityFactor> factors = new ArrayList<>();
        for (String unit : controlUnits) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, "B10",
                    SensitivityVariableType.BUS_TARGET_VOLTAGE, unit, false, ContingencyContext.all()));
            for (String monitored : controlUnits) {
                factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_REACTIVE_POWER, monitored,
                        SensitivityVariableType.BUS_TARGET_VOLTAGE, unit, false, ContingencyContext.all()));
            }
        }
        assertFalse(factors.isEmpty(), "SVC factors should not be empty");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(createParameters(false, "B1_vl_0", true)));

        double dvPilotPerG6 = result.getBusVoltageSensitivityValue("B6-G", "B10", SensitivityVariableType.BUS_TARGET_VOLTAGE);
        double dvPilotPerG8 = result.getBusVoltageSensitivityValue("B8-G", "B10", SensitivityVariableType.BUS_TARGET_VOLTAGE);
        double dqG6PerG6 = result.getSensitivityValue("B6-G", "B6-G", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE);
        double dqG8PerG6 = result.getSensitivityValue("B6-G", "B8-G", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE);

        assertNotEquals(0, dvPilotPerG6, 1e-6, "Pilot voltage should respond to G6 Vc change");
        assertNotEquals(0, dvPilotPerG8, 1e-6, "Pilot voltage should respond to G8 Vc change");
        assertNotEquals(0, dqG6PerG6, 1e-6, "Q at G6 should respond to G6 Vc change");
        assertNotEquals(0, dqG8PerG6, 1e-6, "Q at G8 should respond to G6 Vc change");

        LOGGER.info("SVC open-loop sensitivities:{}  dV_pilot/dVc(G6)={}  dV_pilot/dVc(G8)={}  dQ(G6)/dVc(G6)={}  dQ(G8)/dVc(G6)={}",
                System.lineSeparator(), dvPilotPerG6, dvPilotPerG8, dqG6PerG6, dqG8PerG6);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Closed-loop pilot-target sensitivity — finite-difference cross-check across all function types (IEEE-14)
    // ------------------------------------------------------------------------------------------------------------

    @Test
    void testSvcClosedLoopSensitivityIeee14() {
        String zone = "z1";
        String pilotBus = "B10";
        List<String> monitoredBuses = List.of("B10", "B6", "B8", "B9", "B4");
        List<String> monitoredBranches = List.of("L9-10-1", "L7-9-1", "L9-14-1", "T4-9-1");
        double targetV = 13.0;
        double dVtarget = 0.1; // kV step on the pilot target

        SensitivityAnalysisParameters sensiParameters = svcSensiParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        // Snapshot before (pilot target at targetV) and after (pilot target + dVtarget), each re-solved by the SVC outer loop.
        Network nBefore = ieee14WithZone(targetV);
        runLf(nBefore, lfParameters);
        Map<String, Double> vBefore = new LinkedHashMap<>();
        monitoredBuses.forEach(b -> vBefore.put(b, nBefore.getBusBreakerView().getBus(b).getV()));
        Map<String, double[]> flowBefore = new LinkedHashMap<>();
        monitoredBranches.forEach(b -> flowBefore.put(b, captureFlows(nBefore, b)));

        Network nAfter = ieee14WithZone(targetV + dVtarget);
        runLf(nAfter, lfParameters);
        Map<String, Double> vAfter = new LinkedHashMap<>();
        monitoredBuses.forEach(b -> vAfter.put(b, nAfter.getBusBreakerView().getBus(b).getV()));
        Map<String, double[]> flowAfter = new LinkedHashMap<>();
        monitoredBranches.forEach(b -> flowAfter.put(b, captureFlows(nAfter, b)));

        // Analytic closed-loop sensitivities at the base point.
        Network network = ieee14WithZone(targetV);
        SensitivityVariableType svc = SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE;
        List<SensitivityFactor> factors = new ArrayList<>();
        for (String bus : monitoredBuses) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, bus, svc, zone, false, ContingencyContext.all()));
        }
        for (String branch : monitoredBranches) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branch, svc, zone, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, branch, svc, zone, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, branch, svc, zone, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, branch, svc, zone, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, branch, svc, zone, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, branch, svc, zone, false, ContingencyContext.all()));
        }
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        // Report + asserts (predicted change = sensi * dVtarget, vs actual change from the re-solve).
        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testSvcClosedLoopSensitivityIeee14 (zone ").append(zone).append(", pilot ").append(pilotBus)
                .append(", ΔV_pilot* = ").append(dVtarget).append(" kV) ===").append(System.lineSeparator());

        for (String bus : monitoredBuses) {
            double actual = vAfter.get(bus) - vBefore.get(bus);
            double predicted = result.getBusVoltageSensitivityValue(zone, bus, svc) * dVtarget;
            out.append(String.format("  V  %-8s pred=%+10.5f  actual=%+10.5f%n", bus, predicted, actual));
        }
        for (String branch : monitoredBranches) {
            double[] before = flowBefore.get(branch);
            double[] after = flowAfter.get(branch);
            double[] sensi = readFlowSensitivities(result, zone, branch);
            for (int i = 0; i < FLOW_LABELS.size(); i++) {
                double actual = after[i] - before[i];
                double predicted = sensi[i] * dVtarget;
                out.append(String.format("  %-2s %-8s pred=%+10.5f  actual=%+10.5f%n", FLOW_LABELS.get(i), branch, predicted, actual));
            }
        }
        LOGGER.info("{}", out);

        // Pilot bus voltage tracks its own target: dV_pilot/dV_pilot* ≈ 1.
        assertEquals(1.0, result.getBusVoltageSensitivityValue(zone, pilotBus, svc), 1e-2);

        // Every monitored bus voltage change is predicted by the closed-loop sensitivity.
        for (String bus : monitoredBuses) {
            double actual = vAfter.get(bus) - vBefore.get(bus);
            double predicted = result.getBusVoltageSensitivityValue(zone, bus, svc) * dVtarget;
            assertEquals(actual, predicted, Math.max(1e-3, Math.abs(actual) * 5e-2), "V mismatch on " + bus);
        }
        // Every monitored branch flow / current change is predicted by the closed-loop sensitivity. Active and
        // reactive powers are nearly linear over this step; current magnitudes are the most non-linear quantity
        // (sqrt of p²+q², worst on a low-current transformer such as T4-9-1), so they use a looser tolerance.
        for (String branch : monitoredBranches) {
            double[] before = flowBefore.get(branch);
            double[] after = flowAfter.get(branch);
            double[] sensi = readFlowSensitivities(result, zone, branch);
            for (int i = 0; i < FLOW_LABELS.size(); i++) {
                double actual = after[i] - before[i];
                double predicted = sensi[i] * dVtarget;
                boolean isCurrent = i >= 4;
                double tol = isCurrent ? Math.max(0.5, Math.abs(actual) * 0.15) : Math.max(5e-2, Math.abs(actual) * 5e-2);
                assertEquals(actual, predicted, tol, FLOW_LABELS.get(i) + " mismatch on " + branch);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Closed-loop pilot-target sensitivity on a two-zone network — validates the global (all-zone) coordination
    // against an outer-loop re-solve, including the cross-zone reaction (IEEE-118)
    // ------------------------------------------------------------------------------------------------------------

    @Test
    void testSvcClosedLoopSensitivityTwoZonesIeee118() {
        List<String> z1ControlledBuses = List.of("B59", "B61");
        List<String> z2ControlledBuses = List.of("B65", "B66");
        List<String> allControlledBuses = new ArrayList<>();
        allControlledBuses.addAll(z1ControlledBuses);
        allControlledBuses.addAll(z2ControlledBuses);

        // Equilibrium pilot targets = natural pilot bus voltages (plain LF, no SVC).
        Network n0 = IeeeCdfNetworkFactory.create118();
        runLf(n0, new LoadFlowParameters());
        double targetZ1 = n0.getBusBreakerView().getBus("B60").getV();
        double targetZ2 = n0.getBusBreakerView().getBus("B67").getV();
        double dVtarget = 0.001 * targetZ1; // small perturbation of z1 pilot target

        LoadFlowParameters svcLfp = new LoadFlowParameters();
        OpenLoadFlowParameters.create(svcLfp).setSecondaryVoltageControl(true);

        // Ground truth: finite-difference outer-loop re-solve.
        Network nBefore = IeeeCdfNetworkFactory.create118();
        addTwoSvcZones(nBefore, targetZ1, targetZ2);
        runLf(nBefore, svcLfp);
        Map<String, Double> vcBefore = new LinkedHashMap<>();
        allControlledBuses.forEach(b -> vcBefore.put(b, nBefore.getBusBreakerView().getBus(b).getV()));

        Network nAfter = IeeeCdfNetworkFactory.create118();
        addTwoSvcZones(nAfter, targetZ1 + dVtarget, targetZ2); // only z1 pilot target moves
        runLf(nAfter, svcLfp);
        Map<String, Double> fd = new LinkedHashMap<>();
        for (String b : allControlledBuses) {
            fd.put(b, (nAfter.getBusBreakerView().getBus(b).getV() - vcBefore.get(b)) / dVtarget);
        }

        // Analytic closed-loop prediction via the public sensitivity runner with SVC_PILOT factors on zone z1.
        Network network = IeeeCdfNetworkFactory.create118();
        addTwoSvcZones(network, targetZ1, targetZ2);
        SensitivityVariableType svc = SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE;
        List<SensitivityFactor> factors = allControlledBuses.stream()
                .map(b -> new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, b, svc, "z1", false, ContingencyContext.all()))
                .toList();
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        OpenLoadFlowParameters.create(sensiParameters.getLoadFlowParameters()).setSecondaryVoltageControl(true);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));
        Map<String, Double> sensi = new LinkedHashMap<>();
        for (String b : allControlledBuses) {
            sensi.put(b, result.getBusVoltageSensitivityValue("z1", b, svc));
        }

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testSvcClosedLoopSensitivityTwoZonesIeee118 (perturb z1 pilot target by ").append(dVtarget).append(" kV) ===").append(System.lineSeparator());
        out.append(String.format("  %-6s %-6s %-18s %-18s%n", "bus", "zone", "fd dVc/dVpilot", "closed-loop sensi"));
        for (String b : allControlledBuses) {
            out.append(String.format("  %-6s %-6s %+-18.6f %+-18.6f%n",
                    b, z1ControlledBuses.contains(b) ? "z1" : "z2", fd.get(b), sensi.get(b)));
        }
        LOGGER.info("{}", out);

        // The closed-loop sensitivity matches the actual outer-loop re-solve on every controlled bus, in both zones.
        for (String b : allControlledBuses) {
            assertEquals(fd.get(b), sensi.get(b), 1e-2, "closed-loop sensitivity should match the re-solve on " + b);
        }
        // The other zone genuinely re-coordinates (large cross-zone reaction on B65), and it is captured.
        assertTrue(fd.get("B65") > 0.1, "z2 should react significantly to z1's pilot change (cross-zone coupling)");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Querying a pilot-target sensitivity on a network with no active SVC zone yields a zero (empty-weight) result
    // rather than failing the whole analysis
    // ------------------------------------------------------------------------------------------------------------

    @Test
    void testSvcPilotSensitivityWithoutSecondaryVoltageControl() {
        // IEEE-14 without any secondary voltage control extension: no SVC zone is loaded, so the queried pilot
        // bus belongs to no active zone. Perturbing its target has no closed-loop effect, so the coordination
        // builder returns no controlled-bus weights (identically-zero sensitivity) instead of failing.
        Network network = IeeeCdfNetworkFactory.create14();
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters()).get(0);
        LfBus anyBus = lfNetwork.getBuses().get(0);

        AcLoadFlowParameters acParameters = new AcLoadFlowParameters()
                .setMatrixFactory(commonTestConfig.matrixFactory());
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            Map<LfBus, Double> weights = SvcPilotPointClosedLoopSensitivity.computeControlledBusWeights(anyBus, context);
            assertTrue(weights.isEmpty(), "pilot-target sensitivity should be zero (no weights) when the zone has no active controller");
        }
    }

    @Test
    void testSvcPilotSensitivityZeroWhenZoneControllerClampedToPq() {
        // End-to-end via the public runner: a single-controller zone whose only generator has a very narrow
        // reactive range. The controller saturates and is clamped from PV to PQ during the solve, so at the
        // converged state the zone has no enabled controller bus. Perturbing its pilot target then has no
        // closed-loop effect, so every pilot-target sensitivity comes back exactly zero (rather than the whole
        // analysis failing).
        String zone = "z1";
        Network network = IeeeCdfNetworkFactory.create14();
        network.getGenerator("B6-G").newMinMaxReactiveLimits().setMinQ(-0.6).setMaxQ(0.6).add();
        network.newExtension(SecondaryVoltageControlAdder.class)
                .newControlZone()
                    .withName(zone)
                    .newPilotPoint().withTargetV(14.0).withBusbarSectionsOrBusesIds(List.of("B10")).add()
                    .newControlUnit().withId("B6-G").add()
                    .add()
                .add();

        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        sensiParameters.getLoadFlowParameters().setUseReactiveLimits(true);
        OpenLoadFlowParameters.create(sensiParameters.getLoadFlowParameters())
                .setSecondaryVoltageControl(true)
                .setMaxPlausibleTargetVoltage(1.6);

        SensitivityVariableType svc = SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE;
        List<SensitivityFactor> factors = List.of(
                new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, "B10", svc, zone, false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, "B6", svc, zone, false, ContingencyContext.all()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        // The zone's only controller is clamped to PQ, so there is no active lever: the pilot-target sensitivity
        // is exactly zero on every monitored quantity, including the pilot bus itself.
        assertEquals(0.0, result.getBusVoltageSensitivityValue(zone, "B10", svc), 0.0);
        assertEquals(0.0, result.getBusVoltageSensitivityValue(zone, "B6", svc), 0.0);
    }
}
