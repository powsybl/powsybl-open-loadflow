/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
public class PstFactory extends AbstractLoadFlowNetworkFactory {

    private static VoltageLevel newVoltageLevel(Network n, String id) {
        return n.newVoltageLevel()
                .setId(id)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .setNominalV(225)
                .add();
    }

    private static VoltageLevel newVoltageLevel(Substation n, String id) {
        return n.newVoltageLevel()
                .setId(id)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .setNominalV(225)
                .add();
    }

    /**
     *  -------------------l14--------------------------|
     *  |                                               |
     *  b1---l12-----b2------PST1--------b3-----l34-----b4
     *  |                                               |
     *  g1                                              l4
     */
    public static Network createPSTWithLineAtEachSide() {
        Network n = NetworkFactory.findDefault().createNetwork("Test", "java");
        VoltageLevel vl1 = newVoltageLevel(n, "vl1");
        Substation s = n.newSubstation().setId("s").add();
        VoltageLevel vl2 = newVoltageLevel(s, "vl2");
        VoltageLevel vl3 = newVoltageLevel(s, "vl3");
        VoltageLevel vl4 = newVoltageLevel(n, "vl4");

        Bus b1 = vl1.getBusBreakerView().newBus().setId("b1").add();
        Bus b2 = vl2.getBusBreakerView().newBus().setId("b2").add();
        Bus b3 = vl3.getBusBreakerView().newBus().setId("b3").add();
        Bus b4 = vl4.getBusBreakerView().newBus().setId("b4").add();

        createLine(n, b1, b2, "l12", 0.1, 0.1);
        createLine(n, b1, b4, "l14", 0.1, 0.1);
        createLine(n, b3, b4, "l34", 0.1, 0.1);

        TwoWindingsTransformer pst = s.newTwoWindingsTransformer()
                .setId("pst")
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setBus2(b3.getId())
                .setConnectableBus2(b3.getId())
                .setR(5)
                .setX(10)
                .setG(5e-5)
                .setB(5e-5)
                .add();
        pst
                .newPhaseTapChanger()
                .setLowTapPosition(0)
                .setTapPosition(0)
                .beginStep()
                .setAlpha(-10)  // Also with -10 or 0
                .endStep()
                .add();

        vl1.newGenerator()
                .setId("g1")
                .setTargetP(300)
                .setMaxP(500)
                .setMinP(100)
                .setVoltageRegulatorOn(true)
                .setTargetV(225)
                .setBus("b1")
                .setConnectableBus("b1")
                .add();

        vl4.newLoad()
                .setId("l4")
                .setP0(100)
                .setQ0(50)
                .setBus("b4")
                .setConnectableBus("b4")
                .add();

        return n;
    }
}
