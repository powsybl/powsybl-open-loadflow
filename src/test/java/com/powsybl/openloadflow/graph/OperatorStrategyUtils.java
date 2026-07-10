/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)  
 * This Source Code Form is subject to the terms of the Mozilla Public  
 * License, v. 2.0. If a copy of the MPL was not distributed with this  
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.  
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.action.Action;
import com.powsybl.action.SwitchAction;
import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.graph.ng.Edge;
import com.powsybl.openloadflow.graph.ng.Vertex;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class OperatorStrategyUtils {

    public static Pair<List<OperatorStrategy>, List<Action>> operatorStrategiesFor(Network network, List<Contingency> contingencies, Random random) {
        // vertex = node in a voltage level node breaker view
        // edge = something between two nodes
//        NetworkGraphBase<?> ng = BusBreakerGraph.create(network);
//        Map<String, Edge<?>> idToEdge = ng.idToEdge;
//
//        System.out.println(ng.vertexSet().size() + " - " + ng.edgeSet().size());
//
//        List<Switch> switches = ng.edgeSet().stream()
//                .map(Edge::switchh)
//                .filter(Objects::nonNull)
//                .filter(Switch::isOpen)
//                .collect(Collectors.toList());
//
//        // compute connected components, but open switch are filtered
//        GraphConnectivity<Vertex, Edge<?>> connectivity = new DTreeGraphConnectivity<>();
//        initialConnectivity(ng, connectivity);
//
//        // simulate each contingency
//        BFSShortestPath<Vertex, Edge> sp = new BFSShortestPath<>(graph);
//
//        Set<Action> actions = new HashSet<>();
//        List<OperatorStrategy> strategies = new ArrayList<>();
//
//        int pathFoundCount = 0;
//        int noPathCount = 0;
//
//        for (Contingency contingency : contingencies) {
//            Line line = network.getLine(contingency.getElements().getFirst().getId());
//            Edge edge = Objects.requireNonNull(idToEdge.get(line.getId()));
//
//            strategies.add(randomSwitch(contingency, switches, random, actions));
//
//            /*connectivity.startTemporaryChanges();
//            int prev = connectivity.getNbConnectedComponents();
//            connectivity.removeEdge(edge);
//            int after = connectivity.getNbConnectedComponents();
//            connectivity.undoTemporaryChanges();
//
//            if (after - prev > 0) {
//                // find a path between edge.src and edge.dest in the original graph
//                // but without edge.
//                graph.removeEdge(edge);
//
//                GraphPath<Vertex, Edge> path = sp.getPath(edge.src(), edge.dest());
//                if (path != null) {
//                    strategies.add(createOperatorStrategy(contingency, path.getEdgeList(), actions));
//                } else {
//                    System.err.println("no path found for " + contingency.getId());
//                    noPathCount++;
//                }
//
//                graph.addEdge(edge.src(), edge.dest(), edge);
//            }*/ /* else {
//                strategies.add(operatorStrategyConnectNearest(contingency, actions, network, 1));
//            }*/
//        }
//
//        System.out.printf("Path found/No path: %d / %d%n", pathFoundCount, noPathCount);
//
//        return new ImmutablePair<>(strategies, new ArrayList<>(actions));

        throw new UnsupportedOperationException();
    }

    private static void checkSame(Set<Vertex> vertices, GraphConnectivity<Vertex, Edge> expected, GraphConnectivity<Vertex, Edge> current) {
        assertEquals(expected.getNbConnectedComponents(), current.getNbConnectedComponents());
        assertEquals(expected.getLargestConnectedComponent(), current.getLargestConnectedComponent());

        Set<Vertex> done = new HashSet<>();
        for (Vertex v : vertices) {
            if (!done.contains(v)) {
                Set<Vertex> compExpected = expected.getConnectedComponent(v);
                Set<Vertex> compCurrent = current.getConnectedComponent(v);
                assertEquals(compExpected, compCurrent);
                done.addAll(compExpected);
            }
        }

        assertEquals(expected.getVerticesRemovedFromMainComponent(), current.getVerticesRemovedFromMainComponent());
        assertEquals(expected.getVerticesAddedToMainComponent(), current.getVerticesAddedToMainComponent());
        assertEquals(expected.getEdgesAddedToMainComponent(), current.getEdgesAddedToMainComponent());
        assertEquals(expected.getEdgesRemovedFromMainComponent(), current.getEdgesRemovedFromMainComponent());
    }

    private static <V extends Vertex> void initialConnectivity(Graph<Vertex, Edge<V>> graph, GraphConnectivity<Vertex, Edge<V>> connectivity) {
        for (Vertex v : graph.vertexSet()) {
            connectivity.addVertex(v);
        }
        for (Edge<V> e : graph.edgeSet()) {
            if (e.switchh() == null || !e.switchh().isOpen()) {
                connectivity.addEdge(e.src(), e.dest(), e);
            }
        }
    }

    private static OperatorStrategy randomSwitch(Contingency ct, List<Switch> switches, Random random, Set<Action> actions) {
        Collections.shuffle(switches, random);

        Switch s = switches.getFirst();
        SwitchAction action = new SwitchAction("sw-" + s.getId(), s.getId(), false);
        actions.add(action);

        return new OperatorStrategy("op-" + ct.getId(),
                ContingencyContext.specificContingency(ct.getId()),
                new TrueCondition(),
                List.of(action.getId()));
    }

    private static OperatorStrategy createOperatorStrategy(Contingency ct, List<Edge> edges, Set<Action> actions) {
        List<String> actionIds = new ArrayList<>();

        for (Edge edge : edges) {
            if (edge.switchh() != null && edge.switchh().isOpen()) {
                String id = edge.switchh().getId();
                edge.switchh().setRetained(true);
                SwitchAction action = new SwitchAction("act-" + id, id, false);
                actions.add(action);

                actionIds.add(action.getId());
            }
        }

        if (actionIds.isEmpty()) {
            throw new IllegalArgumentException("no actions found for " + ct.getId());
        }

        return new OperatorStrategy("op-" + ct.getId(),
                ContingencyContext.specificContingency(ct.getId()),
                new TrueCondition(),
                actionIds);
    }

    private static OperatorStrategy operatorStrategyConnectNearest(Contingency ct, Set<Action> actions, Network network, int maxPerLine) {
        List<String> actionIds = new ArrayList<>();

        for (ContingencyElement elem : ct.getElements()) {
            Line line = network.getLine(elem.getId());
            if (line != null) {
                List<Line> toConnect = getNearestOpenLines(network, network.getLine(elem.getId()), maxPerLine);

                for (Branch<?> branch : toConnect) {
                    Action action = new TerminalsConnectionAction("tca-" + branch.getId(), branch.getId(), null, false);

                    actions.add(action);
                    actionIds.add(action.getId());
                }

                for (Switch sw : network.getSwitches()) {
                    if (sw.isOpen()) {
                        Action action = new SwitchAction("sw-" + sw.getId(), sw.getId(), false);
                        actions.add(action);
                        actionIds.add(action.getId());
                        break;
                    }
                }
            }
        }

        return new OperatorStrategy("op-" + ct.getId(),
                ContingencyContext.specificContingency(ct.getId()),
                new TrueCondition(),
                actionIds);
    }

    private static List<Line> getNearestOpenLines(Network network, Line line, int max) {
        Set<Terminal> visited = new HashSet<>();
        Queue<Terminal> queue = new ArrayDeque<>();

        visited.add(line.getTerminal1());
        visited.add(line.getTerminal2());

        queue.offer(line.getTerminal1());
        queue.offer(line.getTerminal2());

        Set<Line> lines = new HashSet<>();
        loop:
        while (!queue.isEmpty() && lines.size() < max) {
            Terminal t = queue.poll();

            for (Connectable<?> conn : t.getVoltageLevel().getConnectables()) {
                for (Terminal terminal : conn.getTerminals()) {
                    if (conn instanceof Line l && !terminal.isConnected() && l.getTerminal1().getBusBreakerView().getBus().getLineStream().toList().contains(l)) {
                        lines.add(l);

                        if (lines.size() >= max) {
                            break loop;
                        }
                    }

                    if (visited.add(terminal)) {
                        queue.offer(terminal);
                    }
                }
            }
        }

        return new ArrayList<>(lines);
    }
}
