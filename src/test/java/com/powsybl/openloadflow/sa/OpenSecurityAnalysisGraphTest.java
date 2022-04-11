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
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.MinimumSpanningTreeGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.security.LimitViolationFilter;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
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
    private SecurityAnalysisParameters securityAnalysisParameters;

    @BeforeEach
    void setUp() {

        network = NodeBreakerNetworkFactory.create();

        // Testing all contingencies at once
        contingenciesProvider = network -> network.getBranchStream()
            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
            .collect(Collectors.toList());

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        securityAnalysisParameters = new SecurityAnalysisParameters().setLoadFlowParameters(lfParameters);
    }

    @Test
    void testEvenShiloach() {
        LOGGER.info("Test Even-Shiloach on test network containing {} branches", network.getBranchCount());
        List<List<LfContingency>> lfContingencies = getLoadFlowContingencies(new EvenShiloachGraphDecrementalConnectivityFactory<>());
        printResult(lfContingencies);
        checkResult(lfContingencies, computeReference());
    }

    @Test
    void testMst() {
        LOGGER.info("Test Minimum Spanning Tree on test network containing {} branches", network.getBranchCount());
        List<List<LfContingency>> lfContingencies = getLoadFlowContingencies(new MinimumSpanningTreeGraphDecrementalConnectivityFactory<>());
        printResult(lfContingencies);
        checkResult(lfContingencies, computeReference());
    }

    private List<List<LfContingency>> computeReference() {
        List<List<LfContingency>> result = getLoadFlowContingencies(new NaiveGraphDecrementalConnectivityFactory<>(LfBus::getNum));
        LOGGER.info("Reference established (naive connectivity calculation) on test network containing {} branches", network.getBranchCount());
        return result;
    }

    @Test
    void testNullVertices() {
        network.getSwitch("B3").setOpen(true);
        contingenciesProvider = n -> Collections.singletonList(
            new Contingency("L1", new BranchContingency("L1")));
        List<List<LfContingency>> reference = computeReference();
        checkResult(getLoadFlowContingencies(new MinimumSpanningTreeGraphDecrementalConnectivityFactory<>()), reference);
        checkResult(getLoadFlowContingencies(new EvenShiloachGraphDecrementalConnectivityFactory<>()), reference);

        contingenciesProvider = n -> Collections.singletonList(
            new Contingency("L2", new BranchContingency("L2")));
        network.getSwitch("B3").setOpen(false);
        network.getSwitch("B1").setOpen(true);
        reference = computeReference();
        checkResult(getLoadFlowContingencies(new MinimumSpanningTreeGraphDecrementalConnectivityFactory<>()), reference);
        checkResult(getLoadFlowContingencies(new EvenShiloachGraphDecrementalConnectivityFactory<>()), reference);
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

    List<List<LfContingency>> getLoadFlowContingencies(GraphDecrementalConnectivityFactory<LfBus> connectivityFactory) {

        var matrixFactory = new DenseMatrixFactory();
        AcSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), matrixFactory, connectivityFactory, Collections.emptyList());

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency
        long start = System.currentTimeMillis();
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSecurityAnalysis(network, contingencies, allSwitchesToOpen,
                lfParameters.isShuntCompensatorVoltageControlOn(), lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        LOGGER.info("Contingencies contexts calculated from contingencies in {} ms", System.currentTimeMillis() - start);

        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
            lfParameters, lfParametersExt, matrixFactory, connectivityFactory, Reporter.NO_OP, true, false);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = securityAnalysis.createNetworks(allSwitchesToOpen, acParameters.getNetworkParameters());

        // run simulation on each network
        start = System.currentTimeMillis();
        List<List<LfContingency>> listLfContingencies = new ArrayList<>();
        for (LfNetwork lfNetwork : lfNetworks) {
            listLfContingencies.add(propagatedContingencies.stream()
                    .flatMap(propagatedContingency -> propagatedContingency.toLfContingency(lfNetwork, lfNetwork.getConnectivity(), true).stream())
                    .collect(Collectors.toList()));
        }
        LOGGER.info("LoadFlow contingencies calculated from contingency contexts in {} ms", System.currentTimeMillis() - start);

        return listLfContingencies;
    }

}
