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
        }

        private final Deque<Task> tasks = new ArrayDeque<>();

        private final Map<String, Long> cumulatedTimePerTaskName = new TreeMap<>();

        @Override
        public void beforeTask(String taskName) {
            tasks.push(new Task(taskName));
        }

        @Override
        public void afterTask(String taskName) {
            Objects.requireNonNull(taskName);
            Task task = tasks.pop();
            task.stopwatch.stop();
            if (!taskName.equals(task.name)) {
                throw new IllegalStateException("Task nesting issue, last is: " + task.name);
            }
            long elapsed = task.stopwatch.elapsed(TimeUnit.MICROSECONDS);
            // remove this time from all parent tasks
            for (Task parentTask : tasks) {
                parentTask.timeToRemove += elapsed;
            }
            long ownedElapsed = elapsed - task.timeToRemove;
            cumulatedTimePerTaskName.compute(taskName, (s, time) -> {
                if (time == null) {
                    return ownedElapsed;
                }
                return time + ownedElapsed;
            });
            LOGGER.debug(Markers.PERFORMANCE_MARKER, "Task '{}' done in {} us", taskName, ownedElapsed);
        }

        @Override
        public void printSummary() {
            if (LOGGER.isDebugEnabled()) {
                StringWriter writer = new StringWriter();
                try (AsciiTableFormatter formatter = new AsciiTableFormatter(writer,
                        "Detailed profiling",
                        new Column("Task name"),
                        new Column("Time (ms)"))) {
                    for (Map.Entry<String, Long> e : cumulatedTimePerTaskName.entrySet()) {
                        formatter.writeCell(e.getKey()).writeCell(e.getValue().intValue() / 1000);
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
