/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.characteristics;

import com.powsybl.openloadflow.graph.log.Log;
import com.powsybl.openloadflow.graph.log.ProgressFormatter;
import com.powsybl.openloadflow.graph.log.ProgressManager;
import com.powsybl.openloadflow.graph.log.TProgress;
import gnu.trove.map.hash.TIntIntHashMap;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.BFSShortestPath;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class Diameter {

    private Diameter() { }

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

            Log.get().log("Diameter: %d", diameter);
            for (int j = 0; j <= diameter; j++) {
                Log.get().log("Number of unordered pair of vertices separated by %d edges: %d", j, get(j));
            }

            Log.get().log("%d", sumShortestPath);
            double avgsp = sumShortestPath / (n * (n - 1d) / 2d);
            Log.get().log("AVGsp: %f", avgsp);
        }
    }
}
