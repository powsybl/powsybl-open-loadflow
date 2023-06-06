/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;

public final class NodeBreakerNetworkFactory {

    private NodeBreakerNetworkFactory() {
    }

    /**
     * <pre>
     *                   G
     *              C    |
     * BBS1 -------[+]------- BBS2     VL1
     *         |        [+] B1
     *         |         |
     *     L1  |         | L2
     *         |         |
     *     B3 [+]       [+] B4
     * BBS3 -----------------          VL2
     *             |
     *             LD
     * </pre>
     * 6 buses
     * 6 branches
     *
     *            G
     *            |
     *      o--C--o
     *      |     |
     *      |     B1
     *      |     |
     *      |     o
     *      |     |
     *      L1    L2
     *      |     |
     *      o     o
     *      |     |
     *      B3    B4
     *      |     |
     *      ---o---
     *         |
     *         LD
     *
     * @author Gael Macharel <gael.macherel at artelys.com>
     */
    public static Network create() {
        Network network = Network.create("test", "test");
        Substation s = network.newSubstation()
                .setId("S")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400)
                .setLowVoltageLimit(370.)
                .setHighVoltageLimit(420.)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        vl1.getNodeBreakerView().newBusbarSection()
                .setId("BBS1")
                .setNode(0)
                .add();
        vl1.getNodeBreakerView().newBusbarSection()
                .setId("BBS2")
                .setNode(1)
                .add();
        vl1.getNodeBreakerView().newDisconnector()
            .setId("D")
            .setNode1(0)
            .setNode2(6)
            .add();
        vl1.getNodeBreakerView().newBreaker()
                .setId("C")
                .setNode1(6)
                .setNode2(1)
                .setRetained(true)
                .add();
        vl1.getNodeBreakerView().newBreaker()
                .setId("B1")
                .setNode1(1)
                .setNode2(3)
                .add();
        vl1.getNodeBreakerView().newInternalConnection()
                .setNode1(1)
                .setNode2(4)
                .add();
        vl1.getNodeBreakerView().newInternalConnection()
                .setNode1(0)
                .setNode2(5)
                .add();
        vl1.newGenerator()
                .setId("G")
                .setNode(4)
                .setMinP(0.0)
                .setMaxP(1000.0)
                .setVoltageRegulatorOn(true)
                .setTargetV(398)
                .setTargetP(603.77)
                .setTargetQ(301.0)
                .add();

        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .setLowVoltageLimit(370.)
                .setHighVoltageLimit(420.)
                .add();
        vl2.getNodeBreakerView().newBusbarSection()
                .setId("BBS3")
                .setNode(0)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("B3")
                .setNode1(0)
                .setNode2(1)
                .setRetained(true)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("B4")
                .setNode1(0)
                .setNode2(2)
                .add();
        vl2.getNodeBreakerView().newInternalConnection()
                .setNode1(0)
                .setNode2(3)
                .add();
        vl2.newLoad()
                .setId("LD")
                .setNode(3)
                .setP0(600.0)
                .setQ0(200.0)
                .add();

        network.newLine()
                .setId("L1")
                .setVoltageLevel1("VL1")
                .setNode1(5)
                .setVoltageLevel2("VL2")
                .setNode2(1)
                .setR(3.0)
                .setX(33.0)
                .setB1(386E-6 / 2)
                .setB2(386E-6 / 2)
                .add();

        network.newLine()
                .setId("L2")
                .setVoltageLevel1("VL1")
                .setNode1(3)
                .setVoltageLevel2("VL2")
                .setNode2(2)
                .setR(3.0)
                .setX(33.0)
                .setB1(386E-6 / 2)
                .setB2(386E-6 / 2)
                .add();

        network.getLine("L1").newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine("L1").newCurrentLimits2().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits2().setPermanentLimit(940.0).add();

