/*
 *
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

import java.time.ZonedDateTime;
import java.util.Objects;

public final class MyNetworkFactory {

    private MyNetworkFactory() {

    }

    public static Network create() {
        return create(NetworkFactory.findDefault());
    }

    public static Network create(NetworkFactory networkFactory) {
        Objects.requireNonNull(networkFactory);

        Network network = networkFactory.createNetwork("phaseShifterTestCase", "code");
        network.setCaseDate(ZonedDateTime.parse("2016-10-18T10:06:00.000+02:00"));
        Substation s1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        b1.setV(400).setAngle(0);
        Generator g1 = vl1.newGenerator()
                .setId("G1")
                .setConnectableBus("B1")
                .setBus("B1")
                .setVoltageRegulatorOn(true)
                .setTargetP(100.0)
                .setTargetV(400.0)
                .setMinP(50.0)
                .setMaxP(150.0)
                .add();
        g1.getTerminal().setP(-100.16797).setQ(-58.402832);
        VoltageLevel vl2 = s1.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        b2.setV(400).setAngle(-3.6792064);
        Load ld2 = vl2.newLoad()
                .setId("LD2")
                .setConnectableBus("B2")
                .setBus("B2")
                .setP0(100.0)
                .setQ0(50.0)
                .add();
        ld2.getTerminal().setP(100.0).setQ(50.0);
        network.newLine()
                .setId("L1")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(0)
                .setX(200.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("L2")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(0)
                .setX(200.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        TwoWindingsTransformer ps1 = s1.newTwoWindingsTransformer()
                .setId("PS1")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setRatedU1(400)
                .setRatedU2(400)
                .setR(0)
                .setX(100.0)
                .setG(0.0)
                .setB(0.0)
                .add();
        ps1.newPhaseTapChanger()
                .setTapPosition(1)
                .setRegulationTerminal(ps1.getTerminal2())
                .setRegulationMode(PhaseTapChanger.RegulationMode.FIXED_TAP)
                .setRegulationValue(200)
                .beginStep()
                .setAlpha(-5)
                .setRho(1.0)
                .setR(0.0)
                .setX(50)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(0.1)
                .setRho(1.0)
                .setR(0.0)
                .setX(100)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(5.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(200)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .add();
        return network;
    }

    public static Network create2() {
        Network network = NetworkFactory.findDefault().createNetwork("phaseShifterTestCase", "code");
        network.setCaseDate(ZonedDateTime.parse("2016-10-18T10:06:00.000+02:00"));
        Substation s1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        b1.setV(400).setAngle(0);
        Generator g1 = vl1.newGenerator()
                .setId("G1")
                .setConnectableBus("B1")
                .setBus("B1")
                .setVoltageRegulatorOn(true)
                .setTargetP(100.0)
                .setTargetV(400.0)
                .setMinP(50.0)
                .setMaxP(150.0)
                .add();
        g1.getTerminal().setP(-100.16797).setQ(-58.402832);
        VoltageLevel vl2 = s1.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        b2.setV(400).setAngle(-3.6792064);
        Load ld2 = vl2.newLoad()
                .setId("LD2")
                .setConnectableBus("B2")
                .setBus("B2")
                .setP0(100.0)
                .setQ0(50.0)
                .add();
        ld2.getTerminal().setP(100.0).setQ(50.0);
        VoltageLevel vl3 = s1.newVoltageLevel()
                .setId("VL3")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        b3.setV(400).setAngle(-3.6792064);
        Load ld3 = vl3.newLoad()
                .setId("LD3")
                .setConnectableBus("B3")
                .setBus("B3")
                .setP0(100.0)
                .setQ0(50.0)
                .add();
        ld3.getTerminal().setP(100.0).setQ(50.0);
        network.newLine()
                .setId("L23")
                .setVoltageLevel1("VL2")
                .setConnectableBus1("B2")
                .setBus1("B2")
                .setVoltageLevel2("VL3")
                .setConnectableBus2("B3")
                .setBus2("B3")
                .setR(0)
                .setX(200.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        TwoWindingsTransformer ps1 = s1.newTwoWindingsTransformer()
                .setId("PS1")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setRatedU1(400)
                .setRatedU2(400)
                .setR(0)
                .setX(100.0)
                .setG(0.0)
                .setB(0.0)
                .add();
        ps1.newPhaseTapChanger()
                .setTapPosition(1)
                .setRegulationTerminal(ps1.getTerminal2())
                .setRegulationMode(PhaseTapChanger.RegulationMode.FIXED_TAP)
                .setRegulationValue(200)
                .beginStep()
                .setAlpha(-5)
                .setRho(1.0)
                .setR(0.0)
                .setX(50)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(-5)
                .setRho(1.0)
                .setR(0.0)
                .setX(100)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(5.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(200)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .add();
        return network;
    }

    /**
     *     G1                   LD2
     *     |          L1        |
     *     |  ----------------- |
     *     |          L2        |
     *     |  ----------------- |
     *     |     PS1      PS2   |
     *     B1 --------B3------- B2
     */
    public static Network createWithTwoPhaseTapChangers() {
        Network network = NetworkFactory.findDefault().createNetwork("two-phase-tap-changers-test", "test");
        network.setCaseDate(ZonedDateTime.parse("2016-10-18T10:06:00.000+02:00"));
        Substation s1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("VL1")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        b1.setV(400).setAngle(0);
        Generator g1 = vl1.newGenerator()
                .setId("G1")
                .setConnectableBus("B1")
                .setBus("B1")
                .setVoltageRegulatorOn(true)
                .setTargetP(100.0)
                .setTargetV(400.0)
                .setMinP(50.0)
                .setMaxP(150.0)
                .add();
        g1.getTerminal().setP(-100.16797).setQ(-58.402832);
        VoltageLevel vl2 = s1.newVoltageLevel()
                .setId("VL2")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        b2.setV(385.6934).setAngle(-3.6792064);
        Load ld2 = vl2.newLoad()
                .setId("LD+2")
                .setConnectableBus("B2")
                .setBus("B2")
                .setP0(100.0)
                .setQ0(50.0)
                .add();
        ld2.getTerminal().setP(100.0).setQ(50.0);
        Line l1 = network.newLine()
                .setId("L1")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(4.0)
                .setX(200.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        // TODO : clean this class
        Line l2 = network.newLine()
                .setId("L2")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(4.0)
                .setX(200.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        l1.getTerminal1().setP(50.084026).setQ(29.201416);
        l1.getTerminal2().setP(-50.0).setQ(-25.0);
        VoltageLevel vl3 = s1.newVoltageLevel()
                .setId("VL3")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        b3.setV(392.6443).setAngle(-1.8060945);
        TwoWindingsTransformer ps1 = s1.newTwoWindingsTransformer()
                .setId("PS1")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL3")
                .setConnectableBus2("B3")
                .setBus2("B3")
                .setRatedU1(380.0)
                .setRatedU2(380.0)
                .setR(2.0)
                .setX(50.0)
                .setG(0.0)
                .setB(0.0)
                .add();
        ps1.getTerminal1().setP(50.08403).setQ(29.201416);
        ps1.getTerminal2().setP(-50.042015).setQ(-27.100708);
        ps1.newPhaseTapChanger()
                .setTapPosition(1)
                .setRegulationTerminal(ps1.getTerminal2())
                .setRegulationMode(PhaseTapChanger.RegulationMode.FIXED_TAP)
                .setRegulationValue(200)
                .beginStep()
                .setAlpha(-20.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(25.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(0.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(50.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(20.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(75.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .add();
        TwoWindingsTransformer ps2 = s1.newTwoWindingsTransformer()
                .setId("PS2")
                .setVoltageLevel1("VL3")
                .setConnectableBus1("B3")
                .setBus1("B3")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setRatedU1(400.0)
                .setRatedU2(400.0)
                .setR(2.0)
                .setX(50.0)
                .setG(0.0)
                .setB(0.0)
                .add();
        ps2.getTerminal1().setP(50.08403).setQ(29.201416);
        ps2.getTerminal2().setP(-50.042015).setQ(-27.100708);
        ps2.newPhaseTapChanger()
                .setTapPosition(1)
                .setRegulationTerminal(ps2.getTerminal2())
                .setRegulationMode(PhaseTapChanger.RegulationMode.FIXED_TAP)
                .setRegulationValue(200)
                .beginStep()
                .setAlpha(-10.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(25.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(0.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(50.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(10.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(75.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .add();
        return network;
    }
}
