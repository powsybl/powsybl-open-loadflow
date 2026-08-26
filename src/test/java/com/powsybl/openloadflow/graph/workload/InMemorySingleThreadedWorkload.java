/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class InMemorySingleThreadedWorkload implements Workload {

    public static InMemorySingleThreadedWorkload create(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file)) {
            return new InMemorySingleThreadedWorkload(lines.map(Operation::deserialize).toList(), file);
        }
    }

    Workload parentWorkload;
    private final List<Operation> operations;
    private final Type type;
    private final Path source;

    private InMemorySingleThreadedWorkload(List<Operation> operations, Path source) {
        this.operations = operations;
        this.type = Type.fromOperationList(operations);
        this.source = source;
    }

    @Override
    public Operations operations(int thread) {
        if (thread != 0) {
            return null;
        }
        return new ListOperations();
    }

    @Override
    public int threadCount() {
        return 1;
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public Path source() {
        return source;
    }

    @Override
    public int totalOperations() {
        return operations.size();
    }

    private final class ListOperations implements Operations {

        private int pos = 0;

        @Override
        public Workload workload() {
            return parentWorkload == null ? InMemorySingleThreadedWorkload.this : parentWorkload;
        }

        @Override
        public Path source() {
            return parentWorkload == null ? source.getFileName() : source;
        }

        @Override
        public void reset() {
            pos = 0;
        }

        @Override
        public boolean hasNext() {
            return pos < operations.size();
        }

        @Override
        public Operation next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Operation op = operations.get(pos);
            pos++;
            return op;
        }

        @Override
        public int size() {
            return operations.size();
        }

        @Override
        public void close() {

        }
    }
}
