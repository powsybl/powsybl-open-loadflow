/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.generators.WorkloadUtils;
import org.apache.commons.text.StringSubstitutor;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpyOutputFolder {

    private String outputFormat;
    private final Map<String, String> outputPathParameters;

    public SpyOutputFolder() {
        this.outputPathParameters = new HashMap<>();
    }

    public SpyOutputFolder(SpyOutputFolder other) {
        this.outputFormat = other.outputFormat;
        this.outputPathParameters = new HashMap<>(other.outputPathParameters);
    }

    public Path getOutputPath() {
        if (outputFormat == null) {
            return null;
        }

        StringSubstitutor substitutor = new StringSubstitutor(outputPathParameters);
        substitutor.setEnableUndefinedVariableException(true);

        return Path.of(substitutor.replace(outputFormat));
    }

    public void set(String key, String value) {
        outputPathParameters.put(key, value);
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public void setGraphConnectivityFactory(Class<?> factory) {
        outputPathParameters.put("class", WorkloadUtils.getClassName(factory));
    }

    public void setWorkload(Workload workload) {
        outputPathParameters.put("workload", workload.source().getFileName().toString());
    }

    public void setWorkloadName(String workloadName) {
        outputPathParameters.put("workload", workloadName);
    }

    public void setOperations(Operations operations) {
        outputPathParameters.put("operations", operations.source().toString());
    }
}
