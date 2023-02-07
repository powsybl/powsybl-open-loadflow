/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class T3wtFactory extends AbstractLoadFlowNetworkFactory {

    /**
     * Three windings transformer test case.
     *
     *  g1            ld2
     *  |             |
     *  b1-----OO-----b2
     *         O
     *         |
     *         b3
     *         |
     *         sc3
     *
     * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
     */
    public static Network create() {
        Network network = Network.create("vsc", "test");

        Substation s = network.newSubstation()
            .setId("s")
            .add();
        VoltageLevel vl1 = s.newVoltageLevel()
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
            .setTargetP(161)
            .setTargetV(405)
            .setMinP(0)
            .setMaxP(500)
            .setVoltageRegulatorOn(true)
            .add();

        VoltageLevel vl2 = s.newVoltageLevel()
            .setId("vl2")
            .setNominalV(225)
            .setTopologyKind(TopologyKind.BUS_BREAKER)
            .add();
        vl2.getBusBreakerView().newBus()
            .setId("b2")
            .add();
        vl2.newLoad()
            .setId("ld2")
            .setConnectableBus("b2")
            .setBus("b2")
            .setP0(161)
            .setQ0(74)
            .add();

        VoltageLevel vl3 = s.newVoltageLevel()
            .setId("vl3")
            .setNominalV(20)
            .setTopologyKind(TopologyKind.BUS_BREAKER)
            .add();
        vl3.getBusBreakerView().newBus()
            .setId("b3")
            .add();
        vl3.newShuntCompensator()
            .setId("sc3")
            .setConnectableBus("b3")
            .setBus("b3")
            .setSectionCount(0)
            .newLinearModel()
            .setBPerSection(-0.16)
            .setMaximumSectionCount(1)
            .add()
            .add();

        s.newThreeWindingsTransformer()
            .setId("3wt")
            .newLeg1()
            .setConnectableBus("b1")
            .setBus("b1")
            .setRatedU(380)
            .setR(0.08)
            .setX(47.3)
            .add()
            .newLeg2()
            .setConnectableBus("b2")
            .setBus("b2")
            .setRatedU(225)
            .setR(0.4)
            .setX(-7.7)
            .add()
            .newLeg3()
            .setConnectableBus("b3")
            .setBus("b3")
            .setRatedU(20)
            .setR(4.98)
            .setX(133.5)
            .add()
            .add();

        return network;
    }
}
