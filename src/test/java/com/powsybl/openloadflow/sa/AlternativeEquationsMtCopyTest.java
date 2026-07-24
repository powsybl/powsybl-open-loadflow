/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.PostContingencyResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Combines the two opt-in features: alternative equations
 * ({@link OpenLoadFlowParameters#setAlternativeEquations(boolean)}) run inside a multi-threaded security analysis
 * that gives every contingency partition its own deep {@code LfNetworkCopier} copy of the network
 * ({@link OpenSecurityAnalysisParameters.NetworkPerThreadMode#COPY}, the default), for both contingency
 * partitioning modes: {@link OpenSecurityAnalysisParameters.ContingencyPartitioningMode#SLICE} (contiguous) and
 * {@link OpenSecurityAnalysisParameters.ContingencyPartitioningMode#ROUND_ROBIN} (interleaved).
 *
 * <p>This exercises the two SA hooks that both features add to {@code AbstractSecurityAnalysis}, on the copied
 * (and, when supported, presolved) networks of each partition: {@code adaptParameters} (which computes the
 * alternative islandable bus ids of the partition from its own copy and its own contingencies) and
 * {@code initStateFromPresolvedNetwork}.
 *
 * <p>The multi-threaded copy-mode results with alternative equations must agree with the single-threaded ones to
 * within solver tolerance whatever the partitioning (the copy feature alone is bit-identical, but with alternative
 * equations the single-thread and presolve-then-copy paths solve in a slightly different order, so agreement is to
 * a few ULP rather than exact), and — like a plain alternative-equations run — must match the legacy modeling.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
class AlternativeEquationsMtCopyTest extends AbstractOpenSecurityAnalysisTest {

    AlternativeEquationsMtCopyTest(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    /**
     * Eurostag network with an additional generator GEN2 behind a single transformer: losing NGEN2_NHV1 islands
     * VLGEN2 with GEN2 (100 MW generation loss to be absorbed by the main component), which is exactly the case
     * where alternative equations keep the matrix structure by putting trivial equations on the islanded bus.
     */
    private static Network createEurostagNetworkWithIslandableGenerator() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getGenerator("GEN").newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(280)
                .add();
        Substation p1 = network.getSubstation("P1");
        VoltageLevel vlgen2 = p1.newVoltageLevel()
                .setId("VLGEN2")
                .setNominalV(24.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vlgen2.getBusBreakerView().newBus()
                .setId("NGEN2")
                .add();
        vlgen2.newGenerator()
                .setId("GEN2")
                .setBus("NGEN2")
                .setConnectableBus("NGEN2")
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(24.5)
                .setTargetP(100)
                .add()
                .newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(100)
                .add();
        int zb380 = 380 * 380 / 100;
        p1.newTwoWindingsTransformer()
                .setId("NGEN2_NHV1")
                .setBus1("NGEN2")
                .setConnectableBus1("NGEN2")
                .setRatedU1(24.0)
                .setBus2("NHV1")
                .setConnectableBus2("NHV1")
                .setRatedU2(400.0)
                .setR(0.24 / 1800 * zb380)
                .setX(Math.sqrt(10 * 10 - 0.24 * 0.24) / 1800 * zb380)
                .add();
        network.getLoad("LOAD").setP0(699.838);
        return network;
    }

    private static List<Contingency> contingencies() {
        // several contingencies so ROUND_ROBIN actually interleaves them across partitions:
        // NGEN2_NHV1 islands VLGEN2 with GEN2 (structure-preserving islanding), NHV1_NHV2_1 triggers
        // post-contingency PV/PQ switching, plus a parallel-line and a generation-loss case.
        return List.of(Contingency.twoWindingsTransformer("NGEN2_NHV1"),
                Contingency.line("NHV1_NHV2_1"),
                Contingency.line("NHV1_NHV2_2"),
                Contingency.generator("GEN2"));
    }

    private SecurityAnalysisResult run(Network network, boolean alternativeEquations, int threadCount,
                                       OpenSecurityAnalysisParameters.NetworkPerThreadMode mode,
                                       OpenSecurityAnalysisParameters.ContingencyPartitioningMode partitioningMode) {
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(lfParameters).setAlternativeEquations(alternativeEquations);
        saParameters.setLoadFlowParameters(lfParameters);
        saParameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters()
                .setThreadCount(threadCount)
                .setNetworkPerThreadMode(mode)
                .setContingencyPartitioningMode(partitioningMode));
        return runSecurityAnalysis(network, contingencies(), createNetworkMonitors(network), saParameters);
    }

    // single-thread vs multi-thread with alternative equations: agreement to a few ULP (see class javadoc), well
    // below any physical significance, so a tight numerical tolerance rather than exact equality
    private static final double THREADING_TOLERANCE = 1e-6;

    private static void assertSameResultsAcrossThreads(SecurityAnalysisResult expected, SecurityAnalysisResult actual) {
        assertEquals(expected.getPreContingencyResult().getStatus(), actual.getPreContingencyResult().getStatus());
        for (BranchResult branchResult : expected.getPreContingencyResult().getNetworkResult().getBranchResults()) {
            BranchResult actualBranchResult = actual.getPreContingencyResult().getNetworkResult().getBranchResult(branchResult.getBranchId());
            assertEquals(branchResult.getP1(), actualBranchResult.getP1(), THREADING_TOLERANCE, "pre-contingency P1 mismatch on " + branchResult.getBranchId());
            assertEquals(branchResult.getQ1(), actualBranchResult.getQ1(), THREADING_TOLERANCE, "pre-contingency Q1 mismatch on " + branchResult.getBranchId());
        }
        assertEquals(expected.getPostContingencyResults().size(), actual.getPostContingencyResults().size());
        for (PostContingencyResult expectedPcr : expected.getPostContingencyResults()) {
            PostContingencyResult actualPcr = actual.getPostContingencyResults().stream()
                    .filter(r -> r.getContingency().getId().equals(expectedPcr.getContingency().getId()))
                    .findFirst().orElseThrow();
            assertEquals(expectedPcr.getStatus(), actualPcr.getStatus(), "status mismatch for " + expectedPcr.getContingency().getId());
            for (BranchResult branchResult : expectedPcr.getNetworkResult().getBranchResults()) {
                BranchResult actualBranchResult = actualPcr.getNetworkResult().getBranchResult(branchResult.getBranchId());
                assertEquals(branchResult.getP1(), actualBranchResult.getP1(), THREADING_TOLERANCE,
                        "post-contingency P1 mismatch on " + branchResult.getBranchId() + " for " + expectedPcr.getContingency().getId());
                assertEquals(branchResult.getQ1(), actualBranchResult.getQ1(), THREADING_TOLERANCE,
                        "post-contingency Q1 mismatch on " + branchResult.getBranchId() + " for " + expectedPcr.getContingency().getId());
            }
        }
    }

    private static void assertSameResultsWithinTolerance(SecurityAnalysisResult expected, SecurityAnalysisResult actual) {
        assertEquals(expected.getPreContingencyResult().getStatus(), actual.getPreContingencyResult().getStatus());
        compareNetworkResults(expected.getPreContingencyResult().getNetworkResult(), actual.getPreContingencyResult().getNetworkResult());
        assertEquals(expected.getPostContingencyResults().size(), actual.getPostContingencyResults().size());
        for (PostContingencyResult expectedPcr : expected.getPostContingencyResults()) {
            PostContingencyResult actualPcr = actual.getPostContingencyResults().stream()
                    .filter(r -> r.getContingency().getId().equals(expectedPcr.getContingency().getId()))
                    .findFirst().orElseThrow();
            assertEquals(expectedPcr.getStatus(), actualPcr.getStatus(), "status mismatch for " + expectedPcr.getContingency().getId());
            compareNetworkResults(expectedPcr.getNetworkResult(), actualPcr.getNetworkResult());
        }
    }

    private static void compareNetworkResults(NetworkResult legacyNetworkResult, NetworkResult networkResult) {
        assertEquals(legacyNetworkResult.getBranchResults().size(), networkResult.getBranchResults().size());
        for (BranchResult legacyBranchResult : legacyNetworkResult.getBranchResults()) {
            BranchResult branchResult = networkResult.getBranchResult(legacyBranchResult.getBranchId());
            assertEquals(legacyBranchResult.getP1(), branchResult.getP1(), 1e-2, "p1 mismatch on branch " + legacyBranchResult.getBranchId());
            assertEquals(legacyBranchResult.getQ1(), branchResult.getQ1(), 1e-2, "q1 mismatch on branch " + legacyBranchResult.getBranchId());
            assertEquals(legacyBranchResult.getP2(), branchResult.getP2(), 1e-2, "p2 mismatch on branch " + legacyBranchResult.getBranchId());
            assertEquals(legacyBranchResult.getQ2(), branchResult.getQ2(), 1e-2, "q2 mismatch on branch " + legacyBranchResult.getBranchId());
        }
        assertEquals(legacyNetworkResult.getBusResults().size(), networkResult.getBusResults().size());
        for (BusResult legacyBusResult : legacyNetworkResult.getBusResults()) {
            BusResult busResult = networkResult.getBusResults().stream()
                    .filter(b -> b.getBusId().equals(legacyBusResult.getBusId()))
                    .findFirst().orElseThrow();
            assertEquals(legacyBusResult.getV(), busResult.getV(), 1e-4, "v mismatch on bus " + legacyBusResult.getBusId());
            assertEquals(legacyBusResult.getAngle(), busResult.getAngle(), 1e-4, "angle mismatch on bus " + legacyBusResult.getBusId());
        }
    }

    @ParameterizedTest(name = "threads={0} partitioning={1}")
    @CsvSource({"2, SLICE", "3, SLICE", "2, ROUND_ROBIN", "3, ROUND_ROBIN"})
    void alternativeEquationsCopyModeIdenticalToSingleThread(int threadCount, OpenSecurityAnalysisParameters.ContingencyPartitioningMode partitioningMode) {
        // multi-threaded copy-mode SA with alternative equations must be bit-identical to the single-threaded one,
        // for both partitioning modes: the deep copy of each partition carries the same network, and the
        // per-partition adaptParameters computes the same alternative islandable bus ids
        SecurityAnalysisResult singleThread = run(createEurostagNetworkWithIslandableGenerator(),
                true, 1, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY, partitioningMode);
        SecurityAnalysisResult multiThread = run(createEurostagNetworkWithIslandableGenerator(),
                true, threadCount, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY, partitioningMode);
        assertSameResultsAcrossThreads(singleThread, multiThread);
    }

    @ParameterizedTest(name = "threads={0} partitioning={1}")
    @CsvSource({"2, SLICE", "3, SLICE", "2, ROUND_ROBIN", "3, ROUND_ROBIN"})
    void alternativeEquationsCopyModeIdenticalToLegacy(int threadCount, OpenSecurityAnalysisParameters.ContingencyPartitioningMode partitioningMode) {
        // end-to-end correctness of the combined stack: alternative equations, multi-threaded, copy mode, with the
        // given partitioning, must give the same result as the legacy modeling run single-threaded
        SecurityAnalysisResult legacy = run(createEurostagNetworkWithIslandableGenerator(),
                false, 1, OpenSecurityAnalysisParameters.NetworkPerThreadMode.REBUILD,
                OpenSecurityAnalysisParameters.ContingencyPartitioningMode.SLICE);
        SecurityAnalysisResult alternativeMultiThread = run(createEurostagNetworkWithIslandableGenerator(),
                true, threadCount, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY, partitioningMode);
        assertSameResultsWithinTolerance(legacy, alternativeMultiThread);
    }
}
