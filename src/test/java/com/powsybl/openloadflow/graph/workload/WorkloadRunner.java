/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.DTreeStandalone;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.dtree.DTNode;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.generators.WorkloadUtils;
import com.powsybl.openloadflow.graph.log.Log;
import com.powsybl.openloadflow.graph.log.ProgressFormatter;
import com.powsybl.openloadflow.graph.log.ProgressManager;
import com.powsybl.openloadflow.graph.log.TProgress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class WorkloadRunner {

    private WorkloadRunner() {

    }

    private static final BenchmarkParameters PERFORMANCE = new BenchmarkParameters.Performance()
            .setWarmup(20)
            .setMeasurement(10)
            .setOutput("results/workload/${workload}/${class}_no_tree_edge_boolean.${ext}")
            .setOverwrite(true); //.setReplacement("results/workload/${workload}/${class}_list_of_root.${ext}");
    private static final BenchmarkParameters VALIDATOR = new BenchmarkParameters.Validator();
    private static final BenchmarkParameters STATS_WRITER = new BenchmarkParameters.StatsWriter()
            .setOutput("graph_stats/data/${workload}/${class}/${operations}");

    private static final BenchmarkParameters WORKLOAD_PARAMS = PERFORMANCE;

    private static final Log LOG = Log.init("results.txt");
    private static final MyProgressManager PROGRESS = new MyProgressManager();

    public static void main(String[] args) throws IOException {
        //List<Workload> workloads = getAllWorkloads(Path.of("workload/"), Set.of()); //, Set.of("spy_10000_10_10_10000_10_10_2026-07-09T08:47:18.906235251Z.zip"));
        List<Workload> workloads = List.of(
                Workload.inMemory(Path.of("workload/spy_5541_1_1_2026-07-03T12:31:54.685462530Z.txt")),
                Workload.inMemory(Path.of("workload/spy_5541_1_1_5541_1_1_2026-07-03T11:50:06.510031405Z.txt")),
                Workload.inMemory(Path.of("workload/spy_10000_10_10_10000_10_10_2026-08-07T07:59:16.649371906Z.zip"))
        );

        List<GraphConnectivityFactory<Integer, Integer>> factories = List.of(
                // new OldNaiveGraphConnectivity.Factory<>((Integer i) -> i)
                // new NaiveGraphConnectivityFactory<>((Integer i) -> i)
                // new MinimumSpanningTreeGraphConnectivityFactory<>(),
                // new EvenShiloachGraphDecrementalConnectivityFactory<>(),
                // new HolmEtAlGraphConnectivityFactory<>(),
                // new HolmEtAlWithoutLevelGraphConnectivityFactory<>(),
                // new NewHolmGraphConnectivityFactory<>(),
                // new HolmStandaloneFactory<>()
                new DTreeGraphConnectivityFactory<>()
                // new DTreeStandaloneFactory<>()
                // new Delta2DTreeStandalone.Factory<>(),
                // new Delta2ReplaceWithBestDTreeStandalone.Factory<>(),
                // new ReplaceWithBestDTreeStandalone.Factory<>()
                // IDTreeStandalone::new,
                // new IndexedDTreeStandalone2ndVerFactory<>((Integer i) -> i, (Integer i) -> i)
                // new DnDTreeStandaloneFactory<>()
                //new OptDTreeStandaloneFactory<>()
        );

        LOG.log("Workloads:");
        for (Workload w : workloads) {
            LOG.log("%s", w.source());
        }

        try (ExecutorService executor = createExecutorIfNeeded(workloads)) {
            for (Workload workload : workloads) {
                LOG.log("-----------------------------------------");
                LOG.log("Running workload at %s", workload.source());
                for (GraphConnectivityFactory<Integer, Integer> factory : factories) {
                    if (factory instanceof EvenShiloachGraphDecrementalConnectivityFactory<?, ?> && workload.type() != Workload.Type.DECREMENTAL) {
                        LOG.log("skipping EvenShiloachGraphDecrementalConnectivity, because of a %s workload", workload.type());
                        continue;
                    }

                    PROGRESS.advance(factory);
                    ISpyGraphConnectivityFactory<Integer, Integer> spy = WORKLOAD_PARAMS.wrapIntoSpyFactory(workload, factory);
                    String partialResults = run(executor, workload, spy, WORKLOAD_PARAMS.warmup(), WORKLOAD_PARAMS.measurement());
                    LOG.log(partialResults);
                }
            }
        }

        System.out.println(DTNode.N.get());
        System.out.println(DTreeStandalone.N.get());
    }

    private static List<Workload> getAllWorkloads(Path folder, Set<String> filter) {
        try (Stream<Path> directory = Files.list(folder)) {
            return directory.filter(Files::isRegularFile)
                    .filter(p -> !filter.contains(p.getFileName().toString()))
                    .map(p -> {
                        try {
                            return Workload.inMemory(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ExecutorService createExecutorIfNeeded(List<Workload> workloads) {
        int threadNeeded = 0;
        for (Workload w : workloads) {
            threadNeeded = Math.max(threadNeeded, w.threadCount());
        }

        if (threadNeeded > 1) {
            return ExecutorWithException.newFixedThreadPool(threadNeeded);
        } else {
            return null;
        }
    }

    private static String run(ExecutorService executor,
                              Workload workload,
                              ISpyGraphConnectivityFactory<Integer, Integer> factory,
                              int warmup,
                              int measurement) {
        if (workload.threadCount() > 1) {
            runMultiThreadedWorkload(executor, workload, factory, warmup, measurement);
        } else {
            runSingleThreadedWorkload(workload, factory, warmup, measurement);
        }

        return factory.resultsToString(measurement);
    }

    private static void runSingleThreadedWorkload(Workload workload,
                                                  ISpyGraphConnectivityFactory<Integer, Integer> factory,
                                                  int warmup,
                                                  int measurement) {
        try (Operations operations = workload.operations(0)) {
            var progress = PROGRESS.newProgress(new Progress());
            runOperationsMultipleTimesWithWarmup(progress, operations, 0, factory, null, warmup, measurement);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Run the workload using the given executor and GraphConnectivity.
     * One task is created for each Operations in workload. They are all
     * ran in parallel WARMUP + MEASUREMENT times. All tasks will
     * wait each other once the task Operations is done, before starting
     * a new run.
     */
    private static void runMultiThreadedWorkload(ExecutorService executor,
                                                   Workload workload,
                                                   ISpyGraphConnectivityFactory<Integer, Integer> factory,
                                                   int warmup,
                                                   int measurement) {
        CyclicBarrier barrier = new CyclicBarrier(workload.threadCount());

        // launch each Operations in a thread
        List<Future<?>> futures = new ArrayList<>();
        for (int thread = 0; thread < workload.threadCount(); thread++) {
            final int threadId = thread;
            Progress progress = PROGRESS.newProgress(new Progress());

            var future = executor.submit(() -> {
                try (Operations operations = workload.operations(threadId)) {
                    runOperationsMultipleTimesWithWarmup(progress, operations, threadId, factory, barrier, warmup, measurement);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            futures.add(future);
        }

        // wait completion of each future
        for (var future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void runOperationsMultipleTimesWithWarmup(Progress progress,
                                                             Operations operations,
                                                             int threadId,
                                                             ISpyGraphConnectivityFactory<Integer, Integer> spy,
                                                             CyclicBarrier barrier,
                                                             int warmup,
                                                             int measurement) {
        if (warmup > 0) {
            progress.newIterations(warmup, IterationType.WARMUP);

            if (threadId == 0) {
                spy.beginIterations(warmup, IterationType.WARMUP);
            }
            runOperationsMultipleTimes(progress, operations, spy.createUnregistered(operations), barrier, warmup);

            if (threadId == 0) {
                spy.endIterations(warmup, IterationType.WARMUP);
            }
        }
        if (measurement > 0) {
            progress.newIterations(measurement, IterationType.MEASURE);

            if (threadId == 0) {
                spy.beginIterations(measurement, IterationType.MEASURE);
            }
            runOperationsMultipleTimes(progress, operations, spy.create(operations), barrier, measurement);
            if (threadId == 0) {
                spy.endIterations(measurement, IterationType.MEASURE);
            }
        }
    }

    /**
     * Run the given operations num times, each time using
     * a new GraphConnectivity provided by factory. Results will
     * be accumulated in the given SpyGraphConnectivity.
     */
    private static void runOperationsMultipleTimes(Progress progress,
                                                   Operations operations,
                                                   ISpyGraphConnectivity<Integer, Integer> spy,
                                                   CyclicBarrier barrier,
                                                   int num) {
        for (int i = 0; i < num; i++) {
            progress.newIteration(i);
            spy.newDelegate();
            runOperations(progress, operations, spy);

            if (barrier != null) {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Run the operations on the given DelegateGraphConnectivity
     */
    private static void runOperations(Progress progress,
                                      Operations operations,
                                      ISpyGraphConnectivity<Integer, Integer> spy) {
        spy.beginOperations(operations);
        operations.reset();

        int n = 0;
        while (operations.hasNext()) {
            spy.notifyOperation(n + 1);
            operations.next().execute(spy);
            n++;

            progress.newOperation(n, operations.size());
        }

        spy.endOperations(operations);
    }

    private static final class MyProgressManager extends ProgressManager<Progress> implements ProgressFormatter<Progress> {

        private GraphConnectivityFactory<?, ?> connectivity;

        MyProgressManager() {
            setFormatter(this);
        }

        public void advance(GraphConnectivityFactory<?, ?> connectivity) {
            this.connectivity = connectivity;
            removeAll();
        }

        @Override
        public String format(List<Progress> progresses, long elapsedTime) {
            Progress first = progresses.getFirst();

            int progress = 0;
            int total = 0;
            for (Progress p : progresses) {
                progress += p.operation;
                total += p.maxOperation;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(WorkloadUtils.getClassName(connectivity.getClass())).append(": ");
            switch (first.iterType) {
                case WARMUP -> {
                    appendProgress(sb, first.iter, first.maxIter);
                    sb.append(" (warmup)");
                }
                case MEASURE -> appendProgress(sb, first.iter, first.maxIter);
            }
            sb.append(" - ");
            appendProgress(sb, progress, total);

            if (progresses.size() > 1) {
                for (Progress p : progresses) {
                    sb.append(" [");
                    appendProgress(sb, p.operation, p.maxOperation);
                    sb.append("]");
                }
            }

            return sb.toString();
        }

        private void appendProgress(StringBuilder sb, int current, int max) {
            int emptySpace = digits(max) - digits(current);

            sb.repeat(" ", Math.max(0, emptySpace))
                    .append(current)
                    .append("/")
                    .append(max);
        }

        private int digits(int value) {
            if (value == 0) {
                return 1;
            } else if (value < 0) {
                return (int) (Math.log10(-value) + 2);
            } else {
                return (int) (Math.log10(value) + 1);
            }
        }

        public Progress get(int i) {
            return progress.get(i);
        }
    }

    private static final class Progress extends TProgress<Progress> {

        private IterationType iterType;
        private int iter;
        private int maxIter;

        private int operation;
        private int maxOperation;

        public void newIterations(int maxIter, IterationType iterType) {
            this.maxIter = maxIter;
            this.iterType = iterType;
            notifyProgressManager();
        }

        public void newIteration(int iteration) {
            iter = iteration;
            notifyProgressManager();
        }

        public void newOperation(int operation, int maxOperation) {
            this.operation = operation;
            this.maxOperation = maxOperation;
            notifyProgressManager();
        }
    }
}
