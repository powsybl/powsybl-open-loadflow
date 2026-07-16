/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.*;
import com.powsybl.openloadflow.graph.generators.WorkloadUtils;
import com.powsybl.openloadflow.graph.log.Log;
import com.powsybl.openloadflow.graph.log.ProgressFormatter;
import com.powsybl.openloadflow.graph.log.ProgressManager;
import com.powsybl.openloadflow.graph.log.TProgress;

import java.io.IOException;
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

    private static final int WARMUP = 10;
    private static final int MEASUREMENT = 10;

    private static final Log LOG = Log.init("results.txt");
    private static final MyProgressManager PROGRESS = new MyProgressManager();

    public static void main(String[] args) throws IOException {
        // List<Workload> workloads = getAllWorkloads(Path.of("workload/"), Set.of("spy_10000_10_10_10000_10_10_2026-07-09T08:47:18.906235251Z.zip"));
        List<Workload> workloads = List.of(
                Workload.inMemory(Path.of("workload/spy_5541_1_1_5541_1_1_2026-07-03T11:50:06.510031405Z.txt"))
        );

        List<GraphConnectivityFactory<Integer, Integer>> factories = List.of(
                //new OldNaiveGraphConnectivity.Factory<>((Integer i) -> i)
                // new NaiveGraphConnectivityFactory<>((Integer i) -> i)
                // new MinimumSpanningTreeGraphConnectivityFactory<>(),
                // new EvenShiloachGraphDecrementalConnectivityFactory<>(),
                // new HolmEtAlGraphConnectivityFactory<>(),
                // new HolmEtAlWithoutLevelGraphConnectivityFactory<>(),
                new DTreeGraphConnectivityFactory<>(),
                new DTreeStandaloneFactory<>()
                //new NewDTreeGraphConnectivityFactory<>(i -> i, i -> i)
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
                    String partialResults = run(executor, workload, factory);
                    LOG.log(partialResults);
                }
            }
        }
    }

    private static List<Workload> getAllWorkloads(Path folder, Set<String> filter) {
        try (Stream<Path> directory = Files.list(folder)) {
            return directory.filter(Files::isRegularFile)
                    .filter(p -> !filter.contains(p.getFileName().toString()))
                    .map(p -> {
                        try {
                            return Workload.inMemory(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
                              GraphConnectivityFactory<Integer, Integer> factory) {
        if (workload.threadCount() > 1) {
            return runMultiThreadedWorkload(executor, workload, factory);
        } else {
            return runSingleThreadedWorkload(workload, factory);
        }
    }

    private static String runSingleThreadedWorkload(Workload workload, GraphConnectivityFactory<Integer, Integer> factory) {
        SpyGraphConnectivity<Integer, Integer> spy = new SpyGraphConnectivity<>();

        try (Operations operations = workload.operations(0)) {
            runOperationsMultipleTimes(PROGRESS.newProgress(new Progress()), operations, spy, factory, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return spy.resultsToString(MEASUREMENT);
    }

    /**
     * Run the workload using the given executor and GraphConnectivity.
     * One task is created for each Operations in workload. They are all
     * ran in parallel WARMUP + MEASUREMENT times. All tasks will
     * wait each other once the task Operations is done, before starting
     * a new run.
     */
    private static String runMultiThreadedWorkload(ExecutorService executor,
                                                   Workload workload,
                                                   GraphConnectivityFactory<Integer, Integer> factory) {
        SpyGraphConnectivityFactory<Integer, Integer> spyFactory = new SpyGraphConnectivityFactory<>(factory);
        CyclicBarrier barrier = new CyclicBarrier(workload.threadCount());

        // launch each Operations in a thread
        List<Future<?>> futures = new ArrayList<>();
        for (int thread = 0; thread < workload.threadCount(); thread++) {
            final int threadId = thread;
            Progress progress = PROGRESS.newProgress(new Progress());

            var future = executor.submit(() -> {
                try (Operations operations = workload.operations(threadId)) {
                    runOperationsMultipleTimes(progress, operations, spyFactory.create(), factory, barrier);
                } catch (IOException e) {
                    throw new RuntimeException(e);
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

        return spyFactory.resultsToString(MEASUREMENT);
    }

    /**
     * Run the given operations WARMUP + MEASUREMENT times, each time using
     * a new GraphConnectivity provided by factory. The results for the MEASUREMENT
     * last iterations will be accumulated in the give SpyGraphConnectivity.
     */
    private static void runOperationsMultipleTimes(
            Progress progress,
            Operations operations,
            SpyGraphConnectivity<Integer, Integer> spy,
            GraphConnectivityFactory<Integer, Integer> factory,
            CyclicBarrier barrier) {
        // warmup
        SpyGraphConnectivity<Integer, Integer> warmup = new SpyGraphConnectivity<>();
        for (int i = 0; i < WARMUP; i++) {
            progress.newIteration(i, true);
            warmup.setDelegate(factory.create());
            runOperations(progress, operations, warmup);

            if (barrier != null) {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // actual measurement
        for (int i = 0; i < MEASUREMENT; i++) {
            progress.newIteration(i, false);
            spy.setDelegate(factory.create());
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
     * Run the operations on the given SpyGraphConnectivity
     */
    private static void runOperations(Progress progress,
                                      Operations operations,
                                      SpyGraphConnectivity<Integer, Integer> spy) {
        spy.setInitialGraphBuildDone(false);
        operations.reset();

        int n = 0;
        while (operations.hasNext()) {
            WorkloadUtils.executeFromLine(spy, operations.next());
            n++;

            progress.newOperation(n, operations.size());
        }
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
            sb.append(connectivity.getClass().getSimpleName()).append(": ")
                    .append(first.iter).append("/");
            if (first.warmup) {
                sb.append(WARMUP).append(" (warmup)");
            } else {
                sb.append(MEASUREMENT);
            }
            sb.append(" - ").append(progress).append("/").append(total);

            if (progresses.size() > 1) {
                for (Progress p : progresses) {
                    sb.append(" [").append(p.operation).append("/").append(p.maxOperation).append("]");
                }
            }

            return sb.toString();
        }
    }

    private static final class Progress extends TProgress<Progress> {

        private boolean warmup;
        private int iter;

        private int operation;
        private int maxOperation;

        public void newIteration(int iteration, boolean warmup) {
            iter = iteration;
            this.warmup = warmup;
            notifyProgressManager();
        }

        public void newOperation(int operation, int maxOperation) {
            this.operation = operation;
            this.maxOperation = maxOperation;
            notifyProgressManager();
        }
    }
}
