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
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RandomWorkload extends AbstractGenerateWorkload {

    private static final int QUERY = 0;
    private static final int INSERT = 1;
    private static final int REMOVE = 2;

    public int query;
    public int insert;
    public int remove;
    public int computeSd;

    public RandomWorkload(Random random, Graph<Integer, Integer> fullyConnectedGraph, List<Integer> defaultEdges) {
        super(random, fullyConnectedGraph, defaultEdges);
    }

    @Override
    public String getName() {
        return "random_" + query + "_" + insert + "_" + remove;
    }

    @Override
    protected void generateOperationsImpl(BufferedWriter bw) throws IOException {
        int total = query + insert + remove;

        int[] opRemaining = new int[3];
        opRemaining[QUERY] = query;
        opRemaining[INSERT] = insert;
        opRemaining[REMOVE] = remove;

        if (computeSd > 0) {
            Assertions.assertEquals(0, total % computeSd, "the total number of update should be a multiple of computeSd");

            WorkloadUtils.sd(bw);
            int delta = total / computeSd;
            for (int i = 0; i < computeSd; i++) {
                generateN(bw, opRemaining, delta);
            }
            WorkloadUtils.sd(bw);

            Assertions.assertEquals(0, Arrays.stream(opRemaining).sum());
        } else {
            generateN(bw, opRemaining, total);
        }

        System.out.println("Remaining query/insert/remove: " + opRemaining[QUERY] + "/" + opRemaining[INSERT] + "/" + opRemaining[REMOVE]);
    }

    private void generateN(BufferedWriter bw, int[] opRemaining, int limit) throws IOException {
        for (int i = 0; i < limit; i++) {
            int opId = nextOpId(opRemaining);
            if (opId < 0) {
                opId = QUERY;
                System.out.println("falling back to query");
            }

            switch (opId) {
                case QUERY -> randomQuery(bw);
                case INSERT -> randomInsert(bw);
                case REMOVE -> randomRemove(bw);
            }

            opRemaining[opId] -= 1;
        }
    }

    private int nextOpId(int[] opRemaining) {
        int sum = Arrays.stream(opRemaining).sum();

        int n = random.nextInt(sum);
        int partialSum = 0;
        for (int i = 0; i < opRemaining.length; i++) {
            partialSum += opRemaining[i];
            if (n < partialSum && (i == QUERY || i == INSERT && canInsert() || i == REMOVE && canRemove())) {
                return i;
            }
        }

        return -1;
    }
}
