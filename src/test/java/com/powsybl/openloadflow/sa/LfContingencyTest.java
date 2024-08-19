/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.AbstractSerDeTest;
import com.powsybl.commons.test.ComparisonUtils;
import com.powsybl.contingency.*;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.BatteryNetworkFactory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.network.impl.PropagatedContingency.createList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class LfContingencyTest extends AbstractSerDeTest {

    @Override
    @BeforeEach
    public void setUp() throws IOException {
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        super.tearDown();
    }

    @Test
    void test() throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();

        GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();

        List<LfNetwork> lfNetworks = Networks.load(network, new LfNetworkParameters()
                .setConnectivityFactory(connectivityFactory)
                .setSlackBusSelector(new MostMeshedSlackBusSelector()));
        LfNetwork mainNetwork = lfNetworks.get(0);
        assertEquals(2, lfNetworks.size());

        new AcSecurityAnalysis(network, new DenseMatrixFactory(), connectivityFactory, Collections.emptyList(), ReportNode.NO_OP);

        String branchId = "LINE_S3S4";
        Contingency contingency = new Contingency(branchId, new BranchContingency(branchId));
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
                .setContingencyPropagation(false)
                .setHvdcAcEmulation(false);
        List<PropagatedContingency> propagatedContingencies =
            createList(network, Collections.singletonList(contingency), new LfTopoConfig(), creationParameters);

        List<LfContingency> lfContingencies = propagatedContingencies.stream()
                .flatMap(propagatedContingency -> propagatedContingency.toLfContingency(mainNetwork).stream())
                .collect(Collectors.toList());
        assertEquals(1, lfContingencies.size());

        Path file = fileSystem.getPath("/work/lfc.json");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            lfContingencies.get(0).writeJson(writer);
        }

        try (InputStream is = Files.newInputStream(file)) {
            ComparisonUtils.assertTxtEquals(Objects.requireNonNull(getClass().getResourceAsStream("/lfc.json")), is);
        }
    }

    @Test
    void testBatteryContingencyPropagated() {
        Network network = BatteryNetworkFactory.create();
        ContingencyElement element = new BatteryContingency("BAT");
        Contingency contingency = new Contingency("contingencyId", "contingencyName", List.of(element));
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters();
        List<PropagatedContingency> propagatedContingencies = createList(network, List.of(contingency), new LfTopoConfig(), creationParameters);
        assertEquals(1, propagatedContingencies.size());
        assertEquals("contingencyId", propagatedContingencies.get(0).getContingency().getId());
    }

    @Test
    void testGeneratorNotFound() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(2, lfNetworks.size());

        GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();
        new AcSecurityAnalysis(network, new DenseMatrixFactory(), connectivityFactory, Collections.emptyList(), ReportNode.NO_OP);

        String generatorId = "GEN";
        Contingency contingency = new Contingency(generatorId, new GeneratorContingency(generatorId));
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
                .setHvdcAcEmulation(false);
        assertThrows(PowsyblException.class, () ->
                        createList(network, Collections.singletonList(contingency), new LfTopoConfig(), creationParameters),
                "Generator 'GEN' not found in the network");
    }

    @Test
    void testLoadNotFound() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(2, lfNetworks.size());

        GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();
        new AcSecurityAnalysis(network, new DenseMatrixFactory(), connectivityFactory, Collections.emptyList(), ReportNode.NO_OP);

        String loadId = "LOAD";
        Contingency contingency = new Contingency(loadId, new LoadContingency(loadId));
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
                .setHvdcAcEmulation(false);
        assertThrows(PowsyblException.class, () ->
                        createList(network, Collections.singletonList(contingency), new LfTopoConfig(), creationParameters),
                "Load 'LOAD' not found in the network");
    }

    @Test
    void testOpenBranchOutOfMainComponentIssue() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT3wt();
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters().setBreakers(true)).get(0);
        Contingency contingency = Contingency.threeWindingsTransformer("T3wT");
        PropagatedContingency propagatedContingency = createList(network, List.of(contingency), new LfTopoConfig(), new PropagatedContingencyCreationParameters()).get(0);
        LfContingency lfContingency = propagatedContingency.toLfContingency(lfNetwork).orElseThrow();
        assertEquals(Map.of(lfNetwork.getBranchById("T3wT_leg_1"), DisabledBranchStatus.BOTH_SIDES,
                            lfNetwork.getBranchById("T3wT_leg_2"), DisabledBranchStatus.BOTH_SIDES,
                            lfNetwork.getBranchById("T3wT_leg_3"), DisabledBranchStatus.BOTH_SIDES),
                lfContingency.getDisabledNetwork().getBranchesStatus());
    }
}
