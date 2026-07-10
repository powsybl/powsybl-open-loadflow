/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import java.util.concurrent.*;

public class ExecutorWithException extends ThreadPoolExecutor {

    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ExecutorWithException(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
    }

    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ExecutorWithException(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory);
    }

    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory, int keepAlive, TimeUnit unit) {
        return new ExecutorWithException(nThreads, nThreads,
                keepAlive, unit,
                new LinkedBlockingQueue<>(),
                threadFactory);
    }

    public static ExecutorService newCachedThreadPool() {
        return new ExecutorWithException(0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new ExecutorWithException(0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                threadFactory);
    }

    public ExecutorWithException(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public ExecutorWithException(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ExecutorWithException(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ExecutorWithException(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @SuppressWarnings({"checkstyle:ParameterAssignment", "checkstyle:CatchParameterName"})
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        if (t == null && r instanceof Future<?> future) {
            try {
                if (future.isDone()) {
                    future.get();
                }
            } catch (CancellationException ce) {
                t = ce;
            } catch (ExecutionException ee) {
                t = ee.getCause();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        if (t != null) {
            t.printStackTrace();
        }
    }
}
