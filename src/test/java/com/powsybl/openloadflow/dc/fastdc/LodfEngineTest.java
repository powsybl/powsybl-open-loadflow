/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PhaseControlFactory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
class LodfEngineTest {

    private static final double DELTA_LODF = 1e-6;

    private final DcLoadFlowParameters dcParameters = new DcLoadFlowParameters();

    private Map<String, Double> calculateFlows(Network network) {
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), dcParameters.getNetworkParameters()).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            new DcLoadFlowEngine(context).run();
            Map<String, Double> flows = new HashMap<>();
            for (LfBranch branch : lfNetwork.getBranches()) {
                flows.put(branch.getId(), branch.getP1().eval());
            }
            return flows;
        }
    }

    private LodfResult run(Network network, List<String> monitoredIds, List<String> outagedIds, LodfParameters parameters) {
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), dcParameters.getNetworkParameters()).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            List<LfBranch> monitored = monitoredIds.stream().map(lfNetwork::getBranchById).toList();
            List<LfBranch> outaged = outagedIds.stream().map(lfNetwork::getBranchById).toList();
            return LodfEngine.run(context, monitored, outaged, parameters);
        }
    }

    private LodfResult run(Network network, List<String> branchIds, double threshold) {
        return run(network, branchIds, branchIds, new LodfParameters().setThreshold(threshold));
    }

    /**
     * Checks every LODF against (post-outage flow - pre-outage flow) / pre-outage flow of the outaged branch, from DC load flows.
     */
    private void checkAgainstPostOutageFlows(Supplier<Network> networkSupplier, List<String> branchIds) {
        Map<String, Double> preFlows = calculateFlows(networkSupplier.get());
        LodfResult result = run(networkSupplier.get(), branchIds, 0);
        assertTrue(result.undefinedOutageIndices().isEmpty());
        DenseMatrix lodf = result.lodfMatrix().toDense();
        for (int column = 0; column < branchIds.size(); column++) {
            String outagedId = branchIds.get(column);
            Network postOutageNetwork = networkSupplier.get();
            Branch<?> outaged = postOutageNetwork.getBranch(outagedId);
            outaged.getTerminal1().disconnect();
            outaged.getTerminal2().disconnect();
            Map<String, Double> postFlows = calculateFlows(postOutageNetwork);
            for (int row = 0; row < branchIds.size(); row++) {
                String monitoredId = branchIds.get(row);
                double expected = row == column ? -1d
                        : (postFlows.get(monitoredId) - preFlows.get(monitoredId)) / preFlows.get(outagedId);
                assertEquals(expected, lodf.get(row, column), DELTA_LODF, "LODF of '" + monitoredId + "' for outage of '" + outagedId + "'");
            }
        }
    }

    @Test
    void testAgainstPostOutageFlows() {
        checkAgainstPostOutageFlows(FourBusNetworkFactory::create, List.of("l14", "l12", "l23", "l34", "l13"));
    }

    @Test
    void testWithPhaseShift() {
        Supplier<Network> networkSupplier = () -> {
            Network network = PhaseControlFactory.createWithOneT2wtTwoLines();
            network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(2);
            return network;
        };
        checkAgainstPostOutageFlows(networkSupplier, List.of("L1", "L2", "PS1"));
    }

    @Test
    void testThreshold() {
        List<String> ids = List.of("l14", "l12", "l23", "l34", "l13");
        DenseMatrix reference = run(FourBusNetworkFactory.create(), ids, 0).lodfMatrix().toDense();
        double threshold = 0.4;
        SparseMatrix sparse = run(FourBusNetworkFactory.create(), ids, threshold).lodfMatrix();
        DenseMatrix kept = sparse.toDense();
        int expectedCount = 0;
        for (int row = 0; row < ids.size(); row++) {
            for (int column = 0; column < ids.size(); column++) {
                double expected = Math.abs(reference.get(row, column)) >= threshold ? reference.get(row, column) : 0;
                assertEquals(expected, kept.get(row, column), 1e-12);
                if (expected != 0) {
                    expectedCount++;
                }
            }
        }
        assertEquals(expectedCount, sparse.getValueCount());
        assertTrue(expectedCount > 0 && expectedCount < ids.size() * ids.size());
    }

    @Test
    void testMaxFactorsPerOutage() {
        List<String> ids = List.of("l14", "l12", "l23", "l34", "l13");
        DenseMatrix reference = run(FourBusNetworkFactory.create(), ids, 0).lodfMatrix().toDense();
        for (int maxFactors : new int[] {0, 2}) {
            LodfParameters parameters = new LodfParameters().setThreshold(0).setMaxFactorsPerOutage(maxFactors);
            DenseMatrix kept = run(FourBusNetworkFactory.create(), ids, ids, parameters).lodfMatrix().toDense();
            for (int column = 0; column < ids.size(); column++) {
                int c = column;
                // the -1 of the outaged branch, plus its maxFactors largest other factors
                List<Integer> expectedRows = IntStream.range(0, ids.size()).filter(row -> row != c).boxed()
                        .sorted(Comparator.comparingDouble(row -> -Math.abs(reference.get(row, c))))
                        .limit(maxFactors)
                        .toList();
                for (int row = 0; row < ids.size(); row++) {
                    double expected = row == column || expectedRows.contains(row) ? reference.get(row, column) : 0;
                    assertEquals(expected, kept.get(row, column), 0);
                }
            }
        }
    }

    @Test
    void testOutageBatchingGivesSameResult() {
        List<String> ids = List.of("l14", "l12", "l23", "l34", "l13");
        LodfResult reference = run(FourBusNetworkFactory.create(), ids, 0);
        for (int batchSize : new int[] {1, 2, 3, Integer.MAX_VALUE}) {
            assertEquals(reference, run(FourBusNetworkFactory.create(), ids, ids, new LodfParameters().setThreshold(0).setOutageBatchSize(batchSize)));
        }
    }

    @Test
    void testParallelLinesAndConnectivityBreak() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        LodfResult result = run(network, List.of("NHV1_NHV2_1", "NHV1_NHV2_2", "NGEN_NHV1", "NHV2_NLOAD"), 0);
        DenseMatrix lodf = result.lodfMatrix().toDense();
        assertEquals(1d, lodf.get(1, 0), DELTA_LODF);
        assertEquals(1d, lodf.get(0, 1), DELTA_LODF);
        assertEquals(0d, lodf.get(2, 0), DELTA_LODF);
        assertEquals(0d, lodf.get(3, 0), DELTA_LODF);
        assertEquals(List.of(2, 3), result.undefinedOutageIndices());
        for (int row = 0; row < 4; row++) {
            assertEquals(0d, lodf.get(row, 2));
            assertEquals(0d, lodf.get(row, 3));
        }
    }

    @Test
    void testOpenBranch() {
        Network network = FourBusNetworkFactory.create();
        network.getLine("l23").getTerminal1().disconnect();
        LodfResult result = run(network, List.of("l14", "l23", "l13"), 0);
        DenseMatrix lodf = result.lodfMatrix().toDense();
        assertEquals(List.of(1), result.undefinedOutageIndices());
        for (int column = 0; column < 3; column++) {
            assertEquals(0d, lodf.get(1, column));
        }
    }

    @Test
    void testInvalidInputs() {
        Network network = FourBusNetworkFactory.create();
        List<String> ids = List.of("l12");
        List<String> none = List.of();
        LodfParameters parameters = new LodfParameters();
        PowsyblException e = assertThrows(PowsyblException.class, () -> run(network, none, ids, parameters));
        assertEquals("LODF computation needs at least one monitored branch and one outaged branch", e.getMessage());
        e = assertThrows(PowsyblException.class, () -> run(network, ids, none, parameters));
        assertEquals("LODF computation needs at least one monitored branch and one outaged branch", e.getMessage());
        e = assertThrows(PowsyblException.class, () -> parameters.setThreshold(-1));
        assertEquals("LODF threshold must be positive or zero: -1.0", e.getMessage());
        e = assertThrows(PowsyblException.class, () -> parameters.setThreshold(Double.NaN));
        assertEquals("LODF threshold must be positive or zero: NaN", e.getMessage());
        e = assertThrows(PowsyblException.class, () -> parameters.setMaxFactorsPerOutage(-1));
        assertEquals("LODF max factors per outage must be positive or zero: -1", e.getMessage());
        e = assertThrows(PowsyblException.class, () -> parameters.setOutageBatchSize(0));
        assertEquals("LODF outage batch size must be strictly positive: 0", e.getMessage());
    }
}
