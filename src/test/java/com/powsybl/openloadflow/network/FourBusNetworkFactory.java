/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.RemoteReactivePowerControlAdder;

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

    public static Network createWith2GeneratorsAtBus1() {
        Network network = create();
        Bus b1 = network.getBusBreakerView().getBus("b1");
        createGenerator(b1, "g1Bis", 2);
        network.getLoad("d3").setQ0(1);
        return network;
    }

    public static Network createWithReactiveControl() {
        Network network = create();
        network.getLoad("d3").setQ0(1);
        Line l34 = network.getLine("l34");
        double remoteTargetQ = 2.0;
        Generator g4 = network.getGenerator("g4");
        g4.setTargetQ(0).setVoltageRegulatorOn(false);
        Generator g1 = network.getGenerator("g1");
        g1.setTargetQ(0).setVoltageRegulatorOn(false);
        g1.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(remoteTargetQ)
                .withRegulatingTerminal(l34.getTerminal(TwoSides.TWO))
                .withEnabled(true)
                .add();
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(remoteTargetQ)
                .withRegulatingTerminal(l34.getTerminal(TwoSides.TWO))
                .withEnabled(true).add();
        return network;
    }

    public static Network createWithReactiveControl2GeneratorsOnSameBus() {
        Network network = create();
        network.getLoad("d3").setQ0(1);
        Line l34 = network.getLine("l34");
        double remoteTargetQ = 2.0;
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Generator g1 = network.getGenerator("g1");
        g1.setTargetQ(0).setVoltageRegulatorOn(false);
        g1.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(remoteTargetQ)
                .withRegulatingTerminal(l34.getTerminal(TwoSides.TWO))
                .withEnabled(true)
                .add();
        Generator g1Bis = createGenerator(b1, "g1Bis", 2);
        g1Bis.setTargetQ(0).setVoltageRegulatorOn(false);
        g1Bis.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(remoteTargetQ)
                .withRegulatingTerminal(l34.getTerminal(TwoSides.TWO))
                .withEnabled(true)
                .add();
        return network;
    }

    public static Network createWithReactiveControl2GeneratorsOnSameBusAnd1Extra() {
        Network network = createWithReactiveControl2GeneratorsOnSameBus();
        Generator g4 = network.getGenerator("g4");
        g4.setTargetQ(0).setVoltageRegulatorOn(false);
        Line l34 = network.getLine("l34");
        double remoteTargetQ = 2.0;
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(remoteTargetQ)
                .withRegulatingTerminal(l34.getTerminal(TwoSides.TWO))
                .withEnabled(true)
                .add();
        return network;
    }

    public static Network createWithAdditionalReactiveTerms() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b2, "g2", 2);
        createGenerator(b3, "g3", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 3);
        createLine(network, b1, b2, "l12", 0.5f);
        createLine(network, b1, b3, "l13", 0.5f);
        createLine(network, b2, b4, "l24", 1f);
        createLine(network, b3, b4, "l34", 1f);
        Generator g2 = network.getGenerator("g2");
        g2.setRegulatingTerminal(network.getLine("l24").getTerminal2());
        Generator g3 = network.getGenerator("g3");
        g3.setRegulatingTerminal(network.getLine("l24").getTerminal2());
        return network;
    }

    public static Network createWithCondenser() {
        Network network = createBaseNetwork();
        network.getGenerator("g4")
                .setTargetP(0.0)
                .setMaxP(0.0)
                .setMinP(0.0);
        return network;
    }

    public static Network createWithTwoScs() {
        Network network = createBaseNetwork();
        Bus c1 = createBus(network, "c1");
        Bus c2 = createBus(network, "c2");
        createGenerator(c1, "gc1", 2);
        createLoad(c2, "dc2", 1);
        createLine(network, c1, c2, "lc12", 1f);
        createLine(network, c1, c2, "lc12Bis", 1f);
        return network;
    }
}

