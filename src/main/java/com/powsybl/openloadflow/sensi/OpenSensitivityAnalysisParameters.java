/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class OpenSensitivityAnalysisParameters extends AbstractExtension<SensitivityAnalysisParameters> {

    private String debugDir;
    private boolean useWarmStart = USE_WARM_START_DEFAULT_VALUE;

    public static final String DEBUG_DIR_PARAM_NAME = "debugDir";
    public static final String DEBUG_DIR_DEFAULT_VALUE = "";
    public static final String USE_WARM_START_PARAM_NAME = "useWarmStart";
    public static final boolean USE_WARM_START_DEFAULT_VALUE = true;
    public static final List<String> SPECIFIC_PARAMETERS_NAMES = List.of(DEBUG_DIR_PARAM_NAME);

    @Override
    public String getName() {
        return "open-sensitivity-parameters";
    }

    public String getDebugDir() {
        return debugDir;
    }

    public OpenSensitivityAnalysisParameters setDebugDir(String debugDir) {
        this.debugDir = debugDir;
        return this;
    }

    public boolean isUseWarmStart() {
        return useWarmStart;
    }

    public OpenSensitivityAnalysisParameters setUseWarmStart(boolean useWarmStart) {
        this.useWarmStart = useWarmStart;
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
                        .setUseWarmStart(config.getBooleanProperty(USE_WARM_START_PARAM_NAME, USE_WARM_START_DEFAULT_VALUE)));
        return parameters;
    }

    public static OpenSensitivityAnalysisParameters load(Map<String, String> properties) {
        OpenSensitivityAnalysisParameters parameters = new OpenSensitivityAnalysisParameters();
        Optional.ofNullable(properties.get(DEBUG_DIR_PARAM_NAME)).ifPresent(parameters::setDebugDir);
        Optional.ofNullable(properties.get(USE_WARM_START_PARAM_NAME))
                .ifPresent(value -> parameters.setUseWarmStart(Boolean.parseBoolean(value)));
        return parameters;
    }
}
