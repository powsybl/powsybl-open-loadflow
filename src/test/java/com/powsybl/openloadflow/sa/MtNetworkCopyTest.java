/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.action.Action;
import com.powsybl.action.SwitchAction;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.OperatorStrategyResult;
import com.powsybl.security.results.PostContingencyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With the network per thread COPY mode, a multi-threaded security analysis simulates the very same
 * network as the single-threaded one (built once with the topo config covering all contingencies),
 * so the results must be identical whatever the thread count, including on node-breaker networks
 * where contingency propagation used to give each partition a different network.
 *
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
class MtNetworkCopyTest extends AbstractOpenSecurityAnalysisTest {

    MtNetworkCopyTest(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    private SecurityAnalysisResult run(Network network, List<Contingency> contingencies, int threadCount, boolean dc, boolean dcFastMode,
                                       OpenSecurityAnalysisParameters.NetworkPerThreadMode mode) {
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        saParameters.setLoadFlowParameters(new LoadFlowParameters().setDc(dc));
        OpenSecurityAnalysisParameters saExt = new OpenSecurityAnalysisParameters()
                .setThreadCount(threadCount)
                .setDcFastMode(dcFastMode)
                .setNetworkPerThreadMode(mode);
        saParameters.addExtension(OpenSecurityAnalysisParameters.class, saExt);
        return runSecurityAnalysis(network, contingencies, createNetworkMonitors(network), saParameters);
    }

    private static void assertSameResults(SecurityAnalysisResult expected, SecurityAnalysisResult actual) {
        // pre-contingency flows
        for (BranchResult branchResult : expected.getPreContingencyResult().getNetworkResult().getBranchResults()) {
            BranchResult actualBranchResult = actual.getPreContingencyResult().getNetworkResult().getBranchResult(branchResult.getBranchId());
            assertEquals(branchResult.getP1(), actualBranchResult.getP1(), 0, "pre-contingency P1 mismatch on " + branchResult.getBranchId());
        }
        assertEquals(expected.getPostContingencyResults().size(), actual.getPostContingencyResults().size());
        for (PostContingencyResult expectedPcr : expected.getPostContingencyResults()) {
            PostContingencyResult actualPcr = actual.getPostContingencyResults().stream()
                    .filter(r -> r.getContingency().getId().equals(expectedPcr.getContingency().getId()))
                    .findFirst().orElseThrow();
            assertEquals(expectedPcr.getStatus(), actualPcr.getStatus());
            // post-contingency flows, bit identical
            for (BranchResult branchResult : expectedPcr.getNetworkResult().getBranchResults()) {
                BranchResult actualBranchResult = actualPcr.getNetworkResult().getBranchResult(branchResult.getBranchId());
                assertEquals(branchResult.getP1(), actualBranchResult.getP1(), 0,
                        "post-contingency P1 mismatch on " + branchResult.getBranchId() + " for " + expectedPcr.getContingency().getId());
            }
            // bus results under the same identifiers (same topology view as single thread)
            assertEquals(expectedPcr.getNetworkResult().getBusResults().stream().map(BusResult::getBusId).sorted().toList(),
                    actualPcr.getNetworkResult().getBusResults().stream().map(BusResult::getBusId).sorted().toList(),
                    "bus result ids mismatch for " + expectedPcr.getContingency().getId());
        }
    }

    @ParameterizedTest(name = "threads={0} dc={1} dcFastMode={2}")
    @CsvSource({"2, false, false", "4, false, false", "2, true, false", "4, true, false", "2, true, true", "4, true, true"})
    void testMultiThreadResultsIdenticalToSingleThread(int threadCount, boolean dc, boolean dcFastMode) {
        // node-breaker network: contingency propagation gives partitions different topo configs,
        // the historical source of multi-thread vs single-thread divergence
        Network network = createNodeBreakerNetwork();
        List<Contingency> contingencies = List.of(
                new Contingency("L1", new BranchContingency("L1")),
                new Contingency("L2", new BranchContingency("L2")),
                new Contingency("LD", new LoadContingency("LD")));

        SecurityAnalysisResult singleThread = run(network, contingencies, 1, dc, dcFastMode, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY);
        SecurityAnalysisResult multiThread = run(network, contingencies, threadCount, dc, dcFastMode, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY);
        assertSameResults(singleThread, multiThread);
    }

    @Test
    void testOperatorStrategiesIdenticalToSingleThread() {
        // remedial switch-closing actions make the network go through an initial topology restoration
        // (switches built closed then reopened, leaving disabled elements and removed connectivity
        // edges); the deep copy reproduces that state, and post-contingency and operator-strategy
        // results must be identical to single-thread mode
        Network network = NodeBreakerNetworkFactory.create3Bars();
        network.getSwitch("C1").setOpen(true);
        network.getSwitch("C2").setOpen(true);

        List<Contingency> contingencies = List.of(
                new Contingency("L1", new BranchContingency("L1")),
                new Contingency("L3", new BranchContingency("L3")),
                new Contingency("L2", new BranchContingency("L2")));
        List<Action> actions = List.of(new SwitchAction("action1", "C1", false), new SwitchAction("action3", "C2", false));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyL1", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("action1")),
                new OperatorStrategy("strategyL3", ContingencyContext.specificContingency("L3"), new TrueCondition(), List.of("action3")),
                new OperatorStrategy("strategyL2", ContingencyContext.specificContingency("L2"), new TrueCondition(), List.of("action1", "action3")));

        LoadFlowParameters parameters = new LoadFlowParameters().setDistributedSlack(false);
        setSlackBusId(parameters, "VL2_0");

        SecurityAnalysisResult single = runStrategies(network, contingencies, operatorStrategies, actions, parameters, 1);
        SecurityAnalysisResult multi = runStrategies(network, contingencies, operatorStrategies, actions, parameters, 2);

        assertSameResults(single, multi);
        assertEquals(single.getOperatorStrategyResults().size(), multi.getOperatorStrategyResults().size());
        for (OperatorStrategyResult expected : single.getOperatorStrategyResults()) {
            OperatorStrategyResult actual = multi.getOperatorStrategyResults().stream()
                    .filter(r -> r.getOperatorStrategy().getId().equals(expected.getOperatorStrategy().getId()))
                    .findFirst().orElseThrow();
            for (BranchResult branchResult : expected.getNetworkResult().getBranchResults()) {
                assertEquals(branchResult.getI1(), actual.getNetworkResult().getBranchResult(branchResult.getBranchId()).getI1(), 0,
                        "operator strategy " + expected.getOperatorStrategy().getId() + " I1 mismatch on " + branchResult.getBranchId());
            }
        }
    }

