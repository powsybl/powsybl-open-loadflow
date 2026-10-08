/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.workload;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.SpanningForestGraphConnectivity;

import java.util.Random;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public sealed interface Operation {

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
            case "get_comp" -> {
                if (parts.length >= 3) {
                    yield new GetConnectedComponent(parts[1], Integer.parseInt(parts[2]));
                } else {
                    yield new GetConnectedComponent(null, Integer.parseInt(parts[1]));
                }
            }
            case "largest" -> {
                if (parts.length >= 2) {
                    yield new GetLargestConnectedComponent(parts[1]);
                } else {
                    yield new GetLargestConnectedComponent(null);
                }
            }
            case "comp_get_num" -> new ComponentGetNum(parts[0]);
            case "comp_to_owned_set" -> new ComponentToOwnedSet(parts[0]);
            case "comp_size" -> new ComponentSize(parts[0]);
            case "comp_contains" -> new ComponentContains(parts[0], Integer.parseInt(parts[1]));
            case "v_added" -> GetVerticesAddedToMainComponent.INSTANCE;
            case "e_added" -> GetEdgesAddedToMainComponent.INSTANCE;
            case "v_removed" -> GetVerticesRemovedFromMainComponent.INSTANCE;
            case "e_removed" -> GetEdgesRemovedFromMainComponent.INSTANCE;
            case "q" -> new Connected(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            case "testpoint" -> new TestPoint(Integer.parseInt(parts[1]), Long.parseLong(parts[2]));
            case "Sd" -> ComputeSd.INSTANCE;
            case "new" -> New.INSTANCE;
            default -> null;
        };
    }

    void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context);

    record AddVertex(int vertex) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.addVertex(vertex);
        }
    }

    record AddEdge(int u, int v, int e) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.addEdge(u, v, e);
        }
    }

    record RemoveEdge(int e) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.removeEdge(e);
        }
    }

    record StartTemporaryChanges(boolean computeComparisons) implements Operation {

        public static final Operation TRUE = new StartTemporaryChanges(true);
        public static final Operation FALSE = new StartTemporaryChanges(false);

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.startTemporaryChanges(computeComparisons);
        }
    }

    record UndoTemporaryChanges() implements Operation {

        public static final UndoTemporaryChanges INSTANCE = new UndoTemporaryChanges();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.undoTemporaryChanges();
        }
    }

    record GetComponentNumber(int vertex) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.getComponentNumber(vertex);
        }
    }

    record SetMainComponentVertex(int vertex) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.setMainComponentVertex(vertex);
        }
    }

    record GetNbConnectedComponents() implements Operation {

        public static final GetNbConnectedComponents INSTANCE = new GetNbConnectedComponents();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.getNbConnectedComponents();
        }
    }

    record GetConnectedComponent(String name, int vertex) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            context.newComponent(connectivity.getConnectedComponent(vertex), name);
        }
    }

    record GetLargestConnectedComponent(String name) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            context.newComponent(connectivity.getLargestConnectedComponent(), name);
        }
    }

    record ComponentGetNum(String compName) implements Operation {
        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            context.getComponent(compName).getNumber();
        }
    }

    record ComponentToOwnedSet(String compName) implements Operation {
        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            context.getComponent(compName).toOwnedSet();
        }
    }

    record ComponentSize(String compName) implements Operation {
        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            context.getComponent(compName).size();
        }
    }

    record ComponentContains(String compName, int element) implements Operation {
        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            context.getComponent(compName).contains(element);
        }
    }

    record GetVerticesAddedToMainComponent() implements Operation {

        public static final GetVerticesAddedToMainComponent INSTANCE = new GetVerticesAddedToMainComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.getVerticesAddedToMainComponent();
        }
    }

    record GetEdgesAddedToMainComponent() implements Operation {

        public static final GetEdgesAddedToMainComponent INSTANCE = new GetEdgesAddedToMainComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.getEdgesAddedToMainComponent();
        }
    }

    record GetEdgesRemovedFromMainComponent() implements Operation {

        public static final GetEdgesRemovedFromMainComponent INSTANCE = new GetEdgesRemovedFromMainComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.getEdgesRemovedFromMainComponent();
        }
    }

    record GetVerticesRemovedFromMainComponent() implements Operation {

        public static final GetVerticesRemovedFromMainComponent INSTANCE = new GetVerticesRemovedFromMainComponent();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.getVerticesRemovedFromMainComponent();
        }
    }

    record Connected(int u, int v) implements Operation {

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            connectivity.connected(u, v);
        }
    }

    record TestPoint(int vertexCount, long seed) implements Operation {

        public static final int LIMIT = 1_000_000;

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            if (vertexCount * (vertexCount - 1) / 2 <= LIMIT) {
                // test connectivity for EVERY pair of vertices

                for (int i = 0; i < vertexCount; i++) {
                    for (int j = 0; j < vertexCount; j++) {
                        connectivity.connected(i, j);
                    }
                }

            } else {
                Random random = new Random(seed);

                for (int i = 0; i < LIMIT; i++) {
                    int v1 = random.nextInt(vertexCount);
                    int v2 = random.nextInt(v1 + 1);
                    connectivity.connected(v1, v2);
                }
            }
        }
    }

    record ComputeSd() implements Operation {

        public static final ComputeSd INSTANCE = new ComputeSd();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            if (connectivity instanceof SpanningForestGraphConnectivity<Integer, Integer> spanningForest) {
                spanningForest.computeSumOfDistances();
            }
        }
    }

    record New() implements Operation {

        public static final New INSTANCE = new New();

        @Override
        public void execute(GraphConnectivity<Integer, Integer> connectivity, ExecutionContext context) {
            if (connectivity instanceof ISpyGraphConnectivity<Integer, Integer> spy) {
                spy.newDelegate();
            }
        }
    }
}
