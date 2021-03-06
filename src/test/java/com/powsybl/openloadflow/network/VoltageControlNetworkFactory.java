/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

/**
 * @author Anne Tilloy <anne.tilloy@rte-france.com>
 */
public class VoltageControlNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network createWithGeneratorRemoteControl() {

        Network network = Network.create("generator-remote-control-test", "code");

        Substation s = network.newSubstation()
                .setId("s")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        VoltageLevel vl3 = s.newVoltageLevel()
                .setId("vl3")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b3 = vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        VoltageLevel vl4 = s.newVoltageLevel()
                .setId("vl4")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b4 = vl4.getBusBreakerView().newBus()
                .setId("b4")
                .add();
        Load l4 = vl4.newLoad()
                .setId("l4")
                .setBus("b4")
                .setConnectableBus("b4")
                .setP0(299.6)
                .setQ0(200)
                .add();
        Generator g1 = b1.getVoltageLevel()
                .newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4) // 22 413.4
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        Generator g2 = b2.getVoltageLevel()
                .newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        Generator g3 = b3.getVoltageLevel()
                .newGenerator()
                .setId("g3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        TwoWindingsTransformer tr1 = s.newTwoWindingsTransformer()
                .setId("tr1")
                .setVoltageLevel1(b1.getVoltageLevel().getId())
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .setG(0)
                .setB(0)
                .add();
        TwoWindingsTransformer tr2 = s.newTwoWindingsTransformer()
                .setId("tr2")
                .setVoltageLevel1(b2.getVoltageLevel().getId())
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.2)
                .setRatedU2(398)
                .setR(1)
                .setX(36)
                .setG(0)
                .setB(0)
                .add();
        TwoWindingsTransformer tr3 = s.newTwoWindingsTransformer()
                .setId("tr3")
                .setVoltageLevel1(b3.getVoltageLevel().getId())
                .setBus1(b3.getId())
                .setConnectableBus1(b3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(21.3)
                .setRatedU2(397)
                .setR(2)
                .setX(50)
                .setG(0)
                .setB(0)
                .add();

        return network;
    }

    public static Network createTransformerBaseNetwork(String id) {

        Network network = Network.create(id, "test");

        Substation substation1 = network.newSubstation()
                .setId("SUBSTATION1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(132.0)
                .setLowVoltageLimit(118.8)
                .setHighVoltageLimit(145.2)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("BUS_1")
                .add();
        vl1.newGenerator()
                .setId("GEN_1")
                .setBus("BUS_1")
                .setMinP(0.0)
                .setMaxP(140)
                .setTargetP(25)
                .setTargetV(135)
                .setVoltageRegulatorOn(true)
                .add();

        Substation substation = network.newSubstation()
                .setId("SUBSTATION")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = substation.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(132.0)
                .setLowVoltageLimit(118.8)
                .setHighVoltageLimit(145.2)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("BUS_2")
                .add();
        vl2.newLoad()
                .setId("LOAD_2")
                .setBus("BUS_2")
                .setP0(11.2)
                .setQ0(7.5)
                .add();

        VoltageLevel vl3 = substation.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(33.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(100)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl3.getBusBreakerView().newBus()
                .setId("BUS_3")
                .add();
        vl3.newLoad()
                .setId("LOAD_3")
                .setBus("BUS_3")
                .setQ0(0)
                .setP0(5)
                .add();

        network.newLine()
                .setId("LINE_12")
                .setVoltageLevel1("VL_1")
                .setVoltageLevel2("VL_2")
                .setBus1("BUS_1")
                .setBus2("BUS_2")
                .setR(1.05)
                .setX(10.0)
                .setG1(0.0000005)
                .setG2(0.)
                .setB1(0.)
                .setB2(0.)
                .add();

        return network;
    }

    /**
     * A very small network to test with a T2wt.
     *
     *     G1        LD2      LD3
     *     |    L12   |        |
     *     |  ------- |        |
     *     B1         B2      B3
     *                  \    /
     *                   T2WT
     */
    public static Network createNetworkWithT2wt() {

        Network network = VoltageControlNetworkFactory.createTransformerBaseNetwork("two-windings-transformer-control");

        TwoWindingsTransformer t2wt = network.getSubstation("SUBSTATION").newTwoWindingsTransformer()
                .setId("T2wT")
                .setVoltageLevel1("VL_2")
                .setVoltageLevel2("VL_3")
                .setRatedU1(132.0)
                .setRatedU2(33.0)
                .setR(17.0)
                .setX(10.0)
                .setG(0.00573921028466483)
                .setB(0.000573921028466483)
                .setBus1("BUS_2")
                .setBus2("BUS_3")
                .add();

        t2wt.newRatioTapChanger()
                .beginStep()
                .setRho(0.9)
                .setR(0.1089)
                .setX(0.01089)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.0)
                .setR(0.121)
                .setX(0.0121)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .setTapPosition(0)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(false)
                .setTargetV(33.0)
                .setRegulationTerminal(network.getLoad("LOAD_3").getTerminal())
                .add();

        return network;
    }

    /**
     * A very small network to test with a T3wt.
     *
     *     G1        LD2        LD3
     *     |    L12   |          |
     *     |  ------- |          |
     *     B1         B2         B3
     *                  \        /
     *                leg1     leg2
     *                   \      /
     *                     T3WT
     *                      |
     *                     leg3
     *                      |
     *                      B4
     *                      |
     *                     LD4
     */
    public static Network createNetworkWithT3wt() {

        Network network = VoltageControlNetworkFactory.createTransformerBaseNetwork("two-windings-transformer-control");

        VoltageLevel vl4 = network.getSubstation("SUBSTATION").newVoltageLevel()
                .setId("VL_4")
                .setNominalV(10.0)
                .setLowVoltageLimit(5.0)
                .setHighVoltageLimit(15.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl4.getBusBreakerView().newBus()
                .setId("BUS_4")
                .add();

        vl4.newLoad()
                .setId("LOAD_4")
                .setBus("BUS_4")
                .setQ0(0)
                .setP0(5)
                .add();

        ThreeWindingsTransformer t3wt = network.getSubstation("SUBSTATION").newThreeWindingsTransformer()
                .setId("T3wT")
                .setRatedU0(200.0)
                .newLeg1()
                .setR(2.0)
                .setX(10.0)
                .setG(0.0)
                .setB(0.0)
                .setRatedU(130.0)
                .setVoltageLevel("VL_2")
                .setConnectableBus("BUS_2")
                .setBus("BUS_2")
                .add()
                .newLeg2()
                .setR(2.0)
                .setX(10.0)
                .setG(0.0)
                .setB(0.0)
                .setRatedU(30.0)
                .setVoltageLevel("VL_3")
                .setConnectableBus("BUS_3")
                .setBus("BUS_3")
                .add()
                .newLeg3()
                .setR(2.0)
                .setX(10.0)
                .setG(0.0)
                .setB(0.0)
                .setRatedU(10.0)
                .setVoltageLevel("VL_4")
                .setConnectableBus("BUS_4")
                .setBus("BUS_4")
                .add()
                .add();

        t3wt.getLeg2().newRatioTapChanger()
                .beginStep()
                .setRho(0.9)
                .setR(0.1089)
                .setX(0.01089)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.0)
                .setR(0.121)
                .setX(0.0121)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .setTapPosition(0)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(false)
                .setTargetV(33.0)
                .setRegulationTerminal(network.getLoad("LOAD_3").getTerminal())
                .add();

        return network;
    }

}
