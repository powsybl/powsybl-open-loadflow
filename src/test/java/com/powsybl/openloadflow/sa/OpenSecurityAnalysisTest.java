/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.io.table.AsciiTableFormatterFactory;
import com.powsybl.commons.io.table.TableFormatterConfig;
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
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResults;
import com.powsybl.security.results.ThreeWindingsTransformerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Double.NaN;
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

        AbstractSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));

        SecurityAnalysisReport report = securityAnalysis.runSync(saParameters, contingenciesProvider);
        SecurityAnalysisResult result = report.getResult();
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

        AbstractSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network);

        SecurityAnalysisReport report = securityAnalysis.runSync(saParameters, contingenciesProvider);
        SecurityAnalysisResult result = report.getResult();
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
               .setValue(1.0)
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

        OpenSecurityAnalysisFactory osaFactory = new OpenSecurityAnalysisFactory();
        AbstractSecurityAnalysis securityAnalysis = osaFactory.create(network, new LimitViolationFilter(), null, 0);

        SecurityAnalysisResult result = securityAnalysis.runSync(saParameters, contingenciesProvider);
        assertEquals(0, result.getPreContingencyResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());

        int activePowerLimitViolationsCount = 0;
        for (PostContingencyResult r : result.getPostContingencyResults()) {
            for (LimitViolation v : r.getLimitViolationsResult().getLimitViolations()) {
                if ( v.getLimitType() == LimitViolationType.ACTIVE_POWER ) {
                    activePowerLimitViolationsCount++;
                }
            }
        }
        assertEquals(1, activePowerLimitViolationsCount);
    }

    @Test
    void testApparentPowerLimitViolations() {
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

        AbstractSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertTrue(report.getResult().getPreContingencyResult().getLimitViolationsResult().isComputationOk());

        saParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        SecurityAnalysisReport report2 = securityAnalysis.runSync(saParameters, contingenciesProvider);
        assertTrue(report2.getResult().getPreContingencyResult().getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testNoGenerator() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").getTerminal().disconnect();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        saParameters.setLoadFlowParameters(new LoadFlowParameters());

        ContingenciesProvider contingenciesProvider = n -> Collections.emptyList();

        CompletableFuture<SecurityAnalysisReport> report = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new)
            .run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider);

        CompletionException exception = assertThrows(CompletionException.class, report::join);
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

        SecurityAnalysisReport report = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new)
            .run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider)
            .join();

        assertTrue(report.getResult().getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
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

        SecurityAnalysisReport report = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new)
            .run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider)
            .join();

        assertTrue(report.getResult().getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
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

        AbstractSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertTrue(report.getResult().getPreContingencyResult().getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testSAWithStateMonitor() {
        Network network = DistributedSlackNetworkFactory.create();
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

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("l24"), Collections.singleton("b1_vl"), Collections.emptySet()));
        AbstractSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();

        assertEquals(1, report.getResult().getPreContingencyResult().getPreContingencyBusResults().size());
        assertEquals(new BusResults("b1_vl", "b1", 400, 0.00411772307613007), report.getResult().getPreContingencyResult().getPreContingencyBusResults().get(0));
        assertEquals(1, report.getResult().getPreContingencyResult().getPreContingencyBranchResults().size());
        assertEquals(new BranchResult("l24", 244.9999975189166, 3.0000094876289114, 558.8517346879828, -244.9999975189166, -299.86040688975567, 558.8517346881861),
                report.getResult().getPreContingencyResult().getPreContingencyBranchResults().get(0));

        assertEquals(new BranchResult("l24", 300.0000316340358, 2.999999961752837, 14224.917799052932, -300.00003163403585, -208.94326368159844, 14224.917799052939),
                report.getResult().getPostContingencyResults().get(0).getBranchResult("l24"));
    }

    @Test
    void testSAWithStateMonitorNotExistingBranchBus() {
        Network network = DistributedSlackNetworkFactory.create();
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

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("l1"), Collections.singleton("bus"), Collections.singleton("three windings")));
        AbstractSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertEquals(0, report.getResult().getPreContingencyResult().getPreContingencyBusResults().size());
        assertEquals(0, report.getResult().getPreContingencyResult().getPreContingencyBranchResults().size());
    }

    @Test
    void testSAWithStateMonitorDisconnectBranch() {
        Network network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal1().disconnect();
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

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("l24"), Collections.singleton("b1_vl"), Collections.emptySet()));
        AbstractSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();

        assertEquals(1, report.getResult().getPreContingencyResult().getPreContingencyBusResults().size());

        assertEquals(new BusResults("b1_vl", "b1", 400, 0.003581299841270782), report.getResult().getPreContingencyResult().getPreContingencyBusResults().get(0));
        assertEquals(1, report.getResult().getPreContingencyResult().getPreContingencyBusResults().size());
        assertEquals(new BranchResult("l24", NaN, NaN, NaN, 0.0, -0.0, 0.0),
                report.getResult().getPreContingencyResult().getPreContingencyBranchResults().get(0));

        network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal2().disconnect();
        report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertEquals(1, report.getResult().getPreContingencyResult().getPreContingencyBranchResults().size());
        assertEquals(new BranchResult("l24", NaN, NaN, NaN, 0.0, -0.0, 0.0),
                report.getResult().getPreContingencyResult().getPreContingencyBranchResults().get(0));

        network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal2().disconnect();
        network.getBranch("l24").getTerminal1().disconnect();
        report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertEquals(1, report.getResult().getPreContingencyResult().getPreContingencyBranchResults().size());
        assertEquals(new BranchResult("l24", NaN, NaN, NaN, 0.0, -0.0, 0.0),
                report.getResult().getPreContingencyResult().getPreContingencyBranchResults().get(0));

    }

    @Test
    void testSAWithStateMonitorDanglingLine() {
        Network network = DanglingLineFactory.create();
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

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.singleton("dl1"), Collections.singleton("vl1"), Collections.emptySet()));
        CompletableFuture<SecurityAnalysisReport> report = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors)
                .run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider);

        CompletionException exception = assertThrows(CompletionException.class, report::join);
        assertEquals("Unsupported type of branch for branch result: dl1", exception.getCause().getMessage());
    }

    @Test
    void testSAWithStateMonitorLfLeg() {
        Network network = T3wtFactory.create();
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

        List<StateMonitor> monitors = new ArrayList<>();
        monitors.add(new StateMonitor(ContingencyContext.all(), Collections.emptySet(), Collections.emptySet(), Collections.singleton("3wt")));
        AbstractSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertEquals(1, report.getResult().getPreContingencyResult().getPreContingencyThreeWindingsTransformerResults().size());
        assertEquals(new ThreeWindingsTransformerResult("3wt", 1.6109556638123288,
                        0.8188422244941966, 1030.4601542294686, -1.6100000049961467, -0.7400000017321797,
                        978.937148970407, -1.7348031191643076E-16, 0.0, 4.0061722632990915E-14),
                report.getResult().getPreContingencyResult().getPreContingencyThreeWindingsTransformerResults().get(0));
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

        fourBusNetwork.getLine("l14").newActivePowerLimits1().setPermanentLimit(0.1).add();
        fourBusNetwork.getLine("l12").newActivePowerLimits1().setPermanentLimit(0.2).add();
        fourBusNetwork.getLine("l23").newActivePowerLimits1().setPermanentLimit(0.25).add();
        fourBusNetwork.getLine("l34").newActivePowerLimits1().setPermanentLimit(0.15).add();
        fourBusNetwork.getLine("l13").newActivePowerLimits1().setPermanentLimit(0.1).add();

        AbstractSecurityAnalysis securityAnalysis = new DcSecurityAnalysis(network);

        SecurityAnalysisReport report = securityAnalysis.runSync(saParameters, contingenciesProvider);
        assertTrue(report.getResult().getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(5, report.getResult().getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(5, report.getResult().getPostContingencyResults().size());
        assertEquals(4, report.getResult().getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, report.getResult().getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, report.getResult().getPostContingencyResults().get(2).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, report.getResult().getPostContingencyResults().get(3).getLimitViolationsResult().getLimitViolations().size());
        assertEquals(4, report.getResult().getPostContingencyResults().get(4).getLimitViolationsResult().getLimitViolations().size());

        StringWriter writer = new StringWriter();
        Security.print(report.getResult(), fourBusNetwork, writer, new AsciiTableFormatterFactory(), new TableFormatterConfig());
        System.out.println(writer.toString());
    }
}
