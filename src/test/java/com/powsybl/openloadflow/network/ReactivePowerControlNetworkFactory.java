/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.RemoteReactivePowerControlAdder;

/**
 * @author Caio Luke {@literal <caio.luke at artelys.com>}
 */
public class ReactivePowerControlNetworkFactory extends AbstractLoadFlowNetworkFactory {

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
     */
    public static Network createWithGeneratorRemoteControl() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 2);
        Generator g4 = createGenerator(b4, "g4", 1);
        createLoad(b2, "d2", 1);
        createLoad(b3, "d3", 4);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        Line l34 = createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);

        double targetQ = 4.0;

        // disable voltage control on g4
        g4.setTargetQ(0.0).setVoltageRegulatorOn(false);

        // generator g4 regulates reactive power on line 4->3 (on side of g4)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        g4.newMinMaxReactiveLimits().setMinQ(-5.0).setMaxQ(5.0).add();

        return network;
    }
}
