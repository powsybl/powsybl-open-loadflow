/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.io.table.AsciiTableFormatterFactory;
import com.powsybl.commons.io.table.TableFormatterConfig;
import com.powsybl.contingency.*;
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

        OpenSecurityAnalysisProvider osaProvider = new OpenSecurityAnalysisProvider();
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

        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));

        SecurityAnalysisReport report = securityAnalysis.runSync(saParameters, contingenciesProvider);
        SecurityAnalysisResult result = report.getResult();
        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().isComputationOk());
        assertEquals(1, result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().size());
        assertEquals(1, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
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

        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));

        SecurityAnalysisReport report = securityAnalysis.runSync(saParameters, contingenciesProvider);
        SecurityAnalysisResult result = report.getResult();
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

        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));

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

        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
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

        CompletableFuture<SecurityAnalysisReport> report = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
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

        SecurityAnalysisReport report = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
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

        SecurityAnalysisReport report = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
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

        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
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
        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();

        assertEquals(1, report.getResult().getBusResultsAsList().size());
        assertEquals(new BusResults("b1_vl", "b1", 400, 0.00411772307613007), report.getResult().getBusResultsAsList().get(0));
        assertEquals(1, report.getResult().getBranchResultsAsList().size());
        assertEquals(new BranchResult("l24", 244.9999975189166, 3.0000094876289114, 558.8517346879828, -244.9999975189166, -299.86040688975567, 558.8517346881861),
            report.getResult().getBranchResultsAsList().get(0));
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
        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertEquals(0, report.getResult().getBranchResultsAsList().size());
        assertEquals(0, report.getResult().getBusResultsAsList().size());
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
        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();

        assertEquals(1, report.getResult().getBusResultsAsList().size());

        assertEquals(new BusResults("b1_vl", "b1", 400, 0.003581299841270782), report.getResult().getBusResultsAsList().get(0));
        assertEquals(1, report.getResult().getBranchResultsAsList().size());
        assertEquals(new BranchResult("l24", 0.0, 0.0, 0.0, 0.0, -0.0, 0.0),
            report.getResult().getBranchResultsAsList().get(0));

        network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal2().disconnect();
        report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertEquals(1, report.getResult().getBranchResultsAsList().size());
        assertEquals(new BranchResult("l24", 0.0, 0.0, 0.0, 0.0, -0.0, 0.0),
            report.getResult().getBranchResultsAsList().get(0));

        network = DistributedSlackNetworkFactory.create();
        network.getBranch("l24").getTerminal2().disconnect();
        network.getBranch("l24").getTerminal1().disconnect();
        report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertEquals(1, report.getResult().getBranchResultsAsList().size());
        assertEquals(new BranchResult("l24", 0.0, 0.0, 0.0, 0.0, -0.0, 0.0),
            report.getResult().getBranchResultsAsList().get(0));

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
        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();

        assertEquals(1, report.getResult().getBusResultsAsList().size());

        assertEquals(new BusResults("vl1", "b1", 390, 0.058104236530870684), report.getResult().getBusResultsAsList().get(0));
        assertEquals(1, report.getResult().getBranchResultsAsList().size());
        assertEquals(new BranchResult("dl1", 101.30214011268102, 1.497638251697259, 268.6405409741444, NaN, NaN, NaN),
            report.getResult().getBranchResultsAsList().get(0));

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
        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new, monitors);

        SecurityAnalysisReport report = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertEquals(1, report.getResult().getThreeWindingsTransformerResultsAsList().size());
        assertEquals(new ThreeWindingsTransformerResult("3wt", 1.6109556638123288,
                0.8188422244941966, 1030.4601542294686, -1.6100000049961467, -0.7400000017321797,
                978.937148970407, -1.7348031191643076E-16, 0.0, 4.0061722632990915E-14),
            report.getResult().getThreeWindingsTransformerResultsAsList().get(0));
    }
}
