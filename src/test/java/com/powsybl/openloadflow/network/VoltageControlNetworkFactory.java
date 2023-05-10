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

    /**
     *   g1     g2      g3
     *   |      |       |
     *  b1      b2      b3
     *  |       |       |
     *  8 tr1   8 tr2   8 tr3
     *  |       |       |
     *  +------ b4 -----+
     *          |
     *          l4
     */
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
        b1.getVoltageLevel()
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
        b2.getVoltageLevel()
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
        b3.getVoltageLevel()
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
        s.newTwoWindingsTransformer()
                .setId("tr1")
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr2")
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.2)
                .setRatedU2(398)
                .setR(1)
                .setX(36)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr3")
                .setBus1(b3.getId())
                .setConnectableBus1(b3.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(21.3)
                .setRatedU2(397)
                .setR(2)
                .setX(50)
                .add();

        return network;
    }

    public static Network createWithIdenticalTransformers() {

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
        b1.getVoltageLevel()
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
        b2.getVoltageLevel()
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
        b3.getVoltageLevel()
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
        s.newTwoWindingsTransformer()
                .setId("tr1")
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr2")
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr3")
                .setBus1(b3.getId())
                .setConnectableBus1(b3.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
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
                .setBus1("BUS_1")
                .setBus2("BUS_2")
                .setR(1.05)
                .setX(10.0)
                .setG1(0.0000005)
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
                .setRho(1.05)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
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

    public static Network createNetworkWithT2wt2() {

        Network network = VoltageControlNetworkFactory.createTransformerBaseNetwork("two-windings-transformer-control");

        TwoWindingsTransformer t2wt = network.getSubstation("SUBSTATION").newTwoWindingsTransformer()
                .setId("T2wT")
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
                .setRho(1.75)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .beginStep()
                .setRho(1.7)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .beginStep()
                .setRho(1.5)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .beginStep()
                .setRho(1.2)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .beginStep()
                .setRho(1.05)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .beginStep()
                .setRho(1.0)
                .setR(0.121)
                .setX(0.0121)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(0.9)
                .setR(0.1089)
                .setX(0.01089)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
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
     * A very small network to test with two T2wt.
     *
     *     G1        LD2         LD3
     *     |    L12   |  T2WT2    |
     *     |  ------- | /     \  |
     *     B1         B2       B3
     *                 \      /
     *                  T2WT1
     */
    public static Network createNetworkWith2T2wt() {

        Network network = VoltageControlNetworkFactory.createTransformerBaseNetwork("two-windings-transformer-control");

        TwoWindingsTransformer t2wt1 = network.getSubstation("SUBSTATION").newTwoWindingsTransformer()
                .setId("T2wT1")
                .setRatedU1(132.0)
                .setRatedU2(33.0)
                .setR(17.0)
                .setX(10.0)
                .setG(0.00573921028466483)
                .setB(0.000573921028466483)
                .setBus1("BUS_2")
                .setBus2("BUS_3")
                .add();

        t2wt1.newRatioTapChanger()
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
                .setRho(1.05)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
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

        TwoWindingsTransformer t2wt2 = network.getSubstation("SUBSTATION").newTwoWindingsTransformer()
                .setId("T2wT2")
                .setRatedU1(132.0)
                .setRatedU2(33.0)
                .setR(17.0)
                .setX(10.0)
                .setG(0.00573921028466483)
                .setB(0.000573921028466483)
                .setBus1("BUS_2")
                .setBus2("BUS_3")
                .add();

        t2wt2.newRatioTapChanger()
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
                .setRho(1.05)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
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
                .setRatedU(130.0)
                .setConnectableBus("BUS_2")
                .setBus("BUS_2")
                .add()
                .newLeg2()
                .setR(2.0)
                .setX(10.0)
                .setRatedU(30.0)
                .setConnectableBus("BUS_3")
                .setBus("BUS_3")
                .add()
                .newLeg3()
                .setR(2.0)
                .setX(10.0)
                .setRatedU(10.0)
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

    /**
     *
     *     G1        LD2           LD3
     *     |    L12   |   T2WT2    |
     *     |  ------- |  /     \   |
     *     B1         B2        B3
     *                  \      /
     *                    T2WT
     */
    public static Network createWithTransformerSharedRemoteControl() {
        Network network = createNetworkWithT2wt();

        TwoWindingsTransformer t2wt2 = network.getSubstation("SUBSTATION")
                .newTwoWindingsTransformer()
                    .setId("T2wT2")
                    .setRatedU1(132.0)
                    .setRatedU2(33.0)
                    .setR(17.0)
                    .setX(10.0)
                    .setG(0.00573921028466483)
                    .setB(0.000573921028466483)
                    .setBus1("BUS_2")
                    .setBus2("BUS_3")
                .add();

        t2wt2.newRatioTapChanger()
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
                    .setRho(1.05)
                    .setR(0.1331)
                    .setX(0.01331)
                    .setG(0.9090909090909092)
                    .setB(0.09090909090909092)
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
                .setRegulating(true)
                .setTargetV(34.0)
                .setTargetDeadband(0)
                .setRegulationTerminal(t2wt2.getTerminal2())
                .add();

        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("T2wT");
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);

        return network;
    }

    /**
     *   g1     SHUNT2  SHUNT3
     *   |      |       |
     *  b1      b2      b3
     *  |       |       |
     *  8 tr1   8 tr2   8 tr3
     *  |       |       |
     *  +------ b4 -----+
     *          |
     *          l4
     */
    public static Network createWithShuntSharedRemoteControl() {

        Network network = Network.create("shunt-remote-control-test", "code");

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
                .setQ0(0)
                .add();
        b1.getVoltageLevel()
                .newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(21)
                .setVoltageRegulatorOn(true)
                .add();
        b2.getVoltageLevel().newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .setTargetV(400)
                .setTargetDeadband(5.0)
                .newLinearModel()
                .setMaximumSectionCount(50)
                .setBPerSection(-1E-2)
                .setGPerSection(0.0)
                .add()
                .add();
        b3.getVoltageLevel().newShuntCompensator()
                .setId("SHUNT3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .setTargetV(400)
                .setTargetDeadband(5.0)
                .newLinearModel()
                .setMaximumSectionCount(50)
                .setBPerSection(-1E-2)
                .setGPerSection(0.0)
                .add()
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr1")
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr2")
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr3")
                .setBus1(b3.getId())
                .setConnectableBus1(b3.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .add();

        return network;
    }

    /**
     * SVC test case.
     *
     * g1        ld1
     * |          |
     * b1---------b2
     *      l1    |
     *           svc1
     */
    public static Network createWithStaticVarCompensator() {
        Network network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("S2")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setTargetP(101.3664)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(101)
                .setQ0(150)
                .add();
        vl2.newStaticVarCompensator()
                .setId("svc1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();
        network.newLine()
                .setId("l1")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();
        return network;
    }

    public static Network createWithSimpleRemoteControl() {
        Network network = Network.create("remoteControl", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b2, "g2", 2, 1);
        createGenerator(b3, "g3", 2, 1);
        createGenerator(b4, "g4", 2, 1);
        createLoad(b3, "l3", 5, 1);
        createLoad(b1, "l1", 1, 1);
        createLine(network, b1, b2, "l12", 0.0);
        createLine(network, b2, b4, "l24", 0.01);
        createLine(network, b4, b3, "l43", 0.01);
        createLine(network, b3, b1, "l31", 0.0);
        return network;
    }
}
