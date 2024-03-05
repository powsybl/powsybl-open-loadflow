/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class ZeroImpedanceNetworkFactory extends AbstractLoadFlowNetworkFactory {

    /**
     *
     * g0 (regulate b1)                     g4
     * |                                    | t34 (regulate b3)
     * b0 ----- b1 ===== b2 ===== b3 --OO-- b4
     *          |                 |
     *           ------- b5 ------
     *                   |
     *                   ld5
     */
    public static Network createWithVoltageControl() {
        Network network = Network.create("test", "code");
        Bus b0 = createBus(network, "s", "b0");
        Bus b1 = createBus(network, "s", "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        Bus b4 = createBus(network, "s", "b4");
        Bus b5 = createBus(network, "s", "b5");
        Generator g0 = createGenerator(b0, "g0", 2, 1); // 1 kV
        createGenerator(b4, "g4", 2, 1.15); // 1.15 kV
        createLoad(b5, "ld5", 4);
        Line l01 = createLine(network, b0, b1, "l01", 0.1);
        createLine(network, b1, b2, "l12", 0.0);
        createLine(network, b2, b3, "l23", 0.0);
        createLine(network, b1, b5, "l15", 0.1);
        createLine(network, b5, b3, "l53", 0.1);
        g0.setRegulatingTerminal(l01.getTerminal2()); // remote
        TwoWindingsTransformer t34 = createTransformer(network, "s", b3, b4, "tr34", 0.15, 1);
        t34.newRatioTapChanger()
                .beginStep()
                .setRho(0.9)
                .endStep()
                .beginStep()
                .setRho(1)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .endStep()
                .beginStep()
                .setRho(1.2)
                .endStep()
                .setTapPosition(1)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(true)
                .setTargetV(1.1)
                .setRegulationTerminal(t34.getTerminal1())
                .setTargetDeadband(0.01)
                .add();
        return network;
    }
}
