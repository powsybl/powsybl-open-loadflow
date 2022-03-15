/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.*;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.LoadDetailAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.security.*;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Double.NaN;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSecurityAnalysisTest {

    private static Network createNodeBreakerNetwork() {
        Network network = NodeBreakerNetworkFactory.create();

        network.getLine("L1").newCurrentLimits1()
                .setPermanentLimit(940.0)
                .beginTemporaryLimit()
                .setName("60")
                .setAcceptableDuration(60)
                .setValue(1000)
                .endTemporaryLimit()
                .add();
        network.getLine("L1").newCurrentLimits2()
                .setPermanentLimit(940.0)
                .add();
        network.getLine("L2").newCurrentLimits1()
                .setPermanentLimit(940.0)
                .beginTemporaryLimit()
                .setName("60")
                .setAcceptableDuration(60)
                .setValue(950)
                .endTemporaryLimit()
                .add();
        network.getLine("L2").newCurrentLimits2()
                .setPermanentLimit(940.0)
                .beginTemporaryLimit()
                .setName("600")
                .setAcceptableDuration(600)
                .setValue(945)
                .endTemporaryLimit()
                .beginTemporaryLimit()
                .setName("60")
                .setAcceptableDuration(60)
                .setValue(970)
                .endTemporaryLimit()
                .add();

        return network;
    }

    /**
     * Runs a security analysis with default parameters + most meshed slack bus selection
     */
    private static SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors,
                                                              LoadFlowParameters lfParameters) {
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        return runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
    }

    private static SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors,
                                                              SecurityAnalysisParameters saParameters) {
        ContingenciesProvider provider = n -> contingencies;
        var saProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        var computationManager = Mockito.mock(ComputationManager.class);
        Mockito.when(computationManager.getExecutor()).thenReturn(ForkJoinPool.commonPool());
        SecurityAnalysisReport report = saProvider.run(network,
                network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(),
                new LimitViolationFilter(),
                computationManager,
                saParameters,
                provider,
                Collections.emptyList(),
                monitors)
                .join();
        return report.getResult();
    }

    private static SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors) {
        return runSecurityAnalysis(network, contingencies, monitors, new LoadFlowParameters());
    }

    private static SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, LoadFlowParameters loadFlowParameters) {
        return runSecurityAnalysis(network, contingencies, Collections.emptyList(), loadFlowParameters);
    }

    private static SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies) {
        return runSecurityAnalysis(network, contingencies, Collections.emptyList());
    }

    private static SecurityAnalysisResult runSecurityAnalysis(Network network) {
        return runSecurityAnalysis(network, Collections.emptyList(), Collections.emptyList());
    }

    private static List<StateMonitor> createAllBranchesMonitors(Network network) {
        Set<String> allBranchIds = network.getBranchStream().map(Identifiable::getId).collect(Collectors.toSet());
        return List.of(new StateMonitor(ContingencyContext.all(), allBranchIds, Collections.emptySet(), Collections.emptySet()));
    }

    private static List<Contingency> allBranches(Network network) {
        return network.getBranchStream()
                .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
                .collect(Collectors.toList());
    }

    private static void setSlackBusId(LoadFlowParameters lfParameters, String slackBusId) {
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId(slackBusId);
    }

    private static Optional<PostContingencyResult> getOptionalPostContingencyResult(SecurityAnalysisResult result, String contingencyId) {
        return result.getPostContingencyResults().stream()
                .filter(r -> r.getContingency().getId().equals(contingencyId))
                .findFirst();
    }

    private static PostContingencyResult getPostContingencyResult(SecurityAnalysisResult result, String contingencyId) {
        return getOptionalPostContingencyResult(result, contingencyId)
                .orElseThrow();
    }

    private static void assertAlmostEquals(BusResults expected, BusResults actual, double epsilon) {
        assertEquals(expected.getVoltageLevelId(), actual.getVoltageLevelId());
        assertEquals(expected.getBusId(), actual.getBusId());
        assertEquals(expected.getV(), actual.getV(), epsilon);
        assertEquals(expected.getAngle(), actual.getAngle(), epsilon);
    }

    private static void assertAlmostEquals(BranchResult expected, BranchResult actual, double epsilon) {
        assertEquals(expected.getBranchId(), actual.getBranchId());
        assertEquals(expected.getP1(), actual.getP1(), epsilon);
        assertEquals(expected.getQ1(), actual.getQ1(), epsilon);
        assertEquals(expected.getI1(), actual.getI1(), epsilon);
        assertEquals(expected.getP2(), actual.getP2(), epsilon);
        assertEquals(expected.getQ2(), actual.getQ2(), epsilon);
        assertEquals(expected.getI2(), actual.getI2(), epsilon);
    }

    private static void assertAlmostEquals(ThreeWindingsTransformerResult expected, ThreeWindingsTransformerResult actual, double epsilon) {
        assertEquals(expected.getThreeWindingsTransformerId(), actual.getThreeWindingsTransformerId());
        assertEquals(expected.getP1(), actual.getP1(), epsilon);
        assertEquals(expected.getQ1(), actual.getQ1(), epsilon);
        assertEquals(expected.getI1(), actual.getI1(), epsilon);
        assertEquals(expected.getP2(), actual.getP2(), epsilon);
        assertEquals(expected.getQ2(), actual.getQ2(), epsilon);
        assertEquals(expected.getI2(), actual.getI2(), epsilon);
        assertEquals(expected.getP3(), actual.getP3(), epsilon);
        assertEquals(expected.getQ3(), actual.getQ3(), epsilon);
        assertEquals(expected.getI3(), actual.getI3(), epsilon);
    }

    @Test
    void testCurrentLimitViolations() {
        Network network = createNodeBreakerNetwork();

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL1_1");

        List<Contingency> contingencies = Stream.of("L1", "L2")
            .map(id -> new Contingency(id, new BranchContingency(id)))
            .collect(Collectors.toList());

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, lfParameters);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(0, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertTrue(result.getPostContingencyResults().get(1).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
    }

    @Test
    void testCurrentLimitViolations2() {
        Network network = createNodeBreakerNetwork();
        network.getLine("L1").getCurrentLimits1().setPermanentLimit(200);

        List<Contingency> contingencies = List.of(new Contingency("L2", new BranchContingency("L2")));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(1, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
    }

    @Test
    void testLowVoltageLimitViolations() {
        Network network = createNodeBreakerNetwork();
        network.getGenerator("G").setTargetV(393);

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL1_1");

        List<Contingency> contingencies = Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, lfParameters);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(0, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(3, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());

        List<LimitViolation> limitViolations = result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations();
        Optional<LimitViolation> limitViolationL21 = limitViolations.stream().filter(limitViolation -> limitViolation.getSubjectId().equals("L2") && limitViolation.getSide() == Branch.Side.ONE).findFirst();
        assertTrue(limitViolationL21.isPresent());
        assertEquals(0, limitViolationL21.get().getAcceptableDuration());
        assertEquals(950, limitViolationL21.get().getLimit());
        Optional<LimitViolation> limitViolationL22 = limitViolations.stream().filter(limitViolation -> limitViolation.getSubjectId().equals("L2") && limitViolation.getSide() == Branch.Side.TWO).findFirst();
        assertTrue(limitViolationL22.isPresent());
        assertEquals(0, limitViolationL22.get().getAcceptableDuration());
        assertEquals(970, limitViolationL22.get().getLimit());

        assertTrue(result.getPostContingencyResults().get(1).getLimitViolationsResult().isComputationOk());
        assertEquals(3, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());

        List<LimitViolation> limitViolations1 = result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations();
        LimitViolation lowViolation = limitViolations1.get(2);
        assertEquals(LimitViolationType.LOW_VOLTAGE, lowViolation.getLimitType());
        assertEquals(370, lowViolation.getLimit());
    }

    @Test
    void testHighVoltageLimitViolations() {
        Network network = createNodeBreakerNetwork();
        network.getGenerator("G").setTargetV(421);

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL1_1");

        List<Contingency> contingencies = Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, lfParameters);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());

        assertEquals(0, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertTrue(result.getPostContingencyResults().get(1).getLimitViolationsResult().isComputationOk());
        assertEquals(0, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
    }

    @Test
    void testActivePowerLimitViolations() {
        Network network = createNodeBreakerNetwork();
        network.getLine("L1").newActivePowerLimits1()
               .setPermanentLimit(1.0)
               .beginTemporaryLimit()
               .setName("60")
               .setAcceptableDuration(60)
               .setValue(1.2)
               .endTemporaryLimit()
               .add();

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL1_1");

        List<Contingency> contingencies = Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, lfParameters);

        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());

        LimitViolation limitViolation0 = result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().get(0);
        assertEquals("L1", limitViolation0.getSubjectId());
        assertEquals(LimitViolationType.ACTIVE_POWER, limitViolation0.getLimitType());
        assertEquals(608.334, limitViolation0.getValue(), 10E-3);

        int activePowerLimitViolationsCount = 0;
        for (PostContingencyResult r : result.getPostContingencyResults()) {
            for (LimitViolation v : r.getLimitViolationsResult().getLimitViolations()) {
                if (v.getLimitType() == LimitViolationType.ACTIVE_POWER) {
                    activePowerLimitViolationsCount++;
                }
            }
        }
        assertEquals(1, activePowerLimitViolationsCount);
    }

    @Test
    void testApparentPowerLimitViolations() {
        Network network = createNodeBreakerNetwork();
        network.getLine("L1").newApparentPowerLimits1()
               .setPermanentLimit(1.0)
               .beginTemporaryLimit()
               .setName("60")
               .setAcceptableDuration(60)
               .setValue(1.2)
               .endTemporaryLimit()
               .add();

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL1_1");

        List<Contingency> contingencies = Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, lfParameters);

        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());

        LimitViolation limitViolation0 = result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().get(0);
        assertEquals("L1", limitViolation0.getSubjectId());
        assertEquals(LimitViolationType.APPARENT_POWER, limitViolation0.getLimitType());
        assertEquals(651.796, limitViolation0.getValue(), 10E-3);

        int apparentPowerLimitViolationsCount = 0;
        for (PostContingencyResult r : result.getPostContingencyResults()) {
            for (LimitViolation v : r.getLimitViolationsResult().getLimitViolations()) {
                if (v.getLimitType() == LimitViolationType.APPARENT_POWER) {
                    apparentPowerLimitViolationsCount++;
                }
            }
        }
        assertEquals(1, apparentPowerLimitViolationsCount);
    }

    @Test
    void testFourSubstations() {
        Network network = FourSubstationsNodeBreakerFactory.create();

        // Testing all contingencies at once
        List<Contingency> contingencies = allBranches(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters()
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        result = runSecurityAnalysis(network, contingencies, loadFlowParameters);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testNoGenerator() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").getTerminal().disconnect();

        CompletionException exception = assertThrows(CompletionException.class, () -> runSecurityAnalysis(network));
        assertEquals("Largest network is invalid", exception.getCause().getMessage());
    }

    @Test
    void testNoRemainingGenerator() {
        Network network = EurostagTutorialExample1Factory.create();

        List<Contingency> contingencies = List.of(new Contingency("NGEN_NHV1", new BranchContingency("NGEN_NHV1")));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);

        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testNoRemainingLoad() {
        Network network = EurostagTutorialExample1Factory.create();

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDistributedSlack(true)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("NHV2_NLOAD", new BranchContingency("NHV2_NLOAD")));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, lfParameters);

        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testSaWithSeveralConnectedComponents() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();

        // Testing all contingencies at once
        List<Contingency> contingencies = allBranches(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testSaWithStateMonitor() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        // 2 N-1 on the 2 lines
        List<Contingency> contingencies = List.of(
            new Contingency("NHV1_NHV2_1", new BranchContingency("NHV1_NHV2_1")),
            new Contingency("NHV1_NHV2_2", new BranchContingency("NHV1_NHV2_2"))
        );

        // Monitor on branch and step-up transformer for all states
        List<StateMonitor> monitors = List.of(
            new StateMonitor(ContingencyContext.all(), Set.of("NHV1_NHV2_1", "NGEN_NHV1"), Set.of("VLLOAD"), emptySet())
        );

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        List<BusResults> busResults = preContingencyResult.getPreContingencyBusResults();
        BusResults expectedBus = new BusResults("VLLOAD", "NLOAD", 147.6, -9.6);
        assertEquals(1, busResults.size());
        assertAlmostEquals(expectedBus, busResults.get(0), 0.1);

        assertEquals(2, preContingencyResult.getPreContingencyBranchResults().size());
        assertAlmostEquals(new BranchResult("NHV1_NHV2_1", 302, 99, 457, -300, -137.2, 489),
                           preContingencyResult.getPreContingencyBranchResult("NHV1_NHV2_1"), 1);
        assertAlmostEquals(new BranchResult("NGEN_NHV1", 606, 225, 15226, -605, -198, 914),
                           preContingencyResult.getPreContingencyBranchResult("NGEN_NHV1"), 1);

        //No result when the branch itself is disconnected
        assertNull(result.getPostContingencyResults().get(0).getBranchResult("NHV1_NHV2_1"));

        assertAlmostEquals(new BranchResult("NHV1_NHV2_1", 611, 334, 1009, -601, -285, 1048),
                result.getPostContingencyResults().get(1).getBranchResult("NHV1_NHV2_1"), 1);
        assertAlmostEquals(new BranchResult("NGEN_NHV1", 611, 368, 16815, -611, -334, 1009),
                           result.getPostContingencyResults().get(1).getBranchResult("NGEN_NHV1"), 1);
    }

    @Test
    void testSaWithStateMonitorNotExistingBranchBus() {
        Network network = DistributedSlackNetworkFactory.create();

        List<StateMonitor> monitors = List.of(
            new StateMonitor(ContingencyContext.all(), Collections.singleton("l1"), Collections.singleton("bus"), Collections.singleton("three windings"))
        );

        SecurityAnalysisResult result = runSecurityAnalysis(network, allBranches(network), monitors);

        assertEquals(0, result.getPreContingencyResult().getPreContingencyBusResults().size());
        assertEquals(0, result.getPreContingencyResult().getPreContingencyBranchResults().size());
    }

    @Test
    void testSaWithStateMonitorDisconnectBranch() {
        Network network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal1().disconnect();

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("l24"), Collections.singleton("b1_vl"), emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(network, allBranches(network), monitors);

        assertEquals(1, result.getPreContingencyResult().getPreContingencyBusResults().size());

        assertEquals(new BusResults("b1_vl", "b1", 400, 0.003581299841270782), result.getPreContingencyResult().getPreContingencyBusResults().get(0));
        assertEquals(1, result.getPreContingencyResult().getPreContingencyBusResults().size());
        assertEquals(new BranchResult("l24", NaN, NaN, NaN, 0.0, -0.0, 0.0),
                     result.getPreContingencyResult().getPreContingencyBranchResults().get(0));

        network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal2().disconnect();

        result = runSecurityAnalysis(network, allBranches(network), monitors);

        assertEquals(0, result.getPreContingencyResult().getPreContingencyBranchResults().size());

        network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal2().disconnect();
        network.getBranch("l24").getTerminal1().disconnect();

        result = runSecurityAnalysis(network, allBranches(network), monitors);

        assertEquals(0, result.getPreContingencyResult().getPreContingencyBranchResults().size());
    }

    @Test
    void testSaWithStateMonitorDanglingLine() {
        Network network = BoundaryFactory.create();

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("dl1"), Collections.singleton("vl1"), emptySet()));

        List<Contingency> contingencies = allBranches(network);
        CompletionException exception = assertThrows(CompletionException.class, () -> runSecurityAnalysis(network, contingencies, monitors));
        assertEquals("Unsupported type of branch for branch result: dl1", exception.getCause().getMessage());
    }

    @Test
    void testSaWithStateMonitorLfLeg() {
        Network network = T3wtFactory.create();

        // Testing all contingencies at once
        List<StateMonitor> monitors = List.of(
            new StateMonitor(ContingencyContext.all(), emptySet(), emptySet(), Collections.singleton("3wt"))
        );

        SecurityAnalysisResult result = runSecurityAnalysis(network, allBranches(network), monitors);

        assertEquals(1, result.getPreContingencyResult().getPreContingencyThreeWindingsTransformerResults().size());
        assertAlmostEquals(new ThreeWindingsTransformerResult("3wt", 161, 82, 258,
                                                              -161, -74, 435, 0, 0, 0),
                result.getPreContingencyResult().getPreContingencyThreeWindingsTransformerResults().get(0), 1);
    }

    @Test
    void testSaDcMode() {
        Network fourBusNetwork = FourBusNetworkFactory.create();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDc(true);
        setSlackBusId(lfParameters, "b1_vl_0");
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);

        List<Contingency> contingencies = allBranches(fourBusNetwork);

        fourBusNetwork.getLine("l14").newActivePowerLimits1().setPermanentLimit(0.1).add();
        fourBusNetwork.getLine("l12").newActivePowerLimits1().setPermanentLimit(0.2).add();
        fourBusNetwork.getLine("l23").newActivePowerLimits1().setPermanentLimit(0.25).add();
        fourBusNetwork.getLine("l34").newActivePowerLimits1().setPermanentLimit(0.15).add();
        fourBusNetwork.getLine("l13").newActivePowerLimits1().setPermanentLimit(0.1).add();

        List<StateMonitor> monitors = List.of(new StateMonitor(ContingencyContext.all(), Set.of("l14", "l12", "l23", "l34", "l13"), Collections.emptySet(), Collections.emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(fourBusNetwork, contingencies, monitors, securityAnalysisParameters);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(5, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(5, result.getPostContingencyResults().size());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(2).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(3).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(4).getLimitViolationsResult().getLimitViolations().size());

        //Branch result for first contingency
        assertEquals(5, result.getPostContingencyResults().get(0).getBranchResults().size());

        //Check branch results for flowTransfer computation for contingency on l14
        PostContingencyResult postContl14 = getPostContingencyResult(result, "l14");
        assertEquals("l14", postContl14.getContingency().getId());

        BranchResult brl14l12 = postContl14.getBranchResult("l12");
        assertEquals(0.333, brl14l12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.333, brl14l12.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l14 = postContl14.getBranchResult("l14");
        assertEquals(0.0, brl14l14.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.0, brl14l14.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l23 = postContl14.getBranchResult("l23");
        assertEquals(1.333, brl14l23.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.333, brl14l23.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l34 = postContl14.getBranchResult("l34");
        assertEquals(-1.0, brl14l34.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.0, brl14l34.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l13 = postContl14.getBranchResult("l13");
        assertEquals(1.666, brl14l13.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.666, brl14l13.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaDcModeWithIncreasedParameters() {
        Network fourBusNetwork = FourBusNetworkFactory.create();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDc(true);
        setSlackBusId(lfParameters, "b1_vl_0");
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        SecurityAnalysisParameters.IncreasedViolationsParameters increasedViolationsParameters = new SecurityAnalysisParameters.IncreasedViolationsParameters();
        increasedViolationsParameters.setFlowProportionalThreshold(0);
        securityAnalysisParameters.setIncreasedViolationsParameters(increasedViolationsParameters);

        List<Contingency> contingencies = allBranches(fourBusNetwork);

        fourBusNetwork.getLine("l14").newActivePowerLimits1().setPermanentLimit(0.1).add();
        fourBusNetwork.getLine("l12").newActivePowerLimits1().setPermanentLimit(0.2).add();
        fourBusNetwork.getLine("l23").newActivePowerLimits1().setPermanentLimit(0.25).add();
        fourBusNetwork.getLine("l34").newActivePowerLimits1().setPermanentLimit(0.15).add();
        fourBusNetwork.getLine("l13").newActivePowerLimits1().setPermanentLimit(0.1).add();

        List<StateMonitor> monitors = List.of(new StateMonitor(ContingencyContext.all(), Set.of("l14", "l12", "l23", "l34", "l13"), Collections.emptySet(), Collections.emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(fourBusNetwork, contingencies, monitors, securityAnalysisParameters);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(5, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(5, result.getPostContingencyResults().size());
        assertEquals(3, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(3, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(2).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(3).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(4).getLimitViolationsResult().getLimitViolations().size());

        //Branch result for first contingency
        assertEquals(5, result.getPostContingencyResults().get(0).getBranchResults().size());

        //Check branch results for flowTransfer computation for contingency on l14
        PostContingencyResult postContl14 = getPostContingencyResult(result, "l14");
        assertEquals("l14", postContl14.getContingency().getId());

        BranchResult brl14l12 = postContl14.getBranchResult("l12");
        assertEquals(0.333, brl14l12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.333, brl14l12.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l14 = postContl14.getBranchResult("l14");
        assertEquals(0.0, brl14l14.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.0, brl14l14.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l23 = postContl14.getBranchResult("l23");
        assertEquals(1.333, brl14l23.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.333, brl14l23.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l34 = postContl14.getBranchResult("l34");
        assertEquals(-1.0, brl14l34.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.0, brl14l34.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l13 = postContl14.getBranchResult("l13");
        assertEquals(1.666, brl14l13.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.666, brl14l13.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaModeAcAllBranchMonitoredFlowTransfer() {
        Network network = FourBusNetworkFactory.create();

        List<Contingency> contingencies = allBranches(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        assertEquals(5, result.getPostContingencyResults().size());

        for (PostContingencyResult r : result.getPostContingencyResults()) {
            assertEquals(4, r.getBranchResults().size());
        }

        //Check branch results for flowTransfer computation for contingency on l14
        PostContingencyResult postContl14 = getPostContingencyResult(result, "l14");
        assertEquals("l14", postContl14.getContingency().getId());

        BranchResult brl14l12 = postContl14.getBranchResult("l12");
        assertEquals(0.335, brl14l12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.336, brl14l12.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l23 = postContl14.getBranchResult("l23");
        assertEquals(1.335, brl14l23.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.336, brl14l23.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l34 = postContl14.getBranchResult("l34");
        assertEquals(-1.0, brl14l34.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.0, brl14l34.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l13 = postContl14.getBranchResult("l13");
        assertEquals(1.664, brl14l13.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.663, brl14l13.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithRemoteSharedControl() {
        Network network = VoltageControlNetworkFactory.createWithIdenticalTransformers();

        List<Contingency> contingencies = allBranches(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-66.667, preContingencyResult.getPreContingencyBranchResult("tr1").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-66.667, preContingencyResult.getPreContingencyBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-66.667, preContingencyResult.getPreContingencyBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "tr1");
        assertEquals(-99.999, postContingencyResult.getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-99.999, postContingencyResult.getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithTransformerRemoteSharedControl() {
        Network network = VoltageControlNetworkFactory.createWithTransformerSharedRemoteControl();

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setTransformerVoltageControlOn(true);

        List<Contingency> contingencies = allBranches(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, lfParameters);
        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-0.659, preContingencyResult.getPreContingencyBranchResult("T2wT2").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.659, preContingencyResult.getPreContingencyBranchResult("T2wT").getQ1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "T2wT2");
        assertEquals(-0.577, postContingencyResult.getBranchResult("T2wT").getQ1(), LoadFlowAssert.DELTA_POWER); // this assertion is not so relevant. It is more relevant to look at the logs.
    }

    @Test
    void testSaWithTransformerRemoteSharedControl2() {
        Network network = VoltageControlNetworkFactory.createWithTransformerSharedRemoteControl();

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setTransformerVoltageControlOn(true);

        List<Contingency> contingencies = List.of(new Contingency("N-2", List.of(new BranchContingency("T2wT"), new BranchContingency("T2wT2"))));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, lfParameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-6.181, preContingencyResult.getPreContingencyBranchResult("LINE_12").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "N-2");
        assertEquals(-7.499, postContingencyResult.getBranchResult("LINE_12").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithShuntRemoteSharedControl() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setShuntCompensatorVoltageControlOn(true);

        List<Contingency> contingencies = allBranches(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, lfParameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-108.596, preContingencyResult.getPreContingencyBranchResult("tr1").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(54.298, preContingencyResult.getPreContingencyBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(54.298, preContingencyResult.getPreContingencyBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult tr2ContingencyResult = getPostContingencyResult(result, "tr2");
        assertEquals(-107.543, tr2ContingencyResult.getBranchResult("tr1").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(107.543, tr2ContingencyResult.getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithPhaseControl() {
        Network network = PhaseControlFactory.createNetworkWithT2wt();

        network.newLine().setId("L3")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(4.0)
                .setX(200.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        network.newLine().setId("L4")
                .setVoltageLevel1("VL3")
                .setConnectableBus1("B3")
                .setBus1("B3")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(4.0)
                .setX(200.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger()
                .setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(ps1.getTerminal1())
                .setRegulationValue(83);

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setPhaseShifterRegulationOn(true);

        List<Contingency> contingencies = List.of(new Contingency("PS1", List.of(new BranchContingency("PS1")))); // allBranches(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, lfParameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(5.682, preContingencyResult.getPreContingencyBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(59.019, preContingencyResult.getPreContingencyBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(5.682, preContingencyResult.getPreContingencyBranchResult("L3").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(29.509, preContingencyResult.getPreContingencyBranchResult("L4").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(88.634, preContingencyResult.getPreContingencyBranchResult("PS1").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult ps1ContingencyResult = getPostContingencyResult(result, "PS1");
        assertEquals(50, ps1ContingencyResult.getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, ps1ContingencyResult.getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER); // because no load on B3
        assertEquals(50, ps1ContingencyResult.getBranchResult("L3").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, ps1ContingencyResult.getBranchResult("L4").getP1(), LoadFlowAssert.DELTA_POWER); // because no load on B3
    }

    @Test
    void testSaWithShuntContingency() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        network.getShuntCompensatorStream().forEach(shuntCompensator -> {
            shuntCompensator.setSectionCount(10);
        });

        List<Contingency> contingencies = List.of(new Contingency("SHUNT2", new ShuntCompensatorContingency("SHUNT2")),
                                                  new Contingency("tr3", new BranchContingency("tr3")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(42.342, preContingencyResult.getPreContingencyBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(42.342, preContingencyResult.getPreContingencyBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult contingencyResult = getPostContingencyResult(result, "SHUNT2");
        assertEquals(0.0, contingencyResult.getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(42.914, contingencyResult.getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult tr3ContingencyResult = getPostContingencyResult(result, "tr3");
        assertEquals(42.914, tr3ContingencyResult.getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithShuntContingency2() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        network.getShuntCompensatorStream().forEach(shuntCompensator -> {
            shuntCompensator.setSectionCount(10);
        });

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setShuntCompensatorVoltageControlOn(true);

        List<Contingency> contingencies = List.of(new Contingency("SHUNT2", new ShuntCompensatorContingency("SHUNT2")),
                                                  new Contingency("tr3", new BranchContingency("tr3")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        CompletionException exception = assertThrows(CompletionException.class, () -> runSecurityAnalysis(network, contingencies, monitors, lfParameters));
        assertEquals("Shunt compensator 'SHUNT2' with voltage control on: not supported yet", exception.getCause().getMessage());
    }

    @Test
    void testSaWithShuntContingency3() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        network.getBusBreakerView().getBus("b2").getVoltageLevel().newShuntCompensator()
                .setId("SHUNT4")
                .setBus("b2")
                .setConnectableBus("b2")
                .setSectionCount(0)
                .newLinearModel()
                .setMaximumSectionCount(50)
                .setBPerSection(-1E-2)
                .setGPerSection(0.0)
                .add()
                .add();
        network.getShuntCompensatorStream().forEach(shuntCompensator -> {
            shuntCompensator.setSectionCount(10);
        });
        network.getGenerator("g1").setMaxP(1000);

        List<Contingency> contingencies = List.of(new Contingency("SHUNT2", new ShuntCompensatorContingency("SHUNT2")),
                new Contingency("SHUNTS", List.of(new ShuntCompensatorContingency("SHUNT2"), new ShuntCompensatorContingency("SHUNT4"))));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(82.342, preContingencyResult.getPreContingencyBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(41.495, preContingencyResult.getPreContingencyBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult contingencyResult = getPostContingencyResult(result, "SHUNT2");
        assertEquals(42.131, contingencyResult.getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(42.131, contingencyResult.getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult contingencyResult2 = getPostContingencyResult(result, "SHUNTS");
        assertEquals(-0.0027, contingencyResult2.getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(42.792, contingencyResult2.getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDcSaWithLoadContingency() {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l2", new LoadContingency("l2")),
                new Contingency("l34", new BranchContingency("l34")),
                new Contingency("l4", new LoadContingency("l4")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(129.411, preContingencyResult.getPreContingencyBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(64.706, preContingencyResult.getPreContingencyBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-58.823, preContingencyResult.getPreContingencyBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l2ContingencyResult = getPostContingencyResult(result, "l2");
        assertEquals(200.0, l2ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(57.143, l2ContingencyResult.getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-71.428, l2ContingencyResult.getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l4ContingencyResult = getPostContingencyResult(result, "l4");
        assertEquals(80.0, l4ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(40.0, l4ContingencyResult.getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-100.0, l4ContingencyResult.getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDcSaWithGeneratorContingency() {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        network.getGenerator("g2").setTargetV(400).setVoltageRegulatorOn(true);

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        parameters.setDc(true);

        List<Contingency> contingencies = List.of(new Contingency("g1", new GeneratorContingency("g1")),
                new Contingency("l34", new BranchContingency("l34")),
                new Contingency("g2", new GeneratorContingency("g2")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(110.0, preContingencyResult.getPreContingencyBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(40.0, preContingencyResult.getPreContingencyBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-50.0, preContingencyResult.getPreContingencyBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g1ContingencyResult = getPostContingencyResult(result, "g1");
        assertEquals(180.00, g1ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-30.0, g1ContingencyResult.getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-50.0, g1ContingencyResult.getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g2ContingencyResult = getPostContingencyResult(result, "g2");
        assertEquals(-60.000, g2ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(210.0, g2ContingencyResult.getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-50.0, g2ContingencyResult.getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithLoadContingency() {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l2", new LoadContingency("l2")),
                new Contingency("l34", new BranchContingency("l34")),
                new Contingency("l4", new LoadContingency("l4")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(129.412, preContingencyResult.getPreContingencyBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-64.706, preContingencyResult.getPreContingencyBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(58.823, preContingencyResult.getPreContingencyBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l2ContingencyResult = getPostContingencyResult(result, "l2");
        assertEquals(200.000, l2ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-57.142, l2ContingencyResult.getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(71.429, l2ContingencyResult.getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l4ContingencyResult = getPostContingencyResult(result, "l4");
        assertEquals(80.003, l4ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-40.002, l4ContingencyResult.getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(99.997, l4ContingencyResult.getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithDisconnectedLoadContingency() {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        network.getLoad("l2").getTerminal().disconnect();

        List<Contingency> contingencies = List.of(new Contingency("l2", new LoadContingency("l2")));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);

        // load is disconnected, contingency is skipped
        assertFalse(getOptionalPostContingencyResult(result, "l2").isPresent());
    }

    @Test
    void testSaWithLoadDetailContingency() {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        network.getLoad("l2").newExtension(LoadDetailAdder.class).withVariableActivePower(40).withFixedActivePower(20).withVariableReactivePower(40).withFixedActivePower(0).add();
        network.getLoad("l4").newExtension(LoadDetailAdder.class).withVariableActivePower(100).withFixedActivePower(40).withVariableReactivePower(100).withFixedActivePower(0).add();

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l2", new LoadContingency("l2")),
                new Contingency("l34", new BranchContingency("l34")),
                new Contingency("l4", new LoadContingency("l4")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(122.857, preContingencyResult.getPreContingencyBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-69.999, preContingencyResult.getPreContingencyBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, preContingencyResult.getPreContingencyBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l2ContingencyResult = getPostContingencyResult(result, "l2");
        assertEquals(200.000, l2ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-70.0, l2ContingencyResult.getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(49.999, l2ContingencyResult.getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l4ContingencyResult = getPostContingencyResult(result, "l4");
        assertEquals(-59.982, l4ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-69.999, l4ContingencyResult.getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(49.999, l4ContingencyResult.getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithGeneratorContingency() {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        network.getGenerator("g2").setTargetV(400).setVoltageRegulatorOn(true);

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        parameters.setNoGeneratorReactiveLimits(true);

        List<Contingency> contingencies = List.of(new Contingency("g1", new GeneratorContingency("g1")),
                new Contingency("l34", new BranchContingency("l34")),
                new Contingency("g2", new GeneratorContingency("g2")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(109.999, preContingencyResult.getPreContingencyBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-39.999, preContingencyResult.getPreContingencyBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, preContingencyResult.getPreContingencyBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g1ContingencyResult = getPostContingencyResult(result, "g1");
        assertEquals(179.999, g1ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(29.999, g1ContingencyResult.getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, g1ContingencyResult.getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g2ContingencyResult = getPostContingencyResult(result, "g2");
        assertEquals(-60.000, g2ContingencyResult.getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-210.000, g2ContingencyResult.getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, g2ContingencyResult.getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithTransformerContingency() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("T2wT", new BranchContingency("T2wT")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(22.111, preContingencyResult.getPreContingencyBranchResult("LINE_12").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult contingencyResult = getPostContingencyResult(result, "T2wT");
        assertEquals(11.228, contingencyResult.getBranchResult("LINE_12").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testPostContingencyFiltering() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        network.getLine("NHV1_NHV2_2").newCurrentLimits1()
                .setPermanentLimit(300)
                .add();
        network.getVoltageLevel("VLHV1").setLowVoltageLimit(410);

        List<Contingency> contingencies = List.of(new Contingency("NHV1_NHV2_1", new BranchContingency("NHV1_NHV2_1")));
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        parameters.getIncreasedViolationsParameters().setFlowProportionalThreshold(0.0);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, Collections.emptyList(), parameters);

        List<LimitViolation> preContingencyLimitViolationsOnLine = result.getPreContingencyResult().getLimitViolationsResult()
                .getLimitViolations().stream().filter(violation -> violation.getSubjectId().equals("NHV1_NHV2_2") && violation.getSide().equals(Branch.Side.ONE)).collect(Collectors.toList());
        assertEquals(LimitViolationType.CURRENT, preContingencyLimitViolationsOnLine.get(0).getLimitType());
        assertEquals(459, preContingencyLimitViolationsOnLine.get(0).getValue(), LoadFlowAssert.DELTA_I);

        List<LimitViolation> postContingencyLimitViolationsOnLine = result.getPostContingencyResults().get(0).getLimitViolationsResult()
                .getLimitViolations().stream().filter(violation -> violation.getSubjectId().equals("NHV1_NHV2_2") && violation.getSide().equals(Branch.Side.ONE)).collect(Collectors.toList());
        assertEquals(LimitViolationType.CURRENT, postContingencyLimitViolationsOnLine.get(0).getLimitType());
        assertEquals(1014.989, postContingencyLimitViolationsOnLine.get(0).getValue(), LoadFlowAssert.DELTA_I);

        List<LimitViolation> preContingencyLimitViolationsOnVoltageLevel = result.getPreContingencyResult().getLimitViolationsResult()
                .getLimitViolations().stream().filter(violation -> violation.getSubjectId().equals("VLHV1")).collect(Collectors.toList());
        assertEquals(LimitViolationType.LOW_VOLTAGE, preContingencyLimitViolationsOnVoltageLevel.get(0).getLimitType());
        assertEquals(400.63, preContingencyLimitViolationsOnVoltageLevel.get(0).getValue(), LoadFlowAssert.DELTA_V);

        List<LimitViolation> postContingencyLimitViolationsOnVoltageLevel = result.getPostContingencyResults().get(0).getLimitViolationsResult()
                .getLimitViolations().stream().filter(violation -> violation.getSubjectId().equals("VLHV1")).collect(Collectors.toList());
        assertEquals(LimitViolationType.LOW_VOLTAGE, postContingencyLimitViolationsOnVoltageLevel.get(0).getLimitType());
        assertEquals(396.70, postContingencyLimitViolationsOnVoltageLevel.get(0).getValue(), LoadFlowAssert.DELTA_V);

        parameters.getIncreasedViolationsParameters().setFlowProportionalThreshold(1.5);
        parameters.getIncreasedViolationsParameters().setLowVoltageProportionalThreshold(0.1);
        parameters.getIncreasedViolationsParameters().setLowVoltageAbsoluteThreshold(5);
        SecurityAnalysisResult result2 = runSecurityAnalysis(network, contingencies, Collections.emptyList(), parameters);

        List<LimitViolation> postContingencyLimitViolationsOnLine2 = result2.getPostContingencyResults().get(0).getLimitViolationsResult()
                .getLimitViolations().stream().filter(violation -> violation.getSubjectId().equals("NHV1_NHV2_2") && violation.getSide().equals(Branch.Side.ONE)).collect(Collectors.toList());
        assertEquals(0, postContingencyLimitViolationsOnLine2.size());

        List<LimitViolation> postContingencyLimitViolationsOnVoltageLevel2 = result2.getPostContingencyResults().get(0).getLimitViolationsResult()
                .getLimitViolations().stream().filter(violation -> violation.getSubjectId().equals("VLHV1")).collect(Collectors.toList());
        assertEquals(0, postContingencyLimitViolationsOnVoltageLevel2.size());
    }

    @Test
    void testViolationsWeakenedOrEquivalent() {
        LimitViolation violation1 = new LimitViolation("voltageLevel1", LimitViolationType.HIGH_VOLTAGE, 420, 1, 421);
        LimitViolation violation2 =  new LimitViolation("voltageLevel1", LimitViolationType.HIGH_VOLTAGE, 420, 1, 425.20);
        SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters = new SecurityAnalysisParameters.IncreasedViolationsParameters();
        violationsParameters.setFlowProportionalThreshold(1.5);
        violationsParameters.setHighVoltageProportionalThreshold(0.1);
        violationsParameters.setHighVoltageAbsoluteThreshold(3);
        assertFalse(AbstractSecurityAnalysis.violationWeakenedOrEquivalent(violation1, violation2, violationsParameters));
        violationsParameters.setHighVoltageProportionalThreshold(0.01); // 4.21 kV
        violationsParameters.setHighVoltageAbsoluteThreshold(5);
        assertTrue(AbstractSecurityAnalysis.violationWeakenedOrEquivalent(violation1, violation2, violationsParameters));

        LimitViolation violation3 = new LimitViolation("voltageLevel1", LimitViolationType.LOW_VOLTAGE, 380, 1, 375);
        LimitViolation violation4 =  new LimitViolation("voltageLevel1", LimitViolationType.LOW_VOLTAGE, 380, 1, 371.26);
        violationsParameters.setFlowProportionalThreshold(1.5);
        violationsParameters.setLowVoltageProportionalThreshold(0.1);
        violationsParameters.setLowVoltageAbsoluteThreshold(3);
        assertFalse(AbstractSecurityAnalysis.violationWeakenedOrEquivalent(violation3, violation4, violationsParameters));
        violationsParameters.setLowVoltageProportionalThreshold(0.01); // 3.75 kV
        violationsParameters.setLowVoltageAbsoluteThreshold(5);
        assertTrue(AbstractSecurityAnalysis.violationWeakenedOrEquivalent(violation3, violation4, violationsParameters));

        assertFalse(AbstractSecurityAnalysis.violationWeakenedOrEquivalent(violation1, violation4, violationsParameters));
    }

    @Test
    void testPhaseShifterNecessaryForConnectivity() {
        Network network = PhaseControlFactory.createNetworkWithT2wt();

        // switch PS1 to active power control
        var ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger()
                .setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setRegulationValue(83);

        LoadFlowParameters parameters = new LoadFlowParameters()
                .setPhaseShifterRegulationOn(true);

        List<Contingency> contingencies = List.of(Contingency.line("L2"), Contingency.twoWindingsTransformer("PS1"), Contingency.line("L1")); // I added L2 and PS1 before to assert there is no impact on L1 contingency

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);
        assertEquals(3, result.getPostContingencyResults().size());
        PostContingencyResult l1ContingencyResult = getPostContingencyResult(result, "L1");
        assertTrue(l1ContingencyResult.getLimitViolationsResult().isComputationOk());
        assertEquals(100.3689, l1ContingencyResult.getBranchResult("PS1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-100.1844, l1ContingencyResult.getBranchResult("PS1").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithNonImpedantLineConnectedToSlackBus() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getLine("L1-2-1").setR(0).setX(0);
        network.getLine("L4-5-1").setR(0).setX(0);

        List<Contingency> contingencies = allBranches(network);

        List<StateMonitor> monitors = Collections.emptyList();

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);
        assertEquals(20, result.getPostContingencyResults().size()); // assert there is no contingency simulation failure
    }

    @Test
    void testHvdcAcEmulation() {
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setHvdcAcEmulation(true);

        List<Contingency> contingencies = new ArrayList<>();
        contingencies.add(Contingency.line("l12"));
        contingencies.add(Contingency.line("l46"));
        contingencies.add(Contingency.generator("g1"));
        // FIXME: note that the HVDC line contingency is not supported yet.

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-0.883, preContingencyResult.getPreContingencyBranchResult("l25").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g1ContingencyResult = getPostContingencyResult(result, "g1");
        assertEquals(-0.696, g1ContingencyResult.getBranchResult("l25").getP1(), LoadFlowAssert.DELTA_POWER);
    }
}
