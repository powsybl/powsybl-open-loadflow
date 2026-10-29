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
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControlAdder;
import com.powsybl.iidm.network.extensions.StandbyAutomatonAdder;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.AsymmetricalLoadFlowTest;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivityFactory;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import com.powsybl.openloadflow.network.AutomationSystemNetworkFactory;
import com.powsybl.openloadflow.network.BoundaryFactory;
import com.powsybl.openloadflow.network.DistributedSlackNetworkFactory;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.LfNetwork;
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
import com.powsybl.openloadflow.sa.LimitViolationManager;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks that a deep copy of a built LfNetwork is equivalent to the original: identical JSON dump,
 * identical deep field comparison and identical AC load flow results.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class LfNetworkCopierTest {

    private record Case(String name, Network network, Consumer<LoadFlowParameters> parametersCustomizer, LfTopoConfig topoConfig) {

        Case(String name, Network network) {
            this(name, network, p -> { });
        }

        Case(String name, Network network, Consumer<LoadFlowParameters> parametersCustomizer) {
            this(name, network, parametersCustomizer, new LfTopoConfig());
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static Stream<Case> cases() {
        return Stream.of(
                new Case("eurostag", EurostagFactory.fix(EurostagTutorialExample1Factory.createWithFixedCurrentLimits())),
                new Case("nodeBreakerRetainedSwitch", createNodeBreakerCase().getLeft(), p -> { }, createNodeBreakerCase().getRight()),
                new Case("phaseControl", PhaseShifterTestCaseFactory.create(), p -> p.setPhaseShifterRegulationOn(true)),
                new Case("phaseControlT2wt", PhaseControlFactory.createNetworkWithT2wt(), p -> p.setPhaseShifterRegulationOn(true)),
                new Case("shuntVoltageControl", ShuntNetworkFactory.create(), p -> p.setShuntCompensatorVoltageControlOn(true)),
                new Case("transformerVoltageControl", VoltageControlNetworkFactory.createNetworkWithT2wt(), p -> p.setTransformerVoltageControlOn(true)),
                new Case("t3wt", T3wtFactory.create()),
                new Case("battery", DistributedSlackNetworkFactory.createWithBattery()),
                new Case("staticVarCompensator", createNetworkWithRegulatingSvc()),
                new Case("danglingLine", BoundaryFactory.create()),
                new Case("hvdcAcEmulation", createNetworkWithHvdcAcEmulation(), p -> p.setHvdcAcEmulation(true)),
                new Case("svcStandbyAutomaton", createSvcWithStandbyAutomaton(),
                        p -> OpenLoadFlowParameters.create(p).setSvcVoltageMonitoring(true)),
                new Case("secondaryVoltageControl", createNetworkWithSecondaryVoltageControl(),
                        p -> OpenLoadFlowParameters.create(p).setSecondaryVoltageControl(true).setMaxPlausibleTargetVoltage(1.6)),
                new Case("transformerVoltageControlRegulating", createNetworkWithRegulatingT2wt(),
                        p -> p.setTransformerVoltageControlOn(true)),
                new Case("automationAndVoltageAngleLimit", createNetworkWithOverloadManagementAndVoltageAngleLimit().getLeft(),
                        p -> OpenLoadFlowParameters.create(p).setSimulateAutomationSystems(true),
                        createNetworkWithOverloadManagementAndVoltageAngleLimit().getRight()),
                new Case("areas", MultiAreaNetworkFactory.createTwoAreasWithTieLine(),
                        p -> OpenLoadFlowParameters.create(p).setAreaInterchangeControl(true)),
                new Case("transformerPhaseControl", createNetworkWithRegulatingPhaseControl(),
                        p -> p.setPhaseShifterRegulationOn(true)),
                new Case("transformerReactivePowerControl", createNetworkWithTransformerReactivePowerControl(),
                        p -> OpenLoadFlowParameters.create(p).setTransformerReactivePowerControl(true)),
                new Case("remoteReactivePowerControl", ReactivePowerControlNetworkFactory.createWithGeneratorRemoteControl(),
                        p -> OpenLoadFlowParameters.create(p).setGeneratorReactivePowerRemoteControl(true)),
                new Case("acDcThreeConverters", AcDcNetworkFactory.createAcDcNetworkWithThreeConverters(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true)),
                new Case("acDcAcVoltageControl", AcDcNetworkFactory.createAcDcNetworkWithAcVoltageControl(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true)),
                new Case("acDcNetwork1", AcDcNetworkFactory.createAcDcNetwork1(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true)),
                new Case("acDcNetwork1", AcDcNetworkFactory.createAcDcNetworkWithDroopControl(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true)),
                new Case("acDcTwoPccConvertersWithoutVdcReference", AcDcNetworkFactory.createAcDcNetworkTwoPccConvertersWithoutVdcReference(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true)),
                new Case("acDcThreeConverters", AcDcNetworkFactory.createAcDcNetworkWithThreeConverters(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true)),
                new Case("fixedCurrentLimits", EurostagFactory.fix(EurostagTutorialExample1Factory.createWithFixedCurrentLimits())),
                new Case("asymmetrical", AsymmetricalLoadFlowTest.fourNodescreate(),
                        p -> {
                            p.setUseReactiveLimits(false).setDistributedSlack(false);
                            OpenLoadFlowParameters.create(p).setAsymmetrical(true)
                                    .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
                        })
        );
    }

    @Test
    public void testLfNetworkCopyEquivalenceAndCoverageOfAllCases() {
        LfNetworkCopyDeepFieldComparator deepFieldComparator = new LfNetworkCopyDeepFieldComparator();
        cases().forEach(c -> testCopyEquivalence(c, deepFieldComparator)); // testing copy equivalence for each case
        // testing globally at the end if all LfNetwork classes (listed in LfNetworkCopyFieldGuardTest) have been covered by all the cases
        Set<Class<?>> checkedClasses = deepFieldComparator.getAllCheckedClasses();
        for (Class<?> clazz : LfNetworkCopyFieldGuardTest.getLfNetworkExpectedFields().keySet()) {
            if (!checkedClasses.contains(clazz)) {
                fail("LfNetworkCopierTest: Class " + clazz.getSimpleName() + " is never tested, consider adding a new Case "
                        + "with a network covering this class and verify handling of its copy with LfNetwork copy");
            }
        }
    }

    void testCopyEquivalence(Case c, LfNetworkCopyDeepFieldComparator deepFieldComparator) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters);
        c.parametersCustomizer().accept(parameters);
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);

        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(c.network(), parameters, parametersExt,
                new CommonTestConfig().matrixFactory(), new DTreeGraphConnectivityFactory<>());
        if (!c.topoConfig().getSwitchesToOpen().isEmpty()) {
            acParameters.getNetworkParameters().setBreakers(true);
        }

        List<LfNetwork> originals = Networks.load(c.network(), c.topoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP);

        for (LfNetwork original : originals) {
            // First test: copying the initial LfNetwork and checking they are equal
            LfNetwork copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);
            deepFieldComparator.compareLfNetworks(original, copy, c.name());
            assertEquals(dump(original), dump(copy), "different LfNetwork json dump for " + c.name());

            // Second test: running a load flow on both LfNetworks and checking the results are equal
            AcLoadFlowResult originalResult = run(original, acParameters);
            AcLoadFlowResult copyResult = run(copy, acParameters);
            assertEquals(originalResult.toString(), copyResult.toString());

            // Third test: checking both LfNetworks are still equal
            deepFieldComparator.compareLfNetworks(original, copy, c.name());
            assertEquals(dump(original), dump(copy), "different post load flow json dump for " + c.name());

            // Fourth test: copying the newly computed LfNetwork and checking if the copy is equal (to check a post computation copy)
            new LimitViolationManager(Collections.emptyList()).detectViolations(original); // Adding LfLimits before copy
            copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);
            deepFieldComparator.compareLfNetworks(original, copy, c.name());
        }
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

    private static Pair<Network, LfTopoConfig> createNodeBreakerCase() {
        Network nodeBreakerNetwork = NodeBreakerNetworkFactory.create();
        LfTopoConfig nodeBreakerTopoConfig = new LfTopoConfig();
        nodeBreakerTopoConfig.getSwitchesToOpen().add(nodeBreakerNetwork.getSwitch("C"));
        return Pair.of(nodeBreakerNetwork, nodeBreakerTopoConfig);
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
                    .withRegulating(true)
                .build();
        return network;
    }

    private static Network createSvcWithStandbyAutomaton() {
        Network network = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        StaticVarCompensator svc1 = network.getStaticVarCompensator("svc1");
        svc1.setLocalTargetV(385)
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(true)
                .build();
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

    private static Network createNetworkWithHvdcAcEmulation() {
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();
        return network;
    }

    private static Pair<Network, LfTopoConfig> createNetworkWithOverloadManagementAndVoltageAngleLimit() {
        Network network = AutomationSystemNetworkFactory.create();
        Line l34 = network.getLine("l34");
        network.newVoltageAngleLimit()
                .setId("val")
                .from(l34.getTerminal1())
                .to(l34.getTerminal2())
                .setHighLimit(7.0)
                .setLowLimit(-7.0)
                .add();
        LfTopoConfig topoConfig = new LfTopoConfig();
        // retain the switch operated by the overload management system, otherwise the system is dropped
        topoConfig.getSwitchesToOpen().add(network.getSwitch("br1"));
        return Pair.of(network, topoConfig);
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
