/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.workload;

import com.powsybl.openloadflow.graph.Component;
import com.powsybl.openloadflow.graph.benchmark.AverageStopWatch;
import com.powsybl.openloadflow.graph.benchmark.BenchmarkMethod;
import com.powsybl.openloadflow.graph.benchmark.generators.WorkloadUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class StatsWriterGraphConnectivity<V, E> extends AbstractSpyGraphConnectivity<V, E> {

    private final AverageStopWatch asw = new AverageStopWatch();
    private final BufferedWriter bw;
    private int operation;

    public StatsWriterGraphConnectivity(Path file) {
        try {
            bw = WorkloadUtils.newBufferedWriter(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeLine(BenchmarkMethod method, long nanos) {
        try {
            bw.write("%d %s %d %d%n".formatted(operation, method.shortName(), nanos, computeSumOfDistances()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void notifyOperation(int operation) {
        this.operation = operation;
    }

    @Override
    public void endOperations(Operations operations) {
        try {
            bw.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void addVertex(V vertex) {
        asw.start();
        super.addVertex(vertex);
        asw.stop();
        writeLine(BenchmarkMethod.ADD_VERTEX, asw.elapsed());
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        asw.start();
        super.addEdge(vertex1, vertex2, edge);
        asw.stop();
        writeLine(BenchmarkMethod.ADD_EDGE, asw.elapsed());
    }

    @Override
    public void removeEdge(E edge) {
        asw.start();
        super.removeEdge(edge);
        asw.stop();
        writeLine(BenchmarkMethod.REMOVE_EDGE, asw.elapsed());
    }

    @Override
    public void startTemporaryChanges(boolean computeComparisons) {
        asw.start();
        super.startTemporaryChanges(computeComparisons);
        asw.stop();
        writeLine(BenchmarkMethod.START_TEMPORARY_CHANGES, asw.elapsed());
    }

    @Override
    public void undoTemporaryChanges() {
        asw.start();
        super.undoTemporaryChanges();
        asw.stop();
        writeLine(BenchmarkMethod.UNDO_TEMPORARY_CHANGES, asw.elapsed());
    }

    @Override
    public int getComponentNumber(V vertex) {
        asw.start();
        int n = super.getComponentNumber(vertex);
        asw.stop();
        writeLine(BenchmarkMethod.GET_COMPONENT_NUMBER, asw.elapsed());
        return n;
    }

    @Override
    public boolean connected(V vertex1, V vertex2) {
        asw.start();
        boolean connected = super.connected(vertex1, vertex2);
        asw.stop();
        writeLine(BenchmarkMethod.CONNECTED, asw.elapsed());
        return connected;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        asw.start();
        super.setMainComponentVertex(mainComponentVertex);
        asw.stop();
        writeLine(BenchmarkMethod.SET_MAIN_COMPONENT_VERTEX, asw.elapsed());
    }

    @Override
    public int getNbConnectedComponents() {
        asw.start();
        int n = super.getNbConnectedComponents();
        asw.stop();
        writeLine(BenchmarkMethod.GET_NB_CONNECTED_COMPONENTS, asw.elapsed());
        return n;
    }

    @Override
    public Component<V> getConnectedComponent(V vertex) {
        asw.start();
        Component<V> set = super.getConnectedComponent(vertex);
        asw.stop();
        writeLine(BenchmarkMethod.GET_CONNECTED_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Component<V> getLargestConnectedComponent() {
        asw.start();
        Component<V> set = super.getLargestConnectedComponent();
        asw.stop();
        writeLine(BenchmarkMethod.GET_LARGEST_CONNECTED_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        asw.start();
        Set<V> set = super.getVerticesRemovedFromMainComponent();
        asw.stop();
        writeLine(BenchmarkMethod.GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        asw.start();
        Set<E> set = super.getEdgesRemovedFromMainComponent();
        asw.stop();
        writeLine(BenchmarkMethod.GET_EDGES_REMOVED_FROM_MAIN_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        asw.start();
        Set<V> set = super.getVerticesAddedToMainComponent();
        asw.stop();
        writeLine(BenchmarkMethod.GET_VERTICES_ADDED_TO_MAIN_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        asw.start();
        Set<E> set = super.getEdgesAddedToMainComponent();
        asw.stop();
        writeLine(BenchmarkMethod.GET_EDGES_ADDED_TO_MAIN_COMPONENT, asw.elapsed());
        return set;
    }
}
