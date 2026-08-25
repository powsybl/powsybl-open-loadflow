/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface Operations extends Closeable, Iterator<Operation> {

    Workload workload();

    /**
     * For a single-threaded workload stored in a text files, it returns
     * the name of the file. For an operation in a workload stored in a zip file,
     * it returns the absolute path to the operation in the zip file.
     *
     * @return source file relative to the workload file
     */
    Path source();

    void reset();

    int size();
}
