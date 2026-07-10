/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.characteristics;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.graph.log.Log;
import com.powsybl.openloadflow.graph.log.ProgressFormatter;
import com.powsybl.openloadflow.graph.log.ProgressManager;
import com.powsybl.openloadflow.graph.log.TProgress;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import gnu.trove.map.hash.TIntIntHashMap;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.Pseudograph;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class GraphCharacteristics {

    private static final Log LOG = Log.init();

    private GraphCharacteristics() { }

    public static void main(String[] args) {
        Network network = Network.read(Path.of(args[0]));
        LfNetwork lfNetwork = Networks.load(network, new FirstSlackBusSelector()).getFirst();

        Pseudograph<LfBus, LfBranch> graph = new Pseudograph<>(null, null, false);
        for (LfBus lfBus : lfNetwork.getBuses()) {
            graph.addVertex(lfBus);
        }
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            if (lfBranch != null && lfBranch.getBus1() != null && lfBranch.getBus2() != null) {
                graph.addEdge(lfBranch.getBus1(), lfBranch.getBus2(), lfBranch);
            }
        }

        List<LfBus> vertices = new ArrayList<>(graph.vertexSet());

        diameter(graph, vertices);
        diameterMultithreaded(graph, vertices, Runtime.getRuntime().availableProcessors() - 1);
        degree(graph);
        multiEdges(graph, vertices);
    }

    public static <V, E> void diameter(Graph<V, E> graph, List<V> vertices) {
        ProgressManager<Progress> manager = new ProgressManager<>();
        manager.setFormatter(new Formatter());

        Progress progress = manager.newProgress(new Progress());
        TaskResult result = diameterSingleTask(progress, graph, vertices, 0, vertices.size());

        result.print(vertices.size());
    }

    public static <V, E> void diameterMultithreaded(Graph<V, E> graph, List<V> vertices, int threadCount) {
        ProgressManager<Progress> manager = new ProgressManager<>();
        manager.setFormatter(new Formatter());

        int n = vertices.size();
        int delta = n / threadCount;

        CompletableFuture<TaskResult> result = null;
        for (int i = 0; i < threadCount; i++) {
            int start = delta * i;
            int end = i == threadCount - 1 ? n : start + delta;

            Progress progress = manager.newProgress(new Progress());
            progress.setProgress(0, end - start);
            var future = CompletableFuture.supplyAsync(() -> diameterSingleTask(progress, graph, vertices, start, end));
            if (result == null) {
                result = future;
            } else {
                result = result.thenCombine(future, TaskResult::merge);
            }
        }

        manager.printProgress(true);

        TaskResult taskResult = Objects.requireNonNull(result).join();
        taskResult.print(n);
    }

    private static <V, E> TaskResult diameterSingleTask(Progress progress, Graph<V, E> graph, List<V> vertices, int start, int end) {
        TIntIntHashMap distToCount = new TIntIntHashMap();

        BFSShortestPath<V, E> shortestPath = new BFSShortestPath<>(graph);

        for (int i = start; i < end; i++) {
            ShortestPathAlgorithm.SingleSourcePaths<V, E> pathsFromI = shortestPath.getPaths(vertices.get(i));

            for (int j = 0; j < i; j++) {
                int length = (int) pathsFromI.getWeight(vertices.get(j));
                distToCount.adjustOrPutValue(length, 1, 1);
            }

            progress.setProgress(i - start, end - start);
        }

        return new TaskResult(distToCount);
    }

    private static final class Formatter implements ProgressFormatter<Progress> {

        @Override
        public String format(List<Progress> progresses, long elapsedTime) {
            int progress = 0;
            int total = 0;
            for (Progress p : progresses) {
                progress += p.progress;
                total += p.total;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(progress).append("/").append(total);

            if (progresses.size() > 1) {
                for (Progress p : progresses) {
                    sb.append(" [").append(p.progress).append("/").append(p.total).append("]");
                }
            }

            return sb.toString();
        }
    }

    private static final class Progress extends TProgress<Progress> {

        private int progress;
        private int total;

        public void setProgress(int progress, int total) {
            this.progress = progress;
            this.total = total;
            notifyProgressManager();
        }
    }

    record TaskResult(TIntIntHashMap distToCount) {

        public TaskResult merge(TaskResult other) {
            TIntIntHashMap res = new TIntIntHashMap(distToCount);
            for (var it = other.distToCount.iterator(); it.hasNext();) {
                it.advance();
                res.adjustOrPutValue(it.key(), it.value(), it.value());
            }

            return new TaskResult(res);
        }

        public int get(int i) {
            return distToCount.get(i);
        }

        public long diameter() {
            long max = 0;

            for (var it = distToCount.iterator(); it.hasNext();) {
                it.advance();
                max = Math.max(max, it.key());
            }
            return max;
        }

        public long sumShortestPath() {
            long sum = 0;
            for (var it = distToCount.iterator(); it.hasNext();) {
                it.advance();
                sum += (long) it.key() * it.value();
            }
            return sum;
        }

        public void print(int n) {
            long diameter = diameter();
            long sumShortestPath = sumShortestPath();

            System.out.println("Diameter: " + diameter);
            for (int j = 0; j <= diameter; j++) {
                System.out.println("Number of unordered pair of vertices separated by " + j + " edges : " + get(j));
            }

            System.out.println(sumShortestPath);
            double avgsp = sumShortestPath / (n * (n - 1d) / 2d);
            System.out.println("AVGsp: " + avgsp);
        }
    }

    public static <V, E> void degree(Graph<V, E> graph) {
        int minDegree = Integer.MAX_VALUE;
        int maxDegree = Integer.MIN_VALUE;
        int averageDegree = 0;

        TIntIntHashMap degreeToCount = new TIntIntHashMap();

        for (V v : graph.vertexSet()) {
            int degree = graph.degreeOf(v);
            minDegree = Math.min(degree, minDegree);
            maxDegree = Math.max(degree, maxDegree);

            averageDegree += degree;
            degreeToCount.adjustOrPutValue(degree, 1, 1);
        }

        System.out.printf("Min - Average - Max: %d, %f, %d%n",
                minDegree, averageDegree / (double) graph.vertexSet().size(), maxDegree);

        for (int i = 0; i <= maxDegree; i++) {
            System.out.println("Number of vertex of degree " + i + ": " + degreeToCount.get(i));
        }
    }

    public static <V, E> void multiEdges(Graph<V, E> graph, List<V> vertices) {
        int multiEdgeCount = 0;
        int loop = 0;
        for (int i = 0; i < vertices.size(); i++) {
            V vi = vertices.get(i);

            for (int j = 0; j < i; j++) {
                V vj = vertices.get(j);

                if (graph.getAllEdges(vi, vj).size() >= 2) {
                    multiEdgeCount++;
                }
            }

            if (!graph.getAllEdges(vi, vi).isEmpty()) {
                loop++;
            }
        }

        System.out.println("Number of vertices having loop: " + loop);
        System.out.println("Number of multi-edges: " + multiEdgeCount);
    }
}
