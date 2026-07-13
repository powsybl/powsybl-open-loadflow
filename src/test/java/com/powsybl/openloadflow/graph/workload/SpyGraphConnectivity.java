/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.*;
import com.powsybl.openloadflow.graph.utils.Aggregator;
import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.graph.utils.GraphConnectivityMethod;
import org.nocrala.tools.texttablefmt.BorderStyle;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpyGraphConnectivity<V, E> implements GraphConnectivity<V, E> {

    private static final CellStyle RIGHT_ALIGN = new CellStyle(CellStyle.HorizontalAlign.right);

    private GraphConnectivity<V, E> delegate;

    private final AverageStopWatch sw = new AverageStopWatch();

    // these aggregators count the time needed to build the initial graph
    private final Aggregator[] initialGraphBuild = new Aggregator[GraphConnectivityMethod.values().length];

    // these aggregators count the time needed to perform query, insertion and removal request,
    // once the initial graph is build
    private final Aggregator[] temporaryChanges = new Aggregator[GraphConnectivityMethod.values().length];

    // these are the currently used aggregators
    private Aggregator[] current;
    private boolean initialGraphBuildDone = false;

    private final List<Long> sumOfDistances = new ArrayList<>();

    private final Aggregator verticesAddedTo = new Aggregator();
    private final Aggregator verticesRemovedFrom = new Aggregator();
    private final Aggregator edgesAddedTo = new Aggregator();
    private final Aggregator edgesRemovedFrom = new Aggregator();

    public SpyGraphConnectivity() {
        for (int i = 0; i < initialGraphBuild.length; i++) {
            initialGraphBuild[i] = new Aggregator();
            temporaryChanges[i] = new Aggregator();
        }

        current = initialGraphBuild;
    }

    public SpyGraphConnectivity(GraphConnectivity<V, E> delegate) {
        this();
        setDelegate(delegate);
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
        sw.start();
        delegate.addVertex(vertex);
        sw.stop();
        current[GraphConnectivityMethod.ADD_VERTEX.ordinal()].add(sw.elapsed());
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        sw.start();
        delegate.addEdge(vertex1, vertex2, edge);
        sw.stop();
        current[GraphConnectivityMethod.ADD_EDGE.ordinal()].add(sw.elapsed());
    }

    @Override
    public void removeEdge(E edge) {
        sw.start();
        delegate.removeEdge(edge);
        sw.stop();
        current[GraphConnectivityMethod.REMOVE_EDGE.ordinal()].add(sw.elapsed());
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return delegate.supportTemporaryChangesNesting();
    }

    @Override
    public void startTemporaryChanges(boolean quick) {
        sw.start();
        delegate.startTemporaryChanges(quick);
        sw.stop();
        current[GraphConnectivityMethod.START_TEMPORARY_CHANGES.ordinal()].add(sw.elapsed());

        setInitialGraphBuildDone(true);
    }

    @Override
    public void undoTemporaryChanges() {
        sw.start();
        delegate.undoTemporaryChanges();
        sw.stop();
        current[GraphConnectivityMethod.UNDO_TEMPORARY_CHANGES.ordinal()].add(sw.elapsed());
    }

    @Override
    public int getComponentNumber(V vertex) {
        sw.start();
        int n = delegate.getComponentNumber(vertex);
        sw.stop();
        current[GraphConnectivityMethod.GET_COMPONENT_NUMBER.ordinal()].add(sw.elapsed());
        return n;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        sw.start();
        delegate.setMainComponentVertex(mainComponentVertex);
        sw.stop();
        current[GraphConnectivityMethod.SET_MAIN_COMPONENT_VERTEX.ordinal()].add(sw.elapsed());
    }

    @Override
    public int getNbConnectedComponents() {
        sw.start();
        int n = delegate.getNbConnectedComponents();
        sw.stop();
        current[GraphConnectivityMethod.GET_NB_CONNECTED_COMPONENTS.ordinal()].add(sw.elapsed());
        return n;
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        sw.start();
        Set<V> vertices = delegate.getConnectedComponent(vertex);
        sw.stop();
        current[GraphConnectivityMethod.GET_CONNECTED_COMPONENT.ordinal()].add(sw.elapsed());
        return vertices;
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        sw.start();
        Set<V> vertices = delegate.getLargestConnectedComponent();
        sw.stop();
        current[GraphConnectivityMethod.GET_LARGEST_CONNECTED_COMPONENT.ordinal()].add(sw.elapsed());
        return vertices;
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        sw.start();
        Set<V> vertices = delegate.getVerticesRemovedFromMainComponent();
        sw.stop();
        current[GraphConnectivityMethod.GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT.ordinal()].add(sw.elapsed());
        verticesRemovedFrom.add(vertices.size());
        return vertices;
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        sw.start();
        Set<E> edges = delegate.getEdgesRemovedFromMainComponent();
        sw.stop();
        current[GraphConnectivityMethod.GET_EDGES_REMOVED_FROM_MAIN_COMPONENT.ordinal()].add(sw.elapsed());
        edgesRemovedFrom.add(edges.size());
        return edges;
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        sw.start();
        Set<V> vertices = delegate.getVerticesAddedToMainComponent();
        sw.stop();
        current[GraphConnectivityMethod.GET_VERTICES_ADDED_TO_MAIN_COMPONENT.ordinal()].add(sw.elapsed());
        verticesAddedTo.add(vertices.size());
        return vertices;
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        sw.start();
        Set<E> edges = delegate.getEdgesAddedToMainComponent();
        sw.stop();
        current[GraphConnectivityMethod.GET_EDGES_ADDED_TO_MAIN_COMPONENT.ordinal()].add(sw.elapsed());
        edgesAddedTo.add(edges.size());
        return edges;
    }

    public void computeSd() {
        if (delegate instanceof DTreeGraphConnectivity<V, E> dtree) {
            sumOfDistances.add(dtree.computeSd());
        }
    }

    public GraphConnectivity<V, E> getDelegate() {
        return delegate;
    }

    public void merge(SpyGraphConnectivity<V, E> connectivity) {
        sw.merge(connectivity.sw);

        for (int i = 0; i < connectivity.initialGraphBuild.length; i++) {
            initialGraphBuild[i].merge(connectivity.initialGraphBuild[i]);
        }
        for (int i = 0; i < connectivity.temporaryChanges.length; i++) {
            temporaryChanges[i].merge(connectivity.temporaryChanges[i]);
        }

        sumOfDistances.addAll(connectivity.sumOfDistances);

        initialGraphBuildDone |= connectivity.initialGraphBuildDone;
    }

    public String resultsToString(int iterations) {
        StringBuilder sb = new StringBuilder();

        sb.append(delegate.getClass().getSimpleName()).append(":").append(System.lineSeparator());
        sb.append("Total runtime: %.4f s = %.4f s + %.4f s%n".formatted(
                sw.totalElapsed() / 1e9,
                Aggregator.sum(initialGraphBuild) / 1e9,
                Aggregator.sum(temporaryChanges) / 1e9d));
        sb.append("Total runtime/iteration: %.4f s%n".formatted(
                sw.totalElapsed() / 1e9 / iterations));
        sb.append("%.4f ms/operation%n".formatted(
                sw.averageElapsed() / 1e6));
        sb.append("%.4f operation/ms%n".formatted(
                1e6 / sw.averageElapsed()));

        Table table = new Table(14, BorderStyle.UNICODE_BOX);
        table.addCell("");
        table.addCell("Temporary changes", 6);
        table.addCell("Graph build", 6);
        table.addCell("");

        table.addCell("Method");

        for (int i = 0; i < 2; i++) {
            table.addCell("min (μs)");
            table.addCell("avg (μs)");
            table.addCell("max (μs)");
            table.addCell("stdev (μs)");
            table.addCell("count");
            table.addCell("total (ms)");
        }
        table.addCell("total (ms)");

        for (GraphConnectivityMethod method : GraphConnectivityMethod.values()) {
            Aggregator tempChanges = temporaryChanges[method.ordinal()];
            Aggregator init = initialGraphBuild[method.ordinal()];

            if (tempChanges.getCount() > 0 || init.getCount() > 0) {
                table.addCell(method.toString());
                long total = addAggregatorCells(table, tempChanges);
                total += addAggregatorCells(table, init);
                table.addCell(String.format("%.4f", total / 1e6));
            }
        }

        sb.append(table.render()).append(System.lineSeparator());

        if (!sumOfDistances.isEmpty()) {
            sb.append(sumOfDistances);
        }

        sb.append(verticesAddedTo).append(System.lineSeparator());
        sb.append(verticesRemovedFrom).append(System.lineSeparator());
        sb.append(edgesAddedTo).append(System.lineSeparator());
        sb.append(edgesRemovedFrom).append(System.lineSeparator());

        return sb.toString();
    }

    private long addAggregatorCells(Table table, Aggregator aggregator) {
        if (aggregator.getCount() > 0) {
            table.addCell(String.format("%.4f", aggregator.getMin(TimeUnit.MICROSECONDS)), RIGHT_ALIGN);
            table.addCell(String.format("%.4f", aggregator.getMean(TimeUnit.MICROSECONDS)), RIGHT_ALIGN);
            table.addCell(String.format("%.4f", aggregator.getMax(TimeUnit.MICROSECONDS)), RIGHT_ALIGN);
            table.addCell(String.format("%.4f", aggregator.getSampleStandardDeviation(TimeUnit.MICROSECONDS)), RIGHT_ALIGN);
            table.addCell(Integer.toString(aggregator.getCount()), RIGHT_ALIGN);
            table.addCell(String.format("%.4f", aggregator.getSum() / 1e6), RIGHT_ALIGN);
            return aggregator.getSum();
        } else {
            for (int i = 0; i < 6; i++) {
                table.addCell("");
            }
            return 0;
        }
    }

    @Override
    public String toString() {
        return super.toString() + "[" + delegate.toString() + "]";
    }
}
