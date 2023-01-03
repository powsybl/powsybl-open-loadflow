/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.test.AbstractConverterTest;
import com.powsybl.commons.test.ComparisonUtils;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.GeneratorContingency;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
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

        GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();

        List<LfNetwork> lfNetworks = Networks.load(network, new LfNetworkParameters()
                .setConnectivityFactory(connectivityFactory)
                .setSlackBusSelector(new MostMeshedSlackBusSelector()));
        LfNetwork mainNetwork = lfNetworks.get(0);
        assertEquals(2, lfNetworks.size());

        new AcSecurityAnalysis(network, new DenseMatrixFactory(), connectivityFactory, Collections.emptyList(), Reporter.NO_OP);

        String branchId = "LINE_S3S4";
        Contingency contingency = new Contingency(branchId, new BranchContingency(branchId));
        List<PropagatedContingency> propagatedContingencies =
            PropagatedContingency.createList(network, Collections.singletonList(contingency), new HashSet<>(), false, false, false, true);

        List<LfContingency> lfContingencies = propagatedContingencies.stream()
                .flatMap(propagatedContingency -> propagatedContingency.toLfContingency(mainNetwork).stream())
                .collect(Collectors.toList());
        assertEquals(1, lfContingencies.size());

        Path file = fileSystem.getPath("/work/lfc.json");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            lfContingencies.get(0).writeJson(writer);
        }

        try (InputStream is = Files.newInputStream(file)) {
            ComparisonUtils.compareTxt(getClass().getResourceAsStream("/lfc.json"), is);
        }
    }

    @Test
    void testGeneratorNotFound() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(2, lfNetworks.size());

        GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();
        new AcSecurityAnalysis(network, new DenseMatrixFactory(), connectivityFactory, Collections.emptyList(), Reporter.NO_OP);

        String generatorId = "GEN";
        Contingency contingency = new Contingency(generatorId, new GeneratorContingency(generatorId));
        assertThrows(PowsyblException.class, () ->
                        PropagatedContingency.createList(network, Collections.singletonList(contingency), new HashSet<>(), false, false, false, true),
                "Generator 'GEN' not found in the network");
    }

    @Test
    void testLoadNotFound() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(2, lfNetworks.size());

        GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();
        new AcSecurityAnalysis(network, new DenseMatrixFactory(), connectivityFactory, Collections.emptyList(), Reporter.NO_OP);

        String loadId = "LOAD";
        Contingency contingency = new Contingency(loadId, new LoadContingency(loadId));
        assertThrows(PowsyblException.class, () ->
                        PropagatedContingency.createList(network, Collections.singletonList(contingency), new HashSet<>(), false, false, false, true),
                "Load 'LOAD' not found in the network");
    }
}
