package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.util.PerUnit;

public class AcDcNetworkFactory extends AbstractLoadFlowNetworkFactory {

    /**
     * VSC test case.
     * <pre>
     * g1       ld2                                         ld5
     * |         |                                          |
     * b1 ------- b2-cs23-dn3--------------dn4-cs45-b5
     * l12       |        dl34                         |
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

        network.newDcNode().setId("dn3").setNominalV(400.).add();
        network.newDcNode().setId("dn4").setNominalV(400.).add();
        network.newDcNode().setId("dnDummy").setNominalV(400.).add();

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

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(0.62505149 * 400)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy")
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
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy")
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
     * b1 ------- b2-cs23-dn3-------------dn4-cs45-b5
     * l12       |        dl34                         |
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

        network.newDcNode().setId("dn3").setNominalV(400.).add();
        network.newDcNode().setId("dn4").setNominalV(400.).add();
        network.newDcNode().setId("dnDummy").setNominalV(400.).add();

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

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-52.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy")
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
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy")
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
     * b1 ------- b2-cs23-dn3----------dnMiddle----------dn4-cs45-b5
     * l12       |        dl3             dl5            dl4      |
     *           |                                |                           |
     *           |                            dn5-cs56-b6_ld6             |
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

        network.newDcNode().setId("dn3").setNominalV(400.).add();
        network.newDcNode().setId("dn4").setNominalV(400.).add();
        network.newDcNode().setId("dn5").setNominalV(400.).add();
        network.newDcNode().setId("dnMiddle").setNominalV(400.).add();
        network.newDcNode().setId("dnDummy").setNominalV(400.).add();

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
                .setId("dl3")
                .setDcNode1("dn3")
                .setDcNode2("dnMiddle")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dl4")
                .setDcNode1("dn4")
                .setDcNode2("dnMiddle")
                .setR(0.1)
                .add();

        network.newDcLine()
                .setId("dl5")
                .setDcNode1("dn5")
                .setDcNode2("dnMiddle")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(52.)
                .setTargetVdc(320.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy")
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
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy")
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
                .setId("conv56")
                .setBus1("b6")
                .setDcNode1("dn5")
                .setDcNode2("dnDummy")
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
     * b1 ------- b2-cs23-dn3-------------dn4-cs45-b5
     * l12       |       dl34                          |
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

        network.newDcNode().setId("dn3").setNominalV(400.).add();
        network.newDcNode().setId("dn4").setNominalV(400.).add();
        network.newDcNode().setId("dnDummy").setNominalV(400.).add();

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
                .setId("dl34")
                .setDcNode1("dn3")
                .setDcNode2("dn4")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-200.)
                .setTargetVdc(320.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy")
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(600)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy")
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
     * b1 ------- b2-cs23-dn3-------dn4-cs45-b5
     * l12               dl34
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

        network.newDcNode().setId("dn3").setNominalV(400.).add();
        network.newDcNode().setId("dn4").setNominalV(400.).add();
        network.newDcNode().setId("dnDummy").setNominalV(400.).add();

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

        network.newDcLine()
                .setId("dl34")
                .setDcNode1("dn3")
                .setDcNode2("dn4")
                .setR(0.1)
                .add();

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(52.)
                .setTargetVdc(320.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.)
                .add();

        vl5.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetP(50.)
                .setTargetVdc(250.)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.)
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

        network.newDcNode().setId("dn3p").setNominalV(400.).add();
        network.newDcNode().setId("dn3n").setNominalV(400.).add();
        network.newDcNode().setId("dn3r").setNominalV(400.).add();
        network.newDcNode().setId("dn4p").setNominalV(400.).add();
        network.newDcNode().setId("dn4n").setNominalV(400.).add();
        network.newDcNode().setId("dn4r").setNominalV(400.).add();
        network.newDcNode().setId("dnGr").setNominalV(400.).add();
        network.getDcNode("dnGr").setV(0.0);

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

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(125.)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-125.)
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
     * <pre>             dn3p ------------ dl34p ---------- dn4p
     *                   |                                   |
     * g1       ld2  cs23p                                   cs45p ld5
     * |         |   |   |                                   |   |  |
     * b1 ------- b2-|   dn3r -- dl3Gr -- dcGr -- dlG4r -- dn4r  |-b5
     * l12           |   |                 |                 |   |  |
     *               cs23n              GROUND               cs45n g5
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

        network.newDcNode().setId("dn3p").setNominalV(400.).add();
        network.newDcNode().setId("dn3n").setNominalV(400.).add();
        network.newDcNode().setId("dn3r").setNominalV(400.).add();
        network.newDcNode().setId("dn4p").setNominalV(400.).add();
        network.newDcNode().setId("dn4n").setNominalV(400.).add();
        network.newDcNode().setId("dn4r").setNominalV(400.).add();
        network.newDcNode().setId("dnGr").setNominalV(400.).add();
        network.getDcNode("dnGr").setV(0.0);

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

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(125.)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-125.)
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

        network.newDcNode().setId("dn3p").setNominalV(400.).add();
        network.newDcNode().setId("dn3n").setNominalV(400.).add();
        network.newDcNode().setId("dn3r").setNominalV(400.).add();
        network.newDcNode().setId("dn4p").setNominalV(400.).add();
        network.newDcNode().setId("dn4n").setNominalV(400.).add();
        network.newDcNode().setId("dn4r").setNominalV(400.).add();
        network.newDcNode().setId("dnGr").setNominalV(400.).add();
        network.getDcNode("dnGr").setV(0.0);

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

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(125.)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-125.)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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

        network.newDcNode().setId("dn3p").setNominalV(400.).add();
        network.newDcNode().setId("dn3n").setNominalV(400.).add();
        network.newDcNode().setId("dn3r").setNominalV(400.).add();
        network.newDcNode().setId("dn4p").setNominalV(400.).add();
        network.newDcNode().setId("dn4n").setNominalV(400.).add();
        network.newDcNode().setId("dn4r").setNominalV(400.).add();
        network.newDcNode().setId("dn6p").setNominalV(400.).add();
        network.newDcNode().setId("dn6n").setNominalV(400.).add();
        network.newDcNode().setId("dn6r").setNominalV(400.).add();
        network.newDcNode().setId("dnMp").setNominalV(400.).add();
        network.newDcNode().setId("dnMn").setNominalV(400.).add();
        network.newDcNode().setId("dnMr").setNominalV(400.).add();
        network.getDcNode("dnMr").setV(0.0);

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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(125.)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(-125.)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(12)
                .setId("conv6n")
                .setBus1("b6")
                .setDcNode1("dn6n")
                .setDcNode2("dn6r")
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

        network.newDcNode().setId("dn3p").setNominalV(400.).add();
        network.newDcNode().setId("dn3n").setNominalV(400.).add();
        network.newDcNode().setId("dn3r").setNominalV(400.).add();
        network.newDcNode().setId("dn4p").setNominalV(400.).add();
        network.newDcNode().setId("dn4n").setNominalV(400.).add();
        network.newDcNode().setId("dn4r").setNominalV(400.).add();
        network.newDcNode().setId("dnGr").setNominalV(400.).add();
        network.getDcNode("dnGr").setV(0.0);

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

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
    public static Network createAcDcNetworkBipolarModelWithAcSubNetworksAndVoltageControl() {
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

        network.newDcNode().setId("dn3p").setNominalV(400.).add();
        network.newDcNode().setId("dn3n").setNominalV(400.).add();
        network.newDcNode().setId("dn3r").setNominalV(400.).add();
        network.newDcNode().setId("dn4p").setNominalV(400.).add();
        network.newDcNode().setId("dn4n").setNominalV(400.).add();
        network.newDcNode().setId("dn4r").setNominalV(400.).add();
        network.newDcNode().setId("dnGr").setNominalV(400.).add();
        network.getDcNode("dnGr").setV(0.0);

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

        vl2.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25)
                .setId("conv23n")
                .setBus1("b2")
                .setDcNode1("dn3n")
                .setDcNode2("dn3r")
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
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
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
     * VSC test case.
     * <pre>
     * ld1                                                   g5                                              ld9
     * |                                                     |                                               |
     * b1 ------- cs12 - dn2 ------- dn3 - cs34 - b4 ------- b5 ------- b6 - cs67 - dn7 ------- dn8 - cs89 - b9
     *                   dl23                     l45        l56                    dl78
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
                .setId("dnDummy")
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

        vl1.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400)
                .setId("conv12")
                .setBus1("b1")
                .setDcNode1("dn2")
                .setDcNode2("dnDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.)
                .add();

        vl4.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-25.)
                .setId("conv34")
                .setBus1("b4")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy")
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
                .setTargetP(-25.)
                .setId("conv67")
                .setBus1("b6")
                .setDcNode1("dn7")
                .setDcNode2("dnDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        vl9.newVoltageSourceConverter()
                .setIdleLoss(0.5)
                .setSwitchingLoss(1.0)
                .setResistiveLoss(0.2)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400)
                .setId("conv89")
                .setBus1("b9")
                .setDcNode1("dn8")
                .setDcNode2("dnDummy")
                .setDcConnected1(true)
                .setDcConnected2(false)
                .setVoltageRegulatorOn(true)
                .setVoltageSetpoint(400.)
                .add();
        return network;
    }
}
