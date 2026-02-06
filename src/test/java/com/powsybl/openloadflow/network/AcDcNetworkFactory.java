/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class AcDcNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network createBaseNetwork() {
        Network network = Network.create("vsc", "test");

        Substation s1 = network.newSubstation()
                .setId("S1")
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
                .setTargetP(102.56)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        Substation s2 = network.newSubstation()
                .setId("S2")
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
                .setId("ld2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(50)
                .setQ0(10)
                .add();

        network.newDcNode().
                setId("dn3").
                setNominalV(400.).
                add();
        network.newDcNode().
                setId("dn4").
                setNominalV(400.).
                add();
        network.newDcNode().
                setId("dnDummy3").
                setNominalV(400.).
                add();
        network.newDcNode().
                setId("dnDummy4").
                setNominalV(400.).
                add();

        Substation s5 = network.newSubstation()
                .setId("S5")
                .add();
        VoltageLevel vl5 = s5.newVoltageLevel()
                .setId("vl5")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl5.getBusBreakerView().newBus()
                .setId("b5")
                .add();
        vl5.newLoad()
                .setId("ld5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setP0(50)
                .setQ0(10)
                .add();

        network.newLine()
                .setId("l12")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();

        network.newLine()
                .setId("l25")
                .setBus1("b2")
                .setBus2("b5")
                .setR(1)
                .setX(3)
                .add();

        network.newDcLine()
                .setId("dl34")
                .setDcNode1("dn3")
                .setDcNode2("dn4")
                .setR(0.1)
                .add();

        network.newDcGround()
                .setId("dg3")
                .setDcNode("dnDummy3")
                .add();

        network.newDcGround()
                .setId("dg4")
                .setDcNode("dnDummy4")
                .add();

        return network;
    }

    public static Network createBipolarBaseNetwork() {
        Network network = Network.create("vsc", "test");

        Substation s1 = network.newSubstation()
                .setId("S1")
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
                .setTargetP(102.56)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        Substation s2 = network.newSubstation()
                .setId("S2")
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
                .setId("ld2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(20)
                .setQ0(10)
                .add();

        network.newDcNode()
                .setId("dn3p")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn3n")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn3r")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4p")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4n")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4r")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnGr")
                .setNominalV(400.)
                .add();
        network.newDcGround()
                .setId("Ground")
                .setDcNode("dnGr")
                .add();

        Substation s5 = network.newSubstation()
                .setId("S5")
                .add();
        VoltageLevel vl5 = s5.newVoltageLevel()
                .setId("vl5")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl5.getBusBreakerView().newBus()
                .setId("b5")
                .add();
        vl5.newLoad()
                .setId("ld5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setP0(50)
                .setQ0(10)
                .add();

        network.newLine()
                .setId("l12")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();

        network.newLine()
                .setId("l25")
                .setBus1("b2")
                .setBus2("b5")
                .setR(1)
                .setX(3)
                .add();

        network.newDcLine()
                .setId("dl34p")
                .setDcNode1("dn3p")
                .setDcNode2("dn4p")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dl34n")
                .setDcNode1("dn3n")
                .setDcNode2("dn4n")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dl3Gr")
                .setDcNode1("dn3r")
                .setDcNode2("dnGr")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dlG4r")
                .setDcNode1("dnGr")
                .setDcNode2("dn4r")
                .setR(0.1)
                .add();

        return network;
    }

    /**
     * ACDC test case.
     * <pre>
     * g1       ld2                                 ld5
     * |         |                                   |
     * b1 -------b2conv23-dn3--------------dn4conv45-b5
     * l12       |        dl34                       |
     *           |                                   |
     *           |                                   |
     *           |l25--------------------------------
     * </pre>
     */
    public static Network createAcDcNetwork1() {
        Network network = createBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-50.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy3")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400.)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy4")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * ACDC test case.
     * <pre>
     * g1       ld2                                 ld5
     * |         |                                   |
     * b1 -------b2conv23-dn3--------------dn4conv45-b5
     * l12       |        dl34                       |
     *           |                                   |
     *           |                                   |
     *           |l25--------------------------------
     * </pre>
     */
    public static Network createAcDcNetwork2() {
        Network network = createBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(1.0000309358468262 * 400)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy3")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(0.4899642149886045 * PerUnit.SB)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy4")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * ACDC 3 Converters Test Case
     * <pre>
     * g1       ld2                                                ld5
     * |         |                                                  |
     * b1 ------b2-conv23-dn3----------dnMiddle----------dn4-conv45-b5
     * l12       ||       dl3             dl7            dl4        |
     *           ||            ld6         |                        |
     *           | ---l26-------b6-conv67-dn7                       |
     *           l25-------------------------------------------------
     * </pre>
     */
    public static Network createAcDcNetworkWithThreeConverters() {
        Network network = Network.create("vsc", "test");

        Substation s1 = network.newSubstation()
                .setId("S1")
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
                .setTargetP(102.56)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        Substation s2 = network.newSubstation()
                .setId("S2")
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
                .setId("ld2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(25)
                .setQ0(5)
                .add();

        Substation s6 = network.newSubstation()
                .setId("S6")
                .add();
        VoltageLevel vl6 = s6.newVoltageLevel()
                .setId("vl6")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl6.getBusBreakerView().newBus()
                .setId("b6")
                .add();
        vl6.newLoad()
                .setId("ld6")
                .setConnectableBus("b6")
                .setBus("b6")
                .setP0(25)
                .setQ0(5)
                .add();

        network.newDcNode()
                .setId("dn3")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn7")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnMiddle")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnDummy3")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnDummy4")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnDummy7")
                .setNominalV(400.)
                .add();

        Substation s5 = network.newSubstation()
                .setId("S5")
                .add();
        VoltageLevel vl5 = s5.newVoltageLevel()
                .setId("vl5")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl5.getBusBreakerView().newBus()
                .setId("b5")
                .add();
        vl5.newLoad()
                .setId("ld5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setP0(25)
                .setQ0(5)
                .add();

        network.newLine()
                .setId("l12")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();

        network.newLine()
                .setId("l25")
                .setBus1("b2")
                .setBus2("b5")
                .setR(1)
                .setX(3)
                .add();

        network.newLine()
                .setId("l26")
                .setBus1("b2")
                .setBus2("b6")
                .setR(1)
                .setX(3)
                .add();

        network.newDcLine()
                .setId("dl3")
                .setDcNode1("dn3")
                .setDcNode2("dnMiddle")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dl4")
                .setDcNode1("dnMiddle")
                .setDcNode2("dn4")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dl7")
                .setDcNode1("dnMiddle")
                .setDcNode2("dn7")
                .setR(0.1)
                .add();

        network.newDcGround()
                .setId("dg3")
                .setDcNode("dnDummy3")
                .add();

        network.newDcGround()
                .setId("dg4")
                .setDcNode("dnDummy4")
                .add();

        network.newDcGround()
                .setId("dg7")
                .setDcNode("dnDummy7")
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy3")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(25.)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy4")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(25.)
                .setId("conv67")
                .setBus1("b6")
                .setDcNode1("dn7")
                .setDcNode2("dnDummy7")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * ACDC test case.
     * <pre>
     * g1       ld2                                   ld5
     * |         |                                     |
     * b1 -------b2-conv23-dn3--------------dn4-conv45-b5
     * l12       |        dl34                         |
     *           |                                     |
     *           |                                     |
     *           |l25----------------------------------|
     * </pre>
     */
    public static Network createAcDcNetworkWithAcVoltageControl() {
        Network network = createBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-50.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy3")
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400.0)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy4")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.0)
                .add();
        return network;
    }

    /**
     * ACDC test case with AC SubNetworks.
     * <pre>
     * g1         ld2                                  ld5
     * |           |                                     |
     * b1 ------- b2-conv23-dn3--------------dn4-conv45-b5
     * l12                  dl34                         |
     *                                                  g5
     * </pre>
     */
    public static Network createAcDcNetworkWithAcSubNetworks() {
        Network network = createBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");
        network.getLine("l25").remove();
        vl5.newGenerator()
                .setId("g5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setTargetP(50.)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(false)
                .setTargetQ(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(50.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy3")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400.)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy4")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.)
                .add();
        return network;
    }

    /**
     * Bipolar test case
     * <pre>
     *                  dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2 conv23p                                  conv45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12       |   |   |                 |                 |   |  |
     *           |  conv23n              GROUND              conv45n|
     *           |       |                                   |      |
     *           |       dn3n ------------ dl34n ---------- dn4n    |
     *           |                                                  |
     *           |------------------------l25-----------------------|
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModel() {
        Network network = createBipolarBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23p")
                .setBus1("b2")
                .setDcNode1("dn3p")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200.)
                .setId("conv45p")
                .setBus1("b5")
                .setDcNode1("dn4p")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200.)
                .setId("conv45n")
                .setBus1("b5")
                .setDcNode1("dn4n")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * Bipolar test case
     * <pre>
     *                  dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2 conv23p                                  conv45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12       |   |   |                 |                 |   |  |
     *           |  conv23n              GROUND              conv45n|
     *           |       |                                   |      |
     *           |       dn3n ------------ dl34n ---------- dn4n    |
     *           |                                                  |
     *           |------------------------l25-----------------------|
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelGridForming() {
        Network network = createBipolarBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23p")
                .setBus1("b2")
                .setDcNode1("dn3p")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200.)
                .setId("conv45p")
                .setBus1("b5")
                .setDcNode1("dn4p")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200.)
                .setId("conv45n")
                .setBus1("b5")
                .setDcNode1("dn4n")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * Bipolar test case
     * <pre>
     *                   dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2 conv23p                                  conv45p ld5
     * |         |   |   |                                   |   |    |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |----b5
     * l12           |   |                 |                 |   |    |
     *              conv23n              GROUND              conv45n g5
     *                   |                                   |
     *                   dn3n ------------ dl34n ---------- dn4n
     *
     *
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelWithAcSubNetworks() {
        Network network = createBipolarBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");
        vl5.newGenerator()
                .setId("g5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setTargetP(102.56)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();
        network.getLine("l25").remove();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23p")
                .setBus1("b2")
                .setDcNode1("dn3p")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200.)
                .setId("conv45p")
                .setBus1("b5")
                .setDcNode1("dn4p")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200.)
                .setId("conv45n")
                .setBus1("b5")
                .setDcNode1("dn4n")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * Bipolar test case
     * <pre>
     *                  dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2 conv23p                                  conv45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12       |   |   |                 |                 |   |  |
     *           |  conv23n              GROUND              conv45n|
     *           |       |                                   |      |
     *           |       dn3n ------------ dl34n ---------- dn4n    |
     *           |                                                  |
     *           |------------------------l25-----------------------|
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelWithOtherControl() {
        Network network = createBipolarBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200.)
                .setId("conv23p")
                .setBus1("b2")
                .setDcNode1("dn3p")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200.)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(25)
                .setId("conv45p")
                .setBus1("b5")
                .setDcNode1("dn4p")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(25)
                .setId("conv45n")
                .setBus1("b5")
                .setDcNode1("dn4n")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * Bipolar test case
     * <pre>
     *                  dn3p -- dl3Mp -- dnMp ---------------------- dlM4p -- dn4p
     *                   |                 |                                   |
     * g1       ld2  conv2p                |       GROUND                     conv5p  ld5
     * |         |   |   |                 |         |                         |   |  |
     * b1 ------- b2-|  dn3r -- dl3Mr ------------ dnMr ------------ dlM4r -- dn4r |-b5
     * l12       |   |   |                 |         |                         |   |  |
     *           |   conv2n                |         |                        conv5n  |
     *           |       |                 |         |                         |      |
     *           |      dn3n -- dl3Mn ---------------------- dnMn -- dlM4n -- dn4n    |
     *           |                         |         |         |                      |
     *           |                         |         |         |                      |
     *           |                       dlM6p     dlM6r     dlM6n                    |
     *           |                        |          |          |                     |
     *           |                     dn6p-conv6p-dn6r-conv6n-dn6n                   |
     *           |                             |         |                            |
     *           |-------------l26-------------|--- b6 --|                            |
     *           |                                 ld6                                |
     *           |-------------l25----------------------------------------------------|
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelWithThreeConverters() {
        Network network = Network.create("vsc", "test");

        Substation s1 = network.newSubstation()
                .setId("S1")
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
                .setTargetP(102.56)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        Substation s2 = network.newSubstation()
                .setId("S2")
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
                .setId("ld2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(20)
                .setQ0(10)
                .add();

        Substation s6 = network.newSubstation()
                .setId("S6")
                .add();
        VoltageLevel vl6 = s6.newVoltageLevel()
                .setId("vl6")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl6.getBusBreakerView().newBus()
                .setId("b6")
                .add();
        vl6.newLoad()
                .setId("ld6")
                .setConnectableBus("b6")
                .setBus("b6")
                .setP0(25)
                .setQ0(10)
                .add();

        Substation s5 = network.newSubstation()
                .setId("S5")
                .add();
        VoltageLevel vl5 = s5.newVoltageLevel()
                .setId("vl5")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl5.getBusBreakerView().newBus()
                .setId("b5")
                .add();
        vl5.newLoad()
                .setId("ld5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setP0(25)
                .setQ0(10)
                .add();

        network.newDcNode()
                .setId("dn3p")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn3n")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn3r")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4p")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4n")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4r")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn6p")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn6n")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn6r")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnMp")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnMn")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnMr")
                .setNominalV(400.)
                .add();
        network.newDcGround()
                .setId("Ground")
                .setDcNode("dnMr")
                .add();

        network.newLine()
                .setId("l12")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();

        network.newLine()
                .setId("l25")
                .setBus1("b2")
                .setBus2("b5")
                .setR(1)
                .setX(3)
                .add();

        network.newLine()
                .setId("l26")
                .setBus1("b2")
                .setBus2("b6")
                .setR(1)
                .setX(3)
                .add();

        network.newDcLine()
                .setId("dl3Mp")
                .setDcNode1("dn3p")
                .setDcNode2("dnMp")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dl3Mn")
                .setDcNode1("dn3n")
                .setDcNode2("dnMn")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dl3Mr")
                .setDcNode1("dn3r")
                .setDcNode2("dnMr")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dlM4p")
                .setDcNode1("dnMp")
                .setDcNode2("dn4p")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dlM4n")
                .setDcNode1("dnMn")
                .setDcNode2("dn4n")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dlM4r")
                .setDcNode1("dnMr")
                .setDcNode2("dn4r")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dlM6p")
                .setDcNode1("dnMp")
                .setDcNode2("dn6p")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dlM6n")
                .setDcNode1("dnMn")
                .setDcNode2("dn6n")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dlM6r")
                .setDcNode1("dnMr")
                .setDcNode2("dn6r")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23p")
                .setBus1("b2")
                .setDcNode1("dn3p")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200)
                .setId("conv45p")
                .setBus1("b5")
                .setDcNode1("dn4p")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12.5)
                .setId("conv45n")
                .setBus1("b5")
                .setDcNode1("dn4n")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12.5)
                .setId("conv6p")
                .setBus1("b6")
                .setDcNode1("dn6p")
                .setDcNode2("dn6r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200)
                .setId("conv6n")
                .setBus1("b6")
                .setDcNode1("dn6n")
                .setDcNode2("dn6r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400)
                .add();
        return network;
    }

    /**
     * Bipolar test case
     * <pre>
     *                  dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2 conv23p                                  conv45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12       |   |   |                 |                 |   |  |
     *           |  conv23n              GROUND              conv45n|
     *           |       |                                   |      |
     *           |       dn3n ------------ dl34n ---------- dn4n    |
     *           |                                                  |
     *           |------------------------l25------------------------
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelWithAcVoltageControl() {
        Network network = createBipolarBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23p")
                .setBus1("b2")
                .setDcNode1("dn3p")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200.)
                .setId("conv45p")
                .setBus1("b5")
                .setDcNode1("dn4p")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200.)
                .setId("conv45n")
                .setBus1("b5")
                .setDcNode1("dn4n")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * Bipolar test case
     * <pre>
     *                  dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2 conv23p                                  conv45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12           |   |                 |                 |   |
     *              conv23n              GROUND              conv45n
     *                   |                                   |
     *                   dn3n ------------ dl34n ---------- dn4n
     *
     *
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelWithAcSubNetworksAndVoltageControl() {
        Network network = createBipolarBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");
        network.getLine("l25").remove();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23p")
                .setBus1("b2")
                .setDcNode1("dn3p")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(389.7)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200.)
                .setId("conv45p")
                .setBus1("b5")
                .setDcNode1("dn4p")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200.)
                .setId("conv45n")
                .setBus1("b5")
                .setDcNode1("dn4n")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * Two embedded DC networks
     * <pre>
     *       |-----------------l16----------------|
     *       |                                    |
     * ld1--b1---conv12-dn2---dl23---dn3-conv36---b6--g6
     *       |                                    |
     *       |                                    |
     *       |---conv14-dn4---dl45---dn5-conv56---|
     * </pre>
     */
    public static Network createAcDcNetworkTwoDcSubNetworks() {
        Network network = Network.create("vsc", "test");

        // Create the two substations
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newLoad()
                .setId("ld1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setP0(500)
                .setQ0(50)
                .add();

        Substation s6 = network.newSubstation()
                .setId("S6")
                .add();
        VoltageLevel vl6 = s6.newVoltageLevel()
                .setId("vl6")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl6.getBusBreakerView().newBus()
                .setId("b6")
                .add();
        vl6.newGenerator()
                .setId("g6")
                .setConnectableBus("b6")
                .setBus("b6")
                .setTargetP(300)
                .setTargetV(400)
                .setMinP(0)
                .setMaxP(1000)
                .setVoltageRegulatorOn(true)
                .add();

        // Connect the buses through an AC line to ensure there is only one AC Island
        network.newLine()
                .setId("l16")
                .setBus1("b1")
                .setConnectableBus1("b1")
                .setBus2("b6")
                .setConnectableBus2("b6")
                .setR(1)
                .setX(5)
                .add();

        // Create a first DC network
        network.newDcNode()
                .setId("dn2")
                .setNominalV(500)
                .add();
        network.newDcNode()
                .setId("dn2Gr")
                .setNominalV(500)
                .add();
        network.newDcNode()
                .setId("dn3")
                .setNominalV(500)
                .add();
        network.newDcNode()
                .setId("dn3Gr")
                .setNominalV(500)
                .add();
        network.newDcGround()
                .setId("dg2")
                .setDcNode("dn2Gr")
                .add();
        network.newDcGround()
                .setId("dg3")
                .setDcNode("dn3Gr")
                .add();
        network.newDcLine()
                .setId("dl23")
                .setDcNode1("dn2")
                .setDcNode2("dn3")
                .setConnected1(true)
                .setConnected2(true)
                .setR(1)
                .add();

        vl1.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(250)
                .setId("conv12")
                .setBus1("b1")
                .setDcNode1("dn2")
                .setDcNode2("dn2Gr")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(500)
                .setId("conv36")
                .setBus1("b6")
                .setDcNode1("dn3")
                .setDcNode2("dn3Gr")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        // Create a second DC network
        network.newDcNode()
                .setId("dn4")
                .setNominalV(300)
                .add();
        network.newDcNode()
                .setId("dn4Gr")
                .setNominalV(300)
                .add();
        network.newDcNode()
                .setId("dn5")
                .setNominalV(300)
                .add();
        network.newDcNode()
                .setId("dn5Gr")
                .setNominalV(300)
                .add();
        network.newDcGround()
                .setId("dg4")
                .setDcNode("dn4Gr")
                .add();
        network.newDcGround()
                .setId("dg5")
                .setDcNode("dn5Gr")
                .add();
        network.newDcLine()
                .setId("dl45")
                .setDcNode1("dn4")
                .setDcNode2("dn5")
                .setConnected1(true)
                .setConnected2(true)
                .setR(1)
                .add();

        vl1.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(200)
                .setId("conv14")
                .setBus1("b1")
                .setDcNode1("dn4")
                .setDcNode2("dn4Gr")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(300)
                .setId("conv56")
                .setBus1("b6")
                .setDcNode1("dn5")
                .setDcNode2("dn5Gr")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        return network;
    }

    /**
     * VSC test case.
     * <pre>
     * ld1                                            g5                                     ld9
     * |                                              |                                       |
     * b1-------conv12-dn2-------dn3-conv34-b4-------b5-------b6-conv67-dn7-------dn8-conv89-b9
     *                 dl23                 l45      l56                dl78
     *
     * </pre>
     */
    public static Network createAcDcNetworkDcSubNetworks() {
        Network network = Network.create("vsc", "test");

        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newLoad()
                .setId("ld1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setP0(20)
                .setQ0(10)
                .add();

        Substation s4 = network.newSubstation()
                .setId("S4")
                .add();
        VoltageLevel vl4 = s4.newVoltageLevel()
                .setId("vl4")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl4.getBusBreakerView().newBus()
                .setId("b4")
                .add();

        Substation s6 = network.newSubstation()
                .setId("S6")
                .add();
        VoltageLevel vl6 = s6.newVoltageLevel()
                .setId("vl6")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl6.getBusBreakerView().newBus()
                .setId("b6")
                .add();

        Substation s5 = network.newSubstation()
                .setId("S5")
                .add();
        VoltageLevel vl5 = s5.newVoltageLevel()
                .setId("vl5")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl5.getBusBreakerView().newBus()
                .setId("b5")
                .add();
        vl5.newGenerator()
                .setId("g5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setTargetP(102.56)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        Substation s9 = network.newSubstation()
                .setId("S9")
                .add();
        VoltageLevel vl9 = s9.newVoltageLevel()
                .setId("vl9")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl9.getBusBreakerView().newBus()
                .setId("b9")
                .add();
        vl9.newLoad()
                .setId("ld9")
                .setConnectableBus("b9")
                .setBus("b9")
                .setP0(20)
                .setQ0(10)
                .add();

        network.newDcNode()
                .setId("dn2")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn3")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn7")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn8")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dnGround")
                .setNominalV(400.)
                .add();

        network.newLine()
                .setId("l45")
                .setBus1("b4")
                .setBus2("b5")
                .setR(1)
                .setX(3)
                .add();

        network.newLine()
                .setId("l56")
                .setBus1("b5")
                .setBus2("b6")
                .setR(1)
                .setX(3)
                .add();

        network.newDcLine()
                .setId("dl23")
                .setDcNode1("dn2")
                .setDcNode2("dn3")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dl78")
                .setDcNode1("dn7")
                .setDcNode2("dn8")
                .setR(0.1)
                .add();

        network.newDcGround()
                .setId("dg")
                .setDcNode("dnGround")
                .add();

        vl1.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400)
                .setId("conv12")
                .setBus1("b1")
                .setDcNode1("dn2")
                .setDcNode2("dnGround")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.)
                .add();

        vl4.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25.)
                .setId("conv34")
                .setBus1("b4")
                .setDcNode1("dn3")
                .setDcNode2("dnGround")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25.)
                .setId("conv67")
                .setBus1("b6")
                .setDcNode1("dn7")
                .setDcNode2("dnGround")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl9.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(0.001)
                .setResistiveLoss(1)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400)
                .setId("conv89")
                .setBus1("b9")
                .setDcNode1("dn8")
                .setDcNode2("dnGround")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.)
                .add();
        return network;
    }

    /**
     * Rigid bipole test case
     * <pre>
     *                   dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2 conv23p                                  conv45p ld5
     * |         |   |   |                                   |   |    |
     * b1 ------ b2--|   dn3r -- GROUND          GROUND -- dn4r  |----b5
     * l12           |   |                                   |   |    |
     *              conv23n                                  conv45n g5
     *                   |                                   |
     *                   dn3n ------------ dl34n ---------- dn4n
     *
     *
     * </pre>
     */
    public static Network createBipolarModelWithoutMetallicReturn() {
        Network network = Network.create("vsc", "test");

        Substation s1 = network.newSubstation()
                .setId("S1")
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
                .setTargetP(102.56)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        Substation s2 = network.newSubstation()
                .setId("S2")
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
                .setId("ld2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(20)
                .setQ0(10)
                .add();

        network.newLine()
                .setId("l12")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();

        Substation s5 = network.newSubstation()
                .setId("S5")
                .add();
        VoltageLevel vl5 = s5.newVoltageLevel()
                .setId("vl5")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl5.getBusBreakerView().newBus()
                .setId("b5")
                .add();
        vl5.newLoad()
                .setId("ld5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setP0(50)
                .setQ0(10)
                .add();

        network.newLine()
                .setId("l25")
                .setBus1("b2")
                .setBus2("b5")
                .setR(1)
                .setX(3)
                .add();

        // Create the DC network
        network.newDcNode()
                .setId("dn3p")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn3n")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn3r")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4p")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4n")
                .setNominalV(400.)
                .add();
        network.newDcNode()
                .setId("dn4r")
                .setNominalV(400.)
                .add();
        network.newDcGround()
                .setId("Ground3")
                .setDcNode("dn3r")
                .add();
        network.newDcGround()
                .setId("Ground4")
                .setDcNode("dn4r")
                .add();

        network.newDcLine()
                .setId("dl34p")
                .setDcNode1("dn3p")
                .setDcNode2("dn4p")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dl34n")
                .setDcNode1("dn3n")
                .setDcNode2("dn4n")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.0)
                .setSwitchingLoss(0.0)
                .setResistiveLoss(0.0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25.)
                .setId("conv23p")
                .setBus1("b2")
                .setDcNode1("dn3p")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.0)
                .setSwitchingLoss(0.0)
                .setResistiveLoss(0.0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25.)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.0)
                .setSwitchingLoss(0.0)
                .setResistiveLoss(0.0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200.)
                .setId("conv45p")
                .setBus1("b5")
                .setDcNode1("dn4p")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.0)
                .setSwitchingLoss(0.0)
                .setResistiveLoss(0.0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200)
                .setId("conv45n")
                .setBus1("b5")
                .setDcNode1("dn4n")
                .setDcNode2("dn4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }
}
