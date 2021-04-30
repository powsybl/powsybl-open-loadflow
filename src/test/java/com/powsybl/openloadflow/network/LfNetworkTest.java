/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.AbstractConverterTest;
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
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
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

        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.stream().filter(n -> n.getNumCC() == ComponentConstants.MAIN_NUM && n.getNumSC() == ComponentConstants.MAIN_NUM).findAny().orElseThrow();
        assertEquals(1, lfNetworks.size());
        Path file = fileSystem.getPath("/work/n.json");
        mainNetwork.writeJson(file);
        try (InputStream is = Files.newInputStream(file)) {
            compareTxt(getClass().getResourceAsStream("/n.json"), is);
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

        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.stream().filter(n -> n.getNumCC() == ComponentConstants.MAIN_NUM && n.getNumSC() == ComponentConstants.MAIN_NUM).findAny().orElseThrow();
        assertEquals(1, lfNetworks.size());
        Path file = fileSystem.getPath("/work/n2.json");
        mainNetwork.writeJson(file);
        try (InputStream is = Files.newInputStream(file)) {
            compareTxt(getClass().getResourceAsStream("/n2.json"), is);
        }
    }

    @Test
    void getBranchByIdtest() {
        Network network = EurostagTutorialExample1Factory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.stream().filter(n -> n.getNumCC() == ComponentConstants.MAIN_NUM && n.getNumSC() == ComponentConstants.MAIN_NUM).findAny().orElseThrow();
        assertNull(lfNetwork.getBranchById("AAA"));
        assertNotNull(lfNetwork.getBranchById("NHV1_NHV2_1"));
    }

    @Test
    void testDanglingLine() {
        Network network = DanglingLineNetworkFactory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.stream().filter(n -> n.getNumCC() == ComponentConstants.MAIN_NUM && n.getNumSC() == ComponentConstants.MAIN_NUM).findAny().orElseThrow();
        assertFalse(lfNetwork.getBusById("DL_BUS").isDisabled());
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
        assertEquals(ComponentConstants.MAIN_NUM, result.getComponentResults().get(0).getComponentNum());
    }

    @Test
    void testMultipleConnectedComponentsACAllComponents() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters();
        parametersExt.setComputeMainConnectedComponentOnly(false);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
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
        assertEquals(ComponentConstants.MAIN_NUM, result.getComponentResults().get(0).getComponentNum());
    }

    @Test
    void testMultipleConnectedComponentsDCAllComponents() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters();
        parametersExt.setComputeMainConnectedComponentOnly(false);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertEquals(2, result.getComponentResults().size());
    }
}
