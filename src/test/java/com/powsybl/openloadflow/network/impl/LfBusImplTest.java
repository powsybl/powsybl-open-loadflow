/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.PerUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.DELTA_POWER;

/**
 * @author Fabien Rigaux (https://github.com/frigaux)
 */
class LfBusImplTest {
    private Network network;
    private LfNetwork lfNetwork;
    private Bus bus1;
    private Bus bus2;
    private StaticVarCompensator svc1;
    private StaticVarCompensator svc2;
    private StaticVarCompensator svc3;
    private Load load;

    private Network createNetwork() {
        Network network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vl2 = s1.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        svc1 = vl1.newStaticVarCompensator()
                .setId("svc1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.006)
                .setBmax(0.006)
                .add();
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.01)
                .add();
        svc2 = vl1.newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.001)
                .setBmax(0.001)
                .add();
        svc2.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.015)
                .add();
        svc3 = vl1.newStaticVarCompensator()
                .setId("svc3")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.00075)
                .setBmax(0.00075)
                .add();
        svc3.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.02)
                .add();
        load = vl2.newLoad()
                .setId("load")
                .setConnectableBus("b2")
                .setBus("b2")
                .setQ0(100)
                .setP0(0)
                .add();
        Line line = network.newLine()
                .setId("line")
                .setVoltageLevel1("vl1")
                .setVoltageLevel2("vl2")
                .setBus1("b1")
                .setBus2("b2")
                .setB1(0)
                .setB2(0)
                .setG1(0)
                .setG2(0)
                .setR(1)
                .setX(1)
                .add();
        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
        lfNetwork = LfNetwork.load(network, new MostMeshedSlackBusSelector()).get(0);
    }

    @Test
    void updateGeneratorsStateTest() {
        LfBusImpl lfBus = new LfBusImpl(bus1, LfNetwork.load(EurostagTutorialExample1Factory.create(), new MostMeshedSlackBusSelector()).get(0), 385, 0);
        LfNetworkLoadingReport lfNetworkLoadingReport = new LfNetworkLoadingReport();
        lfBus.addStaticVarCompensator(svc1, true, lfNetworkLoadingReport);
        lfBus.addStaticVarCompensator(svc2, true, lfNetworkLoadingReport);
        lfBus.addStaticVarCompensator(svc3, true, lfNetworkLoadingReport);
        double generationQ = -6.412103131789854;
        lfBus.updateGeneratorsState(generationQ * PerUnit.SB, true);
        double sumQ = 0;
        for (LfGenerator lfGenerator : lfBus.getGenerators()) {
            sumQ += lfGenerator.getCalculatedQ();
        }
        Assertions.assertEquals(generationQ, sumQ, DELTA_POWER, "sum of generators calculatedQ should be equals to qToDispatch");
    }
}
