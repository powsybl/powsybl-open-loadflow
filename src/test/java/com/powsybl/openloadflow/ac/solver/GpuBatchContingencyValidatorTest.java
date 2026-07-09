/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.ac.solver.GpuBatchContingencyValidator.Disqualification;
import com.powsybl.openloadflow.ac.solver.GpuBatchContingencyValidator.Qualification;
import com.powsybl.openloadflow.ac.solver.GpuBatchedSecurityAnalysisSolver.BatchContingency;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates {@link GpuBatchContingencyValidator} qualification — a CPU-only graph check,
 * so this runs in the normal test suite (no GPU needed). Eurostag is NGEN—NHV1—(two
 * parallel lines)—NHV2—NLOAD: the parallel lines are meshed (removing one keeps the main
 * component intact → qualifies for the batch), while the two transformers are bridges
 * (removing either islands a bus → rejected, route to the CPU path).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchContingencyValidatorTest {

    private LfNetwork eurostag() {
        Network iidm = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        return Networks.load(iidm, new FirstSlackBusSelector()).get(0);
    }

    private static BatchContingency contingency(LfNetwork network, String branchId) {
        LfBranch branch = network.getBranchById(branchId);
        return new BatchContingency(branchId, branch);
    }

    @Test
    void parallelLineOutageQualifies() {
        LfNetwork network = eurostag();
        GpuBatchElementMapper mapper = new GpuBatchElementMapper(network);
        List<Qualification> q = GpuBatchContingencyValidator.qualify(network,
                List.of(contingency(network, "NHV1_NHV2_1")), mapper, false);

        assertEquals(1, q.size());
        assertTrue(q.get(0).qualified(), "removing one of two parallel lines keeps the network connected");
    }

    @Test
    void bridgeOutageIsRejected() {
        LfNetwork network = eurostag();
        GpuBatchElementMapper mapper = new GpuBatchElementMapper(network);
        // NGEN_NHV1 is the only path to the generator bus NGEN — a bridge. With islanding handling OFF
        // (fixed-iter mode) it is rejected to the CPU path.
        List<Qualification> q = GpuBatchContingencyValidator.qualify(network,
                List.of(contingency(network, "NGEN_NHV1")), mapper, false);

        assertEquals(1, q.size());
        assertFalse(q.get(0).qualified(), "removing a bridge transformer islands a bus");
        assertEquals(Disqualification.FRAGMENTS_NETWORK, q.get(0).reason());
    }

    @Test
    void mixedListPartitionsCorrectly() {
        LfNetwork network = eurostag();
        GpuBatchElementMapper mapper = new GpuBatchElementMapper(network);
        List<Qualification> q = GpuBatchContingencyValidator.qualify(network, List.of(
                contingency(network, "NHV1_NHV2_1"),    // meshed → qualify
                contingency(network, "NGEN_NHV1"),       // bridge → reject
                contingency(network, "NHV1_NHV2_2"),     // meshed → qualify
                contingency(network, "NHV2_NLOAD")),     // bridge → reject
                mapper, false);

        assertEquals(4, q.size());
        assertTrue(q.get(0).qualified());
        assertFalse(q.get(1).qualified());
        assertEquals(Disqualification.FRAGMENTS_NETWORK, q.get(1).reason());
        assertTrue(q.get(2).qualified());
        assertFalse(q.get(3).qualified());
        assertEquals(Disqualification.FRAGMENTS_NETWORK, q.get(3).reason());

        // The connectivity must be fully restored after qualification (temporary changes undone):
        // re-qualifying yields the same verdicts.
        List<Qualification> again = GpuBatchContingencyValidator.qualify(network,
                List.of(contingency(network, "NHV1_NHV2_1")), mapper, false);
        assertTrue(again.get(0).qualified(), "connectivity must be restored after each qualification");
    }

    @Test
    void bridgeOutageWithIslandingHandlingQualifies() {
        LfNetwork network = eurostag();
        GpuBatchElementMapper mapper = new GpuBatchElementMapper(network);
        // NHV2_NLOAD bridges the load bus NLOAD (a leaf, not the slack). With islanding handling ON
        // (full-loadflow mode) the outage QUALIFIES and carries its islanded bus(es), to be frozen
        // per scenario rather than routed to the CPU.
        List<Qualification> q = GpuBatchContingencyValidator.qualify(network,
                List.of(contingency(network, "NHV2_NLOAD")), mapper, true);

        assertEquals(1, q.size());
        assertTrue(q.get(0).qualified(), "with islanding handling on, a fragmenting outage is batched on-GPU");
        assertFalse(q.get(0).islandedBuses().isEmpty(), "the islanded bus set must be reported for freezing");
    }
}
