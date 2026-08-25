/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.SpanningForestGraphConnectivity;

import java.util.Random;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public sealed interface Operation {

    void execute(GraphConnectivity<Integer, Integer> connectivity);

    static Operation deserialize(String line) {
        String[] parts = line.split(" ");

        return switch (parts[0]) {
            case "v" -> new AddVertex(Integer.parseInt(parts[1]));
            case "e" -> new AddEdge(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            case "rm" -> new RemoveEdge(Integer.parseInt(parts[1]));
            case "start" -> {
                if (parts.length == 2 && parts[1].equals("true")) {
                    yield StartTemporaryChanges.TRUE;
                } else {
                    yield StartTemporaryChanges.FALSE;
                }
            }
            case "undo" -> UndoTemporaryChanges.INSTANCE;
            case "get_num" -> new GetComponentNumber(Integer.parseInt(parts[1]));
            case "set_main" -> new SetMainComponentVertex(Integer.parseInt(parts[1]));
            case "count" -> GetNbConnectedComponents.INSTANCE;
            case "get_comp" -> new GetConnectedComponent(Integer.parseInt(parts[1]));
            case "largest" -> GetLargestConnectedComponent.INSTANCE;
            case "v_added" -> GetVerticesAddedToMainComponent.INSTANCE;
            case "e_added" -> GetEdgesAddedToMainComponent.INSTANCE;
            case "v_removed" -> GetVerticesRemovedFromMainComponent.INSTANCE;
            case "e_removed" -> GetEdgesRemovedFromMainComponent.INSTANCE;
            case "q" -> new Query(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            case "testpoint" -> new TestPoint(Integer.parseInt(parts[1]), Long.parseLong(parts[2]));
            case "Sd" -> ComputeSd.INSTANCE;
            case "new" -> New.INSTANCE;
            default -> null;
        };
    }

    record AddVertex(int vertex) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.addVertex(vertex);
        }
    }

    record AddEdge(int u, int v, int e) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.addEdge(u, v, e);
        }
    }

    record RemoveEdge(int e) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.removeEdge(e);
        }
    }

    record StartTemporaryChanges(boolean computeComparisons) implements Operation {

        public static final Operation TRUE = new StartTemporaryChanges(true);
        public static final Operation FALSE = new StartTemporaryChanges(false);

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.startTemporaryChanges(computeComparisons);
        }
    }

    record UndoTemporaryChanges() implements Operation {

        public static final UndoTemporaryChanges INSTANCE = new UndoTemporaryChanges();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.undoTemporaryChanges();
        }
    }

    record GetComponentNumber(int vertex) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.getComponentNumber(vertex);
        }
    }

    record SetMainComponentVertex(int vertex) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.setMainComponentVertex(vertex);
        }
    }

    record GetNbConnectedComponents() implements Operation {

        public static final GetNbConnectedComponents INSTANCE = new GetNbConnectedComponents();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.getNbConnectedComponents();
        }
    }

    record GetConnectedComponent(int vertex) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.getConnectedComponent(vertex);
        }
    }

    record GetLargestConnectedComponent() implements Operation {

        public static final GetLargestConnectedComponent INSTANCE = new GetLargestConnectedComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.getLargestConnectedComponent();
        }
    }

    record GetVerticesAddedToMainComponent() implements Operation {

        public static final GetVerticesAddedToMainComponent INSTANCE = new GetVerticesAddedToMainComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.getVerticesAddedToMainComponent();
        }
    }

    record GetEdgesAddedToMainComponent() implements Operation {

        public static final GetEdgesAddedToMainComponent INSTANCE = new GetEdgesAddedToMainComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.getEdgesAddedToMainComponent();
        }
    }

    record GetEdgesRemovedFromMainComponent() implements Operation {

        public static final GetEdgesRemovedFromMainComponent INSTANCE = new GetEdgesRemovedFromMainComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.getEdgesRemovedFromMainComponent();
        }
    }

    record GetVerticesRemovedFromMainComponent() implements Operation {

        public static final GetVerticesRemovedFromMainComponent INSTANCE = new GetVerticesRemovedFromMainComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            connectivity.getVerticesRemovedFromMainComponent();
        }
    }

    record Query(int u, int v) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            query(connectivity, u, v);
        }

        public static void query(GraphConnectivity<Integer, Integer> conn, int a, int b) {
            conn.getComponentNumber(a);
            conn.getComponentNumber(b);
        }
    }

    record TestPoint(int vertexCount, long seed) implements Operation {

        public static final int LIMIT = 1_000_000;

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            if (vertexCount * (vertexCount - 1) / 2 <= LIMIT) {
                // test connectivity for EVERY pair of vertices

                for (int i = 0; i < vertexCount; i++) {
                    for (int j = 0; j < vertexCount; j++) {
                        Query.query(connectivity, i, j);
                    }
                }

            } else {
                Random random = new Random(seed);

                for (int i = 0; i < LIMIT; i++) {
                    int v1 = random.nextInt(vertexCount);
                    int v2 = random.nextInt(v1 + 1);
                    Query.query(connectivity, v1, v2);
                }
            }
        }
    }

    record ComputeSd() implements Operation {

        public static final ComputeSd INSTANCE = new ComputeSd();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            if (connectivity instanceof SpanningForestGraphConnectivity<Integer, Integer> spanningForest) {
                spanningForest.computeSumOfDistances();
            }
        }
    }

    record New() implements Operation {

        public static final New INSTANCE = new New();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity) {
            if (connectivity instanceof ISpyGraphConnectivity<Integer, Integer> spy) {
                spy.newDelegate();
            }
        }
    }
}
