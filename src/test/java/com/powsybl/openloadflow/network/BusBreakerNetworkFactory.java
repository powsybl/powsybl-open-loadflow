/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

public final class BusBreakerNetworkFactory {

    private BusBreakerNetworkFactory() {
    }

    /**
     * <pre>
     *                   G
     *              C    |
     * BBS1 -------[+]------- BBS2     VL1
     *         |         |
     *         |         |
     *     L1  |         | L2
     *         |         |
     * BBS3 -----------------          VL2
     *             |
     *             LD
     * </pre>
     *
     * @author Anne Tilloy <anne.tilloy at rte-france.com>
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
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("BBS11")
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("BBS1")
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("BBS2")
                .add();
        createBreaker(vl1, "C", "BBS1", "BBS2");
        createGenerator(vl1, "G", "BBS1", 398, 603.77, 301.0);

        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .setLowVoltageLimit(370.)
                .setHighVoltageLimit(420.)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("BBS3")
                .add();
        createLine(network, "L1", "BBS1", "BBS3");
        createLine(network, "L2", "BBS2", "BBS3");

        vl2.newLoad()
                .setId("LD")
                .setBus("BBS3")
                .setP0(600.0)
                .setQ0(200.0)
                .add();

        network.getLine("L1").newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine("L1").newCurrentLimits2().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits2().setPermanentLimit(940.0).add();

        return network;
    }

    private static void createBreaker(VoltageLevel vl, String id, String bus1Id, String bus2Id) {
        vl.getBusBreakerView().newSwitch().setId(id).setBus1(bus1Id).setBus2(bus2Id).add();
    }

    private static void createGenerator(VoltageLevel vl, String id, String busId, double v, double p, double q) {
        vl.newGenerator()
            .setId(id)
            .setBus(busId)
            .setMinP(-4999.99)
            .setMaxP(4999.99)
            .setVoltageRegulatorOn(true)
            .setTargetV(v)
            .setTargetP(p)
            .setTargetQ(q)
            .add();
    }

    private static void createLine(Network network, String id, String bus1Id, String bus2Id) {
        network.newLine()
            .setId(id)
            .setBus1(bus1Id)
            .setBus2(bus2Id)
            .setR(3.0)
            .setX(33.0)
            .setB1(386E-6 / 2)
            .setB2(386E-6 / 2)
            .add();
        network.getLine(id).newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine(id).newCurrentLimits2().setPermanentLimit(940.0).add();
    }
}
