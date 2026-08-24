/**
 * Copyright (c) 2020-2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.graph.derivative.Delta2DTreeStandalone;
import com.powsybl.openloadflow.graph.derivative.Delta2ReplaceWithBestDTreeStandalone;
import com.powsybl.openloadflow.graph.derivative.ReplaceWithBestDTreeStandalone;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import org.jgrapht.alg.connectivity.BiconnectivityInspector;
import org.jgrapht.graph.Pseudograph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class BridgesTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgesTest.class);

    private Network network;
    private LfNetwork lfNetwork;
    private Set<String> bridgesSetReference;

    @BeforeEach
    void setUp() {
        long start = System.currentTimeMillis();
        network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        List<LfNetwork> lfn = Networks.load(network, new FirstSlackBusSelector());
        this.lfNetwork = lfn.get(0);
        LOGGER.info("Reading network of {} buses in {} ms", lfNetwork.getBuses().size(), System.currentTimeMillis() - start);

        this.bridgesSetReference = getBridgesReference();
        LOGGER.info("Reference established");
    }

    private Set<String> getBridgesReference() {
        return Arrays.stream(new String[] {"NGEN_NHV1", "NHV2_NLOAD"}).collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("namedConnectivities")
    void testBridgesOnConnectivityGeneric(GraphConnectivity<LfBus, LfBranch> connectivity, String name) {
        Set<String> bridges = testBridgesOnConnectivity(lfNetwork, connectivity, name);
        assertEquals(bridgesSetReference, bridges);
    }

    private static Stream<Arguments> namedConnectivities() {
        return Stream.of(
                Arguments.of(new NaiveGraphConnectivity<>(LfBus::getNum), "naive algorithm"),
                Arguments.of(new EvenShiloachGraphDecrementalConnectivity<>(), "Even-Shiloach"),
                Arguments.of(new MinimumSpanningTreeGraphConnectivity<>(), "Minimum-Spanning-Tree"),
                Arguments.of(new HolmEtAlGraphConnectivity<>(), "Holm-et-al"),
                Arguments.of(new HolmEtAlWithoutLevelGraphConnectivity<>(), "Holm-et-al (without level)"),
                Arguments.of(new NewHolmGraphConnectivity<>(), "Holm-et-al-2"),
                Arguments.of(new HolmStandalone<>(), "Holm-et-al-standalone"),
                Arguments.of(new DTreeGraphConnectivity<>(), "DTree"),
                Arguments.of(new Delta2DTreeStandalone<>(), "Delta2DTree"),
                Arguments.of(new Delta2ReplaceWithBestDTreeStandalone<>(), "Delta2ReplaceWithBestDTree"),
                Arguments.of(new ReplaceWithBestDTreeStandalone<>(), "ReplaceWithBestDTree"),
                Arguments.of(new DTreeStandalone<>(), "DTreeStandalone"),
                Arguments.of(new IDTreeStandalone<>(), "IDTreeStandalone"),
                Arguments.of(new DnDTreeStandalone<>(), "DNDTreeStandalone"),
                Arguments.of(new OptDTreeStandalone<>(), "OptDTreeStandalone")
        );
    }

    @Test
    void testFindBridges() {
        BridgesFinder<LfBus> graph = new BridgesFinder<>(lfNetwork.getBuses().size(), LfBus::getNum);
        for (LfBus bus : lfNetwork.getBuses()) {
            graph.addVertex(bus);
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                graph.addEdge(bus1, bus2);
            }
        }

        long start = System.currentTimeMillis();
        List<int[]> bridges = graph.getBridges();
        LOGGER.info("Bridges calculated based on Hopcroft-Tarjan algorithm in {} ms", System.currentTimeMillis() - start);

        BiFunction<Integer, Integer, String> verticesToBranchId = (v1, v2) -> {
            Predicate<? super LfBranch> p = b -> b.getBus1() != null && b.getBus2() != null
                && (b.getBus1().getNum() == v1 && b.getBus2().getNum() == v2 || b.getBus1().getNum() == v2 && b.getBus2().getNum() == v1);
            return lfNetwork.getBus(v1).getBranches().stream().filter(p).findFirst().orElseThrow(PowsyblException::new).getId();
        };
        Set<String> set = bridges.stream().collect(HashSet::new, (h, t) -> h.add(verticesToBranchId.apply(t[0], t[1])), HashSet::addAll);
        assertEquals(bridgesSetReference, set);
    }

    @Test
    void testBiconnectivityInspector() {
        org.jgrapht.Graph<String, String> graph = getJgraphTGraph(lfNetwork);
        BiconnectivityInspector<String, String> bi = new BiconnectivityInspector<>(graph);

        long start = System.currentTimeMillis();
        Set<String> bridges = bi.getBridges();
        LOGGER.info("Bridges calculated based on jgraphT BiconnectivityInspector in {} ms", System.currentTimeMillis() - start);

        assertEquals(bridgesSetReference, bridges);
    }

    private Set<String> testBridgesOnConnectivity(LfNetwork lfNetwork, GraphConnectivity<LfBus, LfBranch> connectivity, String method) {
        long start = System.currentTimeMillis();
        initGraphDc(lfNetwork, connectivity);
        LOGGER.info("Graph init for {} in {} ms", method, System.currentTimeMillis() - start);
        start = System.currentTimeMillis();
        Set<String> bridgesSet = getBridges(lfNetwork, connectivity);
        LOGGER.info("Bridges calculated based on {} in {} ms", method, System.currentTimeMillis() - start);
        return bridgesSet;
    }

    private static Set<String> getBridges(LfNetwork lfNetwork, GraphConnectivity<LfBus, LfBranch> connectivity) {
        Set<String> bridgesSet = new HashSet<>();
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                connectivity.startTemporaryChanges();
                connectivity.removeEdge(branch);
                boolean connected = connectivity.getComponentNumber(bus1) == connectivity.getComponentNumber(bus2);
                if (!connected) {
                    bridgesSet.add(branch.getId());
                }
                connectivity.undoTemporaryChanges();
            }
        }
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

    private static void initGraphDc(LfNetwork lfNetwork, GraphConnectivity<LfBus, LfBranch> connectivity) {
        for (LfBus bus : lfNetwork.getBuses()) {
            connectivity.addVertex(bus);
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                connectivity.addEdge(bus1, bus2, branch);
            }
        }
    }

}
