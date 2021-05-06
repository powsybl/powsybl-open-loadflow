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

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSensitivityAnalysisParameters extends AbstractExtension<SensitivityAnalysisParameters> {

    private String debugDir;

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

    public static OpenSensitivityAnalysisParameters load() {
        return new OpenSensitivityAnalysisConfigLoader().load(PlatformConfig.defaultConfig());
    }
}
