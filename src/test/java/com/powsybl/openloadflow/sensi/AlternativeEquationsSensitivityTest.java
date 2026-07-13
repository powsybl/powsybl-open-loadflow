/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityAnalysisRunParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityVariableType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AC sensitivity analysis with alternative equations
 * ({@link OpenLoadFlowParameters#setAlternativeEquations(boolean)}). The voltage target of a controlled bus is not
 * realized by its voltage target equation anymore but carried by the alternative equation of one of its controller
 * buses, so the BUS_TARGET_VOLTAGE right hand side filling resolves the carrier equation, and results must be
 * identical to the legacy modeling.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
class AlternativeEquationsSensitivityTest extends AbstractSensitivityAnalysisTest {

    AlternativeEquationsSensitivityTest(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    @Test
    void busVoltagePerTargetVLocalControlTest() {
        // same reference values as AcSensitivityAnalysisTest.testBusVoltagePerTargetV with the legacy modeling
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class)
                .setAlternativeEquations(true);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "g2"))
                .collect(Collectors.toList());

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0d, result.getBusVoltageSensitivityValue("g2", "b1", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V); // no impact on a pv
        assertEquals(1d, result.getBusVoltageSensitivityValue("g2", "b2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V); // 1 on itself
        assertEquals(0.3423d, result.getBusVoltageSensitivityValue("g2", "b3", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0d, result.getBusVoltageSensitivityValue("g2", "b4", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
    }

    @Test
    void busVoltagePerTargetVRemoteControlTest() {
        // same reference values as AcSensitivityAnalysisTest.testBusVoltagePerTargetVRemoteControl with the legacy
        // modeling: the voltage target of b4 is carried by the alternative equation of the first controller bus
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class)
                .setAlternativeEquations(true);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "g1"))
                .collect(Collectors.toList());

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0.04997d, result.getBusVoltageSensitivityValue("g1", "b1", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0.0507d, result.getBusVoltageSensitivityValue("g1", "b2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0.0525d, result.getBusVoltageSensitivityValue("g1", "b3", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(1d, result.getBusVoltageSensitivityValue("g1", "b4", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
    }

    @Test
    void flowSensitivityWithContingenciesSameResultsAsLegacyModelingTest() {
        // branch flow sensitivities with contingencies, including the islanding of controller bus b1 (tr1): the
        // post-contingency factor states are computed on the structure preserving matrix and must be identical to
        // the legacy modeling
        List<Contingency> contingencies = List.of(Contingency.twoWindingsTransformer("tr1"),
                Contingency.twoWindingsTransformer("tr2"));

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl4_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        OpenLoadFlowParameters lfParametersExt = sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class)
                .setAlternativeEquations(false);

        Network legacyNetwork = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        List<SensitivityFactor> legacyFactors = createFactors(legacyNetwork);
        SensitivityAnalysisResult legacyResult = sensiRunner.run(legacyNetwork, legacyFactors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies));

        lfParametersExt.setAlternativeEquations(true);
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        List<SensitivityFactor> factors = createFactors(network);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies));

        assertEquals(legacyResult.getValues().size(), result.getValues().size());
        for (Contingency contingency : contingencies) {
            for (SensitivityFactor factor : factors) {
                String branchId = factor.getFunctionId();
                assertEquals(legacyResult.getBranchFlow1SensitivityValue(contingency.getId(), "g2", branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER),
                        result.getBranchFlow1SensitivityValue(contingency.getId(), "g2", branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER),
                        1e-4, "sensitivity mismatch for " + branchId + " after " + contingency.getId());
                assertEquals(legacyResult.getBranchFlow1FunctionReferenceValue(contingency.getId(), branchId),
                        result.getBranchFlow1FunctionReferenceValue(contingency.getId(), branchId),
                        1e-2, "function reference mismatch for " + branchId + " after " + contingency.getId());
            }
        }
    }

    private static List<SensitivityFactor> createFactors(Network network) {
        return network.getBranchStream()
                .map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "g2"))
                .collect(Collectors.toList());
    }
}
