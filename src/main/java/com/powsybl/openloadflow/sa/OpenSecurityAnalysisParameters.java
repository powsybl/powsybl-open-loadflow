/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.security.SecurityAnalysisParameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSecurityAnalysisParameters extends AbstractExtension<SecurityAnalysisParameters> {

    private boolean createResultExtension;

    public static final String CREATE_RESULT_EXTENSION_PARAM_NAME = "createResultExtension";
    public static final boolean CREATE_RESULT_EXTENSION_DEFAULT_VALUE = false;
    public static final List<String> SPECIFIC_PARAMETERS_NAMES = List.of(CREATE_RESULT_EXTENSION_PARAM_NAME);

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
                        .setCreateResultExtension(config.getBooleanProperty(CREATE_RESULT_EXTENSION_PARAM_NAME, CREATE_RESULT_EXTENSION_DEFAULT_VALUE)));
        return parameters;
    }

    public static OpenSecurityAnalysisParameters load(Map<String, String> properties) {
        return new OpenSecurityAnalysisParameters()
                .update(properties);
    }

    public OpenSecurityAnalysisParameters update(Map<String, String> properties) {
        Optional.ofNullable(properties.get(CREATE_RESULT_EXTENSION_PARAM_NAME))
                .ifPresent(value -> this.setCreateResultExtension(Boolean.parseBoolean(value)));
        return this;
    }
}