        return network;
    }

    private static void createBar(VoltageLevel vl, String id, int node) {
        vl.getNodeBreakerView().newBusbarSection().setId(id).setNode(node).add();
    }

    private static void createBreaker(VoltageLevel vl, String id, int node1, int node2) {
        vl.getNodeBreakerView().newBreaker().setId(id).setNode1(node1).setNode2(node2).add();
    }

    private static void createConnection(VoltageLevel vl, int node1, int node2) {
        vl.getNodeBreakerView().newInternalConnection().setNode1(node1).setNode2(node2).add();
    }

    private static void createGenerator(VoltageLevel vl, String id, int node, double v, double p, double q) {
        vl.newGenerator()
            .setId(id)
            .setNode(node)
            .setMinP(-4999.99)
            .setMaxP(4999.99)
            .setVoltageRegulatorOn(true)
            .setTargetV(v)
            .setTargetP(p)
            .setTargetQ(q)
            .add();
    }

    private static void createLine(Network network, String id, String vl1, int node1, String vl2, int node2) {
        network.newLine()
            .setId(id)
            .setVoltageLevel1(vl1)
            .setNode1(node1)
            .setVoltageLevel2(vl2)
            .setNode2(node2)
            .setR(3.0)
            .setX(33.0)
            .setB1(386E-6 / 2)
            .setB2(386E-6 / 2)
            .add();
        network.getLine(id).newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine(id).newCurrentLimits2().setPermanentLimit(940.0).add();
    }

    /**
     *
     * <pre>
     *           G1 (400MW)          G2 (200MW)
     *           |   C1  BBS2   C2   |
     *  BBS1 -------[+] -------[+]------- BBS3     VL1
     *       B1 [+]        |        [+] B4
     *           |         |         |
     *        L1 |      L2 |      L3 |
     *           |         |         |
     *       B2 [+]    B3 [+]       [+] B5
     *  BBS4  ---------------------------          VL2
     *                     |
     *                     LD (600MW)
     *</pre>
     *
     * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
     */
    public static Network create3Bars() {
        Network network = Network.create("test", "test");
        Substation s = network.newSubstation()
            .setId("S")
            .add();
        VoltageLevel vl1 = s.newVoltageLevel()
            .setId("VL1")
            .setNominalV(400)
            .setLowVoltageLimit(370.)
            .setHighVoltageLimit(420.)
            .setTopologyKind(TopologyKind.NODE_BREAKER)
            .add();
        createBar(vl1, "BBS1", 0);
        createBar(vl1, "BBS2", 1);
        createBar(vl1, "BBS3", 2);
        createBreaker(vl1, "C1", 0, 1);
        createBreaker(vl1, "C2", 1, 2);
        createBreaker(vl1, "B1", 0, 5);
        createConnection(vl1, 1, 6);
        createBreaker(vl1, "B4", 2, 7);
        createGenerator(vl1, "G1", 3, 400, 400, 0);
        createConnection(vl1, 0, 3);
        createGenerator(vl1, "G2", 4, 400, 200, 0);
        createConnection(vl1, 2, 4);

        VoltageLevel vl2 = s.newVoltageLevel()
            .setId("VL2")
            .setNominalV(400)
            .setTopologyKind(TopologyKind.NODE_BREAKER)
            .setLowVoltageLimit(370.)
            .setHighVoltageLimit(420.)
            .add();
        createBar(vl2, "BBS4", 0);
        createBreaker(vl2, "B2", 0, 2);
        createBreaker(vl2, "B3", 0, 3);
        createBreaker(vl2, "B5", 0, 4);
        vl2.newLoad()
            .setId("LD")
            .setNode(1)
            .setP0(600.0)
            .setQ0(200.0)
            .add();
        createConnection(vl2, 0, 1);

        createLine(network, "L1", "VL1", 5, "VL2", 2);
        createLine(network, "L2", "VL1", 6, "VL2", 3);
        createLine(network, "L3", "VL1", 7, "VL2", 4);

        return network;
    }

    /**
     *
     * <pre>
     *             G1 (3)                      G2 (4)
     *             |    C1    BBS2 (1)   C2    |
     *  BBS1 (0) -------[+] ------------[+]------- BBS3 (2)
     *                           |
     *                           LD (5)
     *</pre>
     *
     * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
     */
    public static Network create3barsAndJustOneVoltageLevel() {
        Network network = Network.create("test", "test");
        Substation s = network.newSubstation()
                .setId("S")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400)
                .setLowVoltageLimit(370.)
                .setHighVoltageLimit(420.)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        createBar(vl1, "BBS1", 0);
        createBar(vl1, "BBS2", 1);
        createBar(vl1, "BBS3", 2);
        createBreaker(vl1, "C1", 0, 1);
        createBreaker(vl1, "C2", 1, 2);
        network.getSwitch("C1").setRetained(true);
        network.getSwitch("C2").setRetained(true);
        createGenerator(vl1, "G1", 3, 400, 400, 0);
        createConnection(vl1, 0, 3);
        createGenerator(vl1, "G2", 4, 400, 200, 0);
        createConnection(vl1, 2, 4);
        vl1.newLoad()
                .setId("LD")
                .setNode(5)
                .setP0(600.0)
                .setQ0(200.0)
                .add();
        createConnection(vl1, 1, 5);

        return network;
    }
}
