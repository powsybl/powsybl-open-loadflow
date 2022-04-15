/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.Pseudograph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementing the Even-Shiloach algorithm (see https://dl.acm.org/doi/10.1145/322234.322235)
 * Due to time computation optimizations, this current implementation is only for graphs which initially have ONLY ONE
 * connected component. If more, an exception is thrown.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class EvenShiloachGraphDecrementalConnectivity<V, E> implements GraphDecrementalConnectivity<V, E> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvenShiloachGraphDecrementalConnectivity.class);

    private final Graph<V, E> graph = new Pseudograph<>(null, null, false);

    private final Map<V, Integer> vertexToConnectedComponent;

    private final List<Set<V>> newConnectedComponents;
    private final Map<V, LevelNeighbours> levelNeighboursMap;

    private final List<Triple<V, E, V>> cutEdges;
    private final List<Triple<V, E, V>> unprocessedCutEdges;
    private final Set<V> vertices;

    private final LinkedList<Map<V, LevelNeighbours>> allSavedChangedLevels;

    private boolean vertexMapCacheInvalidated;
    private boolean init;

    public EvenShiloachGraphDecrementalConnectivity() {
        this.cutEdges = new ArrayList<>();
        this.unprocessedCutEdges = new ArrayList<>();
        this.newConnectedComponents = new ArrayList<>();
        this.vertexToConnectedComponent = new HashMap<>();
        this.levelNeighboursMap = new HashMap<>();
        this.allSavedChangedLevels = new LinkedList<>();
        this.vertexMapCacheInvalidated = false;
        this.vertices = new HashSet<>();
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        graph.addVertex(vertex);
        vertices.add(vertex);
        invalidateInit();
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        if (vertex1 != vertex2) {
            graph.addEdge(vertex1, vertex2, edge);
        } else {
            LOGGER.warn("Loop on vertex {}: problem in input graph", vertex1);
        }
        invalidateInit();
    }

    private void invalidateInit() {
        init = false;
        cutEdges.clear();
        unprocessedCutEdges.clear();
        newConnectedComponents.clear();
        invalidateVertexMapCache();
    }

    @Override
    public void cut(E edge) {
        if (!graph.containsEdge(edge)) {
            throw new PowsyblException("No such edge in graph: " + edge);
        }
        Triple<V, E, V> edgeCut = Triple.of(graph.getEdgeSource(edge), edge, graph.getEdgeTarget(edge));
        if (unprocessedCutEdges.contains(edgeCut)) {
            throw new PowsyblException("Edge already cut: " + edge);
        }
        invalidateVertexMapCache();

        cutEdges.add(edgeCut);
        unprocessedCutEdges.add(edgeCut);
    }

    private void invalidateVertexMapCache() {
        vertexMapCacheInvalidated = true;
        vertexToConnectedComponent.clear();
    }

    private void initConnectedComponents() {
        // Checking if only one connected components at start as algorithm is not implemented for a network with several connected components at start
        // Can be easily extended to the general case at the cost of slower computation
        List<Set<V>> initialConnectedComponents = new ConnectivityInspector<>(graph).connectedSets();
        if (initialConnectedComponents.size() > 1) {
            throw new PowsyblException("Algorithm not implemented for a network with several connected components at start");
        }
    }

    public void initLevels() {
        levelNeighboursMap.clear();
        vertices.stream().findFirst().ifPresent(
            v -> buildNextLevel(Collections.singleton(v), 0));
    }

    public void reset() {
        invalidateVertexMapCache();
        newConnectedComponents.clear();
        allSavedChangedLevels.descendingIterator().forEachRemaining(levelNeighboursMap::putAll);
        allSavedChangedLevels.clear();

        for (Triple<V, E, V> cutEdge : cutEdges) {
            graph.addEdge(cutEdge.getLeft(), cutEdge.getRight(), cutEdge.getMiddle());
        }
        cutEdges.clear();
    }

    @Override
    public int getComponentNumber(V vertex) {
        checkVertex(vertex);
        lazyComputeConnectivity();
        updateVertexMapCache();
        return vertexToConnectedComponent.get(vertex);
    }

    @Override
    public List<Set<V>> getSmallComponents() {
        lazyComputeConnectivity();
        return newConnectedComponents;
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkVertex(vertex);
        lazyComputeConnectivity();
        updateVertexMapCache();
        int cn = vertexToConnectedComponent.get(vertex);
        return cn == 0 ? getMainConnectedComponent() : newConnectedComponents.get(cn - 1);
    }

    private Set<V> getMainConnectedComponent() {
        return vertices.stream().filter(v -> newConnectedComponents.stream().noneMatch(cc -> cc.contains(v))).collect(Collectors.toSet());
    }

    @Override
    public Set<V> getNonConnectedVertices(V vertex) {
        checkVertex(vertex);
        lazyComputeConnectivity();
        List<Set<V>> nonConnectedComponents = new ArrayList<>(newConnectedComponents);
        newConnectedComponents.stream().filter(c -> c.contains(vertex)).findFirst().ifPresent(c -> {
            nonConnectedComponents.remove(c);
            nonConnectedComponents.add(getMainConnectedComponent());
        });
        return nonConnectedComponents.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private void lazyComputeConnectivity() {
        if (init && unprocessedCutEdges.isEmpty()) {
            return;
        }

        init();
        for (Triple<V, E, V> cutEdge : unprocessedCutEdges) {
            V vertex1 = cutEdge.getLeft();
            V vertex2 = cutEdge.getRight();
            graph.removeEdge(vertex1, vertex2);

            GraphProcess processA = new GraphProcessA(vertex1, vertex2);
            GraphProcessB processB = new GraphProcessB(vertex1, vertex2);
            while (!processA.isHalted() && !processB.isHalted()) {
                processA.next();
                if (!processA.isHalted()) {
                    processB.next();
                }
            }

            if (processA.isHalted()) {
                processB.undoChanges();
            } else { // processB halted
                allSavedChangedLevels.add(processB.savedChangedLevels);
            }
        }
        unprocessedCutEdges.clear();

        sortComponents();
    }

    private void init() {
        if (!init) {
            initConnectedComponents();
            initLevels();
            init = true;
        }
    }

    private void sortComponents() {
        if (!newConnectedComponents.isEmpty()) {
            newConnectedComponents.sort(Comparator.comparingInt(c -> -c.size()));
            int nbVerticesOut = countVerticesOut();
            int maxNewComponentsSize = newConnectedComponents.get(0).size();
            if (vertices.size() - nbVerticesOut < maxNewComponentsSize) {
                // The initial connected component is smaller than some new connected components
                // That is, the biggest connected component is among the new connected components list
                Set<V> mainComponent = new HashSet<>(vertices);
                newConnectedComponents.forEach(mainComponent::removeAll);
                newConnectedComponents.add(mainComponent);
                newConnectedComponents.sort(Comparator.comparingInt(c -> -c.size()));
                newConnectedComponents.remove(0);
            }
        }
    }

    private int countVerticesOut() {
        int nbVerticesOut = 0;
        for (Set<V> newConnectedComponent : newConnectedComponents) {
            nbVerticesOut += newConnectedComponent.size();
        }
        return nbVerticesOut;
    }

    private void updateVertexMapCache() {
        if (vertexMapCacheInvalidated) {
            vertices.forEach(v -> vertexToConnectedComponent.put(v, 0));
            int i = 0;
            for (Set<V> newConnectedComponent : newConnectedComponents) {
                int indxCC = ++i;
                newConnectedComponent.forEach(v -> vertexToConnectedComponent.put(v, indxCC));
            }
            vertexMapCacheInvalidated = false;
        }
    }

    private void checkVertex(V vertex) {
        if (!graph.containsVertex(vertex)) {
            throw new AssertionError("given vertex " + vertex + " is not in the graph");
        }
    }

    private interface GraphProcess {
        void next();

        boolean isHalted();
    }

    private class GraphProcessA implements GraphProcess {

        private final Traverser t1;
        private final Traverser t2;
        private boolean halted;

        public GraphProcessA(V vertex1, V vertex2) {
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
                updateConnectedComponents(t1.traversedVertices);
                halted = true;
                return;
            }

            t2.next();
            if (t2.componentBreakDetected()) {
                updateConnectedComponents(t2.traversedVertices);
                halted = true;
            }
        }

        @Override
        public boolean isHalted() {
            return halted;
        }

        private void updateConnectedComponents(Set<V> verticesOut) {
            newConnectedComponents.forEach(cc -> cc.removeAll(verticesOut));
            newConnectedComponents.add(verticesOut);
        }
    }

    private class GraphProcessB implements GraphProcess {

        private final Deque<V> verticesToUpdate;
        private final Map<V, LevelNeighbours> savedChangedLevels;
        private final V vertex1;
        private final V vertex2;
        private boolean init;

        public GraphProcessB(V vertex1, V vertex2) {
            this.vertex1 = vertex1;
            this.vertex2 = vertex2;
            this.verticesToUpdate = new LinkedList<>();
            this.savedChangedLevels = new HashMap<>();
            this.init = false;
        }

        private void initialStep() {
            LevelNeighbours ln1 = getLevelNeighbour(vertex1);
            LevelNeighbours ln2 = getLevelNeighbour(vertex2);
            if (ln1.level == ln2.level) {
                ln1.sameLevel.remove(vertex2);
                ln2.sameLevel.remove(vertex1);
            } else {
                V vertexLowLevel = ln1.level < ln2.level ? vertex1 : vertex2;
                V vertexBigLevel = ln1.level < ln2.level ? vertex2 : vertex1;
                LevelNeighbours nLowLevel = ln1.level < ln2.level ? ln1 : ln2;
                LevelNeighbours nBigLevel = ln1.level < ln2.level ? ln2 : ln1;

                nLowLevel.upperLevel.remove(vertexBigLevel);
                nBigLevel.lowerLevel.remove(vertexLowLevel);
                if (nBigLevel.lowerLevel.isEmpty() && graph.getAllEdges(vertex1, vertex2).isEmpty()) {
                    this.verticesToUpdate.add(vertexBigLevel);
                }
            }
        }

        private LevelNeighbours getLevelNeighbour(V v) {
            LevelNeighbours levelNeighbours = levelNeighboursMap.get(v);
            savedChangedLevels.computeIfAbsent(v, vertex -> new LevelNeighbours(levelNeighbours));
            return levelNeighbours;
        }

        @Override
        public void next() {
            if (!init) {
                initialStep();
                init = true;
            }
            if (verticesToUpdate.isEmpty()) {
                return; // step (1)/(9)
            }
            V w = verticesToUpdate.removeFirst();  // step (2)
            LevelNeighbours levelNeighbours = getLevelNeighbour(w);
            levelNeighbours.level++; // step (3)
            for (V localNeighbour : levelNeighbours.sameLevel) { // step (4)
                if (w != localNeighbour) {
                    LevelNeighbours lnln = getLevelNeighbour(localNeighbour);
                    lnln.sameLevel.remove(w);
                    lnln.upperLevel.add(w);
                }
            }
            levelNeighbours.lowerLevel.addAll(levelNeighbours.sameLevel); // step (5)
            for (V upperNeighbour : levelNeighbours.upperLevel) { // step (6)
                LevelNeighbours lnun = getLevelNeighbour(upperNeighbour);
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
            return init && verticesToUpdate.isEmpty();
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
