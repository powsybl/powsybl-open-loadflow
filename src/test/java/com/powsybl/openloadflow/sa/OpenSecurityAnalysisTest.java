/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.io.table.AsciiTableFormatterFactory;
import com.powsybl.commons.io.table.TableFormatterConfig;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.NaiveGraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.security.*;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Double.NaN;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSecurityAnalysisTest {

    private Network network;

    @BeforeEach
    void setUp() {
        network = NodeBreakerNetworkFactory.create();

        network.getLine("L1").newCurrentLimits1()
                .setPermanentLimit(940.0)
                .beginTemporaryLimit()
                .setName("60")
                .setAcceptableDuration(60)
                .setValue(1000)
                .endTemporaryLimit()
                .add();
        network.getLine("L1").newCurrentLimits2().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits1()
                .setPermanentLimit(940.0)
                .beginTemporaryLimit()
                .setName("60")
                .setAcceptableDuration(60)
                .setValue(950)
                .endTemporaryLimit()
                .add();
        network.getLine("L2").newCurrentLimits2().setPermanentLimit(940.0)
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
    }

    @Test
    void testCurrentLimitViolations() {
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("VL1_1");
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L1", "L2")
            .map(id -> new Contingency(id, new BranchContingency(id)))
            .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(),
            () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
            new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
            contingenciesProvider, Collections.emptyList());

        SecurityAnalysisResult result = futureResult.join().getResult();
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
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());
        network.getLine("L1").getCurrentLimits1().setPermanentLimit(200);

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        SecurityAnalysisResult result = futureResult.join().getResult();

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(1, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());

        StringWriter writer = new StringWriter();
        Security.print(result, network, writer, new AsciiTableFormatterFactory(), new TableFormatterConfig());
        System.out.println(writer.toString());
    }

    @Test
    void testLowVoltageLimitViolations() {

        network.getGenerator("G").setTargetV(393);

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("VL1_1");
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider();
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
            new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
            contingenciesProvider, Collections.emptyList());

        SecurityAnalysisResult result = futureResult.join().getResult();
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

        StringWriter writer = new StringWriter();
        Security.print(result, network, writer, new AsciiTableFormatterFactory(), new TableFormatterConfig());
        List<LimitViolation> limitViolations1 = result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations();
        LimitViolation lowViolation = limitViolations1.get(2);
        assertEquals(LimitViolationType.LOW_VOLTAGE, lowViolation.getLimitType());
        assertEquals(370, lowViolation.getLimit());
        System.out.println(writer.toString());
    }

    @Test
    void testHighVoltageLimitViolations() {

        network.getGenerator("G").setTargetV(421);

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("VL1_1");
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        SecurityAnalysisResult result = futureResult.join().getResult();

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());

        assertEquals(0, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertTrue(result.getPostContingencyResults().get(1).getLimitViolationsResult().isComputationOk());
        assertEquals(0, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());

        StringWriter writer = new StringWriter();
        Security.print(result, network, writer, new AsciiTableFormatterFactory(), new TableFormatterConfig());
        System.out.println(writer.toString());
    }

    @Test
    void testActivePowerLimitViolations() {

        network.getLine("L1").newActivePowerLimits1()
               .setPermanentLimit(1.0)
               .beginTemporaryLimit()
               .setName("60")
               .setAcceptableDuration(60)
               .setValue(1.2)
               .endTemporaryLimit()
               .add();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("VL1_1");
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L1", "L2")
                                                                       .map(id -> new Contingency(id, new BranchContingency(id)))
                                                                       .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        SecurityAnalysisResult result = futureResult.join().getResult();

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

        network.getLine("L1").newApparentPowerLimits1()
               .setPermanentLimit(1.0)
               .beginTemporaryLimit()
               .setName("60")
               .setAcceptableDuration(60)
               .setValue(1.2)
               .endTemporaryLimit()
               .add();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("VL1_1");
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L1", "L2")
                                                                       .map(id -> new Contingency(id, new BranchContingency(id)))
                                                                       .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        SecurityAnalysisResult result = futureResult.join().getResult();

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

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        // Testing all contingencies at once
        ContingenciesProvider contingenciesProvider = n -> n.getBranchStream()
            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
            .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        SecurityAnalysisResult result = futureResult.join().getResult();

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());

        saParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        result = futureResult.join().getResult();

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testNoGenerator() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").getTerminal().disconnect();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        saParameters.setLoadFlowParameters(new LoadFlowParameters());

        ContingenciesProvider contingenciesProvider = n -> Collections.emptyList();

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());

        CompletionException exception = assertThrows(CompletionException.class, futureResult::join);
        assertEquals("Largest network is invalid", exception.getCause().getMessage());
    }

    @Test
    void testNoRemainingGenerator() {

        Network network = EurostagTutorialExample1Factory.create();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        ContingenciesProvider contingenciesProvider = net -> Stream.of("NGEN_NHV1")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        SecurityAnalysisResult result = futureResult.join().getResult();

        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testNoRemainingLoad() {

        Network network = EurostagTutorialExample1Factory.create();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        lfParameters.setDistributedSlack(true).setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        ContingenciesProvider contingenciesProvider = net -> Stream.of("NHV2_NLOAD")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        SecurityAnalysisResult result = futureResult.join().getResult();

        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testSAWithSeveralConnectedComponents() {

        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        // Testing all contingencies at once
        ContingenciesProvider contingenciesProvider = n -> n.getBranchStream()
                                                            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
                                                            .collect(Collectors.toList());

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList());
        SecurityAnalysisResult result = futureResult.join().getResult();

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testSAWithStateMonitor() {
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
    void testSAWithStateMonitorNotExistingBranchBus() {
        Network network = DistributedSlackNetworkFactory.create();

        List<StateMonitor> monitors = List.of(
            new StateMonitor(ContingencyContext.all(), Collections.singleton("l1"), Collections.singleton("bus"), Collections.singleton("three windings"))
        );

        SecurityAnalysisResult result = runSecurityAnalysis(network, allBranches(network), monitors);

        assertEquals(0, result.getPreContingencyResult().getPreContingencyBusResults().size());
        assertEquals(0, result.getPreContingencyResult().getPreContingencyBranchResults().size());
    }

    @Test
    void testSAWithStateMonitorDisconnectBranch() {
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
    void testSAWithStateMonitorDanglingLine() {
        Network network = DanglingLineFactory.create();

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("dl1"), Collections.singleton("vl1"), emptySet()));

        List<Contingency> contingencies = allBranches(network);
        CompletionException exception = assertThrows(CompletionException.class, () -> runSecurityAnalysis(network, contingencies, monitors));
        assertEquals("Unsupported type of branch for branch result: dl1", exception.getCause().getMessage());
    }

    private static List<Contingency> allBranches(Network network) {
        return network.getBranchStream()
            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
            .collect(Collectors.toList());
    }

    @Test
    void testSAWithStateMonitorLfLeg() {
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
    void testSADcMode() {

        Network fourBusNetwork = FourBusNetworkFactory.create();
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("b1");
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = n -> n.getBranchStream()
                .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
                .collect(Collectors.toList());
        lfParameters.setDc(true);

        fourBusNetwork.getLine("l14").newActivePowerLimits1().setPermanentLimit(0.1).add();
        fourBusNetwork.getLine("l12").newActivePowerLimits1().setPermanentLimit(0.2).add();
        fourBusNetwork.getLine("l23").newActivePowerLimits1().setPermanentLimit(0.25).add();
        fourBusNetwork.getLine("l34").newActivePowerLimits1().setPermanentLimit(0.15).add();
        fourBusNetwork.getLine("l13").newActivePowerLimits1().setPermanentLimit(0.1).add();

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Set.of("l14", "l12", "l23", "l34", "l13"), Collections.emptySet(), Collections.emptySet()));

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(fourBusNetwork, fourBusNetwork.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), LocalComputationManager.getDefault(), saParameters,
                contingenciesProvider, Collections.emptyList(), monitors);
        SecurityAnalysisResult result = futureResult.join().getResult();

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(5, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(5, result.getPostContingencyResults().size());
        assertEquals(4, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(2).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(3).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, result.getPostContingencyResults().get(4).getLimitViolationsResult().getLimitViolations().size());

        //Branch result for first contingency
        assertEquals(5, result.getPostContingencyResults().get(0).getBranchResults().size());

        //Check branch results for flowTransfer computation for contingency on l14
        PostContingencyResult postContl14 = result.getPostContingencyResults().stream().filter(r -> r.getContingency().getId().equals("l14")).findFirst().get();
        assertEquals("l14", postContl14.getContingency().getId());

        BranchResult brl14l12 = postContl14.getBranchResult("l12");
        assertEquals(0.33, brl14l12.getP1(), 1e-2);
        assertEquals(0.33, brl14l12.getFlowTransfer(), 1e-2);

        BranchResult brl14l14 = postContl14.getBranchResult("l14");
        assertEquals(0.0, brl14l14.getP1(), 1e-2);
        assertEquals(-1.0, brl14l14.getFlowTransfer(), 1e-2);

        BranchResult brl14l23 = postContl14.getBranchResult("l23");
        assertEquals(1.33, brl14l23.getP1(), 1e-2);
        assertEquals(0.33, brl14l23.getFlowTransfer(), 1e-2);

        BranchResult brl14l34 = postContl14.getBranchResult("l34");
        assertEquals(-1.0, brl14l34.getP1(), 1e-2);
        assertEquals(1.0, brl14l34.getFlowTransfer(), 1e-2);

        BranchResult brl14l13 = postContl14.getBranchResult("l13");
        assertEquals(1.66, brl14l13.getP1(), 1e-2);
        assertEquals(0.66, brl14l13.getFlowTransfer(), 1e-2);

        StringWriter writer = new StringWriter();
        Security.print(result, fourBusNetwork, writer, new AsciiTableFormatterFactory(), new TableFormatterConfig());
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

    /**
     * Runs a security analysis with default parameters + most meshed slack bus selection
     */
    private static SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors) {

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
            .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        ContingenciesProvider provider = n -> contingencies;
        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network,
                                                                                 network.getVariantManager().getWorkingVariantId(),
                                                                                 new DefaultLimitViolationDetector(),
                                                                                 new LimitViolationFilter(),
                                                                                 null,
                                                                                 saParameters,
                                                                                 provider,
                                                                                 Collections.emptyList(),
                                                                                 monitors);
        return futureResult.join().getResult();
    }

    @Test
    void testSAmodeACAllBranchMonitoredFlowTransfer() {
        Network network = FourBusNetworkFactory.create();
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        // Testing all contingencies at once
        ContingenciesProvider contingenciesProvider = n -> n.getBranchStream()
                .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
                .collect(Collectors.toList());

        Set<String> allBranchIds = network.getBranchStream().map(b -> b.getId()).collect(Collectors.toSet());

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), allBranchIds, Collections.emptySet(), Collections.emptySet()));

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);
        CompletableFuture<SecurityAnalysisReport> futureResult = osaProvider.run(network, network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, saParameters,
                contingenciesProvider, Collections.emptyList(), monitors);
        SecurityAnalysisResult result = futureResult.join().getResult();

        assertEquals(5, result.getPostContingencyResults().size());

        for (PostContingencyResult r : result.getPostContingencyResults()) {
            assertEquals(4, r.getBranchResults().size());
        }

        //Check branch results for flowTransfer computation for contingency on l14
        PostContingencyResult postContl14 = result.getPostContingencyResults().stream().filter(r -> r.getContingency().getId().equals("l14")).findFirst().get();
        assertEquals("l14", postContl14.getContingency().getId());

        BranchResult brl14l12 = postContl14.getBranchResult("l12");
        assertEquals(0.33, brl14l12.getP1(), 1e-2);
        assertEquals(0.33, brl14l12.getFlowTransfer(), 1e-2);

        BranchResult brl14l23 = postContl14.getBranchResult("l23");
        assertEquals(1.33, brl14l23.getP1(), 1e-2);
        assertEquals(0.33, brl14l23.getFlowTransfer(), 1e-2);

        BranchResult brl14l34 = postContl14.getBranchResult("l34");
        assertEquals(-1.0, brl14l34.getP1(), 1e-2);
        assertEquals(1.0, brl14l34.getFlowTransfer(), 1e-2);

        BranchResult brl14l13 = postContl14.getBranchResult("l13");
        assertEquals(1.66, brl14l13.getP1(), 1e-2);
        assertEquals(0.66, brl14l13.getFlowTransfer(), 1e-2);
    }
}
