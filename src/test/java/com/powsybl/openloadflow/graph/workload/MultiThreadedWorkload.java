/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class MultiThreadedWorkload implements Workload {

    public static Workload createInMemory(Path source) throws IOException {
        List<Workload> workloads = new ArrayList<>();

        try (FileSystem fs = FileSystems.newFileSystem(source)) {
            for (Path root : fs.getRootDirectories()) {
                try (Stream<Path> stream = Files.walk(root)) {
                    for (Iterator<Path> it = stream.filter(Files::isRegularFile).iterator(); it.hasNext();) {
                        workloads.add(InMemorySingleThreadedWorkload.create(it.next()));
                    }
                }
            }
        }

        return new MultiThreadedWorkload(workloads, source);
    }

    private final List<Workload> workloadPerThread;
    private final Type type;
    private final Path source;
    private final int totalOperations;

    public MultiThreadedWorkload(List<Workload> workloadPerThread, Path source) {
        this.workloadPerThread = workloadPerThread;
        this.type = Type.from(workloadPerThread.stream().map(Workload::type).toList());
        this.source = source;
        this.totalOperations = (int) workloadPerThread.stream().mapToDouble(Workload::totalOperations).sum();
    }

    @Override
    public Operations operations(int thread) {
        return workloadPerThread.get(thread).operations(0);
    }

    @Override
    public int threadCount() {
        return workloadPerThread.size();
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
        return totalOperations;
    }
}
