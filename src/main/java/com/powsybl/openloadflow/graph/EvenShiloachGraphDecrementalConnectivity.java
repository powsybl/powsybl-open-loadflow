/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.jgrapht.Graphs;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementing the Even-Shiloach algorithm (see https://dl.acm.org/doi/10.1145/322234.322235)
 * Due to time computation optimizations, this current implementation is only for graphs which initially have ONLY ONE
 * connected component. If more, an exception is thrown.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public class EvenShiloachGraphDecrementalConnectivity<V, E> extends AbstractGraphConnectivity<V, E> {

    private Map<V, Integer> vertexToConnectedComponent;
    private final List<Set<V>> newConnectedComponents = new ArrayList<>();

    private final Map<V, LevelNeighbours> levelNeighboursMap = new HashMap<>();
    private final Deque<Map<V, LevelNeighbours>> allSavedChangedLevels = new ArrayDeque<>();

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemoval) {
        vertexToConnectedComponent = null;
        componentSets = null;

        GraphProcessA processA = new GraphProcessA(edgeRemoval.v1, edgeRemoval.v2);
        GraphProcessB processB = new GraphProcessB(edgeRemoval.v1, edgeRemoval.v2);
        while (!processA.isHalted() && !processB.isHalted()) {
            processA.next();
            if (!processA.isHalted()) {
                processB.next();
            }
        }

        if (processA.isHalted()) {
            processB.undoChanges();
            updateNewConnectedComponents(processA.verticesOut);
        } else { // processB halted
            allSavedChangedLevels.add(processB.savedChangedLevels);
        }
    }

    private void updateNewConnectedComponents(Set<V> verticesOut) {
        // the removed edge can be in a new connected component!
        newConnectedComponents.forEach(cc -> cc.removeAll(verticesOut));
        newConnectedComponents.add(verticesOut);
    }

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {
        throw new PowsyblException("This implementation does not support incremental connectivity: edges cannot be added once that connectivity is saved");
    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        throw new PowsyblException("This implementation does not support incremental connectivity: vertices cannot be added once that connectivity is saved");
    }

    @Override
    protected void resetConnectivity(Deque<GraphModification<V, E>> m) {
        vertexToConnectedComponent = null;
        componentSets = null;
        newConnectedComponents.clear();
        allSavedChangedLevels.descendingIterator().forEachRemaining(levelNeighboursMap::putAll);
        allSavedChangedLevels.clear();
    }

    @Override
    protected void updateComponents() {
        computeConnectivity();
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return false;
    }

    @Override
    public void startTemporaryChanges() {
        if (!getModificationsContexts().isEmpty()) {
            throw new PowsyblException("This implementation supports only one level of temporary changes");
        }
        super.startTemporaryChanges();
        if (levelNeighboursMap.isEmpty()) {
            Set<V> vertices = getGraph().vertexSet();
            vertices.stream()
                    .max(Comparator.comparingInt(v -> getGraph().degreeOf(v)))
                    .ifPresent(v -> buildLevelNeighbours(Collections.singleton(v), 0));
            if (vertices.size() > levelNeighboursMap.size()) {
                // Checking if only one connected components at start
                throw new PowsyblException("This implementation does not support saving a graph with several connected components");
            }
        }
    }

    private void buildLevelNeighbours(Collection<V> level, int levelIndex) {
        Collection<V> nextLevel = new HashSet<>();
        for (V v : level) {
            LevelNeighbours neighbours = levelNeighboursMap.computeIfAbsent(v, value -> new LevelNeighbours(levelIndex));
            for (V adj : Graphs.neighborListOf(getGraph(), v)) {
                LevelNeighbours adjNeighbours = levelNeighboursMap.computeIfAbsent(adj, value -> new LevelNeighbours(levelIndex + 1));
                fillNeighbours(neighbours, adj, adjNeighbours.level);
            }
            nextLevel.addAll(neighbours.upperLevel);
        }
        if (!nextLevel.isEmpty()) {
            buildLevelNeighbours(nextLevel, levelIndex + 1);
        }
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        Integer ccIndex = getVertexToConnectedComponentMap().get(vertex);
        return ccIndex != null ? ccIndex : 0;
    }

    private Map<V, Integer> getVertexToConnectedComponentMap() {
        if (vertexToConnectedComponent == null) {
            vertexToConnectedComponent = new HashMap<>();
            int i = 0;
            for (Set<V> newConnectedComponent : getSmallComponents()) {
                int indxCC = ++i;
                newConnectedComponent.forEach(v -> vertexToConnectedComponent.put(v, indxCC));
            }
        }
        return vertexToConnectedComponent;
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        int componentNumber = getComponentNumber(vertex);
        if (componentNumber == 0) {
            computeMainConnectedComponent();
        }
        return componentSets.get(componentNumber);
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        checkSavedContext();
        updateComponents();
        computeMainConnectedComponent();
        return componentSets.get(0);
    }

    private void computeMainConnectedComponent() {
        if (componentSets.get(0) == null) {
            Set<V> mainConnectedComponent = new HashSet<>(getGraph().vertexSet());
            getSmallComponents().forEach(mainConnectedComponent::removeAll);
            componentSets.set(0, mainConnectedComponent);
        }
    }

    @Override
    public Set<V> getNonConnectedVertices(V vertex) {
        int componentNumber = getComponentNumber(vertex);
        if (componentNumber != 0) {
            computeMainConnectedComponent();
        }
        List<Set<V>> nonConnectedComponents = new ArrayList<>(componentSets);
        nonConnectedComponents.remove(componentNumber);
        return nonConnectedComponents.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private void computeConnectivity() {
        if (componentSets != null) {
            return;
        }

        componentSets = new ArrayList<>();
        componentSets.add(null); // trying to avoid to compute main connected component

        newConnectedComponents.sort(Comparator.comparingInt(c -> -c.size()));
        componentSets.addAll(newConnectedComponents);
        int nbVerticesOut = newConnectedComponents.stream().mapToInt(Set::size).sum();
        int maxNewComponentsSize = newConnectedComponents.stream().findFirst().map(Set::size).orElse(0);
        Set<V> vertices = getGraph().vertexSet();
        if (vertices.size() - nbVerticesOut < maxNewComponentsSize) {
            // The initial connected component is smaller than some new connected components
            // That is, the biggest connected component is among the new connected components list
            computeMainConnectedComponent(); // it's therefore the initial and not the "main" connected component
            componentSets.sort(Comparator.comparingInt(c -> -c.size()));
        }
    }

    private interface GraphProcess {
        void next();

        boolean isHalted();
    }

    private class GraphProcessA implements GraphProcess {

        private final Traverser t1;
        private final Traverser t2;
        private Set<V> verticesOut;

        public GraphProcessA(V vertex1, V vertex2) {
            Set<V> visitedVerticesT1 = new LinkedHashSet<>();
            Set<V> visitedVerticesT2 = new LinkedHashSet<>();
            this.t1 = new Traverser(vertex1, visitedVerticesT2, visitedVerticesT1);
            this.t2 = new Traverser(vertex2, visitedVerticesT1, visitedVerticesT2);
            this.verticesOut = null;
        }

        @Override
        public void next() {
            if (t1.hasEnded() || t2.hasEnded() || isHalted()) {
                return;
            }

            if (t1.componentBreakDetected()) {
                verticesOut = t1.visitedVertices;
                return;
            }
            t1.next();

            if (t2.componentBreakDetected()) {
                verticesOut = t2.visitedVertices;
                return;
            }
            t2.next();
        }

        @Override
        public boolean isHalted() {
            return verticesOut != null;
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
                if (nBigLevel.lowerLevel.isEmpty() && getGraph().getAllEdges(vertex1, vertex2).isEmpty()) {
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
        private final Set<V> visitedVertices;
        private final Deque<V> verticesToTraverse;
        private final Set<V> vertexEnd;
        private boolean ended;

        public Traverser(V vertexStart, Set<V> vertexEnd, Set<V> visitedVertices) {
            this.vertexEnd = vertexEnd;
            this.visitedVertices = visitedVertices;
            this.visitedVertices.add(vertexStart);
            this.verticesToTraverse = new LinkedList<>();
            this.verticesToTraverse.add(vertexStart);
            this.ended = vertexEnd.contains(vertexStart);
        }

        public void next() {
            V v = verticesToTraverse.removeLast();
            for (V adj : Graphs.neighborListOf(getGraph(), v)) {
                if (visitedVertices.add(adj)) {
                    verticesToTraverse.add(adj);
                    if (vertexEnd.contains(adj)) {
                        ended = true;
                        return;
                    }
                }
            }
        }

        public boolean componentBreakDetected() {
            // 3 possible cases:
            //  - traversing ongoing -> verticesToTraverse not empty
            //  - traversing ended because it has reached a vertex visited by other side -> verticesToTraverse not empty as this vertex is in verticesToTraverse
            //  - traversing ended without reaching any vertex visited by other side -> component break -> new component detected (verticesVisited)
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
