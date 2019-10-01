/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.simple.SimpleLoadFlowParameters;
import com.powsybl.loadflow.simple.SimpleLoadFlowProvider;
import com.powsybl.loadflow.simple.network.FirstSlackBusSelector;
import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.LfNetwork;
import com.powsybl.loadflow.simple.network.LfReactiveDiagram;
import com.powsybl.loadflow.simple.network.impl.LfNetworks;
import com.powsybl.math.matrix.DenseMatrixFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.powsybl.loadflow.simple.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.Assert.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcloadFlowReactiveLimitsTest {

    private Network network;
    private VoltageLevel vlgen;
    private VoltageLevel vlgen2;
    private Generator gen;
    private Generator gen2;
    private Load load;
    private TwoWindingsTransformer nhv2Nload;
    private TwoWindingsTransformer ngen2Nhv1;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private SimpleLoadFlowParameters parametersExt;

    private void createNetwork() {
        network = EurostagTutorialExample1Factory.create();

        // access to already created equipments
        load = network.getLoad("LOAD");
        vlgen = network.getVoltageLevel("VLGEN");
        nhv2Nload = network.getTwoWindingsTransformer("NHV2_NLOAD");
        gen = network.getGenerator("GEN");
        Substation p1 = network.getSubstation("P1");

        // reduce GEN reactive range
        gen.newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(280)
                .add();

        // create a new generator GEN2
        vlgen2 = p1.newVoltageLevel()
                .setId("VLGEN2")
                .setNominalV(24.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vlgen2.getBusBreakerView().newBus()
                .setId("NGEN2")
                .add();
        gen2 = vlgen2.newGenerator()
                .setId("GEN2")
                .setBus("NGEN2")
                .setConnectableBus("NGEN2")
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(24.5)
                .setTargetP(100)
                .add();
        gen2.newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(100)
                .add();
        int zb380 = 380 * 380 / 100;
        ngen2Nhv1 = p1.newTwoWindingsTransformer()
                .setId("NGEN2_NHV1")
                .setVoltageLevel1("VLGEN2")
                .setBus1("NGEN2")
                .setConnectableBus1("NGEN2")
                .setRatedU1(24.0)
                .setVoltageLevel2("VLHV1")
                .setBus2("NHV1")
                .setConnectableBus2("NHV1")
                .setRatedU2(400.0)
                .setR(0.24 / 1800 * zb380)
                .setX(Math.sqrt(10 * 10 - 0.24 * 0.24) / 1800 * zb380)
                .setG(0.0)
                .setB(0.0)
                .add();

        // fix active power balance
        load.setP0(699.838);
    }

    @Before
    public void setUp() {
        createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new SimpleLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = new SimpleLoadFlowParameters()
                .setDistributedSlack(false)
                .setReactiveLimits(true);
        parameters.addExtension(SimpleLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void diagramTest() {
        List<LfNetwork> lfNetworks = LfNetworks.create(network, new FirstSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        LfBus genBus = lfNetwork.getBus(0);
        assertEquals("VLGEN_0", genBus.getId());
        LfReactiveDiagram diagram = genBus.getReactiveDiagram().orElse(null);
        assertNotNull(diagram);
        assertEquals(0, diagram.getMinQ(0), 0);
        assertEquals(2.8, diagram.getMaxQ(0), 0);
    }

    @Test
    public void test() {
        parametersExt.setReactiveLimits(false);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(109.228, gen.getTerminal());
        assertReactivePowerEquals(152.265, gen2.getTerminal());
        assertReactivePowerEquals(-199.998, nhv2Nload.getTerminal2());

        parametersExt.setReactiveLimits(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(164.315, gen.getTerminal());
        assertReactivePowerEquals(100, gen2.getTerminal()); // GEN is correctly limited to 100 MVar
        assertReactivePowerEquals(100, ngen2Nhv1.getTerminal1());
        assertReactivePowerEquals(-200, nhv2Nload.getTerminal2());
    }

    @Test
    public void testWithMixedGenLoad() {
        // add a 20 MVar to LOAD2 connected to same bus as GEN2 and allow 20 MVar additional max reactive power
        // to GEN2 => result should be the same
        vlgen2.newLoad()
                .setId("LOAD2")
                .setConnectableBus("NGEN2")
                .setBus("NGEN2")
                .setP0(0)
                .setQ0(20)
                .add();
        gen2.newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(120)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(164.315, gen.getTerminal());
        assertReactivePowerEquals(120, gen2.getTerminal());
        assertReactivePowerEquals(100, ngen2Nhv1.getTerminal1());
    }
}
