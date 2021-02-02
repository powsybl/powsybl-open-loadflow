/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.AbstractConverterTest;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.test.DanglingLineNetworkFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
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
        assertEquals(1, lfNetworks.size());
        Path file = fileSystem.getPath("/work/n.json");
        lfNetworks.get(0).writeJson(file);
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
        assertEquals(1, lfNetworks.size());
        Path file = fileSystem.getPath("/work/n2.json");
        lfNetworks.get(0).writeJson(file);
        try (InputStream is = Files.newInputStream(file)) {
            compareTxt(getClass().getResourceAsStream("/n2.json"), is);
        }
    }

    @Test
    void getBranchByIdtest() {
        Network network = EurostagTutorialExample1Factory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertNull(lfNetwork.getBranchById("AAA"));
        assertNotNull(lfNetwork.getBranchById("NHV1_NHV2_1"));
    }

    @Test
    void testDanglingLineGenerator() {
        Network network = DanglingLineNetworkFactory.createWithGeneration();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertFalse(lfNetwork.getBusById("DL_BUS").getGenerators().get(0).isParticipating());
        lfNetwork.getBusById("DL_BUS").getGenerators().get(0).setParticipating(true);
        assertFalse(lfNetwork.getBusById("DL_BUS").getGenerators().get(0).isParticipating());
    }

    @Test
    void testDanglingLine() {
        Network network = DanglingLineNetworkFactory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertFalse(lfNetwork.getBusById("DL_BUS").isParticipatingToLoadActivePowerDistribution());
    }
}
