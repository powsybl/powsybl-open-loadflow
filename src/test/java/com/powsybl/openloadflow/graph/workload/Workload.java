/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.utils.GraphConnectivityMethod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface Workload {

    // https://en.wikipedia.org/wiki/List_of_file_signatures
    // there are two other magic bytes for identifying zip files
    // 50 4B 05 06 (empty archive)
    // 50 4B 07 08 (spanned archive)
    byte[] ZIP_MAGIC_BYTES = new byte[] {0x50, 0x4B, 0x03, 0x04};

    static Workload inMemory(Path source) throws IOException {
        boolean zip;
        try (InputStream is = Files.newInputStream(source)) {
            byte[] header = is.readNBytes(4);
            zip = Arrays.equals(header, ZIP_MAGIC_BYTES);
        }

        if (zip) {
            return MultiThreadedWorkload.createInMemory(source);
        } else {
            return InMemorySingleThreadedWorkload.create(source);
        }
    }

    Operations operations(int thread);

    int threadCount();

    Type type();

    Path source();

    int totalOperations();

    enum Type {
        DECREMENTAL,
        INCREMENTAL,
        FULLY_DYNAMIC;

        public static Type fromOperationList(List<String> operations) {
            Type type = null;
            boolean start = false;

            for (String operation : operations) {
                if (operation.startsWith(GraphConnectivityMethod.START_TEMPORARY_CHANGES.shortName())) {
                    start = true;
                }

                // wait the first startTemporaryChanges before computing type.
                if (start) {
                    type = Type.update(type, operation);
                    if (type == FULLY_DYNAMIC) {
                        break;
                    }
                }
            }

            return type;
        }

        public static Type update(Type type, String operation) {
            if (operation.startsWith(GraphConnectivityMethod.REMOVE_EDGE.shortName())) {
                if (type == null) {
                    return DECREMENTAL;
                } else {
                    return type == INCREMENTAL ? FULLY_DYNAMIC : type;
                }
            } else if (operation.startsWith(GraphConnectivityMethod.ADD_EDGE.shortName())) {
                if (type == null) {
                    return INCREMENTAL;
                } else {
                    return type == DECREMENTAL ? FULLY_DYNAMIC : type;
                }
            } else {
                return type;
            }
        }

        public static Type from(Iterable<Type> types) {
            Type type = null; // can never be FULLY_DYNAMIC

            for (Type t : types) {
                if (t == FULLY_DYNAMIC) {
                    return FULLY_DYNAMIC;
                } else if (type == null) {
                    type = t;
                } else if (type != t) { // type is decrement and t is incremental or the opposite
                    return FULLY_DYNAMIC;
                }
            }

            return type;
        }
    }
}
