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
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.NaiveGraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.security.LimitViolationFilter;
import com.powsybl.security.Security;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSecurityAnalysisTest {

    private Network network;

    @BeforeEach
    void setUp() {
        network = NodeBreakerNetworkFactory.create();
    }

    @Test
    void testCurrentLimitViolations() {
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelector(new NameSlackBusSelector("VL1_1"));
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L1", "L2")
            .map(id -> new Contingency(id, new BranchContingency(id)))
            .collect(Collectors.toList());

        OpenSecurityAnalysisFactory osaFactory = new OpenSecurityAnalysisFactory(new DenseMatrixFactory(),
            () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        OpenSecurityAnalysis securityAnalysis = osaFactory.create(network, null, 0);

        SecurityAnalysisResult result = securityAnalysis.runSync(saParameters, contingenciesProvider);
        assertTrue(result.getPreContingencyResult().isComputationOk());
        assertEquals(0, result.getPreContingencyResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertTrue(result.getPostContingencyResults().get(1).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
    }

    @Test
    void testLowVoltageLimitViolations() {

        network.getGenerator("G").setTargetV(393);

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelector(new NameSlackBusSelector("VL1_1"));
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        OpenSecurityAnalysisFactory osaFactory = new OpenSecurityAnalysisFactory();
        OpenSecurityAnalysis securityAnalysis = osaFactory.create(network, new LimitViolationFilter(), null, 0);

        SecurityAnalysisResult result = securityAnalysis.runSync(saParameters, contingenciesProvider);
        assertTrue(result.getPreContingencyResult().isComputationOk());
        assertEquals(0, result.getPreContingencyResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(3, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
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
                .setSlackBusSelector(new NameSlackBusSelector("VL1_1"));
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Stream.of("L1", "L2")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        OpenSecurityAnalysisFactory osaFactory = new OpenSecurityAnalysisFactory();
        OpenSecurityAnalysis securityAnalysis = osaFactory.create(network, new LimitViolationFilter(), null, 0);

        SecurityAnalysisResult result = securityAnalysis.runSync(saParameters, contingenciesProvider);
        assertTrue(result.getPreContingencyResult().isComputationOk());
        assertEquals(2, result.getPreContingencyResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(1, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertTrue(result.getPostContingencyResults().get(1).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());

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
            .setSlackBusSelector(new MostMeshedSlackBusSelector());
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        // Testing all contingencies at once
        ContingenciesProvider contingenciesProvider = n -> n.getBranchStream()
            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
            .collect(Collectors.toList());

        OpenSecurityAnalysisFactory osaFactory = new OpenSecurityAnalysisFactory(new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);
        OpenSecurityAnalysis securityAnalysis = osaFactory.create(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), null, 0);

        SecurityAnalysisResult result = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertTrue(result.getPreContingencyResult().isComputationOk());

        saParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        SecurityAnalysisResult result2 = securityAnalysis.runSync(saParameters, contingenciesProvider);
        assertTrue(result2.getPreContingencyResult().isComputationOk());
    }

    @Test
    void testNoGenerator() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").getTerminal().disconnect();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        saParameters.setLoadFlowParameters(new LoadFlowParameters());

        ContingenciesProvider contingenciesProvider = n -> Collections.emptyList();

        CompletableFuture<SecurityAnalysisResult> result = new OpenSecurityAnalysisFactory(new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new)
                .create(network, new DefaultLimitViolationDetector(), new LimitViolationFilter(), null, 0)
                .run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider);

        CompletionException exception = assertThrows(CompletionException.class, result::join);
        assertEquals("Largest network is invalid", exception.getCause().getMessage());
    }

    @Test
    void testNoRemainingGenerator() {

        Network network = EurostagTutorialExample1Factory.create();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        ContingenciesProvider contingenciesProvider = net -> Stream.of("NGEN_NHV1")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        OpenSecurityAnalysisFactory osaFactory = new OpenSecurityAnalysisFactory(new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);
        OpenSecurityAnalysis securityAnalysis = osaFactory.create(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), null, 0);
        SecurityAnalysisResult result = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
    }

    @Test
    void testNoRemainingLoad() {

        Network network = EurostagTutorialExample1Factory.create();

        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        lfParameters.setDistributedSlack(true).setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);

        ContingenciesProvider contingenciesProvider = net -> Stream.of("NHV2_NLOAD")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        OpenSecurityAnalysisFactory osaFactory = new OpenSecurityAnalysisFactory(new DenseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);
        OpenSecurityAnalysis securityAnalysis = osaFactory.create(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), null, 0);
        SecurityAnalysisResult result = securityAnalysis.run(network.getVariantManager().getWorkingVariantId(), saParameters, contingenciesProvider).join();
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
    }
}
