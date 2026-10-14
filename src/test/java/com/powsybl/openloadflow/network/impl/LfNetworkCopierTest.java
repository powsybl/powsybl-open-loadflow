/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControlAdder;
import com.powsybl.iidm.network.extensions.StandbyAutomatonAdder;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.AsymmetricalLoadFlowTest;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import com.powsybl.openloadflow.network.AutomationSystemNetworkFactory;
import com.powsybl.openloadflow.network.BoundaryFactory;
import com.powsybl.openloadflow.network.DistributedSlackNetworkFactory;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfSynchronousNetwork;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.MultiAreaNetworkFactory;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.network.PhaseControlFactory;
import com.powsybl.openloadflow.network.ReactivePowerControlNetworkFactory;
import com.powsybl.openloadflow.network.ShuntNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.T3wtFactory;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.StringWriter;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that a deep copy of a built LfNetwork is equivalent to the original: identical JSON dump,
 * identical element ordering and identical AC load flow results.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class LfNetworkCopierTest {

    record Case(String name, Network network, Consumer<LoadFlowParameters> parametersCustomizer, LfTopoConfig topoConfig) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> cases() {
        Network nodeBreakerNetwork = NodeBreakerNetworkFactory.create();
        LfTopoConfig nodeBreakerTopoConfig = new LfTopoConfig();
        nodeBreakerTopoConfig.getSwitchesToOpen().add(nodeBreakerNetwork.getSwitch("C"));

        Network automationNetwork = createNetworkWithOverloadManagementAndVoltageAngleLimit();
        LfTopoConfig automationTopoConfig = new LfTopoConfig();
        // retain the switch operated by the overload management system, otherwise the system is dropped
        automationTopoConfig.getSwitchesToOpen().add(automationNetwork.getSwitch("br1"));

        return Stream.of(
                new Case("eurostag", EurostagFactory.fix(EurostagTutorialExample1Factory.createWithFixedCurrentLimits()), p -> { }, new LfTopoConfig()),
                new Case("ieee300", IeeeCdfNetworkFactory.create300(), p -> { }, new LfTopoConfig()),
                new Case("nodeBreakerRetainedSwitch", nodeBreakerNetwork, p -> { }, nodeBreakerTopoConfig),
                new Case("phaseControl", PhaseShifterTestCaseFactory.create(), p -> p.setPhaseShifterRegulationOn(true), new LfTopoConfig()),
                new Case("phaseControlT2wt", PhaseControlFactory.createNetworkWithT2wt(), p -> p.setPhaseShifterRegulationOn(true), new LfTopoConfig()),
                new Case("shuntVoltageControl", ShuntNetworkFactory.create(), p -> p.setShuntCompensatorVoltageControlOn(true), new LfTopoConfig()),
                new Case("transformerVoltageControl", VoltageControlNetworkFactory.createNetworkWithT2wt(), p -> p.setTransformerVoltageControlOn(true), new LfTopoConfig()),
                new Case("t3wt", T3wtFactory.create(), p -> { }, new LfTopoConfig()),
                new Case("battery", DistributedSlackNetworkFactory.createWithBattery(), p -> { }, new LfTopoConfig()),
                new Case("staticVarCompensator", createNetworkWithRegulatingSvc(), p -> { }, new LfTopoConfig()),
                new Case("danglingLine", BoundaryFactory.create(), p -> { }, new LfTopoConfig()),
                new Case("hvdcAcEmulation", HvdcNetworkFactory.createWithHvdcInAcEmulation(), p -> p.setHvdcAcEmulation(true), new LfTopoConfig()),
                new Case("hvdcAcEmulationDroop", HvdcNetworkFactory.createHvdcInAcEmulationInSymetricNetwork(),
                        p -> p.setHvdcAcEmulation(true), new LfTopoConfig()),
                new Case("svcStandbyAutomaton", createSvcWithStandbyAutomaton(),
                        p -> OpenLoadFlowParameters.create(p).setSvcVoltageMonitoring(true), new LfTopoConfig()),
                new Case("secondaryVoltageControl", createNetworkWithSecondaryVoltageControl(),
                        p -> OpenLoadFlowParameters.create(p).setSecondaryVoltageControl(true).setMaxPlausibleTargetVoltage(1.6),
                        new LfTopoConfig()),
                new Case("transformerVoltageControlRegulating", createNetworkWithRegulatingT2wt(),
                        p -> p.setTransformerVoltageControlOn(true), new LfTopoConfig()),
                new Case("automationAndVoltageAngleLimit", automationNetwork,
                        p -> OpenLoadFlowParameters.create(p).setSimulateAutomationSystems(true), automationTopoConfig),
                new Case("areas", MultiAreaNetworkFactory.createTwoAreasWithTieLine(),
                        p -> OpenLoadFlowParameters.create(p).setAreaInterchangeControl(true), new LfTopoConfig()),
                new Case("transformerPhaseControl", createNetworkWithRegulatingPhaseControl(),
                        p -> p.setPhaseShifterRegulationOn(true), new LfTopoConfig()),
                new Case("transformerReactivePowerControl", createNetworkWithTransformerReactivePowerControl(),
                        p -> OpenLoadFlowParameters.create(p).setTransformerReactivePowerControl(true), new LfTopoConfig()),
                new Case("remoteReactivePowerControl", ReactivePowerControlNetworkFactory.createWithGeneratorRemoteControl(),
                        p -> OpenLoadFlowParameters.create(p).setGeneratorReactivePowerRemoteControl(true), new LfTopoConfig()),
                new Case("acDcThreeConverters", AcDcNetworkFactory.createAcDcNetworkWithThreeConverters(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true), new LfTopoConfig()),
                new Case("acDcAcVoltageControl", AcDcNetworkFactory.createAcDcNetworkWithAcVoltageControl(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true), new LfTopoConfig()),
                new Case("acDcNetwork1", AcDcNetworkFactory.createAcDcNetwork1(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true), new LfTopoConfig()),
                new Case("acDcNetwork1", AcDcNetworkFactory.createAcDcNetworkWithDroopControl(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true), new LfTopoConfig()),
                new Case("acDcTwoPccConvertersWithoutVdcReference", AcDcNetworkFactory.createAcDcNetworkTwoPccConvertersWithoutVdcReference(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true), new LfTopoConfig()),
                new Case("asymmetrical", AsymmetricalLoadFlowTest.fourNodescreate(),
                        p -> {
                            p.setUseReactiveLimits(false).setDistributedSlack(false);
                            OpenLoadFlowParameters.create(p).setAsymmetrical(true)
                                    .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
                        }, new LfTopoConfig())
        ).map(Arguments::of);
    }

    private static Network createNetworkWithRegulatingSvc() {
        Network network = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        network.getStaticVarCompensator("svc1")
                .setLocalTargetV(385)
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(true);
        return network;
    }

    private static Network createNetworkWithRegulatingPhaseControl() {
        Network network = PhaseControlFactory.createNetworkWithT2wt();
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("PS1");
        t2wt.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(t2wt.getTerminal1())
                .setRegulationValue(83);
        return network;
    }

    private static Network createNetworkWithTransformerReactivePowerControl() {
        Network network = ReactivePowerControlNetworkFactory.create4BusNetworkWithRatioTapChanger();
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("l34");
        Terminal regulatedTerminal = t2wt.getTerminal2();
        t2wt.getRatioTapChanger()
                .setLoadTapChangingCapabilities(true)
                .newVoltageRegulation()
                    .withMode(RegulationMode.REACTIVE_POWER)
                    .withTargetDeadband(0)
                    .withTargetValue(1)
                    .withTerminal(regulatedTerminal)
                    .withRegulating(true);
        return network;
    }

    private static Network createSvcWithStandbyAutomaton() {
        Network network = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        StaticVarCompensator svc1 = network.getStaticVarCompensator("svc1");
        svc1.setLocalTargetV(385)
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(true);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(400)
                .withLowVoltageThreshold(380)
                .withLowVoltageSetpoint(385)
                .withHighVoltageSetpoint(395)
                .withB0(-0.001f) // non-zero so the LfStandbyAutomatonShunt (svcShunt) is created
                .withStandbyStatus(true)
                .add();
        return network;
    }

    private static Network createNetworkWithSecondaryVoltageControl() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getGenerator("B8-G").newMinMaxReactiveLimits().setMinQ(-6).setMaxQ(200).add();
        network.newExtension(SecondaryVoltageControlAdder.class)
                .newControlZone()
                    .withName("z1")
                    .newPilotPoint().withTargetV(13).withBusbarSectionsOrBusesIds(List.of("B10")).add()
                    .newControlUnit().withId("B6-G").add()
                    .newControlUnit().withId("B8-G").add()
                    .add()
                .add();
        return network;
    }

    private static Network createNetworkWithRegulatingT2wt() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("T2wT");
        t2wt.getRatioTapChanger()
                .setLoadTapChangingCapabilities(true)
                .setTapPosition(0)
                .newVoltageRegulation()
                    .withTargetDeadband(0)
                    .withRegulating(true)
                    .withTerminal(t2wt.getTerminal2())
                    .withTargetValue(34.0)
                    .withMode(RegulationMode.VOLTAGE)
                    .build();
        return network;
    }

    private static Network createNetworkWithOverloadManagementAndVoltageAngleLimit() {
        Network network = AutomationSystemNetworkFactory.create();
        Line l34 = network.getLine("l34");
        network.newVoltageAngleLimit()
                .setId("val")
                .from(l34.getTerminal1())
                .to(l34.getTerminal2())
                .setHighLimit(7.0)
                .setLowLimit(-7.0)
                .add();
        return network;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testCopyEquivalence(Case c) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        if (OpenLoadFlowParameters.get(parameters) == null) {
            OpenLoadFlowParameters.create(parameters);
        }
        c.parametersCustomizer().accept(parameters);
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);

        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(c.network(), parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        if (!c.topoConfig().getSwitchesToOpen().isEmpty()) {
            acParameters.getNetworkParameters().setBreakers(true);
        }

        List<LfNetwork> originals = Networks.load(c.network(), c.topoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP);

        for (LfNetwork original : originals) {
            LfNetwork copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);

            assertEquals(original.getValidity(), copy.getValidity());
            assertEquals(original.getBuses().size(), copy.getBuses().size());
            assertEquals(original.getBranches().size(), copy.getBranches().size());
            for (LfBus bus : original.getBuses()) {
                assertEquals(bus.getId(), copy.getBus(bus.getNum()).getId(), "bus num/id mismatch");
            }
            for (LfBranch branch : original.getBranches()) {
                assertEquals(branch.getId(), copy.getBranch(branch.getNum()).getId(), "branch num/id mismatch");
            }

            if (original.getValidity() != LfNetwork.Validity.VALID) {
                continue;
            }

            assertEquals(original.getSynchronousNetworks().size(), copy.getSynchronousNetworks().size());
            for (LfSynchronousNetwork originalSc : original.getSynchronousNetworks()) {
                LfSynchronousNetwork copySc = copy.getSynchronousNetwork(originalSc.getNumSC());
                assertEquals(originalSc.getReferenceBus().getId(), copySc.getReferenceBus().getId());
                assertEquals(originalSc.getSlackBuses().stream().map(LfBus::getId).toList(),
                        copySc.getSlackBuses().stream().map(LfBus::getId).toList());
            }

            assertEquals(dump(original), dump(copy), "different LfNetwork json dump for " + c.name());

            AcLoadFlowResult originalResult = run(original, acParameters);
            AcLoadFlowResult copyResult = run(copy, acParameters);
            assertEquals(originalResult.getSolverStatus(), copyResult.getSolverStatus());
            assertEquals(originalResult.getSolverIterations(), copyResult.getSolverIterations());
            assertEquals(originalResult.getOuterLoopIterations(), copyResult.getOuterLoopIterations());
            assertEquals(originalResult.getDistributedActivePower(), copyResult.getDistributedActivePower(), 1e-12);
            for (LfBus bus : original.getBuses()) {
                LfBus copiedBus = copy.getBus(bus.getNum());
                assertEquals(bus.getV(), copiedBus.getV(), 1e-12, "V mismatch at " + bus.getId());
                assertEquals(bus.getAngle(), copiedBus.getAngle(), 1e-12, "angle mismatch at " + bus.getId());
            }

            assertEquals(dump(original), dump(copy), "different post load flow json dump for " + c.name());
        }
    }

    @org.junit.jupiter.api.Test
    void testCopyOfRestoredTopologyNetwork() {
        // switches built closed (so that a remedial action can close them) then reopened by the
        // initial topology restoration
        Network network = NodeBreakerNetworkFactory.create3Bars();
        network.getSwitch("C1").setOpen(true);
        network.getSwitch("C2").setOpen(true);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.getSwitchesToClose().add(network.getSwitch("C1"));
        topoConfig.getSwitchesToClose().add(network.getSwitch("C2"));

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        // naive connectivity: supports the direct component queries below
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory<>(LfBus::getNum));
        acParameters.getNetworkParameters().setBreakers(true);

        try (LfNetworkList lfNetworks = Networks.loadWithReconnectableElements(network, topoConfig,
                acParameters.getNetworkParameters(), ReportNode.NO_OP)) {
            LfNetwork original = lfNetworks.getLargest().orElseThrow();
            assertTrue(original.getBranches().stream().anyMatch(b -> b.isDisabled()),
                    "fixture should carry disabled elements from the topology restoration");
            assertEquals(2, original.getConnectivityRemovedBranches().size());

            LfNetwork copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);

            assertEquals(dump(original), dump(copy));
            // the reopened couplers must not join the busbars in the rebuilt connectivity of the copy
            original.getConnectivity(); // make sure both are initialized
            copy.getConnectivity();
            for (LfBus bus : original.getBuses()) {
                assertEquals(original.getConnectivity().getComponentNumber(bus),
                        copy.getConnectivity().getComponentNumber(copy.getBus(bus.getNum())),
                        "connectivity component mismatch for bus " + bus.getId());
            }

            AcLoadFlowResult originalResult = run(original, acParameters);
            AcLoadFlowResult copyResult = run(copy, acParameters);
            assertEquals(originalResult.getSolverStatus(), copyResult.getSolverStatus());
            assertEquals(originalResult.getSolverIterations(), copyResult.getSolverIterations());
            for (LfBus bus : original.getBuses()) {
                assertEquals(bus.getV(), copy.getBus(bus.getNum()).getV(), 1e-12);
                assertEquals(bus.getAngle(), copy.getBus(bus.getNum()).getAngle(), 1e-12);
            }
        }
    }

    @org.junit.jupiter.api.Test
    void testAcDcNetworksAreCopyable() {
        Network acDcNetwork = com.powsybl.openloadflow.network.AcDcNetworkFactory.createAcDcNetworkBipolarModel();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters).setAcDcNetwork(true);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(acDcNetwork, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        List<LfNetwork> lfNetworks = Networks.load(acDcNetwork, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP);
        for (LfNetwork original : lfNetworks) {
            LfNetwork copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);
            assertEquals(dump(original), dump(copy));
            AcLoadFlowResult originalResult = run(original, acParameters);
            AcLoadFlowResult copyResult = run(copy, acParameters);
            assertEquals(originalResult.getSolverStatus(), copyResult.getSolverStatus());
            assertEquals(originalResult.getSolverIterations(), copyResult.getSolverIterations());
            assertEquals(dump(original), dump(copy), "different post load flow json dump");
        }
    }

    @org.junit.jupiter.api.Test
    void testCopyOfSolvedNetwork() {
        Network network = IeeeCdfNetworkFactory.create300();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        List<LfNetwork> lfNetworks = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP);
        LfNetwork original = lfNetworks.get(0);
        AcLoadFlowResult solveResult = run(original, acParameters);
        assertEquals(com.powsybl.openloadflow.ac.solver.AcSolverStatus.CONVERGED, solveResult.getSolverStatus());

        LfNetwork copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);
        assertEquals(dump(original), dump(copy), "solved state not preserved by the copy");

        AcLoadFlowParameters warmParameters = new AcLoadFlowParameters(acParameters);
        warmParameters.setVoltageInitializer(new com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer(true));
        AcLoadFlowResult warmCopyResult = run(copy, warmParameters);
        AcLoadFlowResult warmOriginalResult = run(original, warmParameters);
        assertEquals(com.powsybl.openloadflow.ac.solver.AcSolverStatus.CONVERGED, warmCopyResult.getSolverStatus());
        assertEquals(warmOriginalResult.getSolverIterations(), warmCopyResult.getSolverIterations(),
                "warm start on the solved copy should converge as fast as on the original");
        for (LfBus bus : original.getBuses()) {
            assertEquals(bus.getV(), copy.getBus(bus.getNum()).getV(), 1e-12);
            assertEquals(bus.getAngle(), copy.getBus(bus.getNum()).getAngle(), 1e-12);
        }
    }

    @org.junit.jupiter.api.Test
    void testCopyOfNetworkWithExcludedSlackBuses() {
        // the excluded slack bus set is only populated at runtime (e.g. by a contingency relocating the
        // slack bus), so it is set directly on the built network
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.createWithFixedCurrentLimits());
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork original = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP).get(0);

        LfSynchronousNetwork originalSc = original.getSynchronousNetworks().get(0);
        LfBus excluded = original.getBus(originalSc.getSlackBuses().get(0).getNum());
        originalSc.setExcludedSlackBuses(java.util.Set.of(excluded));
        assertTrue(!originalSc.getExcludedSlackBuses().isEmpty());

        LfNetwork copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);

        LfSynchronousNetwork copySc = copy.getSynchronousNetwork(originalSc.getNumSC());
        assertEquals(originalSc.getExcludedSlackBuses().size(), copySc.getExcludedSlackBuses().size());
        assertEquals(excluded.getId(), copySc.getExcludedSlackBuses().iterator().next().getId());
        assertEquals(dump(original), dump(copy));
    }

    private static AcLoadFlowResult run(LfNetwork network, AcLoadFlowParameters acParameters) {
        try (var context = new AcLoadFlowContext(network, acParameters)) {
            return new AcloadFlowEngine(context).run();
        }
    }

    private static String dump(LfNetwork network) {
        StringWriter writer = new StringWriter();
        network.writeJson(writer);
        return writer.toString();
    }
}
