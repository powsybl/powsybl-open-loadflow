/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.runners;

import com.powsybl.openloadflow.graph.generators.WorkloadUtils;
import com.powsybl.openloadflow.graph.workload.ISpyGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.workload.Operations;
import com.powsybl.openloadflow.graph.workload.Workload;
import org.apache.commons.text.StringSubstitutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard variables:
 * <ul>
 *     <li>time</li>
 *     <li>class: {@link com.powsybl.openloadflow.graph.GraphConnectivityFactory} class</li>
 *     <li>spy: {@link com.powsybl.openloadflow.graph.workload.ISpyGraphConnectivityFactory} spy used to gather output</li>
 * </ul>
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class Output {

    private String outputFormat;
    private String replacementFormat = null;
    private boolean overwrite = false;
    private final Map<String, String> outputPathParameters;

    public Output() {
        this.outputPathParameters = new HashMap<>();
    }

    public Output(Output other) {
        this.outputFormat = other.outputFormat;
        this.replacementFormat = other.replacementFormat;
        this.overwrite = other.overwrite;
        this.outputPathParameters = new HashMap<>(other.outputPathParameters);
    }

    public Path getOutputPath() throws IOException {
        if (outputFormat == null) {
            return null;
        }

        outputPathParameters.put("time", Instant.now().toString());
        StringSubstitutor substitutor = new StringSubstitutor(outputPathParameters);
        substitutor.setEnableUndefinedVariableException(true);

        Path output = Path.of(substitutor.replace(outputFormat));
        boolean exists = Files.exists(output);

        if (exists && replacementFormat != null) {
            Path destination = Path.of(substitutor.replace(replacementFormat));

            if (overwrite) {
                Files.move(output, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(output, destination);
            }

            if (Files.exists(output) && !overwrite) {
                throw new IOException("Replacement file already exists: " + output);
            }
        } else if (exists && !overwrite) {
            throw new IOException("File already exists: " + output);
        }

        return output;
    }

    public Output set(String key, String value) {
        outputPathParameters.put(key, value);
        return this;
    }

    public Output setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
        return this;
    }

    public Output setReplacementFormat(String replacementFormat) {
        this.replacementFormat = replacementFormat;
        return this;
    }

    public Output setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public Output setGraphConnectivityFactory(Class<?> factory) {
        outputPathParameters.put("class", WorkloadUtils.getClassName(factory));
        return this;
    }

    public Output setSpyGraphConnectivityFactory(Class<?> factory) {
        outputPathParameters.put("spy", WorkloadUtils.getClassName(factory));
        return this;
    }

    public Output setWorkload(Workload workload) {
        outputPathParameters.put("workload", workload.source().getFileName().toString());
        return this;
    }

    public Output setWorkloadName(String workloadName) {
        outputPathParameters.put("workload", workloadName);
        return this;
    }

    public Output setOperations(Operations operations) {
        outputPathParameters.put("operations", operations.source().toString());
        return this;
    }
}
