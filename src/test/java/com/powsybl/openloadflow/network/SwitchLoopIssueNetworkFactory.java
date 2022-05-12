package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

public class SwitchLoopIssueNetworkFactory {

    /**
     *          LD(1)
     *          |
     *    ------------- BBS1(0)      VL1
     *          |
     *          |
     *          | L1
     *          | (3)
     *          |
     *    ------------
     *    |          |
     *    BR1        |
     *    |(1)       |              VL2
     *    D1        D2
     *    |          |
     *    ----------- BBS2(0)
     *         |
     *         G(4)
     */
    public static Network create() {
        Network network = Network.create("test", "test");
        Substation s = network.newSubstation()
                .setId("S")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        vl1.getNodeBreakerView().newBusbarSection()
                .setId("BBS1")
                .setNode(0)
                .add();
        vl2.getNodeBreakerView().newBusbarSection()
                .setId("BBS2")
                .setNode(0)
                .add();
        vl1.newLoad()
                .setId("LD")
                .setNode(1)
                .setP0(600.0)
                .setQ0(200.0)
                .add();
        vl2.newGenerator()
                .setId("G")
                .setNode(5)
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(398)
                .setTargetP(600)
                .setTargetQ(300)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("D1")
                .setKind(SwitchKind.DISCONNECTOR)
                .setNode1(0)
                .setNode2(2)
                .setRetained(false)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("D1")
                .setKind(SwitchKind.DISCONNECTOR)
                .setNode1(0)
                .setNode2(1)
                .setRetained(false)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("D1")
                .setKind(SwitchKind.DISCONNECTOR)
                .setNode1(0)
                .setNode2(3)
                .setRetained(false)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("D1")
                .setKind(SwitchKind.BREAKER)
                .setNode1(1)
                .setNode2(3)
                .setRetained(true)
                .add();
        network.newLine()
                .setId("L")
                .setVoltageLevel1("VL1")
                .setNode1(0)
                .setVoltageLevel2("VL2")
                .setNode2(3)
                .setR(3.0)
                .setX(33.0)
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
                .setB2(386E-6 / 2)
                .add();
        return network;
    }
}
