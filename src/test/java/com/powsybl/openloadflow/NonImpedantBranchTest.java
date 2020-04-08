/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.network.AbstractLoadFlowNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NonImpedantBranchTest extends AbstractLoadFlowNetworkFactory {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = new OpenLoadFlowParameters();
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void threeBusesTest() {
        Network network = Network.create("ThreeBusesWithNonImpedantLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        Line l23 = createLine(network, b2, b3, "l23", 0); // non impedant branch

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.858, b2);
        assertVoltageEquals(0.858, b3);
        assertAngleEquals(13.36967, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);

        assertActivePowerEquals(1.99, l23.getTerminal1());
        assertActivePowerEquals(-1.99, l23.getTerminal2());
        assertReactivePowerEquals(1, l23.getTerminal1());
        assertReactivePowerEquals(-1, l23.getTerminal2());

        parametersExt.setDc(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertTrue(Double.isNaN(b1.getV()));
        assertTrue(Double.isNaN(b2.getV()));
        assertTrue(Double.isNaN(b3.getV()));
        assertAngleEquals(0, b1);
        assertAngleEquals(-11.40186, b2);
        assertAngleEquals(-11.40186, b3);
    }

    @Test
    public void fourBusesTest() {
        Network network = Network.create("FourBusesWithNonImpedantLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b4, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.05);
        createLine(network, b2, b3, "l23", 0); // non impedant branch
        createLine(network, b3, b4, "l34", 0.05);

        LoadFlowResult result = loadFlowRunner.run(network);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.921, b2);
        assertVoltageEquals(0.921, b3);
        assertVoltageEquals(0.855, b4);
        assertAngleEquals(6.2301, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);
        assertAngleEquals(-7.248787, b4);

        parametersExt.setDc(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertTrue(Double.isNaN(b1.getV()));
        assertTrue(Double.isNaN(b2.getV()));
        assertTrue(Double.isNaN(b3.getV()));
        assertTrue(Double.isNaN(b4.getV()));
        assertAngleEquals(0, b1);
        assertAngleEquals(-5.70093, b2);
        assertAngleEquals(-5.70093, b3);
        assertAngleEquals(-11.40186, b4);
    }

    @Test
    public void threeBusesAndNonImpTransfoTest() {
        Network network = Network.create("ThreeBusesWithNonImpedantTransfo", "code");
        Bus b1 = createBus(network, "s", "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        TwoWindingsTransformer l23 = createTransformer(network, "s", b2, b3, "l23", 0, 1.1); // non impedant branch

        // TODO: low impedance transformer flow calculation not yet supported
        assertTrue(Double.isNaN(l23.getTerminal1().getP()));
        assertTrue(Double.isNaN(l23.getTerminal2().getP()));
        assertTrue(Double.isNaN(l23.getTerminal1().getQ()));
        assertTrue(Double.isNaN(l23.getTerminal2().getQ()));

        LoadFlowResult result = loadFlowRunner.run(network);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.858, b2);
        assertVoltageEquals(0.944, b3);
        assertAngleEquals(13.36967, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);
    }

    @Test
    public void inconsistentTargetVoltagesTest() {
        Network network = Network.create("FourBusesWithNonImpedantLineAndInconsistentTargetVoltages", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 1, 1);
        createGenerator(b2, "g2", 1, 1.01);
        createLoad(b4, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0); // non impedant branch
        createLine(network, b2, b3, "l23", 0.05);
        createLine(network, b3, b4, "l34", 0.05);

        CompletionException exception = assertThrows(CompletionException.class, () -> loadFlowRunner.run(network));
        assertEquals("Non impedant branch 'l12' is connected to PV buses 'b1_vl_0' and 'b2_vl_0' with inconsistent target voltages: 1.0 and 1.01", exception.getCause().getMessage());
    }
}
