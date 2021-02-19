/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.io.table.AsciiTableFormatter;
import com.powsybl.commons.io.table.Column;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface Profiler {

    Profiler NO_OP = new NoOpProfilerImpl();

    void beforeTask(String taskName);

    void afterTask(String taskName);

    void printSummary();

    class NoOpProfilerImpl implements Profiler {

        @Override
        public void beforeTask(String taskName) {
            // no-op
        }

        @Override
        public void afterTask(String taskName) {
            // no-op
        }

        @Override
        public void printSummary() {
            // no-op
        }
    }

    static Profiler create() {
        return new ProfilerImpl();
    }

    class ProfilerImpl implements Profiler {

        private static final Logger LOGGER = LoggerFactory.getLogger(Profiler.class);

        class Task {

            private final String name;

            private final Stopwatch stopwatch = Stopwatch.createStarted();

            private long timeToRemove = 0L;

            Task(String name) {
                this.name = Objects.requireNonNull(name);
            }

            void stop() {
                stopwatch.stop();
            }

            void removeTime(long time) {
                timeToRemove += time;
            }

            long getSelfElapsedTime() {
                long elapsed = stopwatch.elapsed(TimeUnit.MICROSECONDS);
                return elapsed - timeToRemove;
            }
        }

        class TaskStatistics {

            long cumulatedTime = 0;

            int count = 0;
        }

        private final Deque<Task> tasks = new ArrayDeque<>();

        private final Map<String, TaskStatistics> tasksStatistics = new TreeMap<>();

        @Override
        public void beforeTask(String taskName) {
            tasks.push(new Task(taskName));
        }

        @Override
        public void afterTask(String taskName) {
            Objects.requireNonNull(taskName);
            Task task = tasks.pop();
            task.stop();
            if (!taskName.equals(task.name)) {
                throw new IllegalStateException("Task nesting issue, last is: " + task.name);
            }
            long selfElapsedTime = task.getSelfElapsedTime();
            // remove this time from all parent tasks
            for (Task parentTask : tasks) {
                parentTask.removeTime(selfElapsedTime);
            }
            tasksStatistics.compute(taskName, (s, prevStats) -> {
                TaskStatistics stats;
                stats = Objects.requireNonNullElseGet(prevStats, TaskStatistics::new);
                stats.count++;
                stats.cumulatedTime += selfElapsedTime;
                return stats;
            });
            LOGGER.trace(Markers.PERFORMANCE_MARKER, "Task '{}' done in {} us", taskName, selfElapsedTime);
        }

        @Override
        public void printSummary() {
            if (LOGGER.isDebugEnabled()) {
                StringWriter writer = new StringWriter();
                try (AsciiTableFormatter formatter = new AsciiTableFormatter(writer,
                        "Profiling summary",
                        new Column("Task name"),
                        new Column("Calls"),
                        new Column("Time (ms)"))) {
                    for (Map.Entry<String, TaskStatistics> e : tasksStatistics.entrySet()) {
                        String taskName = e.getKey();
                        TaskStatistics stats = e.getValue();
                        formatter.writeCell(taskName)
                                .writeCell(stats.count)
                                .writeCell((int) (stats.cumulatedTime / 1000L));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                String tableStr = writer.toString();
                LOGGER.debug(Markers.PERFORMANCE_MARKER, "{}", tableStr.substring(0, tableStr.length() - System.lineSeparator().length()));
            }
        }
    }
}
