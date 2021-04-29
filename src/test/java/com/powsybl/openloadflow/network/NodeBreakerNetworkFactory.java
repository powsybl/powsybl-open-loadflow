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

public class NodeBreakerNetworkFactory extends AbstractLoadFlowNetworkFactory {
    /**
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
     *
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
        vl1.getNodeBreakerView().newBreaker()
                .setId("C")
                .setNode1(0)
                .setNode2(1)
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
                .setMinP(-9999.99)
                .setMaxP(9999.99)
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
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
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
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
                .setB2(386E-6 / 2)
                .add();

        network.getLine("L1").newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine("L1").newCurrentLimits2().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits2().setPermanentLimit(940.0).add();

        return network;
    }
}
