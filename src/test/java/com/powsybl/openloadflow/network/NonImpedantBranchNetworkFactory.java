/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;

/**
 * Network with a non impedant line.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NonImpedantBranchNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network create() {
        Network network = Network.create("2-bus", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 2, 1);
        createLine(network, b1, b2, "l12", 0.1);
        createLine(network, b2, b3, "l23", 0); // non impedant branch
        return network;
    }
}
