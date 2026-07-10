/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.generators;

import org.jgrapht.Graph;
import org.junit.jupiter.api.Assertions;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class ChenEtAlWorkload extends AbstractGenerateWorkload {

    public int numberOfUpdate;
    public double ur;
    public int testingPoints;

    public ChenEtAlWorkload(Random random, Graph<Integer, Integer> graph, List<Integer> defaultEdges) {
        super(random, graph, defaultEdges);
    }

    @Override
    public String getName() {
        return "chen_" + numberOfUpdate + "_" + ur + "_" + testingPoints;
    }

    @Override
    protected void generateOperationsImpl(BufferedWriter bw) throws IOException {
        Assertions.assertEquals(0, numberOfUpdate % testingPoints, "numberOfUpdate should be a multiple of testingPoints");
        // numberOfUpdate = insert + remove
        // ur = insert / remove
        int remove = (int) (numberOfUpdate / (ur + 1));
        int insert = numberOfUpdate - remove;

        int updateBetweenTests = numberOfUpdate / testingPoints;
        System.out.printf("Generate a workload with %d insertions, %d deletions and %d testing points%n", insert, remove, testingPoints);

        int maxEdgeInGraph = 0;
        for (int i = 0; i < testingPoints; i++) {

            // first do a certain number of updates
            for (int u = 0; u < updateBetweenTests; u++) {
                boolean shouldInsert = random.nextInt(remove + insert) < insert;

                if (shouldInsert && !canInsert()) {
                    System.err.println("can't insert because there is no edge to insert");
                }

                if (!shouldInsert && !canRemove()) {
                    System.err.println("can't remove because there is no edge to remove");
                }

                if (shouldInsert && canInsert() || !canRemove()) {
                    randomInsert(bw);
                    insert--;
                } else {
                    randomRemove(bw);
                    remove--;
                }

                maxEdgeInGraph = Math.max(maxEdgeInGraph, removable.size());
            }

            // then test connectivity
            long seed = new Random().nextLong();
            WorkloadUtils.testPoint(bw, vertices.size(), seed);
        }

        System.out.println("Remaining insert/remove: " + insert + " - " + remove);
        System.out.println("Max edge in graph: " + maxEdgeInGraph + " / " + (insertable.size() + removable.size()));
    }
}
