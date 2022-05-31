/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.tools.PowsyblCoreVersion;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSensitivityAnalysisProviderTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testGeneralInfos() {
        OpenSensitivityAnalysisProvider provider = new OpenSensitivityAnalysisProvider(new DenseMatrixFactory());
        assertEquals("OpenSensitivityAnalysis", provider.getName());
        assertEquals(new PowsyblCoreVersion().getMavenProjectVersion(), provider.getVersion());
    }

    @Test
    void specificParametersTest() {
        var provider = new OpenSensitivityAnalysisProvider();
        assertEquals(1, provider.getSpecificParametersNames().size());
        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();

        provider.loadSpecificParameters(Collections.emptyMap())
                .ifPresent(parametersExt -> parameters.addExtension((Class) parametersExt.getClass(), parametersExt));
        assertNull(parameters.getExtension(OpenSensitivityAnalysisParameters.class).getDebugDir());

        provider.loadSpecificParameters(Map.of(OpenSensitivityAnalysisParameters.DEBUG_DIR_PARAM_NAME, ""))
                .ifPresent(parametersExt -> parameters.addExtension((Class) parametersExt.getClass(), parametersExt));
        assertEquals("", parameters.getExtension(OpenSensitivityAnalysisParameters.class).getDebugDir());
    }

    @Test
    void updateSpecificParametersTest() {
        Map<String, String> parametersMap = new HashMap<>();
        parametersMap.put("debugDir", "dir");
        OpenSensitivityAnalysisParameters parameters = OpenSensitivityAnalysisParameters.load(parametersMap);
        assertEquals("dir", parameters.getDebugDir());
        Map<String, String> updateParametersMap = new HashMap<>();
        updateParametersMap.put("debugDir", "dir1");
        parameters.update(updateParametersMap);
        assertEquals("dir1", parameters.getDebugDir());
    }
}
