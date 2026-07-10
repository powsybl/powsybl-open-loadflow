/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.DTreeGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.generators.WorkloadUtils;

import java.io.BufferedWriter;
import java.io.Closeable;
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

    private static final Log LOG = new Log();
    private static final ProgressManager PROGRESS = new ProgressManager();

    public static void main(String[] args) {
        List<Workload> workloads = getAllWorkloads(Path.of("workload/"), Set.of("chen_1000000_1.04_10_2026-06-05T12:43:38.813172770Z.txt"));
        /*List<Workload> workloads = List.of(
                Workload.inMemory(Path.of("workload/spy_5541_1_1_2026-07-03T12:31:54.685462530Z.txt"))
        );*/

        List<GraphConnectivityFactory<Integer, Integer>> factories = List.of(
                //new OldNaiveGraphConnectivity.Factory<>((Integer i) -> i)
                // new NaiveGraphConnectivityFactory<>((Integer i) -> i)
                // new MinimumSpanningTreeGraphConnectivityFactory<>(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(),
                // new HolmEtAlGraphConnectivityFactory<>(),
                // new HolmEtAlWithoutLevelGraphConnectivityFactory<>(),
                new DTreeGraphConnectivityFactory<>()
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

                    PROGRESS.advance(workload, factory);
                    String partialResults = run(executor, workload, factory);
                    LOG.log(partialResults);
                }
            }
        } finally {
            try {
                LOG.close();
            } catch (IOException e) {
                e.printStackTrace();
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
            runOperationsMultipleTimes(PROGRESS.newProgress(), operations, spy, factory, null);
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

            var future = executor.submit(() -> {
                try (Operations operations = workload.operations(threadId)) {
                    runOperationsMultipleTimes(PROGRESS.newProgress(), operations, spyFactory.create(), factory, barrier);
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

    private static final class Log implements Closeable {

        private final BufferedWriter output;
        private boolean lastIsProgress = false;

        Log() {
            try {
                output = Files.newBufferedWriter(Path.of("results.txt"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void log(String format, Object... args) {
            if (lastIsProgress) {
                System.out.println();
            }

            String line = format.formatted(args);
            System.out.println(line);
            lastIsProgress = false;

            try {
                output.write(line);
                output.write(System.lineSeparator());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void logProgress(String format, Object... args) {
            System.out.printf("\r\033[2K" + format, args);
            System.out.flush();

            lastIsProgress = true;
        }

        @Override
        public void close() throws IOException {
            output.close();
        }
    }

    private static final class ProgressManager {

        private Workload workload;
        private GraphConnectivityFactory<?, ?> connectivity;

        private final List<Progress> threadProgresses = new ArrayList<>();

        private long last = 0;

        public void advance(Workload workload, GraphConnectivityFactory<?, ?> connectivity) {
            this.workload = workload;
            this.connectivity = connectivity;

            threadProgresses.clear();
        }

        public synchronized Progress newProgress() {
            Progress progress = new Progress(this);
            threadProgresses.add(progress);
            return progress;
        }

        private void printProgress() {
            if (System.currentTimeMillis() - last > 1000) {
                // multiple threads can reach this point
                synchronized (this) {
                    if (System.currentTimeMillis() - last > 1000) {
                        // but only one can print
                        Progress first = threadProgresses.getFirst();
                        int iter = first.iter;
                        int maxIter = first.warmup ? WARMUP : MEASUREMENT;
                        String warmup = first.warmup ? " (warmup)" : "";

                        LOG.logProgress("%s - %s: %d/%d%s", workload.source(), connectivity, iter, maxIter, warmup);
                        last = System.currentTimeMillis();
                    }
                }
            }
        }
    }

    private static final class Progress {

        private final ProgressManager manager;

        private boolean warmup;
        private int iter;

        private int operation;
        private int maxOperation;

        Progress(ProgressManager manager) {
            this.manager = manager;
        }

        public void newIteration(int iteration, boolean warmup) {
            iter = iteration;
            this.warmup = warmup;
            manager.printProgress();
        }

        public void newOperation(int operation, int maxOperation) {
            this.operation = operation;
            this.maxOperation = maxOperation;
            manager.printProgress();
        }
    }

    private static final class ProgressReporter {

        private String path;
        private String clazz;
        private int iter;
        private int maxIter;
        private boolean warmup;
        private int op;
        private int maxOp;

        private long last = System.currentTimeMillis();

        public void newWorkload(Workload workload) {
            path = workload.source().toString();
            maxOp = workload.totalOperations();
        }

        public void newGraphConnectivity(GraphConnectivity<Integer, Integer> connectivity) {
            clazz = connectivity.getClass().getSimpleName();
        }

        public void newIteration(int iteration, boolean warmup) {
            iter = iteration;
            maxIter = warmup ? WARMUP : MEASUREMENT;
            this.warmup = warmup;
        }

        public void newOperation(int operation) {
            op = operation;

            if (System.currentTimeMillis() - last > 1000) {
                System.out.printf("\r\033[2K%s - %s: %d/%d%s - %d/%d", path, clazz, iter, maxIter, warmup ? " (warmup)" : "", op, maxOp);
                System.out.flush();
                last = System.currentTimeMillis();
            }
        }
    }
}
