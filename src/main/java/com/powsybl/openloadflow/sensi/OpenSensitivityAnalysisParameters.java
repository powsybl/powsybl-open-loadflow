/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSensitivityAnalysisParameters extends AbstractExtension<SensitivityAnalysisParameters> {

    private String debugDir;

    private boolean contingencyPropagation;

    public static final String DEBUG_DIR_PARAM_NAME = "debugDir";
    public static final String CONTINGENCY_PROPAGATION_PARAM_NAME = "withContingencyPropagation";
    public static final String DEBUG_DIR_DEFAULT_VALUE = "";
    public static final boolean CONTINGENCY_PROPAGATION_DEFAULT_VALUE = false;
    public static final List<String> SPECIFIC_PARAMETERS_NAMES = List.of(DEBUG_DIR_PARAM_NAME, CONTINGENCY_PROPAGATION_PARAM_NAME);

    @Override
    public String getName() {
        return "open-sensitivity-parameters";
    }

    public String getDebugDir() {
        return debugDir;
    }

    public boolean isContingencyPropagation() {
        return contingencyPropagation;
    }

    public OpenSensitivityAnalysisParameters setDebugDir(String debugDir) {
        this.debugDir = debugDir;
        return this;
    }

    public OpenSensitivityAnalysisParameters setContingencyPropagation(boolean contingencyPropagation) {
        this.contingencyPropagation = contingencyPropagation;
        return this;
    }

    public static OpenSensitivityAnalysisParameters load() {
        return load(PlatformConfig.defaultConfig());
    }

    public static OpenSensitivityAnalysisParameters load(PlatformConfig platformConfig) {
        OpenSensitivityAnalysisParameters parameters = new OpenSensitivityAnalysisParameters();
        platformConfig.getOptionalModuleConfig("open-sensitivityanalysis-default-parameters")
                .ifPresent(config -> parameters
                        .setDebugDir(config.getStringProperty(DEBUG_DIR_PARAM_NAME, DEBUG_DIR_DEFAULT_VALUE))
                        .setContingencyPropagation(config.getBooleanProperty(CONTINGENCY_PROPAGATION_PARAM_NAME, CONTINGENCY_PROPAGATION_DEFAULT_VALUE)));
        return parameters;
    }

    public OpenSensitivityAnalysisParameters update(Map<String, String> properties) {
        Optional.ofNullable(properties.get(DEBUG_DIR_PARAM_NAME)).ifPresent(value -> this.setDebugDir(value));
        Optional.ofNullable(properties.get(CONTINGENCY_PROPAGATION_PARAM_NAME))
                .ifPresent(prop -> this.setContingencyPropagation(Boolean.parseBoolean(prop)));
        return this;
    }

    public static OpenSensitivityAnalysisParameters load(Map<String, String> properties) {
        return new OpenSensitivityAnalysisParameters()
                .update(properties);
    }
}
