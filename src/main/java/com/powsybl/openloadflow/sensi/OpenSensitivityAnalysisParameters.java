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
    private boolean startWithFrozenACEmulation = START_WITH_FROZEN_AC_EMULATION_DEFAULT_VALUE;

    public static final String DEBUG_DIR_PARAM_NAME = "debugDir";
    public static final String DEBUG_DIR_DEFAULT_VALUE = "";
    public static final String START_WITH_FROZEN_AC_EMULATION_PARAM_NAME = "startWithFrozenACEmulation";
    public static final boolean START_WITH_FROZEN_AC_EMULATION_DEFAULT_VALUE = true;
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

    public boolean isStartWithFrozenACEmulation() {
        return startWithFrozenACEmulation;
    }

    public OpenSensitivityAnalysisParameters setStartWithFrozenACEmulation(boolean startWithFrozenACEmulation) {
        this.startWithFrozenACEmulation = startWithFrozenACEmulation;
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
                        .setStartWithFrozenACEmulation(config.getBooleanProperty(START_WITH_FROZEN_AC_EMULATION_PARAM_NAME, START_WITH_FROZEN_AC_EMULATION_DEFAULT_VALUE)));
        return parameters;
    }

    public static OpenSensitivityAnalysisParameters load(Map<String, String> properties) {
        OpenSensitivityAnalysisParameters parameters = new OpenSensitivityAnalysisParameters();
        Optional.ofNullable(properties.get(DEBUG_DIR_PARAM_NAME)).ifPresent(parameters::setDebugDir);
        Optional.ofNullable(properties.get(START_WITH_FROZEN_AC_EMULATION_PARAM_NAME))
                .ifPresent(value -> parameters.setStartWithFrozenACEmulation(Boolean.parseBoolean(value)));
        return parameters;
    }
}
