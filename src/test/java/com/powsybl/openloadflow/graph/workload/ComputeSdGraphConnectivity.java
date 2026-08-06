/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.utils.GraphConnectivityMethod;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class ComputeSdGraphConnectivity<V, E> extends AbstractSpyGraphConnectivity<V, E> {

    private final BufferedWriter bw;

    public ComputeSdGraphConnectivity(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }

            bw = Files.newBufferedWriter(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeSd(GraphConnectivityMethod method) {
        try {
            bw.write("%s %d%n".formatted(method.shortName(), computeSumOfDistances()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beginOperations(Operations operations) {

    }

    @Override
    public void endOperations(Operations operations) {
        try {
            bw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addVertex(V vertex) {
        super.addVertex(vertex);
        writeSd(GraphConnectivityMethod.ADD_VERTEX);
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        super.addEdge(vertex1, vertex2, edge);
        writeSd(GraphConnectivityMethod.ADD_EDGE);
    }

    @Override
    public void removeEdge(E edge) {
        super.removeEdge(edge);
        writeSd(GraphConnectivityMethod.REMOVE_EDGE);
    }

    @Override
    public void startTemporaryChanges(boolean computeComparisons) {
        super.startTemporaryChanges(computeComparisons);
        writeSd(GraphConnectivityMethod.START_TEMPORARY_CHANGES);
    }

    @Override
    public void undoTemporaryChanges() {
        super.undoTemporaryChanges();
        writeSd(GraphConnectivityMethod.UNDO_TEMPORARY_CHANGES);
    }

    @Override
    public int getComponentNumber(V vertex) {
        int n = super.getComponentNumber(vertex);
        writeSd(GraphConnectivityMethod.GET_COMPONENT_NUMBER);
        return n;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        super.setMainComponentVertex(mainComponentVertex);
        writeSd(GraphConnectivityMethod.SET_MAIN_COMPONENT_VERTEX);
    }

    @Override
    public int getNbConnectedComponents() {
        int n = super.getNbConnectedComponents();
        writeSd(GraphConnectivityMethod.GET_NB_CONNECTED_COMPONENTS);
        return n;
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        Set<V> set = super.getConnectedComponent(vertex);
        writeSd(GraphConnectivityMethod.GET_CONNECTED_COMPONENT);
        return set;
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        Set<V> set = super.getLargestConnectedComponent();
        writeSd(GraphConnectivityMethod.GET_LARGEST_CONNECTED_COMPONENT);
        return set;
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        Set<V> set = super.getVerticesRemovedFromMainComponent();
        writeSd(GraphConnectivityMethod.GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT);
        return set;
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        Set<E> set = super.getEdgesRemovedFromMainComponent();
        writeSd(GraphConnectivityMethod.GET_EDGES_REMOVED_FROM_MAIN_COMPONENT);
        return set;
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        Set<V> set = super.getVerticesAddedToMainComponent();
        writeSd(GraphConnectivityMethod.GET_VERTICES_ADDED_TO_MAIN_COMPONENT);
        return set;
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        Set<E> set = super.getEdgesAddedToMainComponent();
        writeSd(GraphConnectivityMethod.GET_EDGES_ADDED_TO_MAIN_COMPONENT);
        return set;
    }
}
