/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.MultiAreaNetworkFactory;
import com.powsybl.openloadflow.network.PhaseControlFactory;
import com.powsybl.openloadflow.network.PiModelArray;
import com.powsybl.openloadflow.network.ShuntNetworkFactory;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round trip of the LfNetwork json state: the state written by {@link LfNetwork#writeJson} from a
 * solved network, read back with {@link LfNetwork#readJson} on a freshly built network from the
 * same case, must restore the full simulation state (identical dump, and a warm started run
 * converges immediately like on the original solved network).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class LfNetworkJsonStateTest {

    record Case(String name, Supplier<Network> networkSupplier, Consumer<LoadFlowParameters> parametersCustomizer) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                new Case("ieee300", IeeeCdfNetworkFactory::create300, p -> { }),
                new Case("phaseControlT2wt", PhaseControlFactory::createNetworkWithT2wt, p -> p.setPhaseShifterRegulationOn(true)),
                new Case("hvdcAcEmulation", HvdcNetworkFactory::createNetworkWithGenerators, p -> p.setHvdcAcEmulation(true)),
                new Case("hvdcAcEmulationDroop", HvdcNetworkFactory::createHvdcInAcEmulationInSymetricNetwork,
                        p -> p.setHvdcAcEmulation(true)),
                new Case("shuntVoltageControl", ShuntNetworkFactory::create, p -> p.setShuntCompensatorVoltageControlOn(true)),
                new Case("transformerVoltageControl", LfNetworkJsonStateTest::createNetworkWithRegulatingT2wt,
                        p -> p.setTransformerVoltageControlOn(true)),
                new Case("areas", MultiAreaNetworkFactory::createTwoAreasWithTieLine,
                        p -> OpenLoadFlowParameters.create(p).setAreaInterchangeControl(true)),
                new Case("acDcThreeConverters", AcDcNetworkFactory::createAcDcNetworkWithThreeConverters,
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true)),
                new Case("staticVarCompensatorsAtReactiveLimit", LfNetworkJsonStateTest::createNetworkWithStaticVarCompensatorsAtReactiveLimit,
                        p -> { })
        ).map(Arguments::of);
    }

    private static Network createNetworkWithStaticVarCompensatorsAtReactiveLimit() {
        Network network = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        network.getLine("l1").setX(100);
        StaticVarCompensator svc1 = network.getStaticVarCompensator("svc1");
        svc1.setBmax(0.0015);
        svc1.setLocalTargetV(450);
        svc1.newVoltageRegulation()
                .withMode(RegulationMode.VOLTAGE)
                .withRegulating(true)
                .build();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        vl2.newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setLocalTargetV(450)
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(true)
                    .add()
                .setBmin(-0.002)
                .setBmax(0.0005)
                .add();
        Generator g2 = vl2.newGenerator().setId("g2").setBus("b2").setConnectableBus("b2").setTargetP(1)
                .setMinP(0).setMaxP(10)
                .setLocalTargetV(450)
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(true)
                    .add()
                .add();
        g2.newMinMaxReactiveLimits().setMinQ(-10).setMaxQ(10).add();
        return network;
    }

    private static Network createNetworkWithRegulatingT2wt() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("T2wT");
        t2wt.getRatioTapChanger()
                .setLoadTapChangingCapabilities(true)
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);
        return network;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testJsonStateRoundTripOnSolvedNetwork(Case c) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters);
        c.parametersCustomizer().accept(parameters);
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);

        Network network = c.networkSupplier().get();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork original = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP).get(0);
        Network network2 = c.networkSupplier().get();
        AcLoadFlowParameters acParameters2 = OpenLoadFlowParameters.createAcParameters(network2, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork restored = Networks.load(network2, new LfTopoConfig(), acParameters2.getNetworkParameters(), ReportNode.NO_OP).get(0);

        AcLoadFlowResult solveResult = run(original, acParameters);
        assertEquals(AcSolverStatus.CONVERGED, solveResult.getSolverStatus());
        String state = dump(original);

        restored.readJson(new StringReader(state));
        assertEquals(state, dump(restored), "json state round trip should be stable");

        AcLoadFlowParameters warmParameters = new AcLoadFlowParameters(acParameters2);
        warmParameters.setVoltageInitializer(new PreviousValueVoltageInitializer(true));
        AcLoadFlowResult warmRestored = run(restored, warmParameters);
        AcLoadFlowParameters warmParametersOriginal = new AcLoadFlowParameters(acParameters);
        warmParametersOriginal.setVoltageInitializer(new PreviousValueVoltageInitializer(true));
        AcLoadFlowResult warmOriginal = run(original, warmParametersOriginal);
        assertEquals(AcSolverStatus.CONVERGED, warmRestored.getSolverStatus());
        assertEquals(warmOriginal.getSolverIterations(), warmRestored.getSolverIterations(),
                "a warm start on the restored network should converge as fast as on the original solved one");
    }

    @Test
    void testReadJsonOptionalStateBranches() {
        // hand crafted json state: a plain round trip never emits these optional fields when they hold
        // their default value
        Network network = ShuntNetworkFactory.create();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        parameters.setShuntCompensatorVoltageControlOn(true);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork lfNetwork = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP).get(0);

        LfBus bus = lfNetwork.getBuses().get(0);
        LfBranch branch = lfNetwork.getBranches().stream()
                .filter(b -> !(b.getPiModel() instanceof PiModelArray))
                .findFirst().orElseThrow();
        LfShunt controllerShunt = lfNetwork.getBuses().stream()
                .map(b -> b.getControllerShunt().orElse(null))
                .filter(s -> s != null)
                .findFirst().orElseThrow();

        String state = "{"
                + "\"buses\":[{\"id\":\"" + bus.getId() + "\",\"disabled\":true}],"
                + "\"branches\":[{\"id\":\"" + branch.getId() + "\",\"r1\":1.5,\"a1\":0.25,"
                + "\"phaseControlEnabled\":false,\"disabled\":true}]"
                + "}";
        lfNetwork.readJson(new StringReader(state));
        assertTrue(bus.isDisabled());
        assertTrue(branch.isDisabled());
        assertEquals(1.5, branch.getPiModel().getR1());
        assertEquals(0.25, branch.getPiModel().getA1());

        String shuntId = controllerShunt.getOriginalIds().get(0);
        LfBus shuntBus = lfNetwork.getBuses().stream()
                .filter(b -> b.getControllerShunt().filter(s -> s == controllerShunt).isPresent())
                .findFirst().orElseThrow();
        String shuntState = "{\"buses\":[{\"id\":\"" + shuntBus.getId() + "\",\"controllerShunt\":{\"id\":\""
                + shuntId + "\",\"b\":0.123,\"g\":0.05}}]}";
        lfNetwork.readJson(new StringReader(shuntState));
        assertEquals(0.123, controllerShunt.getB());
        assertEquals(0.05, controllerShunt.getG());
    }

    @Test
    void testWriteReadJsonDisabledAndShuntGState() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork original = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP).get(0);

        LfBus slackBus = original.getSynchronousNetworks().get(0).getSlackBuses().get(0);
        LfBus busWithLoad = original.getBuses().stream()
                .filter(b -> !b.getLoads().isEmpty() && b != slackBus)
                .findFirst().orElseThrow();
        com.powsybl.openloadflow.network.LfLoad load = busWithLoad.getLoads().get(0);
        java.util.Map<String, Boolean> disabling = new java.util.LinkedHashMap<>(load.getOriginalLoadsDisablingStatus());
        String firstLoadId = disabling.keySet().iterator().next();
        disabling.put(firstLoadId, true);
        load.setOriginalLoadsDisablingStatus(disabling);

        LfShunt shunt = original.getBuses().stream()
                .map(b -> b.getShunt().or(b::getControllerShunt).orElse(null))
                .filter(s -> s != null)
                .findFirst().orElseThrow();
        shunt.setG(0.07);

        original.getBuses().stream()
                .flatMap(b -> b.getGenerators().stream())
                .findFirst().orElseThrow()
                .setDisabled(true);

        LfBus busToDisable = original.getBuses().stream()
                .filter(b -> b != slackBus && b != busWithLoad)
                .findFirst().orElseThrow();
        busToDisable.setDisabled(true);

        String state = dump(original);
        assertTrue(state.contains("disabledLoadIds"), "dump should emit disabledLoadIds");

        Network network2 = IeeeCdfNetworkFactory.create14();
        AcLoadFlowParameters acParameters2 = OpenLoadFlowParameters.createAcParameters(network2, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork restored = Networks.load(network2, new LfTopoConfig(), acParameters2.getNetworkParameters(), ReportNode.NO_OP).get(0);
        restored.readJson(new StringReader(state));
        assertEquals(state, dump(restored), "disabled / shunt-g json state round trip should be stable");
    }

    @Test
    void testRemoveConnectivityRemovedBranch() {
        Network network = com.powsybl.openloadflow.network.NodeBreakerNetworkFactory.create3Bars();
        network.getSwitch("C1").setOpen(true);
        network.getSwitch("C2").setOpen(true);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.getSwitchesToClose().add(network.getSwitch("C1"));
        topoConfig.getSwitchesToClose().add(network.getSwitch("C2"));

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory<>(LfBus::getNum));
        acParameters.getNetworkParameters().setBreakers(true);

        try (LfNetworkList lfNetworks = Networks.loadWithReconnectableElements(network, topoConfig,
                acParameters.getNetworkParameters(), ReportNode.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            LfBranch removed = lfNetwork.getConnectivityRemovedBranches().iterator().next();
            int before = lfNetwork.getConnectivityRemovedBranches().size();
            lfNetwork.removeBranch(removed.getId());
            assertEquals(before - 1, lfNetwork.getConnectivityRemovedBranches().size());
            assertTrue(lfNetwork.getConnectivityRemovedBranches().stream().noneMatch(b -> b.getId().equals(removed.getId())));
        }
    }

    @Test
    void testReadJsonDcStateRoundTrip() {
        Network network = AcDcNetworkFactory.createAcDcNetworkWithThreeConverters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters).setAcDcNetwork(true);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork original = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP)
                .stream().filter(n -> !n.getDcBuses().isEmpty()).findFirst().orElseThrow();
        run(original, acParameters);
        String state = dump(original);

        Network network2 = AcDcNetworkFactory.createAcDcNetworkWithThreeConverters();
        AcLoadFlowParameters acParameters2 = OpenLoadFlowParameters.createAcParameters(network2, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork restored = Networks.load(network2, new LfTopoConfig(), acParameters2.getNetworkParameters(), ReportNode.NO_OP)
                .stream().filter(n -> !n.getDcBuses().isEmpty()).findFirst().orElseThrow();
        restored.readJson(new StringReader(state));
        assertEquals(state, dump(restored), "AC/DC json state round trip should be stable");
    }

    @Test
    void testReadJsonTopologyRestoredStateRoundTrip() {
        Network network = com.powsybl.openloadflow.network.NodeBreakerNetworkFactory.create3Bars();
        network.getSwitch("C1").setOpen(true);
        network.getSwitch("C2").setOpen(true);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.getSwitchesToClose().add(network.getSwitch("C1"));
        topoConfig.getSwitchesToClose().add(network.getSwitch("C2"));
        // mark a line openable so that the dump emits connectedSide1 and connectedSide2
        topoConfig.getBranchIdsOpenableSide1().add("L1");
        topoConfig.getBranchIdsOpenableSide2().add("L1");

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory<>(LfBus::getNum));
        acParameters.getNetworkParameters().setBreakers(true);

        try (LfNetworkList originals = Networks.loadWithReconnectableElements(network, topoConfig,
                acParameters.getNetworkParameters(), ReportNode.NO_OP)) {
            LfNetwork original = originals.getLargest().orElseThrow();
            run(original, acParameters);
            String state = dump(original);
            assertTrue(state.contains("connectedSide"), "dump should emit connectedSide for disconnection allowed branches");

            Network network2 = com.powsybl.openloadflow.network.NodeBreakerNetworkFactory.create3Bars();
            network2.getSwitch("C1").setOpen(true);
            network2.getSwitch("C2").setOpen(true);
            LfTopoConfig topoConfig2 = new LfTopoConfig();
            topoConfig2.getSwitchesToClose().add(network2.getSwitch("C1"));
            topoConfig2.getSwitchesToClose().add(network2.getSwitch("C2"));
            topoConfig2.getBranchIdsOpenableSide1().add("L1");
            topoConfig2.getBranchIdsOpenableSide2().add("L1");
            AcLoadFlowParameters acParameters2 = OpenLoadFlowParameters.createAcParameters(network2, parameters, parametersExt,
                    new DenseMatrixFactory(), new com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory<>(LfBus::getNum));
            acParameters2.getNetworkParameters().setBreakers(true);
            try (LfNetworkList restoredList = Networks.loadWithReconnectableElements(network2, topoConfig2,
                    acParameters2.getNetworkParameters(), ReportNode.NO_OP)) {
                LfNetwork restored = restoredList.getLargest().orElseThrow();
                restored.readJson(new StringReader(state));
                assertEquals(state, dump(restored), "topology restored json state round trip should be stable");
            }
        }
    }

    @Test
    void testReadJsonTooManyLoads() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork lfNetwork = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP).get(0);
        LfBus busWithLoad = lfNetwork.getBuses().stream()
                .filter(b -> !b.getLoads().isEmpty())
                .findFirst().orElseThrow();
        StringBuilder loads = new StringBuilder();
        for (int i = 0; i <= busWithLoad.getLoads().size(); i++) {
            loads.append(i > 0 ? "," : "").append("{\"targetP\":0.0}");
        }
        String state = "{\"buses\":[{\"id\":\"" + busWithLoad.getId() + "\",\"loads\":[" + loads + "]}]}";
        StringReader reader = new StringReader(state);
        PowsyblException e = assertThrows(PowsyblException.class, () -> lfNetwork.readJson(reader));
        assertTrue(e.getMessage().contains("More loads in the json state"));
    }

    @Test
    void testReadJsonShuntNotFound() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork lfNetwork = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP).get(0);
        LfBus busWithoutShunt = lfNetwork.getBuses().stream()
                .filter(b -> b.getShunt().isEmpty())
                .findFirst().orElseThrow();
        String state = "{\"buses\":[{\"id\":\"" + busWithoutShunt.getId() + "\",\"shunt\":{\"id\":\"X\",\"b\":1.0}}]}";
        StringReader reader = new StringReader(state);
        PowsyblException e = assertThrows(PowsyblException.class, () -> lfNetwork.readJson(reader));
        assertTrue(e.getMessage().contains("Shunt of the json state not found"));
    }

    @Test
    void testReadJsonFromPath() throws java.io.IOException {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork original = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP).get(0);
        run(original, acParameters);
        String state = dump(original);

        Network network2 = IeeeCdfNetworkFactory.create14();
        AcLoadFlowParameters acParameters2 = OpenLoadFlowParameters.createAcParameters(network2, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork restored = Networks.load(network2, new LfTopoConfig(), acParameters2.getNetworkParameters(), ReportNode.NO_OP).get(0);

        java.nio.file.Path file = java.nio.file.Files.createTempFile("lfnetwork-state", ".json");
        try {
            java.nio.file.Files.writeString(file, state);
            restored.readJson(file);
            assertEquals(state, dump(restored), "json state round trip through a file should be stable");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    void testReadJsonUnknownId() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        LfNetwork lfNetwork = Networks.load(network, new LfTopoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP).get(0);

        StringReader unknownReader = new StringReader("{\"buses\":[{\"id\":\"UNKNOWN\"}]}");
        PowsyblException e = assertThrows(PowsyblException.class, () -> lfNetwork.readJson(unknownReader));
        assertTrue(e.getMessage().contains("UNKNOWN"));

        StringReader missingIdReader = new StringReader("{\"branches\":[{\"num\":3}]}");
        PowsyblException e2 = assertThrows(PowsyblException.class, () -> lfNetwork.readJson(missingIdReader));
        assertTrue(e2.getMessage().contains("id is missing"));
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
