/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.generators;

import com.powsybl.openloadflow.graph.PerfUtils;
import org.jgrapht.Graph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static com.powsybl.openloadflow.graph.utils.GraphConnectivityMethod.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class PowSyBlWorkload extends AbstractGenerateWorkload {

    public int contingencyCount;
    public int minEdgePerContingency;
    public int maxEdgePerContingency;

    public int actionPerContingency;
    public int minEdgePerAction;
    public int maxEdgePerAction;

    public PowSyBlWorkload(Random random, Graph<Integer, Integer> fullyConnectedGraph,
                           List<Integer> defaultEdges) {
        super(random, fullyConnectedGraph, defaultEdges);
    }

    public PowSyBlWorkload(Random random, Graph<Integer, Integer> fullyConnectedGraph,
                           List<Integer> defaultEdges,
                           List<Integer> lines,
                           List<Integer> switches) {
        super(random, fullyConnectedGraph, defaultEdges);

        insertable.clear();
        removable.clear();

        removable.addAll(lines);
        insertable.addAll(switches);

        // removable edges must be in defaultEdges at the beginning
        defaultEdges.addAll(removable);
        // insertable edges mustn't be in defaultEdges at the beginning
        insertable.removeAll(defaultEdges);
    }

    @Override
    public String getName() {
        return "powsybl_%d_%d_%d_%d_%d_%d".formatted(contingencyCount, minEdgePerContingency, maxEdgePerContingency, actionPerContingency, minEdgePerAction, maxEdgePerAction);
    }

    @Override
    protected void generateOperationsImpl(BufferedWriter bw) throws IOException {
        for (int i = 0; i < contingencyCount; i++) {
            if (i > 0 || actionPerContingency > 0) { // for even shiloach
                WorkloadUtils.write(bw, START_TEMPORARY_CHANGES);
            }

            List<Integer> toRemove = PerfUtils.sample(random, removable, minEdgePerContingency, maxEdgePerContingency).toList();

            for (int edge : toRemove) {
                WorkloadUtils.remove(bw, edge);
            }

            // connectivity queries
            WorkloadUtils.sd(bw);
            WorkloadUtils.write(bw, GET_COMPONENT_NUMBER, 0);
            WorkloadUtils.write(bw, GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT);

            for (int action = 0; action < actionPerContingency; action++) {
                WorkloadUtils.write(bw, START_TEMPORARY_CHANGES);
                List<Integer> toAdd = PerfUtils.sample(random, insertable, minEdgePerAction, maxEdgePerAction).toList();

                for (int edge : toAdd) {
                    insert(bw, edge);
                }

                // connectivity queries
                WorkloadUtils.sd(bw);
                WorkloadUtils.write(bw, GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT);
                WorkloadUtils.write(bw, GET_EDGES_REMOVED_FROM_MAIN_COMPONENT);
                WorkloadUtils.write(bw, GET_VERTICES_ADDED_TO_MAIN_COMPONENT);
                WorkloadUtils.write(bw, GET_EDGES_ADDED_TO_MAIN_COMPONENT);

                WorkloadUtils.write(bw, UNDO_TEMPORARY_CHANGES);
            }

            WorkloadUtils.write(bw, UNDO_TEMPORARY_CHANGES);
        }
    }
}
