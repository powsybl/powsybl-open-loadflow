/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.iidm.network.test.DanglingLineNetworkFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LfNetworkLoaderImplTest extends AbstractLoadFlowNetworkFactory {

    private Network network;

    private Generator g;

    @BeforeEach
    void setUp() {
        network = Network.create("test", "code");
        Bus b = createBus(network, "b", 380);
        Bus b2 = createBus(network, "b2", 380);
        createLine(network, b, b2, "l", 1);
        g = createGenerator(b, "g", 10, 400);
        g.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .withDroop(30)
                .add();
    }

    @Test
    void initialTest() {
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        LfGenerator lfGenerator = lfNetwork.getBus(0).getGenerators().get(0);
        assertEquals("g", lfGenerator.getId());
        assertTrue(lfGenerator.isParticipating());
    }

    @Test
    void generatorZeroActivePowerTargetTest() {
        // targetP == 0, generator is discarded from active power control
        g.setTargetP(0);
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertFalse(lfNetwork.getBus(0).getGenerators().get(0).isParticipating());
    }

    @Test
    void generatorActivePowerTargetGreaterThanMaxTest() {
        // targetP > maxP, generator is discarded from active power control
        g.setTargetP(10);
        g.setMaxP(5);
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertFalse(lfNetwork.getBus(0).getGenerators().get(0).isParticipating());
    }

    @Test
    void generatorReactiveRangeTooSmallTest() {
        // generators with a too small reactive range cannot control voltage
        g.newReactiveCapabilityCurve()
                .beginPoint()
                .setP(5)
                .setMinQ(6)
                .setMaxQ(6.0000001)
                .endPoint()
                .beginPoint()
                .setP(14)
                .setMinQ(7)
                .setMaxQ(7.00000001)
                .endPoint()
                .add();

        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertFalse(lfNetwork.getBus(0).isVoltageControlEnabled());
    }

    @Test
    void generatorNotStartedTest() {
        // targetP is zero and minP > 0, meansn generator is not started and cannot control voltage
        g.setTargetP(0);
        g.setMinP(1);
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertFalse(lfNetwork.getBus(0).isVoltageControlEnabled());
    }

    @Test
    void networkWithDanglingLineTest() {
        network = DanglingLineNetworkFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        assertEquals(1, lfNetworks.size());

        LfNetwork mainNetwork = lfNetworks.get(0);
        LfBus lfDanglingLineBus = mainNetwork.getBusById("DL_BUS");
        assertTrue(lfDanglingLineBus instanceof LfDanglingLineBus);
        assertEquals("VL", lfDanglingLineBus.getVoltageLevelId());
    }

    @Test
    void networkWith3wtTest() {
        network = ThreeWindingsTransformerNetworkFactory.create();
        ThreeWindingsTransformer transformer = network.getThreeWindingsTransformer("3WT");
        assertNotNull(transformer);
        VoltageLevel voltageLevelLeg1 = transformer.getLeg1().getTerminal().getVoltageLevel();

        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        assertEquals(1, lfNetworks.size());

        LfNetwork mainNetwork = lfNetworks.get(0);
        LfBus lfStarBus = mainNetwork.getBusById("3WT_BUS0");
        assertTrue(lfStarBus instanceof LfStarBus);
        assertTrue(lfStarBus.getCountry().isPresent());
        assertEquals(Country.FR, lfStarBus.getCountry().get());
        assertEquals(voltageLevelLeg1.getId(), lfStarBus.getVoltageLevelId());
    }

    @Test
    void defaultMethodsTest() {
        network = EurostagTutorialExample1Factory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        assertEquals(1, lfNetworks.size());

        LfNetwork mainNetwork = lfNetworks.get(0);
        LfGenerator generator = mainNetwork.getBusById("VLGEN_0").getGenerators().get(0);
        assertEquals(0, generator.getSlope(), 10E-3);
        generator.setSlope(10);
        assertEquals(0, generator.getSlope(), 10E-3);
    }

    @Test
    void defaultMethodsTest2() {
        network = BoundaryFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        assertEquals(1, lfNetworks.size());

        LfNetwork mainNetwork = lfNetworks.get(0);
        LfBus lfDanglingLineBus = mainNetwork.getBusById("dl1_BUS");
        LfGenerator generator = lfDanglingLineBus.getGenerators().get(0);
        assertEquals(0, generator.getDroop(), 10E-3);
        generator.setParticipating(true);
        assertFalse(generator.isParticipating());
    }

    @Test
    void validationLevelTest() {
        network = Network.create("test", "code");
        network.setMinimumAcceptableValidationLevel(ValidationLevel.EQUIPMENT);
        Bus b = createBus(network, "b", 380);
        Bus b2 = createBus(network, "b2", 380);
        createLine(network, b, b2, "l", 1);
        g = createGenerator2(b, "g", 10, 400);
        PowsyblException e = assertThrows(PowsyblException.class, () -> Networks.load(network, new FirstSlackBusSelector()));
        assertEquals("Only STEADY STATE HYPOTHESIS validation level of the network is supported", e.getMessage());
    }

    @Test
    void validationLevelTest2() {
        network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        network.setMinimumAcceptableValidationLevel(ValidationLevel.EQUIPMENT);
        network.getTwoWindingsTransformer("T2wT").getRatioTapChanger().setTargetV(Double.NaN).setRegulating(true);
        PowsyblException e = assertThrows(PowsyblException.class, () -> Networks.load(network, new FirstSlackBusSelector()));
        assertEquals("Only STEADY STATE HYPOTHESIS validation level of the network is supported", e.getMessage());
    }

    @Test
    void validationLevelTest3() {
        network = VoltageControlNetworkFactory.createTransformerBaseNetwork("network");
        network.setMinimumAcceptableValidationLevel(ValidationLevel.EQUIPMENT);
        network.getLoad("LOAD_2").setP0(Double.NaN).setQ0(Double.NaN);
        PowsyblException e = assertThrows(PowsyblException.class, () -> Networks.load(network, new FirstSlackBusSelector()));
        assertEquals("Only STEADY STATE HYPOTHESIS validation level of the network is supported", e.getMessage());
    }

    @Test
    void validationLevelTest4() {
        network = HvdcNetworkFactory.createVsc();
        network.setMinimumAcceptableValidationLevel(ValidationLevel.EQUIPMENT);
        network.getHvdcLine("hvdc23").setConvertersMode(null).setActivePowerSetpoint(Double.NaN);
        PowsyblException e = assertThrows(PowsyblException.class, () -> Networks.load(network, new FirstSlackBusSelector()));
        assertEquals("Only STEADY STATE HYPOTHESIS validation level of the network is supported", e.getMessage());
    }

    @Test
    void validationLevelTest5() {
        network = BoundaryFactory.create();
        network.setMinimumAcceptableValidationLevel(ValidationLevel.EQUIPMENT);
        network.getDanglingLine("dl1").setP0(Double.NaN).setQ0(Double.NaN);
        PowsyblException e = assertThrows(PowsyblException.class, () -> Networks.load(network, new FirstSlackBusSelector()));
        assertEquals("Only STEADY STATE HYPOTHESIS validation level of the network is supported", e.getMessage());
    }
}
