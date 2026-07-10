/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.generators;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface IGenerateWorkload {

    default void generate(Path folder) throws IOException {
        String filename = getName() + "_" + Instant.now() + ".txt";

        try (BufferedWriter bw = Files.newBufferedWriter(folder.resolve(filename))) {
            generateOperations(bw);
        }
    }

    String getName();

    void generateOperations(BufferedWriter bw) throws IOException;
}
