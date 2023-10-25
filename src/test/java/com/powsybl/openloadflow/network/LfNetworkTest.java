/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.test.AbstractConverterTest;
import com.powsybl.commons.test.ComparisonUtils;
import com.powsybl.iidm.network.ComponentConstants;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.test.DanglingLineNetworkFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class LfNetworkTest extends AbstractConverterTest {

    @Override
    @BeforeEach
    public void setUp() throws IOException {
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        super.tearDown();
    }

    @Test
    void test() throws IOException {
        Network network = EurostagTutorialExample1Factory.create();
        network.getVoltageLevel("VLLOAD").newShuntCompensator()
                .setId("SC")
                .setBus("NLOAD")
                .setConnectableBus("NLOAD")
                .setSectionCount(1)
                .newLinearModel()
                    .setBPerSection(3.25 * Math.pow(10, -3))
                    .setMaximumSectionCount(1)
                    .add()
                .add();

        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);
        assertEquals(1, lfNetworks.size());
        Path file = fileSystem.getPath("/work/n.json");
        mainNetwork.writeJson(file);
        try (InputStream is = Files.newInputStream(file)) {
            ComparisonUtils.compareTxt(getClass().getResourceAsStream("/n.json"), is);
        }
    }

    @Test
    void testPhaseShifter() throws IOException {
        Network network = PhaseShifterTestCaseFactory.create();
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger()
                .setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(ps1.getTerminal1())
                .setRegulationValue(83);

        LfNetworkParameters parameters = new LfNetworkParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector())
                .setPhaseControl(true);
        List<LfNetwork> lfNetworks = Networks.load(network, parameters);
        LfNetwork mainNetwork = lfNetworks.get(0);
        assertEquals(1, lfNetworks.size());
        Path file = fileSystem.getPath("/work/n2.json");
        mainNetwork.writeJson(file);
        try (InputStream is = Files.newInputStream(file)) {
            ComparisonUtils.compareTxt(getClass().getResourceAsStream("/n2.json"), is);
        }
    }

    @Test
    void getBranchByIdtest() {
        Network network = EurostagTutorialExample1Factory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertNull(lfNetwork.getBranchById("AAA"));
        assertNotNull(lfNetwork.getBranchById("NHV1_NHV2_1"));
    }

    @Test
    void testDanglingLine() {
        Network network = DanglingLineNetworkFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertFalse(lfNetwork.getBusById("DL_BUS").isDisabled());
        assertTrue(lfNetwork.getBusById("DL_BUS").createBusResults().isEmpty());
    }

    @Test
    void testVsc() {
        Network network = HvdcNetworkFactory.createVsc();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(2, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertEquals(0.0, lfNetwork.getGeneratorById("cs2").getParticipationFactor(), 1E-6);
    }

    @Test
    void testMultipleConnectedComponentsACMainComponent() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());

        //Default is only compute load flow on the main component
        assertEquals(1, result.getComponentResults().size());
        assertEquals(ComponentConstants.MAIN_NUM, result.getComponentResults().get(0).getConnectedComponentNum());
    }

    @Test
    void testMultipleConnectedComponentsACAllComponents() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertEquals(2, result.getComponentResults().size());
    }

    @Test
    void testMultipleConnectedComponentsDCMainComponent() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());

        //Default is only compute load flow on the main component
        assertEquals(1, result.getComponentResults().size());
        assertEquals(ComponentConstants.MAIN_NUM, result.getComponentResults().get(0).getConnectedComponentNum());
    }

    @Test
    void testMultipleConnectedComponentsDCAllComponents() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL)
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        parameters.setDc(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertEquals(2, result.getComponentResults().size());
    }

    private static void testGraphViz(Network network, boolean breakers, String ref) throws IOException {
        LfNetworkParameters parameters = new LfNetworkParameters().setBreakers(breakers);
        LfNetwork lfNetwork = Networks.load(network, parameters).get(0);
        try (StringWriter writer = new StringWriter()) {
            lfNetwork.writeGraphViz(writer, LoadFlowModel.AC);
            writer.flush();
            ComparisonUtils.compareTxt(Objects.requireNonNull(LfNetworkTest.class.getResourceAsStream("/" + ref)), writer.toString());
        }
    }

    @Test
    void testGraphViz() throws IOException {
        testGraphViz(EurostagTutorialExample1Factory.create(), false, "sim1.dot");
        testGraphViz(NodeBreakerNetworkFactory.create(), true, "nb.dot");
        // with a disconnected line
        Network network = EurostagTutorialExample1Factory.create();
        network.getLine("NHV1_NHV2_1").getTerminal1().disconnect();
        testGraphViz(network, false, "sim1_disconnected.dot");
    }

    @Test
    void testDisabledVoltageControl() {
        Network network = VoltageControlNetworkFactory.createWithDependentVoltageControls();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        lfNetwork.getZeroImpedanceNetworks(LoadFlowModel.AC); // to update.
        LfBus b1 = lfNetwork.getBusById("b1_vl_0");
        assertSame(VoltageControl.MergeStatus.MAIN, b1.getGeneratorVoltageControl().orElseThrow().getMergeStatus());
        LfBus b2 = lfNetwork.getBusById("b2_vl_0");
        assertSame(VoltageControl.MergeStatus.DEPENDENT, b2.getGeneratorVoltageControl().orElseThrow().getMergeStatus());
        LfBus b3 = lfNetwork.getBusById("b3_vl_0");
        assertSame(VoltageControl.MergeStatus.DEPENDENT, b3.getGeneratorVoltageControl().orElseThrow().getMergeStatus());
        lfNetwork.getBusById("b01_vl_0").setDisabled(true); // only g1
        assertFalse(b1.getGeneratorVoltageControl().orElseThrow().isDisabled());
        assertFalse(b2.getGeneratorVoltageControl().orElseThrow().isDisabled());
        assertFalse(b3.getGeneratorVoltageControl().orElseThrow().isDisabled());
        assertFalse(b1.getGeneratorVoltageControl().orElseThrow().isHidden());
        assertFalse(b2.getGeneratorVoltageControl().orElseThrow().isHidden());
        assertFalse(b3.getGeneratorVoltageControl().orElseThrow().isHidden());

        b1.setDisabled(true);
        assertSame(VoltageControl.MergeStatus.MAIN, b1.getGeneratorVoltageControl().orElseThrow().getMergeStatus());
        assertSame(VoltageControl.MergeStatus.DEPENDENT, b2.getGeneratorVoltageControl().orElseThrow().getMergeStatus());
        assertSame(VoltageControl.MergeStatus.DEPENDENT, b3.getGeneratorVoltageControl().orElseThrow().getMergeStatus());
        assertFalse(b1.getGeneratorVoltageControl().orElseThrow().isDisabled());
        assertFalse(b2.getGeneratorVoltageControl().orElseThrow().isDisabled());
        assertFalse(b3.getGeneratorVoltageControl().orElseThrow().isDisabled());

        b2.setDisabled(true);
        b3.setDisabled(true);
        assertTrue(b1.getGeneratorVoltageControl().orElseThrow().isDisabled());
        assertTrue(b2.getGeneratorVoltageControl().orElseThrow().isDisabled());
        assertTrue(b3.getGeneratorVoltageControl().orElseThrow().isDisabled());
    }
}
