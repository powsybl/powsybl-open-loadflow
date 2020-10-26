/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.Pseudograph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * https://dl.acm.org/doi/10.1145/322234.322235
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class EvenShiloachGraphDecrementalConnectivity<V> implements GraphDecrementalConnectivity<V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvenShiloachGraphDecrementalConnectivity.class);

    private final Graph<V, Object> graph = new Pseudograph<>(Object.class);

    private final Map<V, Set<V>> vertexToConnectedComponent;
    private final List<Set<V>> connectedComponents;
    private final Map<V, LevelNeighbours> levelNeighboursMap;

    private final List<Pair<V, V>> cutEdges;
    private final List<Collection<V>> savedConnectedComponents;
    private final Map<V, LevelNeighbours> savedLevelNeighboursMap;
    private final Set<V> vertices;
    private boolean sortedComponents;

    public EvenShiloachGraphDecrementalConnectivity() {
        this.cutEdges = new ArrayList<>();
        this.connectedComponents = new ArrayList<>();
        this.vertexToConnectedComponent = new HashMap<>();
        this.levelNeighboursMap = new HashMap<>();

        this.savedConnectedComponents = new ArrayList<>();
        this.savedLevelNeighboursMap = new HashMap<>();
        this.vertices = new HashSet<>();
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        graph.addVertex(vertex);
        vertices.add(vertex);
    }

    @Override
    public void addEdge(V vertex1, V vertex2) {
        if (vertex1 == null || vertex2 == null) {
            return;
        }
        if (vertex1 != vertex2) {
            graph.addEdge(vertex1, vertex2, new Object());
        } else {
            LOGGER.warn("Loop on vertex {}: problem in input graph", vertex1);
        }
    }

    @Override
    public void cut(V vertex1, V vertex2) {
        if (vertex1 == null || vertex2 == null) {
            return;
        }

        sortedComponents = false;
        if (connectedComponents.isEmpty()) {
            initConnectedComponents();
            initLevels();
            cloneMaps();
        }

        graph.removeEdge(vertex1, vertex2);
        cutEdges.add(Pair.of(vertex1, vertex2));
        if (graph.getAllEdges(vertex1, vertex2).size() > 1) {
            return;
        }

        GraphProcess processA = new GraphProcessA(vertex1, vertex2);
        GraphProcessB processB = new GraphProcessB(vertex1, vertex2);
        while (!processA.isHalted() && !processB.isHalted()) {
            processA.next();
            if (processA.isHalted()) {
                processB.undoChanges();
            } else {
                processB.next();
            }
        }

    }

    private void initConnectedComponents() {
        Set<V> visited = new HashSet<>();
        for (V v : vertices) {
            if (visited.add(v)) {
                Set<V> newConnectedComponent = new HashSet<>();
                completeConnectedComponent(v, visited, newConnectedComponent);
                connectedComponents.add(newConnectedComponent);
            }
        }
        connectedComponents.forEach(cc -> cc.forEach(v -> vertexToConnectedComponent.put(v, cc)));
    }

    public void initLevels() {
        connectedComponents.forEach(
            cc -> cc.stream().findFirst().ifPresent(
                v -> buildNextLevel(Collections.singleton(v), 0)));
    }

    private void completeConnectedComponent(V v, Set<V> visited, Set<V> currentCc) {
        currentCc.add(v);
        for (V adj : Graphs.neighborListOf(graph, v)) {
            if (visited.add(adj)) {
                completeConnectedComponent(adj, visited, currentCc);
            }
        }
    }

    private void cloneMaps() {
        savedConnectedComponents.clear();
        connectedComponents.forEach(t -> savedConnectedComponents.add(new HashSet<>(t)));

        savedLevelNeighboursMap.clear();
        levelNeighboursMap.forEach((k, v) -> savedLevelNeighboursMap.put(k, new LevelNeighbours(v)));
    }

    @Override
    public void reset() {
        sortedComponents = false;
        connectedComponents.clear();
        savedConnectedComponents.forEach(t -> connectedComponents.add(new HashSet<>(t)));
        vertexToConnectedComponent.clear();
        connectedComponents.forEach(cc -> cc.forEach(v -> vertexToConnectedComponent.put(v, cc)));

        levelNeighboursMap.clear();
        savedLevelNeighboursMap.forEach((k, v) -> levelNeighboursMap.put(k, new LevelNeighbours(v)));

        for (Pair<V, V> cutEdge : cutEdges) {
            addEdge(cutEdge.getLeft(), cutEdge.getRight());
        }
        cutEdges.clear();
    }

    @Override
    public int getComponentNumber(V vertex) {
        if (!sortedComponents) {
            sortedComponents = true;
            connectedComponents.sort(Comparator.comparingInt(c -> -c.size()));
        }
        return connectedComponents.indexOf(vertexToConnectedComponent.get(vertex));
    }

    @Override
    public List<Set<V>> getSmallComponents() {
        if (!sortedComponents) {
            sortedComponents = true;
            connectedComponents.sort(Comparator.comparingInt(c -> -c.size()));
        }
        return connectedComponents.subList(1, connectedComponents.size());
    }

    private interface GraphProcess {
        void next();

        boolean isHalted();
    }

    private class GraphProcessA implements GraphProcess {

        private final Traverser t1;
        private final Traverser t2;
        private final V vertex1;
        private final V vertex2;
        private boolean halted;

        public GraphProcessA(V vertex1, V vertex2) {
            this.vertex1 = vertex1;
            this.vertex2 = vertex2;
            Set<V> traversedVerticesT1 = new LinkedHashSet<>();
            Set<V> traversedVerticesT2 = new LinkedHashSet<>();
            this.t1 = new Traverser(vertex1, traversedVerticesT2, traversedVerticesT1);
            this.t2 = new Traverser(vertex2, traversedVerticesT1, traversedVerticesT2);
            this.halted = false;
        }

        @Override
        public void next() {
            if (t1.hasEnded() || t2.hasEnded()) {
                return;
            }

            t1.next();
            if (t1.componentBreakDetected()) {
                createComponent(vertex1, t1);
                halted = true;
                return;
            }

            t2.next();
            if (t2.componentBreakDetected()) {
                createComponent(vertex2, t2);
                halted = true;
            }
        }

        @Override
        public boolean isHalted() {
            return halted;
        }

        private void createComponent(V vertex, Traverser traverser) {
            Collection<V> cc = vertexToConnectedComponent.get(vertex);
            Set<V> newConnectedComponent = traverser.traversedVertices;
            cc.removeAll(newConnectedComponent);
            for (V v : newConnectedComponent) {
                vertexToConnectedComponent.put(v, newConnectedComponent);
            }
            connectedComponents.add(newConnectedComponent);
        }

    }

    private class GraphProcessB implements GraphProcess {

        private final Deque<V> verticesToUpdate;
        private final Map<V, LevelNeighbours> savedChangedLevels;

        public GraphProcessB(V v, V w) {
            this.verticesToUpdate = new LinkedList<>();
            this.savedChangedLevels = new HashMap<>();
            LevelNeighbours nV = levelNeighboursMap.get(v);
            LevelNeighbours nW = levelNeighboursMap.get(w);
            if (nV.level == nW.level) {
                nV.sameLevel.remove(w);
                nW.sameLevel.remove(v);

            } else {
                V vertexLowLevel = nV.level < nW.level ? v : w;
                V vertexBigLevel = nV.level < nW.level ? w : v;
                LevelNeighbours nLowLevel = nV.level < nW.level ? nV : nW;
                LevelNeighbours nBigLevel = nV.level < nW.level ? nW : nV;

                nLowLevel.upperLevel.remove(vertexBigLevel);
                nBigLevel.lowerLevel.remove(vertexLowLevel);
                if (nBigLevel.lowerLevel.isEmpty()) {
                    verticesToUpdate.add(vertexBigLevel);
                }
            }
        }

        @Override
        public void next() {
            if (verticesToUpdate.isEmpty()) {
                return; // step (1)/(9)
            }
            V w = verticesToUpdate.removeFirst();  // step (2)
            LevelNeighbours levelNeighbours = levelNeighboursMap.get(w);
            if (!savedChangedLevels.containsKey(w)) {
                savedChangedLevels.put(w, new LevelNeighbours(levelNeighbours));
            }
            levelNeighbours.level++; // step (3)
            for (V localNeighbour : levelNeighbours.sameLevel) { // step (4)
                if (w != localNeighbour) {
                    LevelNeighbours lnln = levelNeighboursMap.get(localNeighbour);
                    lnln.sameLevel.remove(w);
                    lnln.upperLevel.add(w);
                }
            }
            levelNeighbours.lowerLevel.addAll(levelNeighbours.sameLevel); // step (5)
            for (V upperNeighbour : levelNeighbours.upperLevel) { // step (6)
                LevelNeighbours lnun = levelNeighboursMap.get(upperNeighbour);
                lnun.lowerLevel.remove(w);
                lnun.sameLevel.add(w);
                if (lnun.lowerLevel.isEmpty()) {
                    verticesToUpdate.add(upperNeighbour);
                }
            }
            levelNeighbours.sameLevel.clear(); // step (7)
            levelNeighbours.sameLevel.addAll(levelNeighbours.upperLevel);
            levelNeighbours.upperLevel.clear();
            if (levelNeighbours.lowerLevel.isEmpty()) { // step (8)
                verticesToUpdate.add(w);
            }
        }

        @Override
        public boolean isHalted() {
            return verticesToUpdate.isEmpty();
        }

        public void undoChanges() {
            levelNeighboursMap.putAll(savedChangedLevels);
            savedChangedLevels.clear();
            verticesToUpdate.clear();
        }
    }

    private class Traverser {
        private final Set<V> traversedVertices;
        private final Deque<V> verticesToTraverse;
        private final Set<V> vertexEnd;
        private boolean ended;

        public Traverser(V vertexStart, Set<V> vertexEnd, Set<V> traversedVertices) {
            this.vertexEnd = vertexEnd;
            this.traversedVertices = traversedVertices;
            this.verticesToTraverse = new LinkedList<>();
            this.verticesToTraverse.add(vertexStart);
            this.ended = false;
        }

        public void next() {
            V v = verticesToTraverse.removeLast();
            if (traversedVertices.add(v)) {
                for (V adj : Graphs.neighborListOf(graph, v)) {
                    verticesToTraverse.add(adj);
                    if (vertexEnd.contains(adj)) {
                        ended = true;
                        return;
                    }
                }
            }
        }

        public boolean componentBreakDetected() {
            return verticesToTraverse.isEmpty();
        }

        public boolean hasEnded() {
            return ended;
        }
    }

    private class LevelNeighbours {
        private final Collection<V> lowerLevel = new LinkedList<>();
        private final Collection<V> sameLevel = new LinkedList<>();
        private final Collection<V> upperLevel = new LinkedList<>();
        private int level;

        public LevelNeighbours(int level) {
            this.level = level;
        }

        public LevelNeighbours(LevelNeighbours origin) {
            this.level = origin.level;
            this.lowerLevel.addAll(origin.lowerLevel);
            this.sameLevel.addAll(origin.sameLevel);
            this.upperLevel.addAll(origin.upperLevel);
        }

    }

    private void buildNextLevel(Collection<V> level, int levelIndex) {
        Collection<V> nextLevel = new HashSet<>();
        for (V v : level) {
            LevelNeighbours neighbours = levelNeighboursMap.computeIfAbsent(v, value -> new LevelNeighbours(levelIndex));
            for (V adj : Graphs.neighborListOf(graph, v)) {
                LevelNeighbours adjNeighbours = levelNeighboursMap.computeIfAbsent(adj, value -> new LevelNeighbours(levelIndex + 1));
                fillNeighbours(neighbours, adj, adjNeighbours.level);
            }
            nextLevel.addAll(neighbours.upperLevel);
        }
        if (!nextLevel.isEmpty()) {
            buildNextLevel(nextLevel, levelIndex + 1);
        }
    }

    private void fillNeighbours(LevelNeighbours neighbours, V neighbour, int neighbourLevel) {
        switch (neighbourLevel - neighbours.level) {
            case -1:
                neighbours.lowerLevel.add(neighbour);
                break;
            case 0:
                neighbours.sameLevel.add(neighbour);
                break;
            case 1:
                neighbours.upperLevel.add(neighbour);
                break;
            default:
                throw new PowsyblException("Unexpected level for vertex " + neighbour);
        }
    }

}
