/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Qualifies single-branch contingencies for the batched GPU N-1 path. The batched solver
 * runs plain Newton-Raphson over the FIXED superset pattern with the outaged branch's
 * contributions zeroed — this is only valid when the outage keeps the main synchronous
 * component intact (same buses, same slack/PV roles). A contingency that FRAGMENTS the
 * network (its branch is a bridge, so removing it islands buses from the main component)
 * cannot be represented as a value-only mask over the base pattern: the islanded buses
 * would be left with a structurally singular sub-block and a meaningless post-contingency
 * state. Those contingencies are REJECTED here (loudly), to be handled by the sequential
 * CPU security-analysis path instead of being silently mis-solved.
 *
 * <p>Qualification uses {@link LfNetwork#getConnectivity()}: for each closed-branch
 * contingency, a temporary edge removal reveals whether any vertex leaves the main
 * component. Open-branch contingencies (one side already disconnected) carry no flow on
 * the open side and cannot fragment the network, so they qualify trivially; a branch the
 * GPU element model does not contain is rejected (it cannot be masked).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class GpuBatchContingencyValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpuBatchContingencyValidator.class);

    public enum Disqualification {
        FRAGMENTS_NETWORK,      // removing the branch islands buses (only when islanding handling is OFF)
        ISLANDS_SLACK,          // the outage islands the slack/reference bus (no on-GPU reference left)
        NOT_IN_GPU_MODEL        // the branch is not a closed/open branch the GPU pack contains
    }

    /**
     * The outcome of qualifying one contingency: either qualified (batchable on the GPU), or
     * disqualified with a reason (route to the CPU path). A qualified contingency that fragments the
     * network carries its {@code islandedBuses} (empty for a non-fragmenting outage) — those buses are
     * frozen to identity (held at base) in that scenario's per-scenario row mode, so the islanded
     * sub-block stays non-singular and the main component solves correctly.
     */
    public record Qualification(GpuBatchedSecurityAnalysisSolver.BatchContingency contingency,
                                boolean qualified, Disqualification reason, List<LfBus> islandedBuses) {

        static Qualification ok(GpuBatchedSecurityAnalysisSolver.BatchContingency c) {
            return new Qualification(c, true, null, List.of());
        }

        static Qualification okIslanding(GpuBatchedSecurityAnalysisSolver.BatchContingency c, List<LfBus> islandedBuses) {
            return new Qualification(c, true, null, List.copyOf(islandedBuses));
        }

        static Qualification rejected(GpuBatchedSecurityAnalysisSolver.BatchContingency c, Disqualification reason) {
            return new Qualification(c, false, reason, List.of());
        }
    }

    private GpuBatchContingencyValidator() {
    }

    /**
     * Qualify each contingency for the batched GPU path.
     *
     * @param network          the base-case AC network
     * @param contingencies    single-branch contingencies to qualify
     * @param elementMapper    maps an {@link LfBranch} to its GPU element index (-1 = absent)
     * @param handleIslanding  when true (full-loadflow mode), a fragmenting outage is QUALIFIED and
     *                         carries its islanded-bus set (frozen per scenario); when false
     *                         (fixed-iter benchmark mode) it is rejected to CPU
     * @return one {@link Qualification} per input contingency, in order
     */
    public static List<Qualification> qualify(LfNetwork network,
                                              List<GpuBatchedSecurityAnalysisSolver.BatchContingency> contingencies,
                                              GpuBatchElementMapper elementMapper,
                                              boolean handleIslanding) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(elementMapper);

        GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();
        Set<LfBus> slackBuses = Set.copyOf(network.getSlackBuses());
        return contingencies.stream()
                .map(c -> qualifyOne(connectivity, slackBuses, c, elementMapper, handleIslanding))
                .toList();
    }

    private static Qualification qualifyOne(GraphConnectivity<LfBus, LfBranch> connectivity,
                                           Set<LfBus> slackBuses,
                                           GpuBatchedSecurityAnalysisSolver.BatchContingency contingency,
                                           GpuBatchElementMapper elementMapper,
                                           boolean handleIslanding) {
        List<LfBranch> branches = contingency.outagedBranches();

        // The GPU disabled mask can only mask branches the element pack contains.
        for (LfBranch branch : branches) {
            if (elementMapper.getElementIndex(branch) < 0) {
                LOGGER.debug("Contingency {} rejected: branch not in GPU element model", contingency.contingencyId());
                return Qualification.rejected(contingency, Disqualification.NOT_IN_GPU_MODEL);
            }
        }

        // Only the closed branches (both sides) are connectivity edges; an open-ended branch carries no flow
        // on its open side and cannot fragment the network. Remove every closed outaged branch as a temporary
        // edge (N-2 removes both at once) and check whether any vertex leaves the main component.
        List<LfBranch> closed = branches.stream()
                .filter(b -> b.getBus1() != null && b.getBus2() != null).toList();
        if (closed.isEmpty()) {
            return Qualification.ok(contingency);
        }
        connectivity.startTemporaryChanges();
        try {
            for (LfBranch branch : closed) {
                connectivity.removeEdge(branch);
            }
            Set<LfBus> islanded = connectivity.getVerticesRemovedFromMainComponent();
            if (islanded.isEmpty()) {
                return Qualification.ok(contingency);
            }
            if (!handleIslanding) {                          // fixed-iter mode: route to CPU
                LOGGER.debug("Contingency {} rejected: outage islands {} bus(es) (islanding handling off)",
                        contingency.contingencyId(), islanded.size());
                return Qualification.rejected(contingency, Disqualification.FRAGMENTS_NETWORK);
            }
            // The main component must keep a slack/reference: if the outage islands the slack itself,
            // the remaining system has no on-GPU reference — route that one to the CPU path.
            for (LfBus b : islanded) {
                if (slackBuses.contains(b)) {
                    LOGGER.debug("Contingency {} rejected: outage islands the slack bus", contingency.contingencyId());
                    return Qualification.rejected(contingency, Disqualification.ISLANDS_SLACK);
                }
            }
            LOGGER.debug("Contingency {} qualified with {} islanded bus(es) (frozen per scenario)",
                    contingency.contingencyId(), islanded.size());
            return Qualification.okIslanding(contingency, List.copyOf(islanded));
        } finally {
            connectivity.undoTemporaryChanges();
        }
    }
}
