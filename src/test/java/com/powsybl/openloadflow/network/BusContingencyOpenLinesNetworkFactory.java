/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;

/**
 *     ---- 2 ----
 *   /            \
 * 1 ----- 3 ----- 5
 *  \             /
 *    ---- 4 ----
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class BusContingencyOpenLinesNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network create() {
        Network network = Network.create("busContingencyOpenLinesNetwork", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        createGenerator(b1, "g1", 2);
        createLoad(b3, "d2", 1);
        createLoad(b5, "d5", 1);
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b2, b5, "l25", 0.1f);
        createLine(network, b3, b5, "l35", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        network.getLine("l25").getTerminal1().disconnect();
        network.getLine("l45").getTerminal1().disconnect();
        return network;
    }
}
