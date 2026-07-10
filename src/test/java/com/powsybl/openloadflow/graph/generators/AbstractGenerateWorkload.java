/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.generators;

import com.powsybl.openloadflow.graph.NaiveGraphConnectivity;
import org.jgrapht.Graph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.powsybl.openloadflow.graph.utils.GraphConnectivityMethod.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public abstract class AbstractGenerateWorkload implements IGenerateWorkload {

    protected final Random random;

    protected final Graph<Integer, Integer> fullyConnectedGraph;
    protected final List<Integer> initialEdges;

    protected final List<Integer> vertices;
    protected final List<Integer> insertable;
    protected final List<Integer> removable;

    public AbstractGenerateWorkload(Random random, Graph<Integer, Integer> fullyConnectedGraph, List<Integer> initialEdges) {
        this.random = random;
        this.fullyConnectedGraph = fullyConnectedGraph;
        this.initialEdges = initialEdges;

        this.vertices = new ArrayList<>(fullyConnectedGraph.vertexSet());

        this.insertable = new ArrayList<>(fullyConnectedGraph.edgeSet());
        this.insertable.removeAll(initialEdges);

        this.removable = new ArrayList<>(initialEdges);
    }

    public void forceOneInitialComponent() {
        NaiveGraphConnectivity<Integer, Integer> connectivity = new NaiveGraphConnectivity<>(i -> i);
        vertices.forEach(connectivity::addVertex);
        initialEdges.forEach(e -> connectivity.addEdge(src(e), dest(e), e));

        connectivity.startTemporaryChanges();
        int n = 0;
        while (connectivity.getNbConnectedComponents() != 1) {
            if (insertable.isEmpty()) {
                throw new IllegalStateException(String.valueOf(connectivity.getNbConnectedComponents()));
            }

            int edge = insertable.removeLast();
            removable.add(edge);
            initialEdges.add(edge);
            connectivity.addEdge(src(edge), dest(edge), edge);
            n++;
        }

        if (n > 0) {
            System.out.println(n + " added edges");
        }
    }

    @Override
    public void generateOperations(BufferedWriter bw) throws IOException {
        for (Integer v : vertices) {
            WorkloadUtils.write(bw, ADD_VERTEX, v);
        }
        for (Integer edge : initialEdges) {
            insert(bw, edge);
        }
        WorkloadUtils.write(bw, START_TEMPORARY_CHANGES);
        generateOperationsImpl(bw);
    }

    protected abstract void generateOperationsImpl(BufferedWriter bw) throws IOException;

    protected void randomQuery(BufferedWriter bw) throws IOException {
        int i1 = random.nextInt(vertices.size());
        int i2 = random.nextInt(vertices.size() - 1);
        if (i2 >= i1) {
            i2++; // distinct vertices
        }

        WorkloadUtils.query(bw, vertices.get(i1), vertices.get(i2));
    }

    protected boolean canInsert() {
        return !insertable.isEmpty();
    }

    protected void randomInsert(BufferedWriter bw) throws IOException {
        int toInsert = insertable.remove(random.nextInt(insertable.size()));
        removable.add(toInsert);
        insert(bw, toInsert);
    }

    protected void insert(BufferedWriter bw, int edge) throws IOException {
        WorkloadUtils.insert(bw, src(edge), dest(edge), edge);
    }

    protected boolean canRemove() {
        return !removable.isEmpty();
    }

    protected void randomRemove(BufferedWriter bw) throws IOException {
        int toRemove = removable.remove(random.nextInt(removable.size()));
        insertable.add(toRemove);
        WorkloadUtils.remove(bw, toRemove);
    }

    protected int src(int edge) {
        return fullyConnectedGraph.getEdgeSource(edge);
    }

    protected int dest(int edge) {
        return fullyConnectedGraph.getEdgeTarget(edge);
    }
}
