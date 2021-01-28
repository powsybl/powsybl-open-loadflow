/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcSensitivityAnalysisTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testEsgTuto() {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getFactors(Network network) {
                return createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                        network.getLineStream().collect(Collectors.toList()));
            }

            @Override
            public List<SensitivityFactor> getFactors(Network network, String s) {
                return getFactors(network);
            }
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(2, result.getSensitivityValues().size());
        assertEquals(0.498d, getValue(result, "GEN", "NHV1_NHV2_1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.498d, getValue(result, "GEN", "NHV1_NHV2_2"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testInjectionNotFound() {
        testInjectionNotFound(false);
    }

    @Test
    void testBranchNotFound() {
        testBranchNotFound(false);
    }

    @Test
    void testEmptyFactors() {
        testEmptyFactors(false);
    }
}
