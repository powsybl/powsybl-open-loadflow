/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class SwitchLoopIssueNetworkFactory {

    private SwitchLoopIssueNetworkFactory() {
    }

    /**
     *                      LD(1)
     *                       |
     *              ----------------------- BBS1(0)      VL1
     *                 |              |(2)
     *                BR5            BR2
     *                 |              |
     *                 | (3)          |
     *                 | L1           |
     *                 | (2)          |
     *                 |              | L2
     *           ------------         |
     *           |          |         |
     *           D2         |         |
     *           |(3)       |         |(1)               VL2
     *           BR1        |        BR3
     *           |          |         |
     *   BBS2(0)---------------BR4-------- BBS3(5)
     *                                |
     *                               G(4)
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
        vl2.getNodeBreakerView().newBusbarSection()
                .setId("BBS3")
                .setNode(5)
                .add();
        vl1.newLoad()
                .setId("LD")
                .setNode(1)
                .setP0(600.0)
                .setQ0(200.0)
                .add();
        vl2.newGenerator()
                .setId("G")
                .setNode(4)
                .setMinP(-999.99)
                .setMaxP(999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(398)
                .setTargetP(600)
                .setTargetQ(300)
                .add();
        vl2.getNodeBreakerView().newInternalConnection()
                .setNode1(4)
                .setNode2(5)
                .add();
        vl2.getNodeBreakerView().newInternalConnection()
                .setNode1(0)
                .setNode2(2)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("BR1")
                .setNode1(0)
                .setNode2(3)
                .setRetained(true)
                .add();
        vl2.getNodeBreakerView().newDisconnector()
                .setId("D2")
                .setNode1(3)
                .setNode2(2)
                .setRetained(false)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("BR3")
                .setNode1(5)
                .setNode2(1)
                .setRetained(true)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("BR4")
                .setNode1(0)
                .setNode2(5)
                .setRetained(true)
                .add();
        vl1.getNodeBreakerView().newBreaker()
                .setId("BR5")
                .setNode1(0)
                .setNode2(3)
                .setRetained(true)
                .add();
        vl1.getNodeBreakerView().newInternalConnection()
                .setNode1(0)
                .setNode2(1)
                .add();
        vl1.getNodeBreakerView().newBreaker()
                .setId("BR2")
                .setNode1(0)
                .setNode2(2)
                .setRetained(false)
                .add();
        network.newLine()
                .setId("L1")
                .setVoltageLevel1("VL1")
                .setNode1(3)
                .setVoltageLevel2("VL2")
                .setNode2(2)
                .setR(3.0)
                .setX(33.0)
                .setB1(386E-6 / 2)
                .setB2(386E-6 / 2)
                .add();
        network.newLine()
                .setId("L2")
                .setVoltageLevel1("VL1")
                .setNode1(2)
                .setVoltageLevel2("VL2")
                .setNode2(1)
                .setR(3.0)
                .setX(33.0)
                .setB1(386E-6 / 2)
                .setB2(386E-6 / 2)
                .add();
        return network;
    }
}