    private SecurityAnalysisResult runStrategies(Network network, List<Contingency> contingencies, List<OperatorStrategy> operatorStrategies,
                                                 List<Action> actions, LoadFlowParameters parameters, int threadCount) {
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        saParameters.setLoadFlowParameters(parameters);
        saParameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters().setThreadCount(threadCount));
        return runSecurityAnalysis(network, contingencies, createAllBranchesMonitors(network), saParameters,
                operatorStrategies, actions, ReportNode.NO_OP);
    }

    private SecurityAnalysisResult runRoundRobin(Network network, List<Contingency> contingencies, int threadCount, boolean dc,
                                                 ReportNode reportNode) {
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        saParameters.setLoadFlowParameters(new LoadFlowParameters().setDc(dc));
        OpenSecurityAnalysisParameters saExt = new OpenSecurityAnalysisParameters()
                .setThreadCount(threadCount)
                .setContingencyPartitioningMode(OpenSecurityAnalysisParameters.ContingencyPartitioningMode.ROUND_ROBIN);
        saParameters.addExtension(OpenSecurityAnalysisParameters.class, saExt);
        return runSecurityAnalysis(network, contingencies, createNetworkMonitors(network), saParameters, reportNode);
    }

    @ParameterizedTest(name = "threads={0} dc={1}")
    @CsvSource({"2, false", "3, false", "2, true"})
    void testRoundRobinPartitioningIdenticalToSingleThread(int threadCount, boolean dc) {
        Network network = createNodeBreakerNetwork();
        List<Contingency> contingencies = List.of(
                new Contingency("L1", new BranchContingency("L1")),
                new Contingency("L2", new BranchContingency("L2")),
                new Contingency("LD", new LoadContingency("LD")));

        SecurityAnalysisResult singleThread = runRoundRobin(network, contingencies, 1, dc, ReportNode.NO_OP);
        SecurityAnalysisResult multiThread = runRoundRobin(network, contingencies, threadCount, dc, ReportNode.NO_OP);
        assertSameResults(singleThread, multiThread);
        // results must come back in the contingency list order despite the interleaved partitions
        assertEquals(singleThread.getPostContingencyResults().stream().map(r -> r.getContingency().getId()).toList(),
                multiThread.getPostContingencyResults().stream().map(r -> r.getContingency().getId()).toList());
    }

    @Test
    void testRoundRobinReportIdenticalToSingleThread() throws java.io.IOException {
        Network network = createNodeBreakerNetwork();
        List<Contingency> contingencies = List.of(
                new Contingency("L1", new BranchContingency("L1")),
                new Contingency("L2", new BranchContingency("L2")),
                new Contingency("LD", new LoadContingency("LD")));

        ReportNode singleThreadReport = newRootReportNode();
        runRoundRobin(network, contingencies, 1, false, singleThreadReport);
        ReportNode multiThreadReport = newRootReportNode();
        runRoundRobin(network, contingencies, 2, false, multiThreadReport);

        java.io.StringWriter singleThreadWriter = new java.io.StringWriter();
        singleThreadReport.print(singleThreadWriter);
        java.io.StringWriter multiThreadWriter = new java.io.StringWriter();
        multiThreadReport.print(multiThreadWriter);
        assertEquals(singleThreadWriter.toString(), multiThreadWriter.toString(),
                "multi-thread round-robin report should be identical to the single-thread one");
    }

    private static ReportNode newRootReportNode() {
        return ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("olf.threadRoot")
                .build();
    }

    @ParameterizedTest(name = "dc={1}")
    @CsvSource({"2, false", "2, true"})
    void testRebuildFallbackModeStillWorks(int threadCount, boolean dc) {
        Network network = createNodeBreakerNetwork();
        List<Contingency> contingencies = List.of(
                new Contingency("L1", new BranchContingency("L1")),
                new Contingency("L2", new BranchContingency("L2")));

        SecurityAnalysisResult copyMode = run(network, contingencies, threadCount, dc, false, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY);
        SecurityAnalysisResult rebuildMode = run(network, contingencies, threadCount, dc, false, OpenSecurityAnalysisParameters.NetworkPerThreadMode.REBUILD);
        assertEquals(copyMode.getPostContingencyResults().size(), rebuildMode.getPostContingencyResults().size());
        // flows agree between the two modes (rebuild on this fixture has no numeric divergence)
        for (PostContingencyResult expectedPcr : copyMode.getPostContingencyResults()) {
            PostContingencyResult actualPcr = rebuildMode.getPostContingencyResults().stream()
                    .filter(r -> r.getContingency().getId().equals(expectedPcr.getContingency().getId()))
                    .findFirst().orElseThrow();
            for (BranchResult branchResult : expectedPcr.getNetworkResult().getBranchResults()) {
                BranchResult actualBranchResult = actualPcr.getNetworkResult().getBranchResult(branchResult.getBranchId());
                assertEquals(branchResult.getP1(), actualBranchResult.getP1(), 1e-6);
            }
        }
    }
}
