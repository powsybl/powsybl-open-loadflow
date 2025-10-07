/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.sensi.mt;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityResultWriter;

import java.io.Closeable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *  @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 * This class ensures that only one thread will write sensitivity results
 */
public class SequentialSensitivityResultWriter implements SensitivityResultWriter, Closeable {

    private final SensitivityResultWriter sensitivityResultWriter;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());

    public SequentialSensitivityResultWriter(SensitivityResultWriter sensitivityResultWriter) {
        this.sensitivityResultWriter = sensitivityResultWriter;
    }

    @Override
    public void writeSensitivityValue(int factorIndex, int contingencyIndex, double value, double functionReference) {
        executor.execute(() -> sensitivityResultWriter.writeSensitivityValue(factorIndex, contingencyIndex, value, functionReference));
    }

    @Override
    public void writeContingencyStatus(int contingencyIndex, SensitivityAnalysisResult.Status status) {
        executor.execute(() -> sensitivityResultWriter.writeContingencyStatus(contingencyIndex, status));
    }

    @Override
    public void close() {
        // close shutdowns the executor service and waits until completion of all submitted tasks
        executor.close();
    }
}
