/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.generators;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.benchmark.BenchmarkMethod;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Random;

import static com.powsybl.openloadflow.graph.benchmark.BenchmarkMethod.ADD_EDGE;
import static com.powsybl.openloadflow.graph.benchmark.BenchmarkMethod.REMOVE_EDGE;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class WorkloadUtils {

    private WorkloadUtils() {

    }

    public static void query(BufferedWriter bw, int v, int u) throws IOException {
        bw.write("q %d %d%n".formatted(v, u));
    }

    public static void insert(BufferedWriter bw, int u, int v, int edge) throws IOException {
        WorkloadUtils.write(bw, ADD_EDGE, u, v, edge);
    }

    public static void remove(BufferedWriter bw, int edge) throws IOException {
        WorkloadUtils.write(bw, REMOVE_EDGE, edge);
    }

    public static void write(BufferedWriter bw, BenchmarkMethod method, Object... args) throws IOException {
        bw.write(method.shortName());
        for (Object o : args) {
            bw.write(" ");
            bw.write(o.toString());
        }
        bw.newLine();
    }

    public static void testPoint(BufferedWriter bw, int vertexCount, long seed) throws IOException {
        bw.write("testpoint %d %d%n".formatted(vertexCount, seed));
    }

    public static void sd(BufferedWriter bw) throws IOException {
        bw.write("Sd");
        bw.newLine();
    }

    public static void newConnectivity(BufferedWriter bw) throws IOException {
        bw.write("new");
        bw.newLine();
    }

    public static final int LIMIT = 1_000_000;

    public static void query(GraphConnectivity<Integer, Integer> conn, int a, int b) {
        conn.getComponentNumber(a);
        conn.getComponentNumber(b);
    }

    public static void testPoint(GraphConnectivity<Integer, Integer> conn, int vertexCount, long seed) {
        System.out.println("test point reached");

        if (vertexCount * (vertexCount - 1) / 2 <= LIMIT) {
            // test connectivity for EVERY pair of vertices

            for (int i = 0; i < vertexCount; i++) {
                for (int j = 0; j < vertexCount; j++) {
                    query(conn, i, j);
                }
            }

        } else {
            Random random = new Random(seed);

            for (int i = 0; i < LIMIT; i++) {
                int v1 = random.nextInt(vertexCount);
                int v2 = random.nextInt(v1 + 1);
                query(conn, v1, v2);
            }
        }
    }

    public static String getClassName(Class<?> clazz) {
        String name = clazz.getName();
        int index = name.lastIndexOf('.');

        return name.substring(index + 1);
    }

    public static BufferedWriter newBufferedWriter(Path path, OpenOption... options) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        return Files.newBufferedWriter(path, options);
    }
}
