/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.extensions.ReferencePriorities;
import com.powsybl.iidm.network.extensions.ReferencePriority;
import com.powsybl.iidm.network.extensions.ReferenceTerminals;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ReferenceBusPrioritiesTest {

    private Network network;
    private Generator g1;
    private Generator g2;
    private Generator g3;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = DistributedSlackNetworkFactory.create();
        g1 = network.getGenerator("g1");
        g2 = network.getGenerator("g2");
        g3 = network.getGenerator("g3");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setReadSlackBus(true)
                .setUseReactiveLimits(false)
                .setDistributedSlack(true)
                .setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setReferenceBusSelectionMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY);
    }

    @Test
    void referencePriorityNotDefinedTest() {
        ReferencePriorities.delete(network);
        // will choose g2 which has highest Pmax
        SlackTerminal.reset(network);
        SlackTerminal.attach(g1.getTerminal().getBusBreakerView().getBus());
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        assertEquals("b2_vl_0", componentResult.getReferenceBusId());
        assertEquals("b1_vl_0", componentResult.getSlackBusResults().get(0).getId());
        assertAngleEquals(0, g2.getTerminal().getBusView().getBus());
        assertTrue(ReferenceTerminals.getTerminals(network).contains(g2.getTerminal()));
    }

    @Test
    void referencePriorityDifferentFromSlackTest() {
        ReferencePriorities.delete(network);
        ReferencePriority.set(g3, 1);
        SlackTerminal.reset(network);
        SlackTerminal.attach(g1.getTerminal().getBusBreakerView().getBus());
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        assertEquals("b3_vl_0", componentResult.getReferenceBusId());
        assertEquals("b1_vl_0", componentResult.getSlackBusResults().get(0).getId());
        assertAngleEquals(0, g3.getTerminal().getBusView().getBus());
        assertTrue(ReferenceTerminals.getTerminals(network).contains(g3.getTerminal()));
    }

    @Test
    void testMultipleComponents() {
        network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();
        // open everything at bus b4 to create 3 components
        network.getBusBreakerView().getBus("b4").getConnectedTerminalStream().forEach(Terminal::disconnect);
        ReferencePriorities.delete(network);
        SlackTerminal.reset(network);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(3, result.getComponentResults().size());
        result.getComponentResults().forEach(cr -> assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, cr.getStatus()));
        Set<Terminal> referenceTerminals = ReferenceTerminals.getTerminals(network);
        assertEquals(3, referenceTerminals.size());
        assertTrue(referenceTerminals.contains(network.getGenerator("g2").getTerminal()));
        assertTrue(referenceTerminals.contains(network.getGenerator("g6").getTerminal()));
        assertTrue(referenceTerminals.contains(network.getGenerator("g10").getTerminal()));
    }

    @Test
    void testNotWritingReferenceTerminals() {
        network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();
        // open everything at bus b4 to create 3 components
        network.getBusBreakerView().getBus("b4").getConnectedTerminalStream().forEach(Terminal::disconnect);
        ReferencePriorities.delete(network);
        ReferenceTerminals.reset(network);
        SlackTerminal.reset(network);
        parametersExt.setWriteReferenceTerminals(false);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(3, result.getComponentResults().size());
        result.getComponentResults().forEach(cr -> assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, cr.getStatus()));
        Set<Terminal> referenceTerminals = ReferenceTerminals.getTerminals(network);
        assertTrue(referenceTerminals.isEmpty());
    }

    @Test
    void testNotWritingReferenceTerminals2() {
        network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();
        // open everything at bus b4 to create 3 components
        network.getBusBreakerView().getBus("b4").getConnectedTerminalStream().forEach(Terminal::disconnect);
        ReferencePriorities.delete(network);
        ReferenceTerminals.reset(network);
        SlackTerminal.reset(network);
        parametersExt.setReferenceBusSelectionMode(ReferenceBusSelectionMode.FIRST_SLACK);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(3, result.getComponentResults().size());
        result.getComponentResults().forEach(cr -> assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, cr.getStatus()));
        Set<Terminal> referenceTerminals = ReferenceTerminals.getTerminals(network);
        assertFalse(referenceTerminals.contains(network.getGenerator("g10").getTerminal()));
        assertTrue(referenceTerminals.contains(network.getLine("l810").getTerminal2()));
        assertFalse(referenceTerminals.contains(network.getLine("l910").getTerminal2()));
    }

    @Test
    void shouldNotSelectStaticVarCompensatorTest() {
        network = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        ReferencePriorities.delete(network);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        assertEquals("vl1_0", componentResult.getReferenceBusId());
        assertTrue(ReferenceTerminals.getTerminals(network).contains(network.getGenerator("g1").getTerminal()));
    }

    @Test
    void shouldNotSelectVscHvdcConverterTest() {
        network = HvdcNetworkFactory.createVsc(false);
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt.setSlackBusPMaxMismatch(0.1);
        ReferencePriorities.delete(network);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResultSc0 = result.getComponentResults().get(0);
        assertEquals("vl1_0", componentResultSc0.getReferenceBusId());
        LoadFlowResult.ComponentResult componentResultSc1 = result.getComponentResults().get(1);
        assertEquals("vl3_0", componentResultSc1.getReferenceBusId());
        // note that no reference terminal is reported for {CC0 SC1} which does not contain real generator (only a VSC converter)
        assertEquals(1, ReferenceTerminals.getTerminals(network).size());
        assertTrue(ReferenceTerminals.getTerminals(network).contains(network.getGenerator("g1").getTerminal()));
    }
}
