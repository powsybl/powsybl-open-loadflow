package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.BoundaryFactory;
import org.junit.jupiter.api.Test;

class AICTests {

    @Test
    void oneAreaBaseTest() {
        Network network = BoundaryFactory.createWithLoad();
        network.newArea()
                .setId("a1")
                .setName("Area 1")
                .setAreaType("ControlArea")
                .setAcInterchangeTarget(20)
                .addVoltageLevel(network.getVoltageLevel("vl1"))
                .addVoltageLevel(network.getVoltageLevel("vl2"))
                .addAreaBoundary(network.getDanglingLine("dl1").getBoundary(), true)
                .addAreaBoundary(network.getLine("l13").getTerminal2(), true)
                .add();
    }

}
