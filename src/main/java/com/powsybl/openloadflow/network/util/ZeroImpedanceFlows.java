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

import com.powsybl.openloadflow.network.LoadFlowModel;
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
    private final LoadFlowModel loadFlowModel;

    public ZeroImpedanceFlows(Graph<LfBus, LfBranch> zeroImpedanceSubGraph, SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree,
                              LoadFlowModel loadFlowModel) {
        this.graph = zeroImpedanceSubGraph;
        this.tree = spanningTree;
        this.loadFlowModel = loadFlowModel;
    }

    public void compute() {
        Set<LfBus> processed = new HashSet<>();

        graph.vertexSet().forEach(lfBus -> {
            if (processed.contains(lfBus)) {
                return;
            }
            TreeByLevels treeByLevels = new TreeByLevels(graph, tree, lfBus);
            treeByLevels.updateFlows(loadFlowModel);
            processed.addAll(treeByLevels.getProcessedLfBuses());
        });

        // zero flows for all zero impedance branches outside the tree
        graph.edgeSet().stream().filter(branch -> !tree.getEdges().contains(branch))
                .forEach(branch -> branch.updateFlows(0.0, 0.0, 0.0, 0.0));
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
                    graph.edgesOf(bus).stream()
                            .filter(branch -> tree.getEdges().contains(branch) && !isParentBranch(parent, bus, branch))
                            .forEach(childrenBranch -> {
                                LfBus otherBus = getOtherSideBus(childrenBranch, bus);
                                nextLevel.add(otherBus);
                                parent.put(otherBus, childrenBranch);
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

        private static LfBus getOtherSideBus(LfBranch branch, LfBus bus) {
            return branch.getBus1().equals(bus) ? branch.getBus2() : branch.getBus1();
        }

        private void updateFlows(LoadFlowModel loadFlowModel) {
            Map<LfBus, PQ> descendantZeroImpedanceFlow = new HashMap<>();

            // traverse the tree from leaves to root
            // (The root itself does not need to be processed)
            int level = levels.size() - 1;
            while (level >= 1) {
                levels.get(level).forEach(bus -> {
                    PQ balance = balanceWithImpedance(bus, loadFlowModel);
                    PQ z0flow = getDescendantZeroImpedanceFlow(descendantZeroImpedanceFlow, bus);
                    PQ branchFlow = balance.add(z0flow);

                    LfBranch branch = parent.get(bus);
                    updateBranchFlows(branch, bus, branchFlow.negate(), branchFlow);
                    descendantZeroImpedanceFlow.merge(getOtherSideBus(branch, bus), branchFlow, PQ::add);
                });
                level--;
            }
        }

        private PQ balanceWithImpedance(LfBus bus, LoadFlowModel loadFlowModel) {
            // balance considering injections and flow from lines with impedance

            double qShunt = bus.getShunt().map(shunt -> shunt.getQ().eval()).orElse(0.0);
            qShunt += bus.getControllerShunt().map(shunt -> shunt.getQ().eval()).orElse(0.0);
            qShunt += bus.getSvcShunt().map(shunt -> shunt.getQ().eval()).orElse(0.0);
            // take care of the sign
            PQ balancePQ = new PQ(-bus.getP().eval(), -bus.getQ().eval() + qShunt);

            // only lines with impedance
            List<LfBranch> adjacentBranchesWithImpedance = bus.getBranches().stream()
                .filter(branch -> !branch.isZeroImpedance(loadFlowModel)).collect(Collectors.toList());

            adjacentBranchesWithImpedance.forEach(branch -> {
                PQ branchFlow = getBranchFlow(branch, bus);
                balancePQ.p += branchFlow.p;
                balancePQ.q += branchFlow.q;
            });

            return balancePQ;
        }

        private PQ getDescendantZeroImpedanceFlow(Map<LfBus, PQ> descendantZeroImpedanceFlow, LfBus bus) {
            return descendantZeroImpedanceFlow.containsKey(bus) ? descendantZeroImpedanceFlow.get(bus) : new PQ(0.0, 0.0);
        }

        private void updateBranchFlows(LfBranch branch, LfBus bus, PQ pqBus, PQ pqOtherBus) {
            if (branch.getBus1() != null && branch.getBus1().equals(bus)) {
                branch.updateFlows(pqBus.p, pqBus.q, pqOtherBus.p, pqOtherBus.q);
            } else {
                branch.updateFlows(pqOtherBus.p, pqOtherBus.q, pqBus.p, pqBus.q);
            }
        }

        private PQ getBranchFlow(LfBranch branch, LfBus bus) {
            if (branch.getBus1() != null && branch.getBus1().equals(bus)) {
                return new PQ(branch.getP1().eval(), branch.getQ1().eval());
            } else {
                return new PQ(branch.getP2().eval(), branch.getQ2().eval());
            }
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
