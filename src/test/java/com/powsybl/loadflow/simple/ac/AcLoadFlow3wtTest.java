/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.simple.SimpleLoadFlowParameters;
import com.powsybl.loadflow.simple.SimpleLoadFlowProvider;
import com.powsybl.loadflow.simple.SlackBusSelectionMode;
import com.powsybl.math.matrix.DenseMatrixFactory;
import org.junit.Before;
import org.junit.Test;

import static com.powsybl.loadflow.simple.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.loadflow.simple.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.Assert.assertTrue;

/**
 * Three windings transformer test case.
 *
 *  g1            ld2
 *  |             |
 *  b1-----OO-----b2
 *         O
 *         |
 *         b3
 *         |
 *         sc3
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlow3wtTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private ThreeWindingsTransformer twt;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private Network createNetwork() {
        Network network = Network.create("vsc", "test");

        Substation s = network.newSubstation()
                .setId("s")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setTargetP(161)
                .setTargetV(405)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(225)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(161)
                .setQ0(74)
                .add();

        VoltageLevel vl3 = s.newVoltageLevel()
                .setId("vl3")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus3 = vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        vl3.newShuntCompensator()
                .setId("sc3")
                .setConnectableBus("b3")
                .setBus("b3")
                .setbPerSection(-0.16)
                .setMaximumSectionCount(1)
                .setCurrentSectionCount(0)
                .add();

        twt = s.newThreeWindingsTransformer()
                .setId("3wt")
                .newLeg1()
                    .setVoltageLevel("vl1")
                    .setConnectableBus("b1")
                    .setBus("b1")
                    .setRatedU(380)
                    .setR(0.08)
                    .setX(47.3)
                    .setG(0)
                    .setB(0)
                .add()
                .newLeg2()
                    .setVoltageLevel("vl2")
                    .setConnectableBus("b2")
                    .setBus("b2")
                    .setRatedU(225)
                    .setR(0.4)
                    .setX(-7.7)
                .add()
                .newLeg3()
                    .setVoltageLevel("vl3")
                    .setConnectableBus("b3")
                    .setBus("b3")
                    .setRatedU(20)
                    .setR(4.98)
                    .setX(133.5)
                .add()
            .add();

        return network;
    }

    @Before
    public void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new SimpleLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        SimpleLoadFlowParameters parametersExt = new SimpleLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setDistributedSlack(false);
        this.parameters.addExtension(SimpleLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(405, bus1);
        assertAngleEquals(2.720264, bus1);
        assertVoltageEquals(235.132, bus2);
        assertAngleEquals(0.462642, bus2);
        assertVoltageEquals(20.834, bus3);
        assertAngleEquals(0, bus3);
    }
}
