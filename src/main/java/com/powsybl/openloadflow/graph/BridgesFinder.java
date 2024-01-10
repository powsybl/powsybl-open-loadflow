/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.Graph;
import org.jgrapht.graph.Pseudograph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class BridgesFinder<V> {

    private static class NeighbourList extends ArrayList<Integer> {
    }

    private final int nbVertices;
    private final ToIntFunction<V> numGetter;
    private final Graph<Integer, Object> graph = new Pseudograph<>(Object.class);

    private List<int[]> bridges;
    private final NeighbourList[] neighbours;
    private final boolean[] visited;

    /**
     * Depth-first search number
     */
    private final int[] dfsn;

    /**
     * Depth-first search number counter
     */
    private int dfsnCount;

    BridgesFinder(int nbVertices, ToIntFunction<V> numGetter) {
        this.numGetter = Objects.requireNonNull(numGetter);
        this.nbVertices = nbVertices;
        this.visited = new boolean[nbVertices];
        this.dfsn = new int[nbVertices];
        this.neighbours = new NeighbourList[nbVertices];
        for (int i = 0; i < nbVertices; ++i) {
            neighbours[i] = new NeighbourList();
        }
        this.dfsnCount = 0;
    }

    /**
     * Finds bridges in a connected component, recursively
     *
     * @param start  vertex to be visited
     * @param parent parent vertex of the vertex to be visited
     * @return the lowest reachable vertex from given vertex
     */
    private int findBridgesFromVertex(int start, int parent) {

        visited[start] = true;
        dfsnCount++;
        dfsn[start] = dfsnCount;
        int lowestReachableVertex = dfsnCount;

        for (int neighbour : neighbours[start]) {
            if (!visited[neighbour]) {
                // Neighbour not visited yet: consider it as child of start and visit it
                int lowestN = findBridgesFromVertex(neighbour, start);

                // Check if neighbour has a connection to one of the ancestors of start
                if (lowestReachableVertex > lowestN) {
                    lowestReachableVertex = lowestN;
                }

                // If the lowest vertex reachable from neighbour is after start,
                // and if edge is not doubled, then start-neighbour is a bridge
                if (lowestN > dfsn[start] && !doubledEdge(start, neighbour)) {
                    bridges.add(new int[] {start, neighbour});
                }
            } else {
                // Already visited neighbour
                if (neighbour != parent && lowestReachableVertex > dfsn[neighbour]) {
                    lowestReachableVertex = dfsn[neighbour];
                }
            }
        }

        return lowestReachableVertex;
    }

    private boolean doubledEdge(int u, int v) {
        return graph.getAllEdges(u, v).size() > 1;
    }


    /**
     * DFS based function to find all bridges
     *
     * @return the list of bridge edges, represented as pairs of vertices
     */
    List<int[]> getBridges() {
        lazySearch();
        return bridges;
    }

    private void lazySearch() {
        if (bridges == null) {
            dfsnCount = 0;
            bridges = new ArrayList<>();
            Arrays.fill(visited, false);
            for (int i = 0; i < nbVertices; i++) {
                if (!visited[i]) {
                    findBridgesFromVertex(i, i);
                }
            }
        }
    }

    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        graph.addVertex(numGetter.applyAsInt(vertex));
    }

    public void addEdge(V vertex1, V vertex2) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        addEdge(numGetter.applyAsInt(vertex1), numGetter.applyAsInt(vertex2));
    }

    private void addEdge(int num1, int num2) {
        graph.addEdge(num1, num2, new Object());
        neighbours[num1].add(num2);
        neighbours[num2].add(num1);
    }

}
