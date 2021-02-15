/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(2, result.getSensitivityValues().size());
        assertEquals(0.498d, getValue(result, "GEN", "NHV1_NHV2_1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.498d, getValue(result, "GEN", "NHV1_NHV2_2"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4buses() {
        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.createBaseNetwork();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(Collections.singletonList(network.getGenerator("g4")),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(5, result.getSensitivityValues().size());

        assertEquals(-0.632d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.122d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.122d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.368d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.245d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesDistributed() {
        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(15, result.getSensitivityValues().size());

        assertEquals(-0.453d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.152d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.248d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.347d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.099d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.175d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.276, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.123d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.024d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.147d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.051d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.352d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.247d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.149d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.099d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesGlsk() {
        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        Map<String, Float> glskMap = new HashMap<>();
        glskMap.put("g1", 0.25f);
        glskMap.put("g4", 0.25f);
        glskMap.put("d2", 0.5f);
        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
            .map(Branch::getId)
            .map(id -> new BranchFlow(id, id, id))
            .map(branchFlow -> new BranchFlowPerLinearGlsk(branchFlow, new LinearGlsk("glsk", "glsk", glskMap)))
            .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(5, result.getSensitivityValues().size());

        assertEquals(-0.044d, getValue(result, "glsk", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.069d, getValue(result, "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.031d, getValue(result, "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.006d, getValue(result, "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.037d, getValue(result, "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesWithTransfoInjection() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(15, result.getSensitivityValues().size());

        assertEquals(-0.453d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.151d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.248d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.346d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.098d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.175d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.276d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.123d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.024d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.147d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.051d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.352d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.247d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.149d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.098d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesPhaseShift() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
            .map(AcSensitivityAnalysisTest::createBranchFlow)
            .map(branchFlow -> new BranchFlowPerPSTAngle(branchFlow, new PhaseTapChangerAngle("l23", "l23", "l23"))).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(5, result.getSensitivityValues().size());

        assertEquals(-0.0217d, getValue(result, "l23", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0647d, getValue(result, "l23", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0217d, getValue(result, "l23", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.0429d, getValue(result, "l23", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0647d, getValue(result, "l23", "l23"), LoadFlowAssert.DELTA_POWER);
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
