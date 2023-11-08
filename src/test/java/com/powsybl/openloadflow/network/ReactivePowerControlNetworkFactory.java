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
     * <p>Based on 4 bus test network:</p>
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
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g4 = network.getGenerator("g4");
        Line l34 = network.getLine("l34");

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

    public static Network createWithGeneratorRemoteControl2() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Line l12 = network.getLine("l12");
        // disable voltage control on g4
        Generator g4 = network.getGenerator("g4");
        g4.setTargetQ(0.0).setVoltageRegulatorOn(false);
        // generator g4 regulates reactive power on line 1->2 in 2
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(1.0)
                .withRegulatingTerminal(l12.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();
        g4.newMinMaxReactiveLimits().setMinQ(-5.0).setMaxQ(5.0).add();
        return network;
    }

    public static Network createWithGeneratorsRemoteControlShared() {
        Network network = FourBusNetworkFactory.createWith2ReactiveControllersOnSameBusAnd1Extra();

        double targetQ = 2.0;

        Generator g1 = network.getGenerator("g1");
        Generator g1Bis = network.getGenerator("g1Bis");
        Generator g4 = network.getGenerator("g4");
        Line l34 = network.getLine("l34");

        g1.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();
        g1Bis.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        return network;
    }
}
