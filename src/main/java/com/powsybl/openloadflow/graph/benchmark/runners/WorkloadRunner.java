/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.runners;

import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.benchmark.AverageStopWatch;
import com.powsybl.openloadflow.graph.benchmark.generators.WorkloadUtils;
import com.powsybl.openloadflow.graph.benchmark.log.Log;
import com.powsybl.openloadflow.graph.benchmark.log.ProgressFormatter;
import com.powsybl.openloadflow.graph.benchmark.log.ProgressManager;
import com.powsybl.openloadflow.graph.benchmark.log.TProgress;
import com.powsybl.openloadflow.graph.benchmark.workload.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class WorkloadRunner extends AbstractRunner<Workload, Integer, Integer> {

    private static final Log LOG = Log.init("results.txt");
    private static final MyProgressManager PROGRESS = new MyProgressManager();

    @Override
    public void run() {
        LOG.log("Workloads:");
        for (Workload w : inputs) {
            LOG.log("%s", w.source());
        }

        try (ExecutorService executor = createExecutorIfNeeded(inputs)) {
            for (Workload workload : inputs) {
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

    private ExecutorService createExecutorIfNeeded(List<Workload> workloads) {
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

    private String run(ExecutorService executor, Workload workload, GraphConnectivityFactory<Integer, Integer> factory) {
        Output output = runParameters.output();
        if (output != null) {
            output.setWorkload(workload);
            output.setGraphConnectivityFactory(factory.getClass());
        }

        ISpyGraphConnectivityFactory<Integer, Integer> spy = runParameters.createFactory(factory, output);

        RunnerContext warmupContext = new RunnerContext(runParameters.warmup(), workload.threadCount(), IterationType.WARMUP);
        RunnerContext measurementContext = new RunnerContext(runParameters.measurement(), workload.threadCount(), IterationType.MEASURE);

        if (workload.threadCount() > 1) {
            runMultiThreadedWorkload(executor, workload, spy, warmupContext, measurementContext);
        } else {
            runSingleThreadedWorkload(workload, spy, warmupContext, measurementContext);
        }

        return spy.resultsToString(runParameters.measurement());
    }

    private void runSingleThreadedWorkload(Workload workload,
                                           ISpyGraphConnectivityFactory<Integer, Integer> factory,
                                           RunnerContext warmup,
                                           RunnerContext measurement) {
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
    private void runMultiThreadedWorkload(ExecutorService executor,
                                          Workload workload,
                                          ISpyGraphConnectivityFactory<Integer, Integer> factory,
                                          RunnerContext warmupContext,
                                          RunnerContext measurementContext) {
        CyclicBarrier barrier = new CyclicBarrier(workload.threadCount());

        // launch each Operation in a thread
        List<Future<?>> futures = new ArrayList<>();
        for (int thread = 0; thread < workload.threadCount(); thread++) {
            final int threadId = thread;
            Progress progress = PROGRESS.newProgress(new Progress());

            var future = executor.submit(() -> {
                try (Operations operations = workload.operations(threadId)) {
                    runOperationsMultipleTimesWithWarmup(progress, operations, threadId, factory, barrier, warmupContext, measurementContext);
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

    private void runOperationsMultipleTimesWithWarmup(Progress progress,
                                                      Operations operations,
                                                      int threadId,
                                                      ISpyGraphConnectivityFactory<Integer, Integer> spyFactory,
                                                      CyclicBarrier barrier,
                                                      RunnerContext warmupContext,
                                                      RunnerContext measurementContext) {
        if (warmupContext.iterations() > 0) {
            progress.newIterations(warmupContext.iterations(), IterationType.WARMUP);

            if (threadId == 0) {
                spyFactory.beginIterations(warmupContext);
            }
            runOperationsMultipleTimes(progress, operations, spyFactory, barrier, warmupContext);

            if (threadId == 0) {
                spyFactory.endIterations(warmupContext, null);
            }
        }

        if (measurementContext.iterations() > 0) {
            progress.newIterations(measurementContext.iterations(), IterationType.MEASURE);

            if (threadId == 0) {
                spyFactory.beginIterations(measurementContext);
            }
            AverageStopWatch asw = runOperationsMultipleTimes(progress, operations, spyFactory, barrier, measurementContext);
            if (threadId == 0) {
                spyFactory.endIterations(measurementContext, asw);
            }
        }
    }

    /**
     * Run the given operations num times, each time using
     * a new GraphConnectivity provided by factory. Results will
     * be accumulated in the given SpyGraphConnectivity.
     */
    private AverageStopWatch runOperationsMultipleTimes(Progress progress,
                                                        Operations operations,
                                                        ISpyGraphConnectivityFactory<Integer, Integer> spyFactory,
                                                        CyclicBarrier barrier,
                                                        RunnerContext context) {
        AverageStopWatch asw = new AverageStopWatch();

        for (int i = 0; i < context.iterations(); i++) {
            progress.newIteration(i);

            asw.start();
            runOperations(progress, operations, spyFactory);

            if (barrier != null) {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
            }

            asw.stop();
        }

        return asw;
    }

    /**
     * Run the operations on the given DelegateGraphConnectivity
     */
    private void runOperations(Progress progress,
                               Operations operations,
                               ISpyGraphConnectivityFactory<Integer, Integer> spyFactory) {
        ISpyGraphConnectivity<Integer, Integer> spy = spyFactory.create(operations);
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
