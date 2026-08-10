/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.graph.utils.GraphConnectivityMethod;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class ComputeSdGraphConnectivity<V, E> extends AbstractSpyGraphConnectivity<V, E> {

    private final AverageStopWatch asw = new AverageStopWatch();
    private final BufferedWriter bw;
    private int operation;

    public ComputeSdGraphConnectivity(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }

            bw = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeLine(GraphConnectivityMethod method, long nanos) {
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
        writeLine(GraphConnectivityMethod.ADD_VERTEX, asw.elapsed());
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        asw.start();
        super.addEdge(vertex1, vertex2, edge);
        asw.stop();
        writeLine(GraphConnectivityMethod.ADD_EDGE, asw.elapsed());
    }

    @Override
    public void removeEdge(E edge) {
        asw.start();
        super.removeEdge(edge);
        asw.stop();
        writeLine(GraphConnectivityMethod.REMOVE_EDGE, asw.elapsed());
    }

    @Override
    public void startTemporaryChanges(boolean computeComparisons) {
        asw.start();
        super.startTemporaryChanges(computeComparisons);
        asw.stop();
        writeLine(GraphConnectivityMethod.START_TEMPORARY_CHANGES, asw.elapsed());
    }

    @Override
    public void undoTemporaryChanges() {
        asw.start();
        super.undoTemporaryChanges();
        asw.stop();
        writeLine(GraphConnectivityMethod.UNDO_TEMPORARY_CHANGES, asw.elapsed());
    }

    @Override
    public int getComponentNumber(V vertex) {
        asw.start();
        int n = super.getComponentNumber(vertex);
        asw.stop();
        writeLine(GraphConnectivityMethod.GET_COMPONENT_NUMBER, asw.elapsed());
        return n;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        asw.start();
        super.setMainComponentVertex(mainComponentVertex);
        asw.stop();
        writeLine(GraphConnectivityMethod.SET_MAIN_COMPONENT_VERTEX, asw.elapsed());
    }

    @Override
    public int getNbConnectedComponents() {
        asw.start();
        int n = super.getNbConnectedComponents();
        asw.stop();
        writeLine(GraphConnectivityMethod.GET_NB_CONNECTED_COMPONENTS, asw.elapsed());
        return n;
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        asw.start();
        Set<V> set = super.getConnectedComponent(vertex);
        asw.stop();
        writeLine(GraphConnectivityMethod.GET_CONNECTED_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        asw.start();
        Set<V> set = super.getLargestConnectedComponent();
        asw.stop();
        writeLine(GraphConnectivityMethod.GET_LARGEST_CONNECTED_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        asw.start();
        Set<V> set = super.getVerticesRemovedFromMainComponent();
        asw.stop();
        writeLine(GraphConnectivityMethod.GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        asw.start();
        Set<E> set = super.getEdgesRemovedFromMainComponent();
        asw.stop();
        writeLine(GraphConnectivityMethod.GET_EDGES_REMOVED_FROM_MAIN_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        asw.start();
        Set<V> set = super.getVerticesAddedToMainComponent();
        asw.stop();
        writeLine(GraphConnectivityMethod.GET_VERTICES_ADDED_TO_MAIN_COMPONENT, asw.elapsed());
        return set;
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        asw.start();
        Set<E> set = super.getEdgesAddedToMainComponent();
        asw.stop();
        writeLine(GraphConnectivityMethod.GET_EDGES_ADDED_TO_MAIN_COMPONENT, asw.elapsed());
        return set;
    }
}
