/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;

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
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class FourBusNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network createBaseNetwork() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 2);
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

    public static Network create() {
        Network network = createBaseNetwork();
        Bus b2 = network.getBusBreakerView().getBus("b2");
        createGenerator(b2, "g2", 2);
        return network;
    }

    public static Network createWithPhaseTapChanger() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "test_s", "b2");
        Bus b3 = createBus(network, "test_s", "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 2);
        createGenerator(b4, "g4", 1);
        createLoad(b2, "d2", 1);
        createLoad(b3, "d3", 4);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b1, b2, "l12", 0.1f);
        TwoWindingsTransformer twt = createTransformer(network, "test_s", b2, b3, "l23", 0.1f, 1d);
        twt.newPhaseTapChanger().setTapPosition(0)
           .beginStep()
           .setX(0.1f)
           .setAlpha(1)
           .endStep()
           .add();
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);

        return network;
    }

    public static Network createWithPhaseTapChangerAndGeneratorAtBus2() {
        Network network = createWithPhaseTapChanger();
        Bus b2 = network.getBusBreakerView().getBus("b2");
        createGenerator(b2, "g2", 2);
        return network;
    }

    public static Network createWithTwoGeneratorsAtBus2() {
        Network network = create();
        Bus b2 = network.getBusBreakerView().getBus("b2");
        createGenerator(b2, "g5", 0.5);
        return network;
    }
}

