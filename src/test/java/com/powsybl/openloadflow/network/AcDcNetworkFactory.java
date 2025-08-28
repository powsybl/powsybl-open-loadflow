package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.util.PerUnit;
public class AcDcNetworkFactory extends AbstractLoadFlowNetworkFactory {

    /**
     * VSC test case.
     * <pre>
     * g1       ld2                                         ld5
     * |         |                                          |
     * b1 ------- b2-cs23-dcNode3--------------dcNode4-cs45-b5
     * l12       |        dcLine34                         |
     *           |                                         |
     *           |                                         |
     *           |l23---------------------------------------
     * </pre>
     */
    public static Network createAcDcNetwork1() {
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

        network.newDcNode().setId("dcNode3").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeDummy").setNominalV(400.).add();


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
                .setId("dcLine34")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNode4")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(0.62505149 * 400)
                .setId("converter23")
                .setBus1("b2")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(0.509949196 * PerUnit.SB)
                .setId("converter45")
                .setBus1("b5")
                .setDcNode1("dcNode4")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * VSC test case.
     * <pre>
     * g1       ld2                                         ld5
     * |         |                                          |
     * b1 ------- b2-cs23-dcNode3-------------dcNode4-cs45-b5
     * l12       |        dcLine34                         |
     *           |                                         |
     *           |                                         |
     *           |l23---------------------------------------
     * </pre>
     */
    public static Network createAcDcNetwork2() {
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

        network.newDcNode().setId("dcNode3").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeDummy").setNominalV(400.).add();


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
                .setId("dcLine34")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNode4")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-52.)
                .setId("converter23")
                .setBus1("b2")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(250.)
                .setId("converter45")
                .setBus1("b5")
                .setDcNode1("dcNode4")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }

    /**
     * VSC test case.
     * <pre>
     * g1       ld2                                                           ld5
     * |         |                                                             |
     * b1 ------- b2-cs23-dcNode3----------dcNodeMiddle----------dcNode4-cs45-b5
     * l12       |        dcLine3             dcLine5            dcLine4      |
     *           |                                |                           |
     *           |                            dcNode5-cs56-b6_ld6             |
     *           |l23----------------------------------------------------------
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
                .setP0(50)
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
                .setP0(50)
                .setQ0(10)
                .add();

        network.newDcNode().setId("dcNode3").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4").setNominalV(400.).add();
        network.newDcNode().setId("dcNode5").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeMiddle").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeDummy").setNominalV(400.).add();


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

        network.newLine()
                .setId("l26")
                .setBus1("b2")
                .setBus2("b6")
                .setR(1)
                .setX(3)
                .add();

        network.newDcLine()
                .setId("dcLine3")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNodeMiddle")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine4")
                .setDcNode1("dcNode4")
                .setDcNode2("dcNodeMiddle")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine5")
                .setDcNode1("dcNode5")
                .setDcNode2("dcNodeMiddle")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(52.)
                .setTargetVdc(320.)
                .setId("converter23")
                .setBus1("b2")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetP(50.)
                .setTargetVdc(250.)
                .setId("converter45")
                .setBus1("b5")
                .setDcNode1("dcNode4")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(50.)
                .setTargetVdc(250.)
                .setId("converter56")
                .setBus1("b6")
                .setDcNode1("dcNode5")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }


    /**
     * VSC test case.
     * <pre>
     * g1       ld2                                         ld5
     * |         |                                          |
     * b1 ------- b2-cs23-dcNode3-------------dcNode4-cs45-b5
     * l12       |       dcLine34                          |
     *           |                                         |
     *           |                                         |
     *           |l23---------------------------------------
     * </pre>
     */
    public static Network createAcDcNetworkWithAcVoltageControl() {
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
                .setP0(30)
                .setQ0(10)
                .add();

        network.newDcNode().setId("dcNode3").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeDummy").setNominalV(400.).add();


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
                .setP0(30)
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
                .setId("dcLine34")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNode4")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-200.)
                .setTargetVdc(320.)
                .setId("converter23")
                .setBus1("b2")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNodeDummy")
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0)
                .setVoltageSetpoint(400)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(600)
                .setId("converter45")
                .setBus1("b5")
                .setDcNode1("dcNode4")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400)
                .add();
        return network;
    }



    /**
     * VSC test case.
     * <pre>
     * g1       ld2                                         ld5
     * |         |                                          |
     * b1 ------- b2-cs23-dcNode3-------dcNode4-cs45-b5
     * l12               dcLine34
     *
     *
     *
     * </pre>
     */
    public static Network createAcDcNetworkWithAcSubNetworks() {
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

        network.newDcNode().setId("dcNode3").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeDummy").setNominalV(400.).add();


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
        vl5.newGenerator()
                .setId("g5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setTargetP(300)
                .setTargetV(450)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        network.newLine()
                .setId("l12")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();


        network.newDcLine()
                .setId("dcLine34")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNode4")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(52.)
                .setTargetVdc(320.)
                .setId("converter23")
                .setBus1("b2")
                .setDcNode1("dcNode3")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetP(50.)
                .setTargetVdc(250.)
                .setId("converter45")
                .setBus1("b5")
                .setDcNode1("dcNode4")
                .setDcNode2("dcNodeDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }


    /**
     * Bipolar test case
     * <pre>             dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2  cs23p                                   cs45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12       |   |   |                 |                 |   |  |
     *           |   cs23n              GROUND               cs45n  |
     *           |       |                                   |      |
     *           |       dn3n ------------ dl34n ---------- dn4n    |
     *           |                                                  |
     *           |------------------------l25------------------------
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModel() {
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


        network.newDcNode().setId("dcNode3p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3r").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4r").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeGr").setNominalV(400.).add();
        network.getDcNode("dcNodeGr").setV(0.0);

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
                .setId("dcLine34p")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode4p")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine34n")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode4n")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine3Gr")
                .setDcNode1("dcNode3r")
                .setDcNode2("dcNodeGr")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLineG4r")
                .setDcNode1("dcNodeGr")
                .setDcNode2("dcNode4r")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("converter23p")
                .setBus1("b2")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("converter23n")
                .setBus1("b2")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(125.)
                .setId("converter45p")
                .setBus1("b5")
                .setDcNode1("dcNode4p")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-125.)
                .setId("converter45n")
                .setBus1("b5")
                .setDcNode1("dcNode4n")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }


    /**
     * Bipolar test case
     * <pre>             dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2  cs23p                                   cs45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12           |   |                 |                 |   |
     *               cs23n              GROUND               cs45n
     *                   |                                   |
     *                   dn3n ------------ dl34n ---------- dn4n
     *
     *
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelWithAcSubNetworks() {
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


        network.newDcNode().setId("dcNode3p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3r").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4r").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeGr").setNominalV(400.).add();
        network.getDcNode("dcNodeGr").setV(0.0);

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
        vl5.newGenerator()
                .setId("g5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setTargetP(300)
                .setTargetV(450)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        network.newLine()
                .setId("l12")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();

        network.newDcLine()
                .setId("dcLine34p")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode4p")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine34n")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode4n")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine3Gr")
                .setDcNode1("dcNode3r")
                .setDcNode2("dcNodeGr")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLineG4r")
                .setDcNode1("dcNodeGr")
                .setDcNode2("dcNode4r")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("converter23p")
                .setBus1("b2")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("converter23n")
                .setBus1("b2")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(125.)
                .setId("converter45p")
                .setBus1("b5")
                .setDcNode1("dcNode4p")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-125.)
                .setId("converter45n")
                .setBus1("b5")
                .setDcNode1("dcNode4n")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }


    /**
     * Bipolar test case
     * <pre>             dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2  cs23p                                   cs45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12       |   |   |                 |                 |   |  |
     *           |   cs23n              GROUND               cs45n  |
     *           |       |                                   |      |
     *           |       dn3n ------------ dl34n ---------- dn4n    |
     *           |                                                  |
     *           |------------------------l25------------------------
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelWithOtherControl() {
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


        network.newDcNode().setId("dcNode3p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3r").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4r").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeGr").setNominalV(400.).add();
        network.getDcNode("dcNodeGr").setV(0.0);

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
                .setId("dcLine34p")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode4p")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine34n")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode4n")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine3Gr")
                .setDcNode1("dcNode3r")
                .setDcNode2("dcNodeGr")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLineG4r")
                .setDcNode1("dcNodeGr")
                .setDcNode2("dcNode4r")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(125.)
                .setId("converter23p")
                .setBus1("b2")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-125.)
                .setId("converter23n")
                .setBus1("b2")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(25)
                .setId("converter45p")
                .setBus1("b5")
                .setDcNode1("dcNode4p")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(25)
                .setId("converter45n")
                .setBus1("b5")
                .setDcNode1("dcNode4n")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }


    /**
     * Bipolar test case
     * <pre>             dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2  cs23p                                   cs45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12       |   |   |                 |                 |   |  |
     *           |   cs23n              GROUND               cs45n  |
     *           |       |                                   |      |
     *           |       dn3n ------------ dl34n ---------- dn4n    |
     *           |                                                  |
     *           |------------------------l25------------------------
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
                .setP0(50)
                .setQ0(10)
                .add();


        network.newDcNode().setId("dcNode3p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3r").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4r").setNominalV(400.).add();
        network.newDcNode().setId("dcNode6p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode6n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode6r").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeMp").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeMn").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeMr").setNominalV(400.).add();
        network.getDcNode("dcNodeMr").setV(0.0);

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

        network.newLine()
                .setId("l26")
                .setBus1("b2")
                .setBus2("b6")
                .setR(1)
                .setX(3)
                .add();

        network.newDcLine()
                .setId("dcLine3Mp")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNodeMp")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dcLine3Mn")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNodeMn")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dcLine3Mr")
                .setDcNode1("dcNode3r")
                .setDcNode2("dcNodeMr")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dcLineM4p")
                .setDcNode1("dcNodeMp")
                .setDcNode2("dcNode4p")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dcLineM4n")
                .setDcNode1("dcNodeMn")
                .setDcNode2("dcNode4n")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dcLineM4r")
                .setDcNode1("dcNodeMr")
                .setDcNode2("dcNode4r")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dcLineM6p")
                .setDcNode1("dcNodeMp")
                .setDcNode2("dcNode6p")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dcLineM6n")
                .setDcNode1("dcNodeMn")
                .setDcNode2("dcNode6n")
                .setR(0.1)
                .add();
        network.newDcLine()
                .setId("dcLineM6r")
                .setDcNode1("dcNodeMr")
                .setDcNode2("dcNode6r")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(125.)
                .setId("converter23p")
                .setBus1("b2")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-125.)
                .setId("converter23n")
                .setBus1("b2")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12)
                .setId("converter45p")
                .setBus1("b5")
                .setDcNode1("dcNode4p")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12)
                .setId("converter45n")
                .setBus1("b5")
                .setDcNode1("dcNode4n")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12)
                .setId("converter6p")
                .setBus1("b6")
                .setDcNode1("dcNode6p")
                .setDcNode2("dcNode6r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl6.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12)
                .setId("converter6n")
                .setBus1("b6")
                .setDcNode1("dcNode6n")
                .setDcNode2("dcNode6r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }


    /**
     * Bipolar test case
     * <pre>             dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2  cs23p                                   cs45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12       |   |   |                 |                 |   |  |
     *           |   cs23n              GROUND               cs45n  |
     *           |       |                                   |      |
     *           |       dn3n ------------ dl34n ---------- dn4n    |
     *           |                                                  |
     *           |------------------------l25------------------------
     * </pre>
     */
    public static Network createAcDcNetworkBipolarModelWithAcVoltageControl() {
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


        network.newDcNode().setId("dcNode3p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode3r").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4p").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4n").setNominalV(400.).add();
        network.newDcNode().setId("dcNode4r").setNominalV(400.).add();
        network.newDcNode().setId("dcNodeGr").setNominalV(400.).add();
        network.getDcNode("dcNodeGr").setV(0.0);

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
                .setId("dcLine34p")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode4p")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine34n")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode4n")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLine3Gr")
                .setDcNode1("dcNode3r")
                .setDcNode2("dcNodeGr")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dcLineG4r")
                .setDcNode1("dcNodeGr")
                .setDcNode2("dcNode4r")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("converter23p")
                .setBus1("b2")
                .setDcNode1("dcNode3p")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("converter23n")
                .setBus1("b2")
                .setDcNode1("dcNode3n")
                .setDcNode2("dcNode3r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(200.)
                .setId("converter45p")
                .setBus1("b5")
                .setDcNode1("dcNode4p")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-200.)
                .setId("converter45n")
                .setBus1("b5")
                .setDcNode1("dcNode4n")
                .setDcNode2("dcNode4r")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();
        return network;
    }
}