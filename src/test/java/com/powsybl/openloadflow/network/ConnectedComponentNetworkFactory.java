package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;

public class ConnectedComponentNetworkFactory extends AbstractLoadFlowNetworkFactory {
    public static Network createTwoCcLinkedByASingleLine() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        return network;
    }

    public static Network createTwoComponentWithGeneratorAndLoad() {
        Network network = createTwoCcLinkedByASingleLine();
        Bus b2 = network.getBusBreakerView().getBus("b2");
        Bus b6 = network.getBusBreakerView().getBus("b6");
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b5 = network.getBusBreakerView().getBus("b5");
        Bus b3 = network.getBusBreakerView().getBus("b3");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        return network;
    }
}
