/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.powsybl.openloadflow.sensi.apiv2.MatrixSensitivityFactor;
import com.powsybl.openloadflow.sensi.apiv2.MultiVariablesSensitivityFactor;
import com.powsybl.openloadflow.sensi.apiv2.SensitivityFactorConfiguration;
import com.powsybl.openloadflow.sensi.apiv2.SimpleSensitivityFactor;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityAnalysisJsonModule extends SimpleModule {

    public SensitivityAnalysisJsonModule() {
        addSerializer(SimpleSensitivityFactor.class, new SimpleSensitivityFactorSerializer());
        addSerializer(MatrixSensitivityFactor.class, new MatrixSensitivityFactorSerializer());
        addSerializer(MultiVariablesSensitivityFactor.class, new MultiVariablesSensitivityFactorSerializer());
        addSerializer(SensitivityFactorConfiguration.class, new SensitivityConfigurationFactorSerializer());
    }
}
