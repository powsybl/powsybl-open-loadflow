/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSensitivityAnalysisParameters extends AbstractExtension<SensitivityAnalysisParameters> {

    private static final boolean DEFAULT_USE_BASE_CASE_VOLTAGE = true;

    private static final boolean DEFAULT_RUN_LF = true;

    private boolean useBaseCaseVoltage = DEFAULT_USE_BASE_CASE_VOLTAGE;

    private boolean runLf = DEFAULT_RUN_LF;

    public boolean isUseBaseCaseVoltage() {
        return useBaseCaseVoltage;
    }

    public OpenSensitivityAnalysisParameters setUseBaseCaseVoltage(boolean useBaseCaseVoltage) {
        this.useBaseCaseVoltage = useBaseCaseVoltage;
        return this;
    }

    public boolean isRunLf() {
        return runLf;
    }

    public OpenSensitivityAnalysisParameters setRunLf(boolean runLf) {
        this.runLf = runLf;
        return this;
    }

    @Override
    public String getName() {
        return "OpenSensitivityAnalysisParameters";
    }
}
