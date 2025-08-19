package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
public class AcDcNetworkFactory extends AbstractLoadFlowNetworkFactory {

    /**
     * VSC test case.
     * <pre>
     * g1       ld2                                         ld5
     * |         |                                          |
     * b1 ------- b2-cs23-------dcNode3-------dcNode4-cs45-b5
     * l12       |              dcLine34                   |
     *           |                                         |
     *           |                                         |
     *           |l23---------------------------------------
     * </pre>
     *
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
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
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
     * VSC test case.
     * <pre>
     * g1       ld2                                         ld5
     * |         |                                          |
     * b1 ------- b2-cs23-------dcNode3-------dcNode4-cs45-b5
     * l12       |              dcLine34                   |
     *           |                                         |
     *           |                                         |
     *           |l23---------------------------------------
     * </pre>
     *
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
     * VSC test case.
     * <pre>
     * g1       ld2                                                           ld5
     * |         |                                                             |
     * b1 ------- b2-cs23-------dcNode3-------dcNodeMiddle-------dcNode4-cs45-b5
     * l12       |              dcLine3       dcLine5            dcLine4      |
     *           |                                |                           |
     *           |                            dcNode5-cs56-b6_ld6             |
     *           |l23----------------------------------------------------------
     * </pre>
     *
     */
    public static Network createAcDcNetwork3() {
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
}