/*
 * Copyright (c) 2020-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.util.ProviderConstants;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.json.JsonSensitivityAnalysisParameters;
import com.powsybl.tools.PowsyblCoreVersion;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class OpenSensitivityAnalysisProviderTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testGeneralInfos() {
        OpenSensitivityAnalysisProvider provider = new OpenSensitivityAnalysisProvider(new DenseMatrixFactory());
        assertEquals(ProviderConstants.NAME, provider.getName());
        assertEquals(new PowsyblCoreVersion().getMavenProjectVersion(), provider.getVersion());
        assertEquals(ProviderConstants.NAME, provider.getLoadFlowProviderName().orElseThrow());
    }

    @Test
    void specificParametersTest() {
        var provider = new OpenSensitivityAnalysisProvider();

        assertEquals(3, provider.getSpecificParametersNames().size());

        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();

        provider.loadSpecificParameters(Collections.emptyMap())
                .ifPresent(parametersExt -> parameters.addExtension((Class) parametersExt.getClass(), parametersExt));
        assertNull(parameters.getExtension(OpenSensitivityAnalysisParameters.class).getDebugDir());

        provider.loadSpecificParameters(Map.of(OpenSensitivityAnalysisParameters.DEBUG_DIR_PARAM_NAME, ""))
                .ifPresent(parametersExt -> parameters.addExtension((Class) parametersExt.getClass(), parametersExt));
        assertEquals("", parameters.getExtension(OpenSensitivityAnalysisParameters.class).getDebugDir());

        provider.loadSpecificParameters(Map.of(OpenSensitivityAnalysisParameters.START_WITH_FROZEN_AC_EMULATION_PARAM_NAME, "false"))
                .ifPresent(parametersExt -> parameters.addExtension((Class) parametersExt.getClass(), parametersExt));
        assertFalse(parameters.getExtension(OpenSensitivityAnalysisParameters.class).isStartWithFrozenACEmulation());

        provider.loadSpecificParameters(Map.of(OpenSensitivityAnalysisParameters.THREAD_COUNT_PARAM_NAME, "2"))
                .ifPresent(parametersExt -> parameters.addExtension((Class) parametersExt.getClass(), parametersExt));
        assertEquals(2, parameters.getExtension(OpenSensitivityAnalysisParameters.class).getThreadCount());
    }

    @Test
    void testParamsExtensionJsonUpdate() {

        // some params with non-default values
        LoadFlowParameters p = new LoadFlowParameters()
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setUseReactiveLimits(false);
        OpenLoadFlowParameters op = OpenLoadFlowParameters.create(p)
                .setMaxNewtonRaphsonIterations(30)
                .setMaxNewtonKrylovIterations(50);
        SensitivityAnalysisParameters sp = new SensitivityAnalysisParameters()
                .setLoadFlowParameters(p);
        OpenSensitivityAnalysisParameters osp = new OpenSensitivityAnalysisParameters()
                .setThreadCount(2)
                .setStartWithFrozenACEmulation(false);
        sp.addExtension(OpenSensitivityAnalysisParameters.class, osp);

        assertEquals(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES, p.getVoltageInitMode());
        assertFalse(op.isNewtonKrylovLineSearch());
        assertNull(osp.getDebugDir());

        byte[] json = """
            {
              "version" : "1.2",
              "load-flow-parameters" : {
                "version" : "1.10",
                "voltageInitMode" : "DC_VALUES",
                "extensions": {
                  "open-load-flow-parameters": {
                    "maxNewtonRaphsonIterations": 35,
                    "newtonKrylovLineSearch" : true
                  }
                }
              },
              "extensions" : {
                "open-sensitivity-parameters" : {
                  "threadCount": 4,
                  "debugDir": "/tmp/my-debug-dir"
                }
              }
            }
            """.getBytes(StandardCharsets.UTF_8);

        JsonSensitivityAnalysisParameters.update(sp, new ByteArrayInputStream(json));

        // unchanged
        assertEquals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD, p.getBalanceType());
        assertFalse(p.isUseReactiveLimits());
        assertEquals(50, op.getMaxNewtonKrylovIterations());
        assertFalse(osp.isStartWithFrozenACEmulation());

        // updated
        assertEquals(35, op.getMaxNewtonRaphsonIterations());
        assertTrue(op.isNewtonKrylovLineSearch());
        assertEquals("/tmp/my-debug-dir", osp.getDebugDir());
        assertEquals(4, osp.getThreadCount());
    }
}
