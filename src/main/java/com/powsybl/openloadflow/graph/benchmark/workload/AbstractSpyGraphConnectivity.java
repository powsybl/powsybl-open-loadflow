/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.workload;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.SpanningForestGraphConnectivity;

import java.util.Set;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public abstract class AbstractSpyGraphConnectivity<V, E> implements ISpyGraphConnectivity<V, E> {

    protected GraphConnectivityFactory<V, E> delegateFactory;
    protected GraphConnectivity<V, E> delegate;

    @Override
    public void setDelegate(GraphConnectivity<V, E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public GraphConnectivity<V, E> getDelegate() {
        return delegate;
    }

    @Override
    public void setDelegateFactory(GraphConnectivityFactory<V, E> delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    @Override
    public GraphConnectivityFactory<V, E> getDelegateFactory() {
        return delegateFactory;
    }

    @Override
    public long computeSumOfDistances() {
        if (delegate instanceof SpanningForestGraphConnectivity<V, E> sfgc) {
            return sfgc.computeSumOfDistances();
        } else {
            return -1;
        }
    }

    @Override
    public int vertexCount() {
        if (delegate instanceof SpanningForestGraphConnectivity<V, E> sfgc) {
            return sfgc.vertexCount();
        } else {
            return -1;
        }
    }

    @Override
    public double averageDepth() {
        if (delegate instanceof SpanningForestGraphConnectivity<V, E> sfgc) {
            return sfgc.averageDepth();
        } else {
            return -1;
        }
    }

    @Override
    public void addVertex(V vertex) {
        delegate.addVertex(vertex);
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        delegate.addEdge(vertex1, vertex2, edge);
    }

    @Override
    public void removeEdge(E edge) {
        delegate.removeEdge(edge);
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return delegate.supportTemporaryChangesNesting();
    }

    @Override
    public void startTemporaryChanges(boolean computeComparisons) {
        delegate.startTemporaryChanges(computeComparisons);
    }

    @Override
    public void undoTemporaryChanges() {
        delegate.undoTemporaryChanges();
    }

    @Override
    public int getComponentNumber(V vertex) {
        return delegate.getComponentNumber(vertex);
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        delegate.setMainComponentVertex(mainComponentVertex);
    }

    @Override
    public int getNbConnectedComponents() {
        return delegate.getNbConnectedComponents();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        return delegate.getConnectedComponent(vertex);
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        return delegate.getLargestConnectedComponent();
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        return delegate.getVerticesRemovedFromMainComponent();
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        return delegate.getEdgesRemovedFromMainComponent();
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        return delegate.getVerticesAddedToMainComponent();
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        return delegate.getEdgesAddedToMainComponent();
    }
}
