/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.google.auto.service.AutoService;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(SensitivityAnalysisParameters.ConfigLoader.class)
public class OpenSensitivityAnalysisConfigLoader implements SensitivityAnalysisParameters.ConfigLoader<OpenSensitivityAnalysisParameters> {

    @Override
    public OpenSensitivityAnalysisParameters load(PlatformConfig platformConfig) {
        OpenSensitivityAnalysisParameters parameters = new OpenSensitivityAnalysisParameters();
        platformConfig.getOptionalModuleConfig("open-sensitivity-default-parameters")
                .ifPresent(config -> parameters.setDebugDir(config.getPathProperty("debug-dir", null)));
        return parameters;
    }

    @Override
    public String getExtensionName() {
        return "openSensitivityParameters";
    }

    @Override
    public String getCategoryName() {
        return "sensitivity-parameters";
    }

    @Override
    public Class<? super OpenSensitivityAnalysisParameters> getExtensionClass() {
        return OpenSensitivityAnalysisParameters.class;
    }
}
