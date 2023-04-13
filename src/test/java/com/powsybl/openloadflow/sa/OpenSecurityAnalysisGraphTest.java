/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.MinimumSpanningTreeGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class OpenSecurityAnalysisGraphTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSecurityAnalysisGraphTest.class);

    private Network network;
    private ContingenciesProvider contingenciesProvider;

    @BeforeEach
    void setUp() {
        network = NodeBreakerNetworkFactory.create();

        // Testing all contingencies at once
        contingenciesProvider = network -> network.getBranchStream()
            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
            .collect(Collectors.toList());
    }

    private TestData computeReferenceLfContingencies() {
        var testData = computeLfContingencies(new NaiveGraphConnectivityFactory<>(LfBus::getNum));
        LOGGER.info("Reference established (naive connectivity calculation) on test network containing {} branches", network.getBranchCount());
        return testData;
    }

    @Test
    void testEvenShiloach() {
        LOGGER.info("Test Even-Shiloach on test network containing {} branches", network.getBranchCount());
        try (var testDataRef = computeReferenceLfContingencies();
             var testData = computeLfContingencies(new EvenShiloachGraphDecrementalConnectivityFactory<>())) {
            printResult(testData.getListLfContingencies());
            checkResult(testData.getListLfContingencies(), testDataRef.getListLfContingencies());
        }
    }

    @Test
    void testMst() {
        LOGGER.info("Test Minimum Spanning Tree on test network containing {} branches", network.getBranchCount());
        try (var testDataRef = computeReferenceLfContingencies();
             var testData = computeLfContingencies(new MinimumSpanningTreeGraphConnectivityFactory<>())) {
            printResult(testData.getListLfContingencies());
            checkResult(testData.getListLfContingencies(), testDataRef.getListLfContingencies());
        }
    }

    @Test
    void testNullVertices() {
        network.getSwitch("B3").setOpen(true);
        contingenciesProvider = n -> Collections.singletonList(new Contingency("L1", new BranchContingency("L1")));
        try (var testDataRef = computeReferenceLfContingencies();
             var testData1 = computeLfContingencies(new MinimumSpanningTreeGraphConnectivityFactory<>());
             var testData2 = computeLfContingencies(new EvenShiloachGraphDecrementalConnectivityFactory<>())) {
            checkResult(testData1.getListLfContingencies(), testDataRef.getListLfContingencies());
            checkResult(testData2.getListLfContingencies(), testDataRef.getListLfContingencies());
        }

        contingenciesProvider = n -> Collections.singletonList(new Contingency("L2", new BranchContingency("L2")));
        network.getSwitch("B3").setOpen(false);
        network.getSwitch("B1").setOpen(true);
        try (var testDataRef = computeReferenceLfContingencies();
             var testData1 = computeLfContingencies(new MinimumSpanningTreeGraphConnectivityFactory<>());
             var testData2 = computeLfContingencies(new EvenShiloachGraphDecrementalConnectivityFactory<>())) {
            checkResult(testData1.getListLfContingencies(), testDataRef.getListLfContingencies());
            checkResult(testData2.getListLfContingencies(), testDataRef.getListLfContingencies());
        }
    }

    private static void checkResult(List<List<LfContingency>> result, List<List<LfContingency>> reference) {
        assertEquals(reference.size(), result.size());
        for (int iNetwork = 0; iNetwork < result.size(); iNetwork++) {
            assertEquals(reference.get(iNetwork).size(), result.get(iNetwork).size());
            for (int iContingency = 0; iContingency < result.get(iNetwork).size(); iContingency++) {
                LfContingency contingencyReference = reference.get(iNetwork).get(iContingency);
                LfContingency contingencyResult = result.get(iNetwork).get(iContingency);
                assertEquals(contingencyReference.getId(), contingencyResult.getId());

                Set<LfBranch> branchesReference = contingencyReference.getDisabledBranches();
                Set<LfBranch> branchesResult = contingencyResult.getDisabledBranches();
                assertEquals(branchesReference.size(), branchesResult.size());
                branchesReference.forEach(b -> assertTrue(branchesResult.stream().anyMatch(b1 -> b1.getId().equals(b.getId()))));

                Set<LfBus> busesReference = contingencyReference.getDisabledBuses();
                Set<LfBus> busesResult = contingencyResult.getDisabledBuses();
                assertEquals(busesReference.size(), busesResult.size());
                busesReference.forEach(b -> assertTrue(busesResult.stream().anyMatch(b1 -> b1.getId().equals(b.getId()))));
            }
        }
    }

    private void printResult(List<List<LfContingency>> result) {
        for (List<LfContingency> networkResult : result) {
            for (LfContingency contingency : networkResult) {
                LOGGER.info("Contingency {} containing {} branches - {} buses (branches: {}, buses: {})",
                    contingency.getId(), contingency.getDisabledBranches().size(), contingency.getDisabledBuses().size(),
                    contingency.getDisabledBranches().stream().map(LfBranch::getId).collect(Collectors.joining(",")),
                    contingency.getDisabledBuses().stream().map(LfBus::getId).collect(Collectors.joining(",")));
            }
        }
    }

    private static class TestData implements AutoCloseable {

        private final LfNetworkList lfNetworks;
        private final List<List<LfContingency>> listLfContingencies;

        public TestData(LfNetworkList lfNetworks, List<List<LfContingency>> listLfContingencies) {
            this.lfNetworks = lfNetworks;
            this.listLfContingencies = listLfContingencies;
        }

        public LfNetworkList getLfNetworks() {
            return lfNetworks;
        }

        public List<List<LfContingency>> getListLfContingencies() {
            return listLfContingencies;
        }

        @Override
        public void close() {
            lfNetworks.close();
        }
    }

    private TestData computeLfContingencies(GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, allSwitchesToOpen, new HashSet<>(), true);

        LfNetworkParameters networkParameters = new LfNetworkParameters()
                .setConnectivityFactory(connectivityFactory)
                .setBreakers(true);

        // create networks including all necessary switches
        LfNetworkList lfNetworks = Networks.load(network, networkParameters, allSwitchesToOpen, Collections.emptySet(), Reporter.NO_OP);

        PropagatedContingency.completeList(propagatedContingencies, false, false, false, true);

        // run simulation on each network
        List<List<LfContingency>> listLfContingencies = new ArrayList<>();
        for (LfNetwork lfNetwork : lfNetworks.getList()) {
            listLfContingencies.add(propagatedContingencies.stream()
                    .flatMap(propagatedContingency -> propagatedContingency.toLfContingency(lfNetwork).stream())
                    .collect(Collectors.toList()));
        }

        return new TestData(lfNetworks, listLfContingencies);
    }
}
