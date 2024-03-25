package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;

/**
 * A Network factory of various combination of manually controlled reactive injections
 */
public class ReactiveInjectionNetworkFactory extends AbstractLoadFlowNetworkFactory {

    static Generator createGeneratorPQ(Bus b, String id, double p, double q) {
        Generator g = b.getVoltageLevel()
                .newGenerator()
                .setId(id)
                .setBus(b.getId())
                .setConnectableBus(b.getId())
                .setEnergySource(EnergySource.OTHER)
                .setMinP(0)
                .setMaxP(2 * p)
                .setTargetP(p)
                .setTargetQ(q)
                .setVoltageRegulatorOn(false)
                .add();
        g.getTerminal().setP(-p).setQ(-q);
        return g;
    }

    /**
     * <pre>
     * g1 (PV)           g2(PQ)
     * |                 |
     * b1                b2
     * |                 |
     * b3 ---------------+
     * |
     * l3 (PQ)
     * </pre>
     * @return
     */
    public static Network createTwoGensOneLoad() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1", 10);
        Bus b2 = createBus(network, "b2", 10);
        Bus b3 = createBus(network, "b3", 10);
        Generator g1 = createGenerator(b1, "g1", 10d, 10d);
        Generator g2 = createGeneratorPQ(b2, "g2", 10d, 10d);
        Line l13 = createLine(network, b1, b3, "l13", 0.1d);
        Line l23 = createLine(network, b2, b3, "l23", 0.1d);
        Load l3 = createLoad(b3, "l3", 20, 0);
        return network;
    }

}
