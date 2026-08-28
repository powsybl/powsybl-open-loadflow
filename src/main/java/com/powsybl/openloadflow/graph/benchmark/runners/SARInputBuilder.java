/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.runners;

import java.util.Objects;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SARInputBuilder {
    private String network = null;
    private String name = null;
    private int lineToDisconnect = 0;
    private int contingencyCount = 0;
    private int linePerContingency = 0;
    private int actionPerOp = 0;
    private SingleSecurityAnalysisRunner.Mode mode = SingleSecurityAnalysisRunner.Mode.DC;
    private int threadCount = 1;

    public SARInputBuilder setNetwork(String network) {
        this.network = Objects.requireNonNull(network);
        return this;
    }

    public SARInputBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public SARInputBuilder setLineToDisconnect(int lineToDisconnect) {
        this.lineToDisconnect = lineToDisconnect;
        return this;
    }

    public SARInputBuilder setContingencyCount(int contingencyCount) {
        this.contingencyCount = contingencyCount;
        return this;
    }

    public SARInputBuilder setLinePerContingency(int linePerContingency) {
        this.linePerContingency = linePerContingency;
        return this;
    }

    public SARInputBuilder setActionPerOp(int actionPerOp) {
        this.actionPerOp = actionPerOp;
        return this;
    }

    public SARInputBuilder setMode(SingleSecurityAnalysisRunner.Mode mode) {
        this.mode = Objects.requireNonNull(mode);
        return this;
    }

    public SARInputBuilder setThreadCount(int threadCount) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be > 0");
        }
        this.threadCount = threadCount;
        return this;
    }

    public SecurityAnalysisRunner.Input createInput() {
        return new SecurityAnalysisRunner.Input(Objects.requireNonNull(network), name, lineToDisconnect, contingencyCount, linePerContingency, actionPerOp, mode, threadCount);
    }
}
