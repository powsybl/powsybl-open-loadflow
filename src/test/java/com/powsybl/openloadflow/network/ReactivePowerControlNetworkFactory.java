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
        Generator g1 = network.getGenerator("g1");
        Generator g4 = network.getGenerator("g4");
        Line l34 = network.getLine("l34");
        g1.setMaxP(10);
        g4.setMaxP(10);
        // disable voltage control on g4
        g4.setTargetQ(0.0).setVoltageRegulatorOn(false);
        // generator g4 regulates reactive power on line 4->3 (on side of g4)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(4.0)
                .withRegulatingTerminal(l34.getTerminal(TwoSides.TWO))
                .withEnabled(true)
                .add();
        return network;
    }

    public static Network createWithGeneratorRemoteControl2() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g1 = network.getGenerator("g1");
        Generator g4 = network.getGenerator("g4");
        Line l12 = network.getLine("l12");
        g1.setMaxP(10);
        g4.setMaxP(10);
        // disable voltage control on g4
        g4.setTargetQ(0.0).setVoltageRegulatorOn(false);
        // generator g4 regulates reactive power on line 1->2 in 2
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(1.0)
                .withRegulatingTerminal(l12.getTerminal(TwoSides.TWO))
                .withEnabled(true)
                .add();
        return network;
    }

    public static Network createWithGeneratorsRemoteControlShared() {
        Network network = FourBusNetworkFactory.createWithReactiveControl2GeneratorsOnSameBusAnd1Extra();
        Generator g1 = network.getGenerator("g1");
        Generator g1Bis = network.getGenerator("g1Bis");
        Generator g4 = network.getGenerator("g4");
        g1.setMaxP(10);
        g1Bis.setMaxP(10);
        g4.setMaxP(10);
        return network;
    }

    public static Network create4BusNetworkWithRatioTapChanger() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "test_s", "b3");
        Bus b4 = createBus(network, "test_s", "b4");
        createGenerator(b1, "g1", 2);
        createGenerator(b4, "g4", 1);
        createLoad(b2, "d2", 1);
        createLoad(b3, "d3", 4);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        TwoWindingsTransformer twt = createTransformer(network, "test_s", b3, b4, "l34", 0.1f, 1d);
        twt.newRatioTapChanger()
                .beginStep()
                .setRho(0.8)
                .setR(0.1089)
                .setX(0.01089)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(0.9)
                .setR(0.121)
                .setX(0.0121)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .setTapPosition(1)
                .setRegulationValue(0.)
                .setRegulationMode(RatioTapChanger.RegulationMode.REACTIVE_POWER)
                .add();
        createLine(network, b1, b3, "l13", 0.1f);

        return network;
    }
}
