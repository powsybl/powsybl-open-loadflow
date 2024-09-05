/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.security.SecurityAnalysisParameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class OpenSecurityAnalysisParameters extends AbstractExtension<SecurityAnalysisParameters> {

    private boolean createResultExtension = CREATE_RESULT_EXTENSION_DEFAULT_VALUE;

    private boolean contingencyPropagation = CONTINGENCY_PROPAGATION_DEFAULT_VALUE;

    private int threadCount = THREAD_COUNT_DEFAULT_VALUE;

    public static final String CREATE_RESULT_EXTENSION_PARAM_NAME = "createResultExtension";
    public static final boolean CREATE_RESULT_EXTENSION_DEFAULT_VALUE = false;
    public static final String CONTINGENCY_PROPAGATION_PARAM_NAME = "contingencyPropagation";
    public static final boolean CONTINGENCY_PROPAGATION_DEFAULT_VALUE = true;
    public static final String THREAD_COUNT_PARAM_NAME = "threadCount";
    public static final int THREAD_COUNT_DEFAULT_VALUE = 1;
    public static final List<String> SPECIFIC_PARAMETERS_NAMES = List.of(CREATE_RESULT_EXTENSION_PARAM_NAME, CONTINGENCY_PROPAGATION_PARAM_NAME, THREAD_COUNT_PARAM_NAME);

    @Override
    public String getName() {
        return "open-security-analysis-parameters";
    }

    public boolean isCreateResultExtension() {
        return createResultExtension;
    }

    public OpenSecurityAnalysisParameters setCreateResultExtension(boolean createResultExtension) {
        this.createResultExtension = createResultExtension;
        return this;
    }

    public boolean isContingencyPropagation() {
        return contingencyPropagation;
    }

    public OpenSecurityAnalysisParameters setContingencyPropagation(boolean contingencyPropagation) {
        this.contingencyPropagation = contingencyPropagation;
        return this;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public OpenSecurityAnalysisParameters setThreadCount(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Invalid thread count value: " + threadCount);
        }
        this.threadCount = threadCount;
        return this;
    }

    public static OpenSecurityAnalysisParameters getOrDefault(SecurityAnalysisParameters parameters) {
        OpenSecurityAnalysisParameters parametersExt = parameters.getExtension(OpenSecurityAnalysisParameters.class);
        if (parametersExt == null) {
            parametersExt = new OpenSecurityAnalysisParameters();
        }
        return parametersExt;
    }

    public static OpenSecurityAnalysisParameters load() {
        return load(PlatformConfig.defaultConfig());
    }

    public static OpenSecurityAnalysisParameters load(PlatformConfig platformConfig) {
        OpenSecurityAnalysisParameters parameters = new OpenSecurityAnalysisParameters();
        platformConfig.getOptionalModuleConfig("open-security-analysis-default-parameters")
                .ifPresent(config -> parameters
                        .setCreateResultExtension(config.getBooleanProperty(CREATE_RESULT_EXTENSION_PARAM_NAME, CREATE_RESULT_EXTENSION_DEFAULT_VALUE))
                        .setContingencyPropagation(config.getBooleanProperty(CONTINGENCY_PROPAGATION_PARAM_NAME, CONTINGENCY_PROPAGATION_DEFAULT_VALUE))
                        .setThreadCount(config.getIntProperty(THREAD_COUNT_PARAM_NAME, THREAD_COUNT_DEFAULT_VALUE)));
        return parameters;
    }

    public static OpenSecurityAnalysisParameters load(Map<String, String> properties) {
        return new OpenSecurityAnalysisParameters()
                .update(properties);
    }

    public OpenSecurityAnalysisParameters update(Map<String, String> properties) {
        Optional.ofNullable(properties.get(CREATE_RESULT_EXTENSION_PARAM_NAME))
                .ifPresent(value -> this.setCreateResultExtension(Boolean.parseBoolean(value)));
        Optional.ofNullable(properties.get(CONTINGENCY_PROPAGATION_PARAM_NAME))
                .ifPresent(value -> this.setContingencyPropagation(Boolean.parseBoolean(value)));
        Optional.ofNullable(properties.get(THREAD_COUNT_PARAM_NAME))
                .ifPresent(value -> this.setThreadCount(Integer.parseInt(value)));
        return this;
    }
}
