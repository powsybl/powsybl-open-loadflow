/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.AbstractConverterTest;
import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.GeneratorContingency;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.security.LimitViolationFilter;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
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
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class LfContingencyTest extends AbstractConverterTest {

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
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);
        assertEquals(2, lfNetworks.size());

        Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider = EvenShiloachGraphDecrementalConnectivity::new;
        new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), connectivityProvider, Collections.emptyList());

        String branchId = "LINE_S3S4";
        Contingency contingency = new Contingency(branchId, new BranchContingency(branchId));
        List<PropagatedContingency> propagatedContingencies =
            PropagatedContingency.createListForSecurityAnalysis(network, Collections.singletonList(contingency), new HashSet<>(), false);

        List<LfContingency> lfContingencies = propagatedContingencies.stream()
                .flatMap(propagatedContingency -> LfContingency.create(propagatedContingency, mainNetwork, mainNetwork.createDecrementalConnectivity(connectivityProvider), true).stream())
                .collect(Collectors.toList());
        assertEquals(1, lfContingencies.size());

        Path file = fileSystem.getPath("/work/lfc.json");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            lfContingencies.get(0).writeJson(writer);
        }

        try (InputStream is = Files.newInputStream(file)) {
            compareTxt(getClass().getResourceAsStream("/lfc.json"), is);
        }
    }

    @Test
    void testGeneratorNotFound() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);
        assertEquals(2, lfNetworks.size());

        Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider = EvenShiloachGraphDecrementalConnectivity::new;
        new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), new DenseMatrixFactory(), connectivityProvider, Collections.emptyList());

        String generatorId = "GEN";
        Contingency contingency = new Contingency(generatorId, new GeneratorContingency(generatorId));
        assertThrows(PowsyblException.class, () ->
                        PropagatedContingency.createListForSecurityAnalysis(network, Collections.singletonList(contingency), new HashSet<>(), false),
                "Generator 'GEN' not found in the network");
    }

    @Test
    void testLoadNotFound() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);
        assertEquals(2, lfNetworks.size());

        Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider = EvenShiloachGraphDecrementalConnectivity::new;
        new AcSecurityAnalysis(network, new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), new DenseMatrixFactory(), connectivityProvider, Collections.emptyList());

        String loadId = "LOAD";
        Contingency contingency = new Contingency(loadId, new LoadContingency(loadId));
        assertThrows(PowsyblException.class, () ->
                        PropagatedContingency.createListForSecurityAnalysis(network, Collections.singletonList(contingency), new HashSet<>(), false),
                "Load 'LOAD' not found in the network");
    }
}
