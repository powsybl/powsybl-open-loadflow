package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.BoundaryFactory;
import org.junit.jupiter.api.Test;

class AreaInterchangeControlTests {

    @Test
    void oneAreaBaseTest() {
        Network network = BoundaryFactory.createWithLoad();
        network.newArea()
                .setId("a1")
                .setName("Area 1")
                .setAreaType("ControlArea")
                .setInterchangeTarget(20)
                .addVoltageLevel(network.getVoltageLevel("vl1"))
                .addVoltageLevel(network.getVoltageLevel("vl2"))
                .addAreaBoundary(network.getDanglingLine("dl1").getBoundary(), true)
                .addAreaBoundary(network.getLine("l13").getTerminal2(), true)
                .add();

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        loadFlowRunner.run(network, loadFlowParameters);
    }

}
