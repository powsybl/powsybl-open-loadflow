/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.google.common.collect.ImmutableList;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.*;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.LoadDetailAdder;
import com.powsybl.iidm.network.extensions.StandbyAutomatonAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.SecurityAnalysisTestNetworkFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.OlfBranchResult;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.security.*;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReportEquals;
import static java.lang.Double.NaN;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSecurityAnalysisTest extends AbstractOpenSecurityAnalysisTest {

    @BeforeEach
    void setUp() {
        computationManager = Mockito.mock(ComputationManager.class);
        Mockito.when(computationManager.getExecutor()).thenReturn(ForkJoinPool.commonPool());
        matrixFactory = new DenseMatrixFactory();
        GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();
        securityAnalysisProvider = new OpenSecurityAnalysisProvider(matrixFactory, connectivityFactory);
        loadFlowProvider = new OpenLoadFlowProvider(matrixFactory, connectivityFactory);
    }

    @Test
    void testCurrentLimitViolations() {
        Network network = createNodeBreakerNetwork();

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL1_1");
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);

        List<Contingency> contingencies = Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());
        contingencies.add(new Contingency("LD", new LoadContingency("LD")));

        StateMonitor stateMonitor = new StateMonitor(ContingencyContext.all(), Collections.emptySet(),
                network.getVoltageLevelStream().map(Identifiable::getId).collect(Collectors.toSet()), Collections.emptySet());

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, List.of(stateMonitor), securityAnalysisParameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertEquals(0, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(3, result.getPostContingencyResults().size());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(1).getStatus());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "LD");
        assertEquals(398.0, postContingencyResult.getNetworkResult().getBusResult("[BBS2]").getV(), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testCurrentLimitViolations2() {
        Network network = createNodeBreakerNetwork();
        network.getLine("L1").getCurrentLimits1().ifPresent(limits -> limits.setPermanentLimit(200));

        List<Contingency> contingencies = List.of(new Contingency("L2", new BranchContingency("L2")));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(1, result.getPostContingencyResults().size());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
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

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertEquals(0, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
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

        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(1).getStatus());
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

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());

        assertEquals(0, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(1).getStatus());
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
        List<Contingency> contingencies = createAllBranchesContingencies(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters()
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        result = runSecurityAnalysis(network, contingencies, loadFlowParameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
    }

    @Test
    void testNoGenerator() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").getTerminal().disconnect();

        SecurityAnalysisResult result = runSecurityAnalysis(network);
        assertNotSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
    }

    @Test
    void testNoRemainingGenerator() {
        Network network = EurostagTutorialExample1Factory.create();

        List<Contingency> contingencies = List.of(new Contingency("NGEN_NHV1", new BranchContingency("NGEN_NHV1")));

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setMaxRealisticVoltage(1.5);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, parameters);

        assertNotSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
    }

    @Test
    void testNoRemainingLoad() {
        Network network = EurostagTutorialExample1Factory.create();

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDistributedSlack(true)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("NHV2_NLOAD", new BranchContingency("NHV2_NLOAD")));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, lfParameters);

        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
    }

    @Test
    void testSaWithSeveralConnectedComponents() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();

        // Testing all contingencies at once
        List<Contingency> contingencies = createAllBranchesContingencies(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
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
        List<BusResult> busResults = preContingencyResult.getNetworkResult().getBusResults();
        BusResult expectedBus = new BusResult("VLLOAD", "NLOAD", 147.6, -9.6);
        assertEquals(1, busResults.size());
        assertAlmostEquals(expectedBus, busResults.get(0), 0.1);

        assertEquals(2, preContingencyResult.getNetworkResult().getBranchResults().size());
        assertAlmostEquals(new BranchResult("NHV1_NHV2_1", 302, 99, 457, -300, -137.2, 489),
                           preContingencyResult.getNetworkResult().getBranchResult("NHV1_NHV2_1"), 1);
        assertAlmostEquals(new BranchResult("NGEN_NHV1", 606, 225, 15226, -605, -198, 914),
                           preContingencyResult.getNetworkResult().getBranchResult("NGEN_NHV1"), 1);

        //No result when the branch itself is disconnected
        assertNull(result.getPostContingencyResults().get(0).getNetworkResult().getBranchResult("NHV1_NHV2_1"));

        assertAlmostEquals(new BranchResult("NHV1_NHV2_1", 611, 334, 1009, -601, -285, 1048),
                result.getPostContingencyResults().get(1).getNetworkResult().getBranchResult("NHV1_NHV2_1"), 1);
        assertAlmostEquals(new BranchResult("NGEN_NHV1", 611, 368, 16815, -611, -334, 1009),
                           result.getPostContingencyResults().get(1).getNetworkResult().getBranchResult("NGEN_NHV1"), 1);
    }

    @Test
    void testSaWithStateMonitorPreContingency() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        // 2 N-1 on the 2 lines
        List<Contingency> contingencies = List.of(
                new Contingency("NHV1_NHV2_1", new BranchContingency("NHV1_NHV2_1")),
                new Contingency("NHV1_NHV2_2", new BranchContingency("NHV1_NHV2_2"))
        );

        // Monitor on branch and step-up transformer for all states
        List<StateMonitor> monitors = List.of(
                new StateMonitor(ContingencyContext.none(), Set.of("NHV1_NHV2_1", "NGEN_NHV1"), Set.of("VLLOAD"), emptySet())
        );

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(2, preContingencyResult.getNetworkResult().getBranchResults().size());
    }

    @Test
    void testSaWithStateMonitorNotExistingBranchBus() {
        Network network = DistributedSlackNetworkFactory.create();

        List<StateMonitor> monitors = List.of(
            new StateMonitor(ContingencyContext.all(), Collections.singleton("l1"), Collections.singleton("bus"), Collections.singleton("three windings"))
        );

        SecurityAnalysisResult result = runSecurityAnalysis(network, createAllBranchesContingencies(network), monitors);

        assertEquals(0, result.getPreContingencyResult().getNetworkResult().getBusResults().size());
        assertEquals(0, result.getPreContingencyResult().getNetworkResult().getBranchResults().size());
    }

    @Test
    void testSaWithStateMonitorDisconnectBranch() {
        Network network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal1().disconnect();

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("l24"), Collections.singleton("b1_vl"), emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(network, createAllBranchesContingencies(network), monitors);

        assertEquals(1, result.getPreContingencyResult().getNetworkResult().getBusResults().size());

        assertEquals(new BusResult("b1_vl", "b1", 400, 0.003581299841270782), result.getPreContingencyResult().getNetworkResult().getBusResults().get(0));
        assertEquals(1, result.getPreContingencyResult().getNetworkResult().getBranchResults().size());
        assertEquals(new BranchResult("l24", NaN, NaN, NaN, 0.0, -0.0, 0.0),
                     result.getPreContingencyResult().getNetworkResult().getBranchResults().get(0));

        network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal2().disconnect();

        result = runSecurityAnalysis(network, createAllBranchesContingencies(network), monitors);

        assertEquals(0, result.getPreContingencyResult().getNetworkResult().getBranchResults().size());

        network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal2().disconnect();
        network.getBranch("l24").getTerminal1().disconnect();

        result = runSecurityAnalysis(network, createAllBranchesContingencies(network), monitors);

        assertEquals(0, result.getPreContingencyResult().getNetworkResult().getBranchResults().size());
    }

    @Test
    void testSaWithStateMonitorDanglingLine() {
        Network network = BoundaryFactory.create();

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("dl1"), Collections.singleton("vl1"), emptySet()));

        List<Contingency> contingencies = createAllBranchesContingencies(network);
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

        SecurityAnalysisResult result = runSecurityAnalysis(network, createAllBranchesContingencies(network), monitors);

        assertEquals(1, result.getPreContingencyResult().getNetworkResult().getThreeWindingsTransformerResults().size());
        assertAlmostEquals(new ThreeWindingsTransformerResult("3wt", 161, 82, 258,
                                                              -161, -74, 435, 0, 0, 0),
                result.getPreContingencyResult().getNetworkResult().getThreeWindingsTransformerResults().get(0), 1);
    }

    @Test
    void testSaDcMode() {
        Network fourBusNetwork = FourBusNetworkFactory.create();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDc(true);
        setSlackBusId(lfParameters, "b1_vl");
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);

        List<Contingency> contingencies = createAllBranchesContingencies(fourBusNetwork);

        fourBusNetwork.getLine("l14").newActivePowerLimits1().setPermanentLimit(0.1).add();
        fourBusNetwork.getLine("l12").newActivePowerLimits1().setPermanentLimit(0.2).add();
        fourBusNetwork.getLine("l23").newActivePowerLimits1().setPermanentLimit(0.25).add();
        fourBusNetwork.getLine("l34").newActivePowerLimits1().setPermanentLimit(0.15).add();
        fourBusNetwork.getLine("l13").newActivePowerLimits1().setPermanentLimit(0.1).add();

        List<StateMonitor> monitors = List.of(new StateMonitor(ContingencyContext.all(), Set.of("l14", "l12", "l23", "l34", "l13"), Collections.emptySet(), Collections.emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(fourBusNetwork, contingencies, monitors, securityAnalysisParameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertEquals(5, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(5, result.getPostContingencyResults().size());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(2).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(3).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(4).getLimitViolationsResult().getLimitViolations().size());

        //Branch result for first contingency
        assertEquals(5, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResults().size());

        //Check branch results for flowTransfer computation for contingency on l14
        PostContingencyResult postContl14 = getPostContingencyResult(result, "l14");
        assertEquals("l14", postContl14.getContingency().getId());

        BranchResult brl14l12 = postContl14.getNetworkResult().getBranchResult("l12");
        assertEquals(0.333, brl14l12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.333, brl14l12.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l14 = postContl14.getNetworkResult().getBranchResult("l14");
        assertEquals(NaN, brl14l14.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(NaN, brl14l14.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l23 = postContl14.getNetworkResult().getBranchResult("l23");
        assertEquals(1.333, brl14l23.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.333, brl14l23.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l34 = postContl14.getNetworkResult().getBranchResult("l34");
        assertEquals(-1.0, brl14l34.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.0, brl14l34.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l13 = postContl14.getNetworkResult().getBranchResult("l13");
        assertEquals(1.666, brl14l13.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.666, brl14l13.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaDcModeSpecificContingencies() {
        Network fourBusNetwork = FourBusNetworkFactory.create();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDc(true);
        setSlackBusId(lfParameters, "b1_vl");
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);

        List<Contingency> contingencies = createAllBranchesContingencies(fourBusNetwork);

        List<StateMonitor> monitors = List.of(
                new StateMonitor(ContingencyContext.all(), Set.of("l14", "l12"), Collections.emptySet(), Collections.emptySet()),
                new StateMonitor(ContingencyContext.specificContingency("l14"), Set.of("l14", "l12", "l23", "l34", "l13"), Collections.emptySet(), Collections.emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(fourBusNetwork, contingencies, monitors, securityAnalysisParameters);

        assertEquals(2, result.getPreContingencyResult().getNetworkResult().getBranchResults().size());
        assertEquals("l12", result.getPreContingencyResult().getNetworkResult().getBranchResults().get(0).getBranchId());
        assertEquals("l14", result.getPreContingencyResult().getNetworkResult().getBranchResults().get(1).getBranchId());

        assertEquals(5, result.getPostContingencyResults().size());
        for (PostContingencyResult pcResult : result.getPostContingencyResults()) {
            if (pcResult.getContingency().getId().equals("l14")) {
                assertEquals(5, pcResult.getNetworkResult().getBranchResults().size());
            } else {
                assertEquals(2, pcResult.getNetworkResult().getBranchResults().size());
            }
        }
    }

    @Test
    void testSaDcModeWithIncreasedParameters() {
        Network fourBusNetwork = FourBusNetworkFactory.create();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDc(true);
        setSlackBusId(lfParameters, "b1_vl");
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        SecurityAnalysisParameters.IncreasedViolationsParameters increasedViolationsParameters = new SecurityAnalysisParameters.IncreasedViolationsParameters();
        increasedViolationsParameters.setFlowProportionalThreshold(0);
        securityAnalysisParameters.setIncreasedViolationsParameters(increasedViolationsParameters);

        List<Contingency> contingencies = createAllBranchesContingencies(fourBusNetwork);

        fourBusNetwork.getLine("l14").newActivePowerLimits1().setPermanentLimit(0.1).add();
        fourBusNetwork.getLine("l12").newActivePowerLimits1().setPermanentLimit(0.2).add();
        fourBusNetwork.getLine("l23").newActivePowerLimits1().setPermanentLimit(0.25).add();
        fourBusNetwork.getLine("l34").newActivePowerLimits1().setPermanentLimit(0.15).add();
        fourBusNetwork.getLine("l13").newActivePowerLimits1().setPermanentLimit(0.1).add();

        List<StateMonitor> monitors = List.of(new StateMonitor(ContingencyContext.all(), Set.of("l14", "l12", "l23", "l34", "l13"), Collections.emptySet(), Collections.emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(fourBusNetwork, contingencies, monitors, securityAnalysisParameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertEquals(5, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(5, result.getPostContingencyResults().size());
        assertEquals(3, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(3, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(2).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(3).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(4).getLimitViolationsResult().getLimitViolations().size());

        //Branch result for first contingency
        assertEquals(5, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResults().size());

        //Check branch results for flowTransfer computation for contingency on l14
        PostContingencyResult postContl14 = getPostContingencyResult(result, "l14");
        assertEquals("l14", postContl14.getContingency().getId());

        BranchResult brl14l12 = postContl14.getNetworkResult().getBranchResult("l12");
        assertEquals(0.333, brl14l12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.333, brl14l12.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l14 = postContl14.getNetworkResult().getBranchResult("l14");
        assertEquals(NaN, brl14l14.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(NaN, brl14l14.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l23 = postContl14.getNetworkResult().getBranchResult("l23");
        assertEquals(1.333, brl14l23.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.333, brl14l23.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l34 = postContl14.getNetworkResult().getBranchResult("l34");
        assertEquals(-1.0, brl14l34.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.0, brl14l34.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l13 = postContl14.getNetworkResult().getBranchResult("l13");
        assertEquals(1.666, brl14l13.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.666, brl14l13.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaModeAcAllBranchMonitoredFlowTransfer() {
        Network network = FourBusNetworkFactory.create();

        List<Contingency> contingencies = createAllBranchesContingencies(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        assertEquals(5, result.getPostContingencyResults().size());

        for (PostContingencyResult r : result.getPostContingencyResults()) {
            assertEquals(4, r.getNetworkResult().getBranchResults().size());
        }

        //Check branch results for flowTransfer computation for contingency on l14
        PostContingencyResult postContl14 = getPostContingencyResult(result, "l14");
        assertEquals("l14", postContl14.getContingency().getId());

        BranchResult brl14l12 = postContl14.getNetworkResult().getBranchResult("l12");
        assertEquals(0.335, brl14l12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.336, brl14l12.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l23 = postContl14.getNetworkResult().getBranchResult("l23");
        assertEquals(1.335, brl14l23.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.336, brl14l23.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l34 = postContl14.getNetworkResult().getBranchResult("l34");
        assertEquals(-1.0, brl14l34.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.0, brl14l34.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);

        BranchResult brl14l13 = postContl14.getNetworkResult().getBranchResult("l13");
        assertEquals(1.664, brl14l13.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.663, brl14l13.getFlowTransfer(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithRemoteSharedControl() {
        Network network = VoltageControlNetworkFactory.createWithIdenticalTransformers();

        List<Contingency> contingencies = createAllBranchesContingencies(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-66.667, preContingencyResult.getNetworkResult().getBranchResult("tr1").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-66.667, preContingencyResult.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-66.667, preContingencyResult.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "tr1");
        assertEquals(-99.999, postContingencyResult.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-99.999, postContingencyResult.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithTransformerRemoteSharedControl() {
        Network network = VoltageControlNetworkFactory.createWithTransformerSharedRemoteControl();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        saParameters.getLoadFlowParameters()
                .setTransformerVoltageControlOn(true);
        saParameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters()
                .setCreateResultExtension(true));

        List<Contingency> contingencies = createAllBranchesContingencies(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, saParameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-0.659, preContingencyResult.getNetworkResult().getBranchResult("T2wT2").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.659, preContingencyResult.getNetworkResult().getBranchResult("T2wT").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.05, preContingencyResult.getNetworkResult().getBranchResult("T2wT2").getExtension(OlfBranchResult.class).getR1(), 0d);
        assertEquals(1.050302, preContingencyResult.getNetworkResult().getBranchResult("T2wT2").getExtension(OlfBranchResult.class).getContinuousR1(), LoadFlowAssert.DELTA_RHO);
        assertEquals(1.05, preContingencyResult.getNetworkResult().getBranchResult("T2wT").getExtension(OlfBranchResult.class).getR1(), 0d);
        assertEquals(1.050302, preContingencyResult.getNetworkResult().getBranchResult("T2wT").getExtension(OlfBranchResult.class).getContinuousR1(), LoadFlowAssert.DELTA_RHO);

        // post-contingency tests
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "T2wT2");
        assertEquals(-0.577, postContingencyResult.getNetworkResult().getBranchResult("T2wT").getQ1(), LoadFlowAssert.DELTA_POWER); // this assertion is not so relevant. It is more relevant to look at the logs.
        assertEquals(1.1, postContingencyResult.getNetworkResult().getBranchResult("T2wT").getExtension(OlfBranchResult.class).getR1(), 0d);
        assertEquals(1.088228, postContingencyResult.getNetworkResult().getBranchResult("T2wT").getExtension(OlfBranchResult.class).getContinuousR1(), LoadFlowAssert.DELTA_RHO);
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
        assertEquals(-6.181, preContingencyResult.getNetworkResult().getBranchResult("LINE_12").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "N-2");
        assertEquals(-7.499, postContingencyResult.getNetworkResult().getBranchResult("LINE_12").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithShuntRemoteSharedControl() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setShuntCompensatorVoltageControlOn(true);

        List<Contingency> contingencies = createAllBranchesContingencies(network);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, lfParameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-108.596, preContingencyResult.getNetworkResult().getBranchResult("tr1").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(54.298, preContingencyResult.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(54.298, preContingencyResult.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult tr2ContingencyResult = getPostContingencyResult(result, "tr2");
        assertEquals(-107.543, tr2ContingencyResult.getNetworkResult().getBranchResult("tr1").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(107.543, tr2ContingencyResult.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithPhaseControl() {
        Network network = PhaseControlFactory.createNetworkWithT2wt();

        network.newLine().setId("L3")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(4.0)
                .setX(200.0)
                .add();

        network.newLine().setId("L4")
                .setConnectableBus1("B3")
                .setBus1("B3")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(4.0)
                .setX(200.0)
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
        assertEquals(5.682, preContingencyResult.getNetworkResult().getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(59.019, preContingencyResult.getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(5.682, preContingencyResult.getNetworkResult().getBranchResult("L3").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(29.509, preContingencyResult.getNetworkResult().getBranchResult("L4").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(88.634, preContingencyResult.getNetworkResult().getBranchResult("PS1").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult ps1ContingencyResult = getPostContingencyResult(result, "PS1");
        assertEquals(50, ps1ContingencyResult.getNetworkResult().getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, ps1ContingencyResult.getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER); // because no load on B3
        assertEquals(50, ps1ContingencyResult.getNetworkResult().getBranchResult("L3").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, ps1ContingencyResult.getNetworkResult().getBranchResult("L4").getP1(), LoadFlowAssert.DELTA_POWER); // because no load on B3
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
        assertEquals(42.342, preContingencyResult.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(42.342, preContingencyResult.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult contingencyResult = getPostContingencyResult(result, "SHUNT2");
        assertEquals(0.0, contingencyResult.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(42.914, contingencyResult.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult tr3ContingencyResult = getPostContingencyResult(result, "tr3");
        assertEquals(42.914, tr3ContingencyResult.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
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
        assertEquals(82.342, preContingencyResult.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(41.495, preContingencyResult.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult contingencyResult = getPostContingencyResult(result, "SHUNT2");
        assertEquals(42.131, contingencyResult.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(42.131, contingencyResult.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult contingencyResult2 = getPostContingencyResult(result, "SHUNTS");
        assertEquals(-0.0027, contingencyResult2.getNetworkResult().getBranchResult("tr2").getQ2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(42.792, contingencyResult2.getNetworkResult().getBranchResult("tr3").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithShuntContingency4() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        network.getShuntCompensatorStream().forEach(shuntCompensator -> {
            shuntCompensator.setSectionCount(10);
        });

        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setShuntCompensatorVoltageControlOn(true);

        List<Contingency> contingencies = List.of(new Contingency("SHUNT4", new ShuntCompensatorContingency("SHUNT4")),
                new Contingency("tr3", new BranchContingency("tr3")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        CompletionException exception = assertThrows(CompletionException.class, () -> runSecurityAnalysis(network, contingencies, monitors, lfParameters));
        assertEquals("Shunt compensator 'SHUNT4' not found in the network", exception.getCause().getMessage());
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
        assertEquals(129.411, preContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(64.706, preContingencyResult.getNetworkResult().getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-58.823, preContingencyResult.getNetworkResult().getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l2ContingencyResult = getPostContingencyResult(result, "l2");
        assertEquals(200.0, l2ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(57.143, l2ContingencyResult.getNetworkResult().getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-71.428, l2ContingencyResult.getNetworkResult().getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l4ContingencyResult = getPostContingencyResult(result, "l4");
        assertEquals(80.0, l4ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(40.0, l4ContingencyResult.getNetworkResult().getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-100.0, l4ContingencyResult.getNetworkResult().getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);
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
        assertEquals(110.0, preContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(40.0, preContingencyResult.getNetworkResult().getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-50.0, preContingencyResult.getNetworkResult().getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g1ContingencyResult = getPostContingencyResult(result, "g1");
        assertEquals(180.00, g1ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-30.0, g1ContingencyResult.getNetworkResult().getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-50.0, g1ContingencyResult.getNetworkResult().getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g2ContingencyResult = getPostContingencyResult(result, "g2");
        assertEquals(-60.000, g2ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(210.0, g2ContingencyResult.getNetworkResult().getBranchResult("l14").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-50.0, g2ContingencyResult.getNetworkResult().getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);
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
        assertEquals(129.412, preContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-64.706, preContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(58.823, preContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l2ContingencyResult = getPostContingencyResult(result, "l2");
        assertEquals(200.000, l2ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-57.142, l2ContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(71.429, l2ContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l4ContingencyResult = getPostContingencyResult(result, "l4");
        assertEquals(80.003, l4ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-40.002, l4ContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(99.997, l4ContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);
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
        assertEquals(122.857, preContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-69.999, preContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, preContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l2ContingencyResult = getPostContingencyResult(result, "l2");
        assertEquals(200.000, l2ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-70.0, l2ContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(49.999, l2ContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult l4ContingencyResult = getPostContingencyResult(result, "l4");
        assertEquals(-59.982, l4ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-69.999, l4ContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(49.999, l4ContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaWithGeneratorContingency() {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        network.getGenerator("g2").setTargetV(400).setVoltageRegulatorOn(true);

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        parameters.setUseReactiveLimits(false);

        List<Contingency> contingencies = List.of(new Contingency("g1", new GeneratorContingency("g1")),
                new Contingency("l34", new BranchContingency("l34")),
                new Contingency("g2", new GeneratorContingency("g2")));

        List<StateMonitor> monitors = createNetworkMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(110, preContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-40, preContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, preContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g1ContingencyResult = getPostContingencyResult(result, "g1");
        assertEquals(180, g1ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(30, g1ContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, g1ContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(399.855, g1ContingencyResult.getNetworkResult().getBusResult("b1").getV(), LoadFlowAssert.DELTA_V);
        assertEquals(400, g1ContingencyResult.getNetworkResult().getBusResult("b2").getV(), LoadFlowAssert.DELTA_V);

        // post-contingency tests
        PostContingencyResult g2ContingencyResult = getPostContingencyResult(result, "g2");
        assertEquals(-60.000, g2ContingencyResult.getNetworkResult().getBranchResult("l24").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-210.000, g2ContingencyResult.getNetworkResult().getBranchResult("l14").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, g2ContingencyResult.getNetworkResult().getBranchResult("l34").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(400.0, g2ContingencyResult.getNetworkResult().getBusResult("b1").getV(), LoadFlowAssert.DELTA_V);
        assertEquals(399.891, g2ContingencyResult.getNetworkResult().getBusResult("b2").getV(), LoadFlowAssert.DELTA_V);
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
        assertEquals(22.111, preContingencyResult.getNetworkResult().getBranchResult("LINE_12").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult contingencyResult = getPostContingencyResult(result, "T2wT");
        assertEquals(11.228, contingencyResult.getNetworkResult().getBranchResult("LINE_12").getP1(), LoadFlowAssert.DELTA_POWER);
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
        LimitViolation violation2 = new LimitViolation("voltageLevel1", LimitViolationType.HIGH_VOLTAGE, 420, 1, 425.20);
        SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters = new SecurityAnalysisParameters.IncreasedViolationsParameters();
        violationsParameters.setFlowProportionalThreshold(1.5);
        violationsParameters.setHighVoltageProportionalThreshold(0.1);
        violationsParameters.setHighVoltageAbsoluteThreshold(3);
        assertFalse(LimitViolationManager.violationWeakenedOrEquivalent(violation1, violation2, violationsParameters));
        violationsParameters.setHighVoltageProportionalThreshold(0.01); // 4.21 kV
        violationsParameters.setHighVoltageAbsoluteThreshold(5);
        assertTrue(LimitViolationManager.violationWeakenedOrEquivalent(violation1, violation2, violationsParameters));

        LimitViolation violation3 = new LimitViolation("voltageLevel1", LimitViolationType.LOW_VOLTAGE, 380, 1, 375);
        LimitViolation violation4 = new LimitViolation("voltageLevel1", LimitViolationType.LOW_VOLTAGE, 380, 1, 371.26);
        violationsParameters.setFlowProportionalThreshold(1.5);
        violationsParameters.setLowVoltageProportionalThreshold(0.1);
        violationsParameters.setLowVoltageAbsoluteThreshold(3);
        assertFalse(LimitViolationManager.violationWeakenedOrEquivalent(violation3, violation4, violationsParameters));
        violationsParameters.setLowVoltageProportionalThreshold(0.01); // 3.75 kV
        violationsParameters.setLowVoltageAbsoluteThreshold(5);
        assertTrue(LimitViolationManager.violationWeakenedOrEquivalent(violation3, violation4, violationsParameters));

        assertFalse(LimitViolationManager.violationWeakenedOrEquivalent(violation1, violation4, violationsParameters));
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
        assertSame(PostContingencyComputationStatus.CONVERGED, l1ContingencyResult.getStatus());
        assertEquals(100.3689, l1ContingencyResult.getNetworkResult().getBranchResult("PS1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-100.1844, l1ContingencyResult.getNetworkResult().getBranchResult("PS1").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithNonImpedantLineConnectedToSlackBus() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getLine("L1-2-1").setR(0).setX(0);
        network.getLine("L4-5-1").setR(0).setX(0);

        List<Contingency> contingencies = createAllBranchesContingencies(network);

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
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .setHvdcAcEmulation(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);

        List<Contingency> contingencies = new ArrayList<>();
        contingencies.add(Contingency.line("l12"));
        contingencies.add(Contingency.line("l46"));
        contingencies.add(Contingency.generator("g1"));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-0.883, preContingencyResult.getNetworkResult().getBranchResult("l25").getP1(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult g1ContingencyResult = getPostContingencyResult(result, "g1");
        assertEquals(-0.696, g1ContingencyResult.getNetworkResult().getBranchResult("l25").getP1(), LoadFlowAssert.DELTA_POWER);

        List<Contingency> contingencies2 = new ArrayList<>();
        contingencies2.add(Contingency.hvdcLine("hvdc34"));
        contingencies2.add(Contingency.generator("g1"));
        SecurityAnalysisResult result2 = runSecurityAnalysis(network, contingencies2, monitors, parameters);

        // post-contingency tests
        PostContingencyResult hvdcContingencyResult = getPostContingencyResult(result2, "hvdc34");
        assertEquals(-0.99999, hvdcContingencyResult.getNetworkResult().getBranchResult("l25").getP1(), LoadFlowAssert.DELTA_POWER);

        PostContingencyResult g1ContingencyResult2 = getPostContingencyResult(result, "g1");
        assertEquals(-0.696, g1ContingencyResult2.getNetworkResult().getBranchResult("l25").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLcc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcWithGenerators();

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        OpenLoadFlowParameters openLoadFlowParameters = new OpenLoadFlowParameters();
        openLoadFlowParameters.setSlackBusPMaxMismatch(0.0001);
        parameters.addExtension(OpenLoadFlowParameters.class, openLoadFlowParameters);

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        network.getHvdcLine("hvdc34").getConverterStation1().getTerminal().disconnect();
        network.getHvdcLine("hvdc34").getConverterStation2().getTerminal().disconnect();
        runLoadFlow(network, parameters);

        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(1.360, preContingencyResult.getNetworkResult().getBranchResult("l12").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.360, preContingencyResult.getNetworkResult().getBranchResult("l13").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.596, preContingencyResult.getNetworkResult().getBranchResult("l23").getP1(), LoadFlowAssert.DELTA_POWER);

        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "hvdc34");
        assertEquals(network.getLine("l12").getTerminal1().getP(), postContingencyResult.getNetworkResult().getBranchResult("l12").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l12").getTerminal1().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l12").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l13").getTerminal1().getP(), postContingencyResult.getNetworkResult().getBranchResult("l13").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l13").getTerminal1().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l13").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l23").getTerminal1().getP(), postContingencyResult.getNetworkResult().getBranchResult("l23").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l23").getTerminal1().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l23").getQ1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcVsc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        OpenLoadFlowParameters openLoadFlowParameters = new OpenLoadFlowParameters();
        openLoadFlowParameters.setSlackBusPMaxMismatch(0.0001);
        parameters.addExtension(OpenLoadFlowParameters.class, openLoadFlowParameters);

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters);

        network.getHvdcLine("hvdc34").getConverterStation1().getTerminal().disconnect();
        network.getHvdcLine("hvdc34").getConverterStation2().getTerminal().disconnect();
        runLoadFlow(network, parameters);

        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(1.25, preContingencyResult.getNetworkResult().getBranchResult("l12").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.228, preContingencyResult.getNetworkResult().getBranchResult("l13").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.727, preContingencyResult.getNetworkResult().getBranchResult("l23").getP1(), LoadFlowAssert.DELTA_POWER);

        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "hvdc34");
        assertEquals(network.getLine("l12").getTerminal1().getP(), postContingencyResult.getNetworkResult().getBranchResult("l12").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l12").getTerminal1().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l12").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l13").getTerminal1().getP(), postContingencyResult.getNetworkResult().getBranchResult("l13").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l13").getTerminal1().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l13").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l23").getTerminal1().getP(), postContingencyResult.getNetworkResult().getBranchResult("l23").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l23").getTerminal1().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l23").getQ1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testEmptyNetwork() {
        Network network = Network.create("empty", "");
        SecurityAnalysisResult result = runSecurityAnalysis(network);
        assertNotSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
    }

    @Test
    void testDivergenceStatus() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getLine("NHV1_NHV2_1").setR(100).setX(-999);
        network.getLine("NHV1_NHV2_2").setR(100).setX(-999);
        SecurityAnalysisResult result = runSecurityAnalysis(network);
        assertNotSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
    }

    @Test
    void testSwitchContingency() {
        Network network = createNodeBreakerNetwork();

        List<Contingency> contingencies = List.of(new Contingency("C", new SwitchContingency("C")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());

        // pre-contingency tests
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(301.884, preContingencyResult.getNetworkResult().getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-300, preContingencyResult.getNetworkResult().getBranchResult("L1").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(301.884, preContingencyResult.getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-300, preContingencyResult.getNetworkResult().getBranchResult("L2").getP2(), LoadFlowAssert.DELTA_POWER);

        // post-contingency tests
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "C");
        assertEquals(0.099, postContingencyResult.getNetworkResult().getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.083, postContingencyResult.getNetworkResult().getBranchResult("L1").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(607.682, postContingencyResult.getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-599.918, postContingencyResult.getNetworkResult().getBranchResult("L2").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSwitchContingency2() {
        Network network = BusBreakerNetworkFactory.create();

        List<Contingency> contingencies = List.of(new Contingency("C", new SwitchContingency("C")),
                                                  new Contingency("C2", new LoadContingency("LD")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());

        // post-contingency tests
        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "C");
        assertEquals(607.782, postContingencyResult.getNetworkResult().getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-600.016, postContingencyResult.getNetworkResult().getBranchResult("L1").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, postContingencyResult.getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0163, postContingencyResult.getNetworkResult().getBranchResult("L2").getP2(), LoadFlowAssert.DELTA_POWER);

        PostContingencyResult postContingencyResult2 = getPostContingencyResult(result, "C2");
        assertEquals(0.0180, postContingencyResult2.getNetworkResult().getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, postContingencyResult2.getNetworkResult().getBranchResult("L1").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0180, postContingencyResult2.getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, postContingencyResult2.getNetworkResult().getBranchResult("L2").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSwitchContingencyNotFound() {
        Network network = createNodeBreakerNetwork();

        List<Contingency> contingencies = List.of(new Contingency("X", new SwitchContingency("X")));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        var e = assertThrows(CompletionException.class, () -> runSecurityAnalysis(network, contingencies, monitors));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Switch 'X' not found in the network", e.getCause().getMessage());
    }

    @Test
    void testSwitchLoopIssue() {
        Network network = SwitchLoopIssueNetworkFactory.create();

        List<Contingency> contingencies = List.of(Contingency.line("L1"));

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        var result = runSecurityAnalysis(network, contingencies, monitors);

        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        assertEquals(-299.977, preContingencyResult.getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(301.862, preContingencyResult.getNetworkResult().getBranchResult("L2").getP2(), LoadFlowAssert.DELTA_POWER);

        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "L1");
        assertEquals(-599.882, postContingencyResult.getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(608.214, postContingencyResult.getNetworkResult().getBranchResult("L2").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDcPermanentCurrentLimitViolations() {
        Network network = FourBusNetworkFactory.create();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDc(true);
        setSlackBusId(lfParameters, "b1_vl");
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);

        List<Contingency> contingencies = createAllBranchesContingencies(network);

        network.getLine("l14").newCurrentLimits1().setPermanentLimit(60.0).add();
        network.getLine("l12").newCurrentLimits1().setPermanentLimit(120.0).add();
        network.getLine("l23").newCurrentLimits2().setPermanentLimit(150.0).add();
        network.getLine("l34").newCurrentLimits1().setPermanentLimit(90.0).add();
        network.getLine("l13").newCurrentLimits2().setPermanentLimit(60.0).add();

        List<StateMonitor> monitors = List.of(new StateMonitor(ContingencyContext.all(), Set.of("l14", "l12", "l23", "l34", "l13"), Collections.emptySet(), Collections.emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertEquals(5, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(5, result.getPostContingencyResults().size());
        assertEquals(2, getPostContingencyResult(result, "l14").getLimitViolationsResult().getLimitViolations().size());
        assertEquals(192.450, getPostContingencyResult(result, "l14").getNetworkResult().getBranchResult("l12").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(962.250, getPostContingencyResult(result, "l14").getNetworkResult().getBranchResult("l13").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(2, getPostContingencyResult(result, "l12").getLimitViolationsResult().getLimitViolations().size());
        assertEquals(192.450, getPostContingencyResult(result, "l12").getNetworkResult().getBranchResult("l14").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(962.250, getPostContingencyResult(result, "l12").getNetworkResult().getBranchResult("l13").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(4, getPostContingencyResult(result, "l13").getLimitViolationsResult().getLimitViolations().size());
        assertEquals(577.350, getPostContingencyResult(result, "l13").getNetworkResult().getBranchResult("l12").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(577.350, getPostContingencyResult(result, "l13").getNetworkResult().getBranchResult("l14").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(1154.700, getPostContingencyResult(result, "l13").getNetworkResult().getBranchResult("l23").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(1154.700, getPostContingencyResult(result, "l13").getNetworkResult().getBranchResult("l34").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(4, getPostContingencyResult(result, "l23").getLimitViolationsResult().getLimitViolations().size());
        assertEquals(577.350, getPostContingencyResult(result, "l23").getNetworkResult().getBranchResult("l12").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(384.900, getPostContingencyResult(result, "l23").getNetworkResult().getBranchResult("l14").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(1347.150, getPostContingencyResult(result, "l23").getNetworkResult().getBranchResult("l13").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(962.250, getPostContingencyResult(result, "l23").getNetworkResult().getBranchResult("l34").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(4, getPostContingencyResult(result, "l34").getLimitViolationsResult().getLimitViolations().size());
        assertEquals(384.900, getPostContingencyResult(result, "l34").getNetworkResult().getBranchResult("l12").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(577.350, getPostContingencyResult(result, "l34").getNetworkResult().getBranchResult("l14").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(1347.150, getPostContingencyResult(result, "l34").getNetworkResult().getBranchResult("l13").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(962.250, getPostContingencyResult(result, "l34").getNetworkResult().getBranchResult("l23").getI1(), LoadFlowAssert.DELTA_I);
    }

    @Test
    void testDcTemporaryCurrentLimitViolations() {
        Network network = FourBusNetworkFactory.create();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters()
                .setDc(true)
                .setDcPowerFactor(Math.tan(0.4));
        setSlackBusId(lfParameters, "b1_vl");
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);

        List<Contingency> contingencies = createAllBranchesContingencies(network);

        network.getLine("l14").newCurrentLimits1().setPermanentLimit(60.0)
                .beginTemporaryLimit().setName("60").setAcceptableDuration(Integer.MAX_VALUE).setValue(200.0).endTemporaryLimit()
                .beginTemporaryLimit().setName("0").setAcceptableDuration(60).setValue(Double.MAX_VALUE).endTemporaryLimit().add();
        network.getLine("l12").newCurrentLimits1().setPermanentLimit(120.0)
                .beginTemporaryLimit().setName("60").setAcceptableDuration(Integer.MAX_VALUE).setValue(300.0).endTemporaryLimit()
                .beginTemporaryLimit().setName("0").setAcceptableDuration(60).setValue(Double.MAX_VALUE).endTemporaryLimit().add();
        network.getLine("l23").newCurrentLimits2().setPermanentLimit(150.0)
                .beginTemporaryLimit().setName("60").setAcceptableDuration(Integer.MAX_VALUE).setValue(500.0).endTemporaryLimit()
                .beginTemporaryLimit().setName("0").setAcceptableDuration(60).setValue(Double.MAX_VALUE).endTemporaryLimit().add();
        network.getLine("l34").newCurrentLimits1().setPermanentLimit(90.0)
                .beginTemporaryLimit().setName("60").setAcceptableDuration(Integer.MAX_VALUE).setValue(300.0).endTemporaryLimit()
                .beginTemporaryLimit().setName("0").setAcceptableDuration(60).setValue(Double.MAX_VALUE).endTemporaryLimit().add();
        network.getLine("l13").newCurrentLimits2().setPermanentLimit(60.0)
                .beginTemporaryLimit().setName("60").setAcceptableDuration(Integer.MAX_VALUE).setValue(300.0).endTemporaryLimit()
                .beginTemporaryLimit().setName("0").setAcceptableDuration(60).setValue(Double.MAX_VALUE).endTemporaryLimit().add();

        List<StateMonitor> monitors = List.of(new StateMonitor(ContingencyContext.all(), Set.of("l14", "l12", "l23", "l34", "l13"), Collections.emptySet(), Collections.emptySet()));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertEquals(5, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(5, result.getPostContingencyResults().size());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(2).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(3).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(4).getLimitViolationsResult().getLimitViolations().size());
    }

    @Test
    void testThreeWindingsTransformerContingency() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT3wt();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(false);
        setSlackBusId(parameters, "VL_1");
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        List<Contingency> contingencies = List.of(new Contingency("T3wT", new ThreeWindingsTransformerContingency("T3wT")));
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);

        network.getThreeWindingsTransformer("T3wT").getLeg1().getTerminal().disconnect();
        network.getThreeWindingsTransformer("T3wT").getLeg2().getTerminal().disconnect();
        network.getThreeWindingsTransformer("T3wT").getLeg3().getTerminal().disconnect();
        setSlackBusId(parameters, "VL_1");
        LoadFlow.run(network, parameters);

        PostContingencyResult contingencyResult = getPostContingencyResult(result, "T3wT");
        assertEquals(network.getLine("LINE_12").getTerminal2().getP(), contingencyResult.getNetworkResult().getBranchResult("LINE_12").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("LINE_12").getTerminal2().getQ(), contingencyResult.getNetworkResult().getBranchResult("LINE_12").getQ2(), LoadFlowAssert.DELTA_POWER);

        network.getThreeWindingsTransformer("T3wT").getLeg1().getTerminal().connect();
        network.getThreeWindingsTransformer("T3wT").getLeg2().getTerminal().connect();
        network.getThreeWindingsTransformer("T3wT").getLeg3().getTerminal().connect();
        List<Contingency> contingencies2 = List.of(new Contingency("T3wT2", new ThreeWindingsTransformerContingency("T3wT2")));
        var e = assertThrows(CompletionException.class, () -> runSecurityAnalysis(network, contingencies2, monitors, securityAnalysisParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Three windings transformer 'T3wT2' not found in the network", e.getCause().getMessage());
    }

    @Test
    void testDanglingLineContingency() {
        Network network = BoundaryFactory.createWithLoad();
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertEquals(75.18, result.getPreContingencyResult().getNetworkResult().getBranchResult("l1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(3.333, getPostContingencyResult(result, "dl1").getNetworkResult().getBranchResult("l1").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void reportTest() throws IOException {
        var network = EurostagTutorialExample1Factory.create();

        List<Contingency> contingencies = createAllBranchesContingencies(network);

        ReporterModel reporter = new ReporterModel("TestSecurityAnalysis", "Test security analysis report");

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(loadFlowParameters)
                .setMaxRealisticVoltage(1.5);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters()
                .setLoadFlowParameters(loadFlowParameters);
        runSecurityAnalysis(network, contingencies, Collections.emptyList(), securityAnalysisParameters, reporter);

        assertReportEquals("/saReport.txt", reporter);
    }

    @Test
    void testBranchOpenAtOneSideLoss() {
        var network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        network.getLine("l46").getTerminal1().disconnect();
        List<Contingency> contingencies = List.of(new Contingency("line", new BranchContingency("l34")));
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, Collections.emptyList(), new SecurityAnalysisParameters(), Reporter.NO_OP);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getPreContingencyResult().getStatus());
        assertSame(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
    }

    @Test
    void testStatusConversion() {
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED,
                AbstractSecurityAnalysis.loadFlowResultStatusFromNRStatus(NewtonRaphsonStatus.CONVERGED));
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED,
                AbstractSecurityAnalysis.loadFlowResultStatusFromNRStatus(NewtonRaphsonStatus.MAX_ITERATION_REACHED));
        assertEquals(LoadFlowResult.ComponentResult.Status.SOLVER_FAILED,
                AbstractSecurityAnalysis.loadFlowResultStatusFromNRStatus(NewtonRaphsonStatus.SOLVER_FAILED));
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED,
                AbstractSecurityAnalysis.loadFlowResultStatusFromNRStatus(NewtonRaphsonStatus.NO_CALCULATION));
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED,
                AbstractSecurityAnalysis.loadFlowResultStatusFromNRStatus(NewtonRaphsonStatus.UNREALISTIC_STATE));

        assertEquals(PostContingencyComputationStatus.CONVERGED,
                AbstractSecurityAnalysis.postContingencyStatusFromNRStatus(NewtonRaphsonStatus.CONVERGED));
        assertEquals(PostContingencyComputationStatus.MAX_ITERATION_REACHED,
                AbstractSecurityAnalysis.postContingencyStatusFromNRStatus(NewtonRaphsonStatus.MAX_ITERATION_REACHED));
        assertEquals(PostContingencyComputationStatus.SOLVER_FAILED,
                AbstractSecurityAnalysis.postContingencyStatusFromNRStatus(NewtonRaphsonStatus.SOLVER_FAILED));
        assertEquals(PostContingencyComputationStatus.NO_IMPACT,
                AbstractSecurityAnalysis.postContingencyStatusFromNRStatus(NewtonRaphsonStatus.NO_CALCULATION));
        assertEquals(PostContingencyComputationStatus.FAILED,
                AbstractSecurityAnalysis.postContingencyStatusFromNRStatus(NewtonRaphsonStatus.UNREALISTIC_STATE));
    }

    @Test
    void testConnectivityResultWhenNoSplit() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("line", new BranchContingency("l12")));
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();

        //Test AC
        parameters.getLoadFlowParameters().setDc(false);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters, Reporter.NO_OP);
        var postContingencyResult = result.getPostContingencyResults().get(0);
        assertSame(PostContingencyComputationStatus.CONVERGED, postContingencyResult.getStatus());
        assertEquals(0, postContingencyResult.getConnectivityResult().getCreatedSynchronousComponentCount());

        //Test DC
        parameters.getLoadFlowParameters().setDc(true);
        result = runSecurityAnalysis(network, contingencies, monitors, parameters, Reporter.NO_OP);
        postContingencyResult = result.getPostContingencyResults().get(0);
        assertSame(PostContingencyComputationStatus.CONVERGED, postContingencyResult.getStatus());
        assertEquals(0, postContingencyResult.getConnectivityResult().getCreatedSynchronousComponentCount());
    }

    @Test
    void testConnectivityResultOnSplit() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("line", new BranchContingency("l34")));
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();

        //Test AC
        parameters.getLoadFlowParameters().setDc(false);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, new SecurityAnalysisParameters(), Reporter.NO_OP);
        PostContingencyResult postContingencyResult = result.getPostContingencyResults().get(0);
        assertSame(PostContingencyComputationStatus.CONVERGED, postContingencyResult.getStatus());
        assertEquals(1, postContingencyResult.getConnectivityResult().getCreatedSynchronousComponentCount());
        assertEquals(3.0, postContingencyResult.getConnectivityResult().getDisconnectedLoadActivePower());
        assertEquals(2.0, postContingencyResult.getConnectivityResult().getDisconnectedGenerationActivePower());
        assertTrue(postContingencyResult.getConnectivityResult().getDisconnectedElements().containsAll(
                List.of("d4", "d5", "g6", "l46", "l34", "l45", "l56")));

        //Test DC
        parameters.getLoadFlowParameters().setDc(true);

        result = runSecurityAnalysis(network, contingencies, monitors, parameters, Reporter.NO_OP);
        postContingencyResult = result.getPostContingencyResults().get(0);
        assertSame(PostContingencyComputationStatus.CONVERGED, postContingencyResult.getStatus());
        assertEquals(1, postContingencyResult.getConnectivityResult().getCreatedSynchronousComponentCount());
        assertEquals(3.0, postContingencyResult.getConnectivityResult().getDisconnectedLoadActivePower());
        assertEquals(2.0, postContingencyResult.getConnectivityResult().getDisconnectedGenerationActivePower());
        assertTrue(postContingencyResult.getConnectivityResult().getDisconnectedElements().containsAll(List.of("d4", "d5", "g6", "l46", "l34", "l45", "l56")));
    }

    @Test
    void testConnectivityResultOnSplitThreeCC() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("line", new BranchContingency("l34"), new BranchContingency("l45")));
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();

        //Test AC
        parameters.getLoadFlowParameters().setDc(false);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters, Reporter.NO_OP);
        PostContingencyResult postContingencyResult = result.getPostContingencyResults().get(0);
        assertSame(PostContingencyComputationStatus.CONVERGED, postContingencyResult.getStatus());
        assertEquals(2, postContingencyResult.getConnectivityResult().getCreatedSynchronousComponentCount());

        //Test DC
        parameters.getLoadFlowParameters().setDc(true);
        runSecurityAnalysis(network, contingencies, monitors, parameters, Reporter.NO_OP);
        postContingencyResult = result.getPostContingencyResults().get(0);
        assertSame(PostContingencyComputationStatus.CONVERGED, postContingencyResult.getStatus());
        assertEquals(2, postContingencyResult.getConnectivityResult().getCreatedSynchronousComponentCount());
    }

    @Test
    void testStaticVarCompensatorContingency() {
        Network network = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        network.getStaticVarCompensator("svc1").setVoltageSetpoint(385).setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("svc1", new StaticVarCompensatorContingency("svc1")));
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();

        // test AC
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters, Reporter.NO_OP);

        // compare with a simple load low
        network.getStaticVarCompensator("svc1").getTerminal().disconnect();
        LoadFlow.run(network, parameters.getLoadFlowParameters());

        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "svc1");
        assertEquals(network.getLine("l1").getTerminal1().getP(), postContingencyResult.getNetworkResult().getBranchResult("l1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal2().getP(), postContingencyResult.getNetworkResult().getBranchResult("l1").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal1().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l1").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal2().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l1").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testStaticVarCompensatorContingencyWithStandByAutomaton() {
        Network network = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        StaticVarCompensator svc1 = network.getStaticVarCompensator("svc1");
        svc1.setVoltageSetpoint(385).setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(400)
                .withLowVoltageThreshold(380)
                .withLowVoltageSetpoint(385)
                .withHighVoltageSetpoint(395)
                .withB0(-0.001f)
                .withStandbyStatus(true)
                .add();
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("svc1", new StaticVarCompensatorContingency("svc1")),
                new Contingency("ld1", new LoadContingency("ld1")));
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();

        // test AC
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, parameters, Reporter.NO_OP);

        // compare with a simple load low
        network.getStaticVarCompensator("svc1").getTerminal().disconnect();
        LoadFlow.run(network);

        PostContingencyResult postContingencyResult = getPostContingencyResult(result, "svc1");
        assertEquals(network.getLine("l1").getTerminal1().getP(), postContingencyResult.getNetworkResult().getBranchResult("l1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal2().getP(), postContingencyResult.getNetworkResult().getBranchResult("l1").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal1().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l1").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal2().getQ(), postContingencyResult.getNetworkResult().getBranchResult("l1").getQ2(), LoadFlowAssert.DELTA_POWER);

        // test restore.
        network.getStaticVarCompensator("svc1").getTerminal().connect();
        network.getLoad("ld1").getTerminal().disconnect();
        LoadFlow.run(network);
        PostContingencyResult postContingencyResult2 = getPostContingencyResult(result, "ld1");
        assertEquals(network.getLine("l1").getTerminal1().getP(), postContingencyResult2.getNetworkResult().getBranchResult("l1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal2().getP(), postContingencyResult2.getNetworkResult().getBranchResult("l1").getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal1().getQ(), postContingencyResult2.getNetworkResult().getBranchResult("l1").getQ1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("l1").getTerminal2().getQ(), postContingencyResult2.getNetworkResult().getBranchResult("l1").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testBusBarSectionContingency() {
        Network network = createNodeBreakerNetwork();

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL1_1");
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);

        List<Contingency> contingencies = Stream.of("BBS1")
                .map(id -> new Contingency(id, new BusbarSectionContingency(id)))
                .collect(Collectors.toList());

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);

        NetworkResult preContingencyNetworkResult = result.getPreContingencyResult().getNetworkResult();
        assertEquals(446.765, preContingencyNetworkResult.getBranchResult("L1").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(446.765, preContingencyNetworkResult.getBranchResult("L2").getI1(), LoadFlowAssert.DELTA_I);

        assertEquals(945.514, getPostContingencyResult(result, "BBS1").getNetworkResult().getBranchResult("L2").getI1(), LoadFlowAssert.DELTA_I);
        assertNull(getPostContingencyResult(result, "BBS1").getNetworkResult().getBranchResult("L1"));

        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setContingencyPropagation(false);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

        SecurityAnalysisResult result2 = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);

        NetworkResult preContingencyNetworkResult2 = result2.getPreContingencyResult().getNetworkResult();
        assertEquals(446.765, preContingencyNetworkResult2.getBranchResult("L1").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(446.765, preContingencyNetworkResult2.getBranchResult("L2").getI1(), LoadFlowAssert.DELTA_I);

        assertEquals(945.514, getPostContingencyResult(result2, "BBS1").getNetworkResult().getBranchResult("L2").getI1(), LoadFlowAssert.DELTA_I);
        assertNull(getPostContingencyResult(result2, "BBS1").getNetworkResult().getBranchResult("L1"));
    }

    @Test
    void testDcBusBarSectionContingency() {
        Network network = createNodeBreakerNetwork();

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        lfParameters.setDc(true);
        setSlackBusId(lfParameters, "VL1_1");
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);

        List<Contingency> contingencies = Stream.of("BBS1")
                .map(id -> new Contingency(id, new BusbarSectionContingency(id)))
                .collect(Collectors.toList());

        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);

        NetworkResult preContingencyNetworkResult = result.getPreContingencyResult().getNetworkResult();
        assertEquals(433.012, preContingencyNetworkResult.getBranchResult("L1").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(433.012, preContingencyNetworkResult.getBranchResult("L2").getI1(), LoadFlowAssert.DELTA_I);

        assertEquals(866.025, getPostContingencyResult(result, "BBS1").getNetworkResult().getBranchResult("L2").getI1(), LoadFlowAssert.DELTA_I);
        assertEquals(Double.NaN, getPostContingencyResult(result, "BBS1").getNetworkResult().getBranchResult("L1").getI1(), LoadFlowAssert.DELTA_I);
    }

    @Test
    void testBusBarSectionContingencyIssue() {
        Network network = NodeBreakerNetworkFactory.create3Bars();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL1_0"); // issue with slack bus to be disabled.
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new SwitchContingency("B1"), new SwitchContingency("C1"))));
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertTrue(result.getPostContingencyResults().isEmpty());
    }

    @Test
    void testLoadContingencyNoImpact() {
        Network network = SecurityAnalysisTestNetworkFactory.createWithFixedCurrentLimits();
        List<Contingency> contingencies = List.of(new Contingency("Load contingency", new LoadContingency("LD1")));
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies);
        assertEquals(1, result.getPostContingencyResults().size());
        contingencies = List.of(new Contingency("Load contingency", new LoadContingency("LD1")),
                                new Contingency("Switch contingency", new SwitchContingency("S1VL1_BBS1_GEN_DISCONNECTOR")));
        result = runSecurityAnalysis(network, contingencies);
        assertEquals(2, result.getPostContingencyResults().size());
    }

    @Test
    void testWithVoltageRemoteControl() {
        Network network = VoltageControlNetworkFactory.createWithSimpleRemoteControl();
        List<Contingency> contingencies = List.of(new Contingency("contingency",
                List.of(new BranchContingency("l12"), new BranchContingency("l31"))));
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "b4_vl");
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        assertDoesNotThrow(() -> {
            runSecurityAnalysis(network, contingencies, Collections.emptyList(), securityAnalysisParameters);
        });
    }

    @Test
    void testWithTransformerVoltageControl() {
        Network network = VoltageControlNetworkFactory.createWithTransformerSharedRemoteControl();
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new BranchContingency("T2wT2"))));
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL_1");
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        lfParameters.setTransformerVoltageControlOn(true);
        OpenLoadFlowParameters openLoadFlowParameters = new OpenLoadFlowParameters();
        openLoadFlowParameters.setTransformerVoltageControlMode(OpenLoadFlowParameters.TransformerVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL);
        lfParameters.addExtension(OpenLoadFlowParameters.class, openLoadFlowParameters);
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, Collections.emptyList(), securityAnalysisParameters);
        assertEquals(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
    }

    @Test
    void testWithTwoVoltageControls() {
        Network network = VoltageControlNetworkFactory.createWithTwoVoltageControls();
        List<Contingency> contingencies = List.of(new Contingency("contingency",
                List.of(new BranchContingency("l12"), new BranchContingency("l13"))));
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "b5_vl");
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        assertDoesNotThrow(() -> {
            runSecurityAnalysis(network, contingencies, Collections.emptyList(), securityAnalysisParameters);
        });
    }

    @Test
    void testWithShuntAndGeneratorVoltageControls() {
        Network network = VoltageControlNetworkFactory.createWithShuntAndGeneratorVoltageControl();
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new BranchContingency("l12"))));
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "b1_vl");
        lfParameters.setShuntCompensatorVoltageControlOn(true);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        List<StateMonitor> monitors = List.of(new StateMonitor(ContingencyContext.all(), Collections.emptySet(), Collections.singleton("b1_vl"), Collections.emptySet()));
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertEquals(0.99044, result.getPostContingencyResults().get(0).getNetworkResult().getBusResult("b1").getV(), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testWithShuntAndGeneratorVoltageControls2() {
        Network network = VoltageControlNetworkFactory.createNetworkWith2T2wt();
        network.getTwoWindingsTransformer("T2wT1").getRatioTapChanger()
                .setTargetDeadband(6.0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(network.getTwoWindingsTransformer("T2wT1").getTerminal1())
                .setTargetV(130.0);
        network.getTwoWindingsTransformer("T2wT2").getRatioTapChanger()
                .setTargetDeadband(6.0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(network.getTwoWindingsTransformer("T2wT2").getTerminal1())
                .setTargetV(130.0);
        network.getGenerator("GEN_1").setRegulatingTerminal(network.getLine("LINE_12").getTerminal2());
        network.getVoltageLevel("VL_3").newGenerator()
                .setId("GEN_3")
                .setBus("BUS_3")
                .setMinP(0.0)
                .setMaxP(140)
                .setTargetP(0)
                .setTargetV(33)
                .setVoltageRegulatorOn(true)
                .add();
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new BranchContingency("LINE_12"))));
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        setSlackBusId(lfParameters, "VL_3");
        lfParameters.setTransformerVoltageControlOn(true);
        OpenLoadFlowParameters openLoadFlowParameters = new OpenLoadFlowParameters();
        openLoadFlowParameters.setTransformerVoltageControlMode(OpenLoadFlowParameters.TransformerVoltageControlMode.AFTER_GENERATOR_VOLTAGE_CONTROL);
        openLoadFlowParameters.setMinRealisticVoltage(0.0);
        openLoadFlowParameters.setMaxRealisticVoltage(3.0);
        lfParameters.addExtension(OpenLoadFlowParameters.class, openLoadFlowParameters);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        List<StateMonitor> monitors = List.of(new StateMonitor(ContingencyContext.all(), Collections.emptySet(), Collections.singleton("VL_2"), Collections.emptySet()));
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertEquals(133.557, result.getPostContingencyResults().get(0).getNetworkResult().getBusResult("BUS_2").getV(), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testWithTieLineContingency() {
        Network network = BoundaryFactory.createWithTieLine();
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new TieLineContingency("t12"))));
        List<StateMonitor> monitors = createNetworkMonitors(network);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertEquals(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
        assertEquals(400.0, result.getPostContingencyResults().get(0).getNetworkResult().getBusResult("b4").getV(), LoadFlowAssert.DELTA_V);
        assertEquals(400.0, result.getPostContingencyResults().get(0).getNetworkResult().getBusResult("b3").getV(), LoadFlowAssert.DELTA_V);
        assertEquals(-0.0038, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResult("l34").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithTieLineContingency2() {
        // using one of the two dangling line ids.
        Network network = BoundaryFactory.createWithTieLine();
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new DanglingLineContingency("h1"))));
        List<StateMonitor> monitors = createNetworkMonitors(network);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertEquals(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
        assertEquals(400.0, result.getPostContingencyResults().get(0).getNetworkResult().getBusResult("b4").getV(), LoadFlowAssert.DELTA_V);
        assertEquals(400.0, result.getPostContingencyResults().get(0).getNetworkResult().getBusResult("b3").getV(), LoadFlowAssert.DELTA_V);
        assertEquals(-0.0038, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResult("l34").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithTieLineContingency3() {
        Network network = BoundaryFactory.createWithTieLine();
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new GeneratorContingency("g1"))));
        List<StateMonitor> monitors = createNetworkMonitors(network);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertEquals(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
        assertEquals(400.0, result.getPostContingencyResults().get(0).getNetworkResult().getBusResult("b4").getV(), LoadFlowAssert.DELTA_V);
        assertEquals(399.875, result.getPostContingencyResults().get(0).getNetworkResult().getBusResult("b3").getV(), LoadFlowAssert.DELTA_V);
        assertEquals(50.006, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResult("t12").getQ2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithTieLineContingency4() {
        Network network = BoundaryFactory.createWithTieLine();
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new GeneratorContingency("g1"))));
        List<StateMonitor> monitors = createNetworkMonitors(network);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.getLoadFlowParameters().setDc(true);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertEquals(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
        assertEquals(-50.0, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResult("t12").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.0, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResult("t12").getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testAcceptableDurations() {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        network.getGenerator("GEN").setMaxP(4000).setMinP(-4000);

        TieLine line = network.getTieLine("NHV1_NHV2_1");
        line.newCurrentLimits2()
                .setPermanentLimit(900.0)
                .beginTemporaryLimit()
                    .setName("10'")
                    .setAcceptableDuration(600)
                    .setValue(1000.0)
                .endTemporaryLimit()
                .beginTemporaryLimit()
                    .setName("1'")
                    .setAcceptableDuration(60)
                    .setValue(1100.0)
                .endTemporaryLimit()
                .add();
        TieLine line2 = network.getTieLine("NHV1_NHV2_2");
        line2.newCurrentLimits2()
                .setPermanentLimit(900.0)
                .beginTemporaryLimit()
                    .setName("20'")
                    .setAcceptableDuration(1200)
                    .setValue(1000.0)
                .endTemporaryLimit()
                .beginTemporaryLimit()
                    .setName("N/A")
                    .setAcceptableDuration(60)
                    .setValue(1.7976931348623157E308D)
                .endTemporaryLimit()
                .add();

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        ContingenciesProvider contingencies = n -> ImmutableList.of(
                new Contingency("contingency1", new BranchContingency("NHV1_NHV2_1")),
                new Contingency("contingency2", new TieLineContingency("NHV1_NHV2_2")),
                new Contingency("contingency3", new DanglingLineContingency("NHV1_XNODE1")),
                new Contingency("contingency4", new DanglingLineContingency("XNODE2_NHV2")));
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies.getContingencies(network), Collections.emptyList(), securityAnalysisParameters);

        LimitViolation violation0 = new LimitViolation("NHV1_NHV2_2", null, LimitViolationType.CURRENT, "20'",
                60, 1000.0, 1.0F, 1047.8598237521767, Branch.Side.TWO);
        int compare0 = LimitViolations.comparator().compare(violation0, result.getPostContingencyResults().get(0)
                .getLimitViolationsResult().getLimitViolations().get(0));
        assertEquals(0, compare0);

        LimitViolation violation1 = new LimitViolation("NHV1_NHV2_1", null, LimitViolationType.CURRENT, "10'",
                60, 1000.0, 1.0F, 1047.8598237521767, Branch.Side.TWO);
        int compare1 = LimitViolations.comparator().compare(violation1, result.getPostContingencyResults().get(1)
                .getLimitViolationsResult().getLimitViolations().get(0));
        assertEquals(0, compare1);

        int compare2 = LimitViolations.comparator().compare(violation0, result.getPostContingencyResults().get(2)
                .getLimitViolationsResult().getLimitViolations().get(0));
        assertEquals(0, compare2); // FIXME line open at one side

        int compare3 = LimitViolations.comparator().compare(violation1, result.getPostContingencyResults().get(3)
                .getLimitViolationsResult().getLimitViolations().get(0));
        assertEquals(0, compare3); // FIXME line open at one side

        line.newCurrentLimits1().setPermanentLimit(900.0).add();
        line2.newCurrentLimits1().setPermanentLimit(900.0).add();
        securityAnalysisParameters.getLoadFlowParameters().setDc(true);
        SecurityAnalysisResult result2 = runSecurityAnalysis(network, contingencies.getContingencies(network), Collections.emptyList(), securityAnalysisParameters);

        LimitViolation violation4 = new LimitViolation("NHV1_NHV2_2", null, LimitViolationType.CURRENT, "permanent",
                2147483647, 900.0, 1.0F, 911.605688194146412890625, Branch.Side.ONE);
        int compare4 = LimitViolations.comparator().compare(violation4, result2.getPostContingencyResults().get(0)
                .getLimitViolationsResult().getLimitViolations().get(0));
        assertEquals(0, compare4);
        LimitViolation violation5 = new LimitViolation("NHV1_NHV2_2", null, LimitViolationType.CURRENT, "permanent",
                1200, 900.0, 1.0F, 911.605688194146412890625, Branch.Side.TWO);
        int compare5 = LimitViolations.comparator().compare(violation5, result2.getPostContingencyResults().get(0)
                .getLimitViolationsResult().getLimitViolations().get(1));
        assertEquals(0, compare5);
    }

    @Test
    void testWithControlledBranchContingency() {
        // PST 'PS1' regulates flow on terminal 1 of line 'L1'. Test contingency of L1.
        Network network = PhaseControlFactory.createNetworkWithT2wt();
        Line line1 = network.getLine("L1");
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(2)
                .setRegulationTerminal(line1.getTerminal1())
                .setRegulationValue(83);
        List<Contingency> contingencies = List.of(new Contingency("contingency", List.of(new LineContingency("L1"))));
        List<StateMonitor> monitors = createNetworkMonitors(network);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.getLoadFlowParameters().setPhaseShifterRegulationOn(true);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
        assertEquals(PostContingencyComputationStatus.CONVERGED, result.getPostContingencyResults().get(0).getStatus());
        assertEquals(100.369, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResult("PS1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(100.184, result.getPostContingencyResults().get(0).getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyPropagation() {
        var network = NodeBreakerNetworkFactory.create3barsAndJustOneVoltageLevel();
        for (Bus bus : network.getBusBreakerView().getBuses()) {
            System.out.println("bus: " + bus.getId());
        }
        List<Contingency> contingencies = List.of(new Contingency("G1", new GeneratorContingency("G1")));
        List<StateMonitor> monitors = createNetworkMonitors(network);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        OpenLoadFlowParameters openLoadFlowParameters = new OpenLoadFlowParameters();
        openLoadFlowParameters.setSlackBusSelectionMode(SlackBusSelectionMode.NAME).setSlackBusId("VL1_1");
        securityAnalysisParameters.getLoadFlowParameters().addExtension(OpenLoadFlowParameters.class, openLoadFlowParameters);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters, Reporter.NO_OP);
        for (BusResult busResult : result.getPreContingencyResult().getNetworkResult().getBusResults()) {
            System.out.println("busResult: " + busResult.getBusId());
        }
    }
}
