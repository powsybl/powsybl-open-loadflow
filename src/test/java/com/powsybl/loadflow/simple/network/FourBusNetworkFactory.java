/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;

/**
 * <p>4 bus test network:</p>
 *<pre>
 *      2pu                 2pu - 1pu
 *   1 =======           2 =======
 *      | | |               |   |
 *      | | +---------------+   |
 *      | |                     |
 *      | +-------------------+ |
 *      |                     | |
 *      |   +---------------+ | |
 *      |   |               | | |
 *   4 =======           3 =======
 *      1pu                 -4pu
 *</pre>
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class FourBusNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network create() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 2);
        createGenerator(b2, "g2", 2);
        createGenerator(b4, "g4", 1);
        createLoad(b2, "d2", 1);
        createLoad(b3, "d3", 4);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        return network;
    }
}

