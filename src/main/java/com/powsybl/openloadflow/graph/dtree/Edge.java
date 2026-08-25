/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.dtree;

/**
 * An edge in a {@link DTGraph}.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class Edge<V, E> {

    private final DTNode<V, E> nodeU;
    private final DTNode<V, E> nodeV;
    private final E edgeData;
    private boolean treeEdge;

    public Edge(DTNode<V, E> nodeU, DTNode<V, E> nodeV, E edgeData, boolean treeEdge) {
        this.nodeU = nodeU;
        this.nodeV = nodeV;
        this.edgeData = edgeData;
        this.treeEdge = treeEdge;
    }

    public DTNode<V, E> opposite(DTNode<V, E> node) {
        if (nodeU == node) {
            return nodeV;
        } else {
            return nodeU;
        }
    }

    public DTNode<V, E> getNodeU() {
        return nodeU;
    }

    public DTNode<V, E> getNodeV() {
        return nodeV;
    }

    public E getEdgeData() {
        return edgeData;
    }

    public void setTreeEdge(boolean treeEdge) {
        this.treeEdge = treeEdge;
    }

    public boolean isTreeEdge() {
        return treeEdge;
    }
}
