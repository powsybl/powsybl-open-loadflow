/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.graphstream.algorithm.ConnectedComponents;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.MultiGraph;
import org.jgrapht.alg.connectivity.BiconnectivityInspector;
import org.jgrapht.graph.Pseudograph;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public final class BridgesTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgesTest.class);

    private LfNetwork lfNetwork;
    private Set<String> bridgesSetReference;

    @Before
    public void setUp() {
        long start = System.currentTimeMillis();
        Network network = EurostagTutorialExample1Factory.create();
        List<LfNetwork> lfn = LfNetwork.load(network, new FirstSlackBusSelector());
        this.lfNetwork = lfn.get(0);
        LOGGER.info("Reading network of {} buses in {} ms", lfNetwork.getBuses().size(), System.currentTimeMillis() - start);

        this.bridgesSetReference = getBridgesReference();
        LOGGER.info("Reference established");
    }

    private Set<String> getBridgesReference() {
        return Arrays.stream(new String[] {"NGEN_NHV1", "NHV2_NLOAD"}).collect(Collectors.toSet());
    }

    @Test
    public void testNaiveConnectivity() {
        Set<String> bridges = testBridgesOnConnectivity(lfNetwork,
            new NaiveGraphDecrementalConnectivity<>(LfBus::getNum), "naive algorithm");
        assertEquals(bridgesSetReference, bridges);
    }

    @Test
    public void testConnectivityInspector() {
        Set<String> bridges = testBridgesOnConnectivity(lfNetwork, new DecrementalConnectivityInspector<>(), "ConnectivityInspector");
        assertEquals(bridgesSetReference, bridges);
    }

    @Test
    public void testFindBridgesConnectivity() {
        Set<String> bridges = testBridgesOnConnectivity(lfNetwork,
            new BridgesFinder<>(lfNetwork.getBuses().size(), LfBus::getNum), "Hopcroft-Tarjan algorithm");
        assertEquals(bridgesSetReference, bridges);
    }

    @Test
    public void testEvenShiloach() {
        Set<String> bridges = testBridgesOnConnectivity(lfNetwork, new EvenShiloachGraphDecrementalConnectivity<>(), "Even-Shiloach");
        assertEquals(bridgesSetReference, bridges);
    }

    @Test
    public void testFindBridges() {
        BridgesFinder<LfBus> graph = new BridgesFinder<>(lfNetwork.getBuses().size(), LfBus::getNum);
        initGraphDc(lfNetwork, graph);

        long start = System.currentTimeMillis();
        List<int[]> bridges = graph.getBridges();
        LOGGER.info("Bridges calculated based on Hopcroft-Tarjan algorithm in {} ms", System.currentTimeMillis() - start);

        BiFunction<Integer, Integer, String> verticesToBranchId = (v1, v2) -> {
            Predicate<? super LfBranch> p = b -> b.getBus1() != null && b.getBus2() != null
                && ((b.getBus1().getNum() == v1 && b.getBus2().getNum() == v2) || (b.getBus1().getNum() == v2 && b.getBus2().getNum() == v1));
            return lfNetwork.getBus(v1).getBranches().stream().filter(p).findFirst().orElseThrow(PowsyblException::new).getId();
        };
        Set<String> set = bridges.stream().collect(HashSet::new, (h, t) -> h.add(verticesToBranchId.apply(t[0], t[1])), HashSet::addAll);
        assertEquals(bridgesSetReference, set);
    }

    @Test
    public void testBiconnectivityInspector() {
        org.jgrapht.Graph<String, String> graph = getJgraphTGraph(lfNetwork);
        BiconnectivityInspector<String, String> bi = new BiconnectivityInspector<>(graph);

        long start = System.currentTimeMillis();
        Set<String> bridges = bi.getBridges();
        LOGGER.info("Bridges calculated based on jgraphT BiconnectivityInspector in {} ms", System.currentTimeMillis() - start);

        assertEquals(bridgesSetReference, bridges);
    }

    @Test
    public void testGraphStream() {
        Graph graph = initGraphStream(lfNetwork);

        ConnectedComponents cc = new ConnectedComponents();
        long start = System.currentTimeMillis();
        cc.init(graph);

        int initialCCC = cc.getConnectedComponentsCount();
        LOGGER.info("{} connected component(s) in this graph, so far.", initialCCC);

        Set<String> bridges = new HashSet<>();
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                String cutAttribute = branch.getId() + "-removed";
                graph.getEdge(branch.getId()).setAttribute(cutAttribute, true);
                cc.setCutAttribute(cutAttribute);
                boolean connected = cc.getConnectedComponentsCount() == initialCCC;
                if (!connected) {
                    bridges.add(branch.getId());
                }
            }
        }

        LOGGER.info("Bridges calculated based on graphstream library in {} ms", System.currentTimeMillis() - start);

        assertEquals(bridgesSetReference, bridges);
    }

    private Set<String> testBridgesOnConnectivity(LfNetwork lfNetwork, GraphDecrementalConnectivity<LfBus> connectivity, String method) {
        long start = System.currentTimeMillis();
        initGraphDc(lfNetwork, connectivity);
        LOGGER.info("Graph init for {} in {} ms", method, System.currentTimeMillis() - start);
        start = System.currentTimeMillis();
        Set<String> bridgesSet = getBridges(lfNetwork, connectivity);
        LOGGER.info("Bridges calculated based on {} in {} ms", method, System.currentTimeMillis() - start);
        return bridgesSet;
    }

    private static org.jgrapht.Graph<String, String> getJgraphTGraph(LfNetwork lfNetwork) {
        org.jgrapht.Graph<String, String> graph = new Pseudograph<>(String.class);
        for (LfBus bus : lfNetwork.getBuses()) {
            graph.addVertex(bus.getId());
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                graph.addEdge(bus1.getId(), bus2.getId(), branch.getId());
            }
        }
        return graph;
    }

    private static Graph initGraphStream(LfNetwork lfNetwork) {
        Graph graph = new MultiGraph("TestCC");
        for (LfBus bus : lfNetwork.getBuses()) {
            graph.addNode(bus.getId());
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                graph.addEdge(branch.getId(), bus1.getId(), bus2.getId());
            }
        }
        return graph;
    }

    private static Set<String> getBridges(LfNetwork lfNetwork, GraphDecrementalConnectivity<LfBus> connectivity) {
        Set<String> bridgesSet = new HashSet<>();
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                connectivity.cut(bus1, bus2);
                boolean connected = connectivity.getComponentNumber(bus1) == connectivity.getComponentNumber(bus2);
                if (!connected) {
                    bridgesSet.add(branch.getId());
                }
                connectivity.reset();
            }
        }
        return bridgesSet;
    }

    private static void initGraphDc(LfNetwork lfNetwork, GraphDecrementalConnectivity<LfBus> connectivity) {
        for (LfBus bus : lfNetwork.getBuses()) {
            connectivity.addVertex(bus);
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                connectivity.addEdge(bus1, bus2);
            }
        }
    }

}
