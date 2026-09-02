/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.workload;

import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.openloadflow.graph.benchmark.Aggregator;
import com.powsybl.openloadflow.graph.benchmark.AverageStopWatch;
import com.powsybl.openloadflow.graph.benchmark.GraphConnectivityMethod;
import org.nocrala.tools.texttablefmt.BorderStyle;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.Table;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class PerformanceGraphConnectivity<V, E> extends AbstractSpyGraphConnectivity<V, E> {

    private static final CellStyle RIGHT_ALIGN = new CellStyle(CellStyle.HorizontalAlign.right);

    private final String name;

    private final AverageStopWatch sw = new AverageStopWatch();

    // these aggregators count the time needed to build the initial graph
    private final Aggregator[] initialGraphBuild = new Aggregator[GraphConnectivityMethod.values().length];

    // these aggregators count the time needed to perform query, insertion and removal request,
    // once the initial graph is build
    private final Aggregator[] temporaryChanges = new Aggregator[GraphConnectivityMethod.values().length];

    // these are the currently used aggregators
    private Aggregator[] current;
    private boolean initialGraphBuildDone = false;

    public PerformanceGraphConnectivity() {
        this(null);
    }

    public PerformanceGraphConnectivity(String name) {
        this.name = name;
        for (int i = 0; i < initialGraphBuild.length; i++) {
            initialGraphBuild[i] = new Aggregator();
            temporaryChanges[i] = new Aggregator();
        }

        current = initialGraphBuild;
    }

    @Override
    public void beginOperations(Operations operations) {
        setInitialGraphBuildDone(false);
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
        return vertices;
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        sw.start();
        Set<E> edges = delegate.getEdgesRemovedFromMainComponent();
        sw.stop();
        current[GraphConnectivityMethod.GET_EDGES_REMOVED_FROM_MAIN_COMPONENT.ordinal()].add(sw.elapsed());
        return edges;
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        sw.start();
        Set<V> vertices = delegate.getVerticesAddedToMainComponent();
        sw.stop();
        current[GraphConnectivityMethod.GET_VERTICES_ADDED_TO_MAIN_COMPONENT.ordinal()].add(sw.elapsed());
        return vertices;
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        sw.start();
        Set<E> edges = delegate.getEdgesAddedToMainComponent();
        sw.stop();
        current[GraphConnectivityMethod.GET_EDGES_ADDED_TO_MAIN_COMPONENT.ordinal()].add(sw.elapsed());
        return edges;
    }

    public void merge(PerformanceGraphConnectivity<V, E> connectivity) {
        sw.merge(connectivity.sw);

        for (int i = 0; i < connectivity.initialGraphBuild.length; i++) {
            initialGraphBuild[i].merge(connectivity.initialGraphBuild[i]);
        }
        for (int i = 0; i < connectivity.temporaryChanges.length; i++) {
            temporaryChanges[i].merge(connectivity.temporaryChanges[i]);
        }

        initialGraphBuildDone |= connectivity.initialGraphBuildDone;
    }

    public void serialize(JsonGenerator g) throws IOException {
        if (name != null) {
            g.writeStringField("name", name);
        }

        g.writeNumberField("totalRuntime", sw.totalElapsed());
        g.writeNumberField("operationsCount", sw.count());

        g.writeObjectFieldStart("initialGraphBuild");
        serialize(g, initialGraphBuild);
        g.writeEndObject();

        g.writeObjectFieldStart("temporaryChanges");
        serialize(g, temporaryChanges);
        g.writeEndObject();
    }

    private void serialize(JsonGenerator g, Aggregator[] aggregators) throws IOException {
        for (GraphConnectivityMethod method : GraphConnectivityMethod.values()) {
            Aggregator agg = aggregators[method.ordinal()];

            if (agg.getCount() > 0) {
                g.writeObjectFieldStart(method.name());
                aggregators[method.ordinal()].serialize(g);
                g.writeEndObject();
            }
        }
    }

    public String resultsToString(int iterations) {
        StringBuilder sb = new StringBuilder();

        if (name != null) {
            sb.append("PerformanceGraphConnectivity: ").append(name).append("\n");
        }

        sb.append(delegateFactory.getClass().getSimpleName()).append(":").append(System.lineSeparator());
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
