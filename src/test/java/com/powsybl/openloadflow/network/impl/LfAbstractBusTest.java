package com.powsybl.openloadflow.network.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.AbstractLoadFlowNetworkFactory;

public class LfAbstractBusTest extends AbstractLoadFlowNetworkFactory {

    private LfBusImpl bus;

    private Network network;

    @BeforeEach
    void setUp() {
        network = Network.create("test", "code");
        Bus bus1 = createBus(network, "b1");
        this.bus = LfBusImpl.create(bus1);
    }

    @Test
    private void variableActivePowerTest() {
        assertEquals(bus.getVariableActivePower(), 0d);
    }

    @Test
    private void fixedActivePowerTest() {
        assertEquals(bus.getFixedActivePower(), 0d);
    }
}
