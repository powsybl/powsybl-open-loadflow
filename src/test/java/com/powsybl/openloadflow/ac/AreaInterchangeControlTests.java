package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaInterchangeControlTests {

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setAreaInterchangeControl(true);
    }

    @Test
    void twoAreasTest2() {
        Network network = MultiAreaNetworkFactory.createTwoAreasBase();

        double interchangeTarget1 = -20;
        double interchangeTarget2 = 20;

        Area area1 = network.getArea("a1");
        Area area2 = network.getArea("a2");
        area1.setInterchangeTarget(interchangeTarget1);
        area2.setInterchangeTarget(interchangeTarget2);

        loadFlowRunner.run(network, parameters);

        assertEquals(interchangeTarget1, area1.getInterchange(), 1e-3);
        assertEquals(interchangeTarget2, area2.getInterchange(), 1e-3);
    }

}
