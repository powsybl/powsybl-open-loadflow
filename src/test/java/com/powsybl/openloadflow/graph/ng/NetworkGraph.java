/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.ng;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.graph.DTreeGraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.jgrapht.graph.Pseudograph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings("checkstyle:AbstractClassName")
public abstract class NetworkGraph<V extends Vertex> extends Pseudograph<V, Edge<V>> {

    public final Network network;

    public final Map<String, Edge<V>> idToEdge = new HashMap<>();
    public final TObjectIntMap<V> vertexToNum = new TObjectIntHashMap<>();
    public final TObjectIntMap<Edge<V>> edgeToNum = new TObjectIntHashMap<>();

    public GraphConnectivity<V, Edge<V>> connectivity;
    public GraphConnectivity<V, Edge<V>> synchronousConnectivity;

    public NetworkGraph(Network network) {
        super(null, null, false);
        this.network = network;
        reset();
    }

    public void reset() {
        idToEdge.clear();
        vertexToNum.clear();
        edgeToNum.clear();

        connectivity = new DTreeGraphConnectivity<>();
        synchronousConnectivity = new DTreeGraphConnectivity<>();

        removeAllEdges(new ArrayList<>(edgeSet()));
        removeAllVertices(new ArrayList<>(vertexSet()));

        generateGraph();

        computeIndex();
        computeConnectivity();
    }

    public abstract void generateGraph();

    protected void addInterVoltageLevelEdges(Network network) {
        for (Branch<?> branch : network.getBranches()) {
            Edge<V> edge = edgeFromBranch(branch);
            addEdge(edge.src(), edge.dest(), edge);
            idToEdge.put(branch.getId(), edge);
        }

        for (ThreeWindingsTransformer throt : network.getThreeWindingsTransformers()) {
            V v1 = vertexFromTerminal(throt.getLeg1().getTerminal());
            V v2 = vertexFromTerminal(throt.getLeg2().getTerminal());
            V v3 = vertexFromTerminal(throt.getLeg3().getTerminal());

            addEdge(v1, v2, new Edge<>(v1, v2, null, throt));
            addEdge(v1, v3, new Edge<>(v1, v3, null, throt));
            addEdge(v2, v3, new Edge<>(v2, v3, null, throt));

            // can't map to a unique edge...
        }

        for (HvdcLine hvdcLine : network.getHvdcLines()) {
            Edge<V> edge = edgeFromTerminals(hvdcLine.getConverterStation1().getTerminal(),
                    hvdcLine.getConverterStation2().getTerminal(),
                    hvdcLine);
            idToEdge.put(hvdcLine.getId(), edge);
            addEdge(edge.src(), edge.dest(), edge);
        }

        if (network.getDcLineStream().findAny().isPresent()) {
            System.err.println("DcLine not implemented");
        }
        if (network.getDcConnectableStream(AcDcConverter.class).findAny().isPresent()) {
            System.err.println("AcDcConverter not implemented");
        }
    }

    @Override
    public boolean addEdge(V sourceVertex, V targetVertex, Edge<V> vEdge) {
        if (sourceVertex == null || targetVertex == null) {
            return false;
        }
        return super.addEdge(sourceVertex, targetVertex, vEdge);
    }

    protected void computeIndex() {
        for (V v : vertexSet()) {
            vertexToNum.put(v, vertexToNum.size());
        }
        for (Edge<V> e : edgeSet()) {
            edgeToNum.put(e, edgeToNum.size());
        }
    }

    protected void computeConnectivity() {
        resetConnectivity();
    }

    public void resetConnectivity() {
        connectivity = new DTreeGraphConnectivity<>();
        synchronousConnectivity = new DTreeGraphConnectivity<>();

        for (V v : vertexSet()) {
            connectivity.addVertex(v);
            synchronousConnectivity.addVertex(v);
        }

        for (Edge<V> e : edgeSet()) {
            if (e.switchh() == null || !e.switchh().isOpen()) {
                connectivity.addEdge(e.src(), e.dest(), e);
                if (!(e.link() instanceof HvdcLine)) {
                    synchronousConnectivity.addEdge(e.src(), e.dest(), e);
                }
            }
        }
    }

    protected abstract V vertexFromTerminal(Terminal terminal);

    protected abstract Edge<V> forSwitch(Switch sw);

    protected Edge<V> edgeFromTerminals(Terminal terminal1, Terminal terminal2, Object link) {
        return new Edge<>(vertexFromTerminal(terminal1), vertexFromTerminal(terminal2), null, link);
    }

    public Edge<V> edgeFromBranch(Branch<?> branch) {
        return edgeFromTerminals(branch.getTerminal1(), branch.getTerminal2(), branch);
    }
}
