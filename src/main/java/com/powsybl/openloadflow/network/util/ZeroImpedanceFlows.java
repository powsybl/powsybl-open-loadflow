/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

/**
 * @author Luma Zamarreño <zamarrenolm at aia.es>
 * @author José Antonio Marqués <marquesja at aia.es>
 */
public class ZeroImpedanceFlows {

    private final Graph<LfBus, LfBranch> graph;
    private final SpanningTreeAlgorithm.SpanningTree<LfBranch> tree;

    public ZeroImpedanceFlows(Graph<LfBus, LfBranch> zeroImpedanceSubGraph,
        SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree) {

        this.graph = zeroImpedanceSubGraph;
        this.tree = spanningTree;
    }

    public void compute(boolean dc) {
        Set<LfBus> processed = new HashSet<>();

        graph.vertexSet().forEach(lfBus -> {
            if (processed.contains(lfBus)) {
                return;
            }
            TreeByLevels treeByLevels = new TreeByLevels(graph, tree, lfBus);
            treeByLevels.updateFlows(dc);
            processed.addAll(treeByLevels.getProcessedLfBuses());
        });

        // Zero flow for all zero impedance branches outside the tree
        List<LfBranch> branches = graph.edgeSet().stream().filter(branch -> !tree.getEdges().contains(branch))
            .collect(Collectors.toList());
        branches.forEach(branch -> branch.updateFlow(0.0, 0.0, 0.0, 0.0));
    }

    private static final class TreeByLevels {
        private final LfBus lfBus;
        private final List<List<LfBus>> levels;
        private final Map<LfBus, LfBranch> parent;
        private final List<LfBus> processedLfBuses;

        private TreeByLevels(Graph<LfBus, LfBranch> graph, SpanningTreeAlgorithm.SpanningTree<LfBranch> tree, LfBus lfBus) {
            this.lfBus = lfBus;
            levels = new ArrayList<>();
            parent = new HashMap<>();
            processedLfBuses = new ArrayList<>();

            createTreeByLevels(graph, tree);
        }

        private List<LfBus> getProcessedLfBuses() {
            return processedLfBuses;
        }

        private void createTreeByLevels(Graph<LfBus, LfBranch> graph, SpanningTreeAlgorithm.SpanningTree<LfBranch> tree) {

            // set the root
            levels.add(new ArrayList<>(Collections.singleton(lfBus)));
            processedLfBuses.add(lfBus);

            int level = 0;
            while (level < levels.size()) {
                List<LfBus> nextLevel = new ArrayList<>();

                for (LfBus bus : levels.get(level)) {
                    List<LfBranch> childrenBranches = graph.edgesOf(bus).stream()
                        .filter(branch -> tree.getEdges().contains(branch) && !isParentBranch(parent, bus, branch))
                        .collect(Collectors.toList());

                    childrenBranches.forEach(branch -> {
                        LfBus otherBus = otherBus(branch, bus);
                        nextLevel.add(otherBus);
                        parent.put(otherBus, branch);
                    });
                }
                if (!nextLevel.isEmpty()) {
                    levels.add(nextLevel);
                    processedLfBuses.addAll(nextLevel);
                }
                level++;
            }
        }

        private static boolean isParentBranch(Map<LfBus, LfBranch> parent, LfBus bus, LfBranch branch) {
            return parent.containsKey(bus) && parent.get(bus).equals(branch);
        }

        private static LfBus otherBus(LfBranch branch, LfBus bus) {
            return branch.getBus1().equals(bus) ? branch.getBus2() : branch.getBus1();
        }

        private void updateFlows(boolean dc) {
            Map<LfBus, PQ> descendentZeroImpedanceFlow = new HashMap<>();

            // Traverse the tree from leaves to root
            // (The root itself does not need to be processed)
            int level = levels.size() - 1;
            while (level >= 1) {
                levels.get(level).forEach(bus -> {
                    PQ balance = balanceWithImpedance(bus, dc);
                    PQ z0flow = getDescendentZeroImpedanceFlow(descendentZeroImpedanceFlow, bus);
                    PQ branchFlow = balance.add(z0flow);

                    LfBranch branch = parent.get(bus);
                    updateBranchFlow(branch, bus, branchFlow.negate(), branchFlow);
                    descendentZeroImpedanceFlow.merge(otherBus(branch, bus), branchFlow, (pq1, pq2) -> pq1.add(pq2));
                });
                level--;
            }
        }

        // Balance considering injections and flow from lines with impedance
        private PQ balanceWithImpedance(LfBus bus, boolean dc) {
            // take care of the sign
            PQ balancePQ = new PQ(-bus.getP().eval(), -bus.getQ().eval());

            // Only lines with impedance
            List<LfBranch> adjacentBranchesWithImpedance = bus.getBranches().stream()
                .filter(branch -> !branch.isZeroImpedanceBranch(dc)).collect(Collectors.toList());

            adjacentBranchesWithImpedance.forEach(branch -> {
                PQ branchFlow = getBranchFlow(branch, bus);
                balancePQ.p += branchFlow.p;
                balancePQ.q += branchFlow.q;
            });

            return balancePQ;
        }

        private PQ getDescendentZeroImpedanceFlow(Map<LfBus, PQ> descendentZeroImpedanceFlow, LfBus bus) {
            return descendentZeroImpedanceFlow.containsKey(bus) ? descendentZeroImpedanceFlow.get(bus) : new PQ(0.0, 0.0);
        }

        private void updateBranchFlow(LfBranch branch, LfBus bus, PQ pqBus, PQ pqOtherBus) {
            if (branch.getBus1().equals(bus)) {
                branch.updateFlow(pqBus.p, pqBus.q, pqOtherBus.p, pqOtherBus.q);
            } else {
                branch.updateFlow(pqOtherBus.p, pqOtherBus.q, pqBus.p, pqBus.q);
            }
        }

        private PQ getBranchFlow(LfBranch branch, LfBus bus) {
            return branch.getBus1().equals(bus) ? new PQ(branch.getP1().eval(), branch.getQ1().eval())
                : new PQ(branch.getP2().eval(), branch.getQ2().eval());
        }
    }

    private static final class PQ {
        private double p;
        private double q;

        private PQ(double p, double q) {
            this.p = p;
            this.q = q;
        }

        private PQ add(PQ pq) {
            return new PQ(this.p + pq.p, this.q + pq.q);
        }

        private PQ negate() {
            return new PQ(-this.p, -this.q);
        }
    }
}
