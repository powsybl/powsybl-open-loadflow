/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class ComputeSdGraphConnectivity<V, E> extends AbstractSpyGraphConnectivity<V, E> {

    private final BufferedWriter bw;

    public ComputeSdGraphConnectivity(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }

            bw = Files.newBufferedWriter(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beginOperations(Operations operations) { }

    @Override
    public void endOperations(Operations operations) {
        try {
            bw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long computeSd() {
        long sd = super.computeSd();
        if (sd >= 0) {
            try {
                bw.write(String.valueOf(sd));
                bw.newLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return sd;
    }

    @Override
    public void addVertex(V vertex) {
        super.addVertex(vertex);
        computeSd();
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        super.addEdge(vertex1, vertex2, edge);
        computeSd();
    }

    @Override
    public void removeEdge(E edge) {
        super.removeEdge(edge);
        computeSd();
    }
}
