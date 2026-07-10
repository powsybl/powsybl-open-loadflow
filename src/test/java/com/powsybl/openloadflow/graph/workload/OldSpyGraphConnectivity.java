/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.graph.DTreeGraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.utils.GraphConnectivityMethod;
import org.nocrala.tools.texttablefmt.BorderStyle;
import org.nocrala.tools.texttablefmt.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class OldSpyGraphConnectivity<V, E> implements GraphConnectivity<V, E> {

    private GraphConnectivity<V, E> delegate;

    // these stopwatches count the time needed to build the initial graph
    private final AverageStopWatch[] initialGraphBuild = new AverageStopWatch[GraphConnectivityMethod.values().length];

    // these stopwatches count the time needed to perform query, insertion and removal request,
    // once the initial graph is build
    private final AverageStopWatch[] temporaryChanges = new AverageStopWatch[GraphConnectivityMethod.values().length];

    // these are the currently used stopwatches
    private AverageStopWatch[] current;
    private boolean initialGraphBuildDone = false;

    private final List<Long> sumOfDistances = new ArrayList<>();

    public OldSpyGraphConnectivity() {
        for (int i = 0; i < initialGraphBuild.length; i++) {
            initialGraphBuild[i] = new AverageStopWatch();
            temporaryChanges[i] = new AverageStopWatch();
        }

        current = initialGraphBuild;
    }

    public void setDelegate(GraphConnectivity<V, E> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public void setInitialGraphBuildDone(boolean initialGraphBuildDone) {
        if (initialGraphBuildDone != this.initialGraphBuildDone) {
            if (initialGraphBuildDone) {
                current = temporaryChanges;
            } else {
                current = initialGraphBuild;
            }
            this.initialGraphBuildDone = initialGraphBuildDone;
        }
    }

    @Override
    public void addVertex(V vertex) {
        current[GraphConnectivityMethod.ADD_VERTEX.ordinal()].start();
        delegate.addVertex(vertex);
        current[GraphConnectivityMethod.ADD_VERTEX.ordinal()].stop();
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        current[GraphConnectivityMethod.ADD_EDGE.ordinal()].start();
        delegate.addEdge(vertex1, vertex2, edge);
        current[GraphConnectivityMethod.ADD_EDGE.ordinal()].stop();
    }

    @Override
    public void removeEdge(E edge) {
        current[GraphConnectivityMethod.REMOVE_EDGE.ordinal()].start();
        delegate.removeEdge(edge);
        current[GraphConnectivityMethod.REMOVE_EDGE.ordinal()].stop();
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return delegate.supportTemporaryChangesNesting();
    }

    @Override
    public void startTemporaryChanges(boolean quick) {
        current[GraphConnectivityMethod.START_TEMPORARY_CHANGES.ordinal()].start();
        delegate.startTemporaryChanges(quick);
        current[GraphConnectivityMethod.START_TEMPORARY_CHANGES.ordinal()].stop();

        setInitialGraphBuildDone(true);
    }

    @Override
    public void undoTemporaryChanges() {
        current[GraphConnectivityMethod.UNDO_TEMPORARY_CHANGES.ordinal()].start();
        delegate.undoTemporaryChanges();
        current[GraphConnectivityMethod.UNDO_TEMPORARY_CHANGES.ordinal()].stop();
    }

    @Override
    public int getComponentNumber(V vertex) {
        current[GraphConnectivityMethod.GET_COMPONENT_NUMBER.ordinal()].start();
        int n = delegate.getComponentNumber(vertex);
        current[GraphConnectivityMethod.GET_COMPONENT_NUMBER.ordinal()].stop();
        return n;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        current[GraphConnectivityMethod.SET_MAIN_COMPONENT_VERTEX.ordinal()].start();
        delegate.setMainComponentVertex(mainComponentVertex);
        current[GraphConnectivityMethod.SET_MAIN_COMPONENT_VERTEX.ordinal()].stop();
    }

    @Override
    public int getNbConnectedComponents() {
        current[GraphConnectivityMethod.GET_NB_CONNECTED_COMPONENTS.ordinal()].start();
        int n = delegate.getNbConnectedComponents();
        current[GraphConnectivityMethod.GET_NB_CONNECTED_COMPONENTS.ordinal()].stop();
        return n;
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        current[GraphConnectivityMethod.GET_CONNECTED_COMPONENT.ordinal()].start();
        Set<V> vertices = delegate.getConnectedComponent(vertex);
        current[GraphConnectivityMethod.GET_CONNECTED_COMPONENT.ordinal()].stop();
        return vertices;
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        current[GraphConnectivityMethod.GET_LARGEST_CONNECTED_COMPONENT.ordinal()].start();
        Set<V> vertices = delegate.getLargestConnectedComponent();
        current[GraphConnectivityMethod.GET_LARGEST_CONNECTED_COMPONENT.ordinal()].stop();
        return vertices;
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        current[GraphConnectivityMethod.GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT.ordinal()].start();
        Set<V> vertices = delegate.getVerticesRemovedFromMainComponent();
        current[GraphConnectivityMethod.GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT.ordinal()].stop();
        return vertices;
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        current[GraphConnectivityMethod.GET_EDGES_REMOVED_FROM_MAIN_COMPONENT.ordinal()].start();
        Set<E> edges = delegate.getEdgesRemovedFromMainComponent();
        current[GraphConnectivityMethod.GET_EDGES_REMOVED_FROM_MAIN_COMPONENT.ordinal()].stop();
        return edges;
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        current[GraphConnectivityMethod.GET_VERTICES_ADDED_TO_MAIN_COMPONENT.ordinal()].start();
        Set<V> vertices = delegate.getVerticesAddedToMainComponent();
        current[GraphConnectivityMethod.GET_VERTICES_ADDED_TO_MAIN_COMPONENT.ordinal()].stop();
        return vertices;
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        current[GraphConnectivityMethod.GET_EDGES_ADDED_TO_MAIN_COMPONENT.ordinal()].start();
        Set<E> edges = delegate.getEdgesAddedToMainComponent();
        current[GraphConnectivityMethod.GET_EDGES_ADDED_TO_MAIN_COMPONENT.ordinal()].stop();
        return edges;
    }

    public AverageStopWatch[] getInitialGraphBuild() {
        return initialGraphBuild;
    }

    public AverageStopWatch[] getTemporaryChanges() {
        return temporaryChanges;
    }

    public void computeSd() {
        if (delegate instanceof DTreeGraphConnectivity<V, E> dtree) {
            sumOfDistances.add(dtree.computeSd());
        }
    }

    public void printResults(int iterations) {
        AverageStopWatch opSW = AverageStopWatch.merge(temporaryChanges);
        System.out.printf("%.4f ms/operation%n", opSW.averageElapsed() / 1e6);
        System.out.printf("%.4f operation/ms%n", 1e6 / opSW.averageElapsed());

        for (GraphConnectivityMethod method : GraphConnectivityMethod.values()) {
            AverageStopWatch asw = temporaryChanges[method.ordinal()];
            if (asw.count() > 0) {
                System.out.printf("Average %s: %s%n", method, asw.averageElapsedString(TimeUnit.MICROSECONDS));
            }
        }

        if (!sumOfDistances.isEmpty()) {
            System.out.println(sumOfDistances);
        }

        System.out.println();
        printGeneralResults(iterations);
    }

    private void printGeneralResults(int iterations) {
        Table table = new Table(4, BorderStyle.UNICODE_BOX);

        long initialGraphBuildTotal = AverageStopWatch.total(initialGraphBuild);
        double averageInitialGraphBuild = initialGraphBuildTotal / (1e9 * iterations);

        long temporaryChangesTotal = AverageStopWatch.total(temporaryChanges);
        double averageTemporaryChanges = temporaryChangesTotal / (1e9 * iterations);

        long runtime = initialGraphBuildTotal + temporaryChangesTotal;
        double averageRuntime = runtime / (1e9 * iterations);

        table.addCell("");
        table.addCell("Initial graph build");
        table.addCell("Temporary changes");
        table.addCell("Sum");
        table.addCell("Average per iteration (s)");
        table.addCell(String.format("%.4f", averageInitialGraphBuild));
        table.addCell(String.format("%.4f", averageTemporaryChanges));
        table.addCell(String.format("%.4f", averageRuntime));
        table.addCell("Sum of all iterations (s)");
        table.addCell(String.format("%.4f", initialGraphBuildTotal / 1e9d));
        table.addCell(String.format("%.4f", temporaryChangesTotal / 1e9d));
        table.addCell(String.format("%.4f", runtime / 1e9d));
        System.out.println(table.render());
    }
}
