/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.MinimumSpanningTreeGraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.NaiveGraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfContingency;
import com.powsybl.security.LimitViolationFilter;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class OpenSecurityAnalysisGraphTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSecurityAnalysisGraphTest.class);

    private Network network;
    private List<List<LfContingency>> reference;
    private ContingenciesProvider contingenciesProvider;
    private SecurityAnalysisParameters securityAnalysisParameters;

    @BeforeEach
    void setUp() {

        network = OpenSecurityAnalysisTest.createNetwork();

        // Testing all contingencies at once
        contingenciesProvider = network -> network.getBranchStream()
            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
            .collect(Collectors.toList());

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        lfParameters.addExtension(OpenLoadFlowParameters.class,
            new OpenLoadFlowParameters().setSlackBusSelector(new FirstSlackBusSelector()));
        securityAnalysisParameters = new SecurityAnalysisParameters().setLoadFlowParameters(lfParameters);

        reference = getLoadFlowContingencies(() -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        LOGGER.info("Reference established (naive connectivity calculation) on test network containing {} branches", network.getBranchCount());
    }

    @Test
    void testEvenShiloach() {
        LOGGER.info("Test Even-Shiloach on test network containing {} branches", network.getBranchCount());
        List<List<LfContingency>> lfContingencies = getLoadFlowContingencies(EvenShiloachGraphDecrementalConnectivity::new);
        printResult(lfContingencies);
        checkResult(lfContingencies);
    }

    @Test
    void testMst() {
        LOGGER.info("Test Minimum Spanning Tree on test network containing {} branches", network.getBranchCount());
        List<List<LfContingency>> lfContingencies = getLoadFlowContingencies(MinimumSpanningTreeGraphDecrementalConnectivity::new);
        printResult(lfContingencies);
        checkResult(lfContingencies);
    }

    private void checkResult(List<List<LfContingency>> result) {
        Assert.assertEquals(reference.size(), result.size());
        for (int iNetwork = 0; iNetwork < result.size(); iNetwork++) {
            Assert.assertEquals(reference.get(iNetwork).size(), result.get(iNetwork).size());
            for (int iContingency = 0; iContingency < result.get(iNetwork).size(); iContingency++) {
                LfContingency contingencyReference = reference.get(iNetwork).get(iContingency);
                LfContingency contingencyResult = result.get(iNetwork).get(iContingency);
                Assert.assertEquals(contingencyReference.getContingency().getId(), contingencyResult.getContingency().getId());

                Set<LfBranch> branchesReference = contingencyReference.getBranches();
                Set<LfBranch> branchesResult = contingencyResult.getBranches();
                Assert.assertEquals(branchesReference.size(), branchesResult.size());
                branchesReference.forEach(b -> Assert.assertTrue(branchesResult.stream().anyMatch(b1 -> b1.getId().equals(b.getId()))));

                Set<LfBus> busesReference = contingencyReference.getBuses();
                Set<LfBus> busesResult = contingencyResult.getBuses();
                Assert.assertEquals(busesReference.size(), busesResult.size());
                busesReference.forEach(b -> Assert.assertTrue(busesResult.stream().anyMatch(b1 -> b1.getId().equals(b.getId()))));
            }
        }
    }

    private void printResult(List<List<LfContingency>> result) {
        for (List<LfContingency> networkResult : result) {
            for (LfContingency contingency : networkResult) {
                LOGGER.info("Contingency {} containing {} branches - {} buses (branches: {}, buses: {})",
                    contingency.getContingency().getId(), contingency.getBranches().size(), contingency.getBuses().size(),
                    contingency.getBranches().stream().map(LfBranch::getId).collect(Collectors.joining(",")),
                    contingency.getBuses().stream().map(LfBus::getId).collect(Collectors.joining(",")));
            }
        }
    }

    List<List<LfContingency>> getLoadFlowContingencies(Provider<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {

        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), connectivityProvider);

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowProvider.getParametersExt(securityAnalysisParameters.getLoadFlowParameters());

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency
        long start = System.currentTimeMillis();
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<OpenSecurityAnalysis.ContingencyContext> contingencyContexts = securityAnalysis.getContingencyContexts(contingencies, allSwitchesToOpen);
        LOGGER.info("Contingencies contexts calculated from contingencies in {} ms", System.currentTimeMillis() - start);

        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network,
            new DenseMatrixFactory(), lfParameters, lfParametersExt, true);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = securityAnalysis.createNetworks(allSwitchesToOpen, acParameters);

        // run simulation on each network
        start = System.currentTimeMillis();
        List<List<LfContingency>> listLfContingencies = new ArrayList<>();
        for (LfNetwork lfNetwork : lfNetworks) {
            listLfContingencies.add(securityAnalysis.createContingencies(contingencyContexts, lfNetwork));
        }
        LOGGER.info("LoadFlow contingencies calculated from contingency contexts in {} ms", System.currentTimeMillis() - start);

        return listLfContingencies;
    }

}
