/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.DanglingLineContingency;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
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

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getLineStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(2, result.getValues().size());
        assertEquals(0.498d, result.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.498d, result.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4buses() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.createBaseNetwork();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g4")),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(-0.632d, result.getBranchFlow1SensitivityValue("g4", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.122d, result.getBranchFlow1SensitivityValue("g4", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.122d, result.getBranchFlow1SensitivityValue("g4", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.368d, result.getBranchFlow1SensitivityValue("g4", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.245d, result.getBranchFlow1SensitivityValue("g4", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesDistributed() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());

        assertEquals(-0.453d, result.getBranchFlow1SensitivityValue("g4", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.152d, result.getBranchFlow1SensitivityValue("g4", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.248d, result.getBranchFlow1SensitivityValue("g4", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.347d, result.getBranchFlow1SensitivityValue("g4", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.099d, result.getBranchFlow1SensitivityValue("g4", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.175d, result.getBranchFlow1SensitivityValue("g1", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.276, result.getBranchFlow1SensitivityValue("g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.123d, result.getBranchFlow1SensitivityValue("g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.024d, result.getBranchFlow1SensitivityValue("g1", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.147d, result.getBranchFlow1SensitivityValue("g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.051d, result.getBranchFlow1SensitivityValue("g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.352d, result.getBranchFlow1SensitivityValue("g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.247d, result.getBranchFlow1SensitivityValue("g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.149d, result.getBranchFlow1SensitivityValue("g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.099d, result.getBranchFlow1SensitivityValue("g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesDistributedSide2() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), null, Branch.Side.TWO);

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());

        assertEquals(0.45328d, result.getBranchFlow2SensitivityValue("g4", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1518d, result.getBranchFlow2SensitivityValue("g4", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.24819d, result.getBranchFlow2SensitivityValue("g4", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3467d, result.getBranchFlow2SensitivityValue("g4", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0986d, result.getBranchFlow2SensitivityValue("g4", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.1757d, result.getBranchFlow2SensitivityValue("g1", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2765, result.getBranchFlow2SensitivityValue("g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1235d, result.getBranchFlow2SensitivityValue("g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.02434d, result.getBranchFlow2SensitivityValue("g1", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1478d, result.getBranchFlow2SensitivityValue("g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.25116d, result.getBranchFlow2FunctionReferenceValue("l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.25116d, result.getBranchFlow2FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2510d, result.getBranchFlow2FunctionReferenceValue("l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.2510d, result.getBranchFlow2FunctionReferenceValue("l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.4975d, result.getBranchFlow2FunctionReferenceValue("l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesGlsk() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("g1", 0.25f),
                                                              new WeightedSensitivityVariable("g4", 0.25f),
                                                              new WeightedSensitivityVariable("d2", 0.5f));
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream()
            .map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk"))
            .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), variableSets, sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(-0.044d, result.getBranchFlow1SensitivityValue("glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.069d, result.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.031d, result.getBranchFlow1SensitivityValue("glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.006d, result.getBranchFlow1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.037d, result.getBranchFlow1SensitivityValue("glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesWithTransfoInjection() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());

        assertEquals(-0.453d, result.getBranchFlow1SensitivityValue("g4", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.151d, result.getBranchFlow1SensitivityValue("g4", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.248d, result.getBranchFlow1SensitivityValue("g4", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.346d, result.getBranchFlow1SensitivityValue("g4", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.098d, result.getBranchFlow1SensitivityValue("g4", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.175d, result.getBranchFlow1SensitivityValue("g1", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.276d, result.getBranchFlow1SensitivityValue("g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.123d, result.getBranchFlow1SensitivityValue("g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.024d, result.getBranchFlow1SensitivityValue("g1", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.147d, result.getBranchFlow1SensitivityValue("g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.051d, result.getBranchFlow1SensitivityValue("g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.352d, result.getBranchFlow1SensitivityValue("g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.247d, result.getBranchFlow1SensitivityValue("g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.149d, result.getBranchFlow1SensitivityValue("g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.098d, result.getBranchFlow1SensitivityValue("g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesPhaseShift() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(-0.0217d, result.getBranchFlow1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0647d, result.getBranchFlow1SensitivityValue("l23", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0217d, result.getBranchFlow1SensitivityValue("l23", "l34", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.0429d, result.getBranchFlow1SensitivityValue("l23", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0647d, result.getBranchFlow1SensitivityValue("l23", "l23", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesOpenPhaseShifterOnPower() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        network.getBranch("l23").getTerminal1().disconnect();

        List<SensitivityFactor> factors = List.of(createBranchFlowPerPSTAngle("l14", "l23"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_ANGLE);

        network.getBranch("l23").getTerminal1().connect();
        network.getBranch("l23").getTerminal2().disconnect();

        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result2.getValues().size());
        assertEquals(0, result2.getBranchFlow1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_ANGLE);
    }

    @Test
    void test4busesOpenPhaseShifterOnCurrent() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        network.getBranch("l23").getTerminal1().disconnect();

        List<SensitivityFactor> factors = List.of(createBranchFlowPerPSTAngle("l14", "l23"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_ANGLE);

        network.getBranch("l23").getTerminal1().connect();
        network.getBranch("l23").getTerminal2().disconnect();

        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result2.getValues().size());
        assertEquals(0, result2.getBranchFlow1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_ANGLE);
    }

    @Test
    void test4busesFunctionReference() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());

        assertEquals(0.2512d, result.getBranchFlow1FunctionReferenceValue("l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2512d, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.2512d, result.getBranchFlow1FunctionReferenceValue("l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2512d, result.getBranchFlow1FunctionReferenceValue("l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4976d, result.getBranchFlow1FunctionReferenceValue("l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesFunctionReferenceWithTransformer() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(0.2296d, result.getBranchFlow1FunctionReferenceValue("l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3154d, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2296d, result.getBranchFlow1FunctionReferenceValue("l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4549d, result.getBranchFlow1FunctionReferenceValue("l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.3154d, result.getBranchFlow1FunctionReferenceValue("l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesPhaseShiftIntensity() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factorsSide1 = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23", Branch.Side.ONE)).collect(Collectors.toList());
        List<SensitivityFactor> factorsSide2 = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23", Branch.Side.TWO)).collect(Collectors.toList());

        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(factorsSide1);
        factors.addAll(factorsSide2);

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(10, result.getValues().size());

        //Check values for side 1 using generic and function type specific api
        assertEquals(37.6799d, result.getSensitivityValue("l23", "l23", SensitivityFunctionType.BRANCH_CURRENT_1, SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(-12.5507d, result.getBranchCurrent1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(37.3710d, result.getBranchCurrent1SensitivityValue("l23", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(-12.6565d, result.getBranchCurrent1SensitivityValue("l23", "l34", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(-25.0905d, result.getBranchCurrent1SensitivityValue("l23", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);

        //Check values for side 2 using generic and function type specific api
        assertEquals(37.6816d, result.getSensitivityValue("l23", "l23", SensitivityFunctionType.BRANCH_CURRENT_2, SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(-12.5509d, result.getBranchCurrent2SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(37.3727d, result.getBranchCurrent2SensitivityValue("l23", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(-12.6567d, result.getBranchCurrent2SensitivityValue("l23", "l34", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(-25.0917d, result.getBranchCurrent2SensitivityValue("l23", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
    }

    @Test
    void testAngleFlowSensitivityFiltering() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.setAngleFlowSensitivityValueThreshold(15.0);

        List<SensitivityFactor> factorsSide1 = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23", Branch.Side.ONE)).collect(Collectors.toList());
        List<SensitivityFactor> factorsSide2 = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23", Branch.Side.TWO)).collect(Collectors.toList());

        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(factorsSide1);
        factors.addAll(factorsSide2);

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(6, result.getValues().size());

        //Check values for side 1 using generic and function type specific api
        assertEquals(37.6799d, result.getSensitivityValue("l23", "l23", SensitivityFunctionType.BRANCH_CURRENT_1, SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(37.3710d, result.getBranchCurrent1SensitivityValue("l23", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(-25.0905d, result.getBranchCurrent1SensitivityValue("l23", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);

        //Check values for side 2 using generic and function type specific api
        assertEquals(37.6816d, result.getSensitivityValue("l23", "l23", SensitivityFunctionType.BRANCH_CURRENT_2, SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(37.3727d, result.getBranchCurrent2SensitivityValue("l23", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
        assertEquals(-25.0917d, result.getBranchCurrent2SensitivityValue("l23", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_I);
    }

    @Test
    void test4busesPhaseShiftIntensityFunctionReference() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());
        assertEquals(766.4654d, result.getBranchCurrent1FunctionReferenceValue("l23"), LoadFlowAssert.DELTA_I);
        assertEquals(132.5631d, result.getBranchCurrent1FunctionReferenceValue("l14"), LoadFlowAssert.DELTA_I);
        assertEquals(182.1272d, result.getBranchCurrent1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_I);
        assertEquals(716.5036d, result.getBranchCurrent1FunctionReferenceValue("l34"), LoadFlowAssert.DELTA_I);
        assertEquals(847.8542d, result.getBranchCurrent1FunctionReferenceValue("l13"), LoadFlowAssert.DELTA_I);
    }

    @Test
    void testBusVoltagePerTargetVRemoteControl() {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "g1"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0.04997d, result.getBusVoltageSensitivityValue("g1", "b1", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0.0507d, result.getBusVoltageSensitivityValue("g1", "b2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0.0525d, result.getBusVoltageSensitivityValue("g1", "b3", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(1d, result.getBusVoltageSensitivityValue("g1", "b4", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetV() {
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "g2"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0d, result.getBusVoltageSensitivityValue("g2", "b1", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V); // no impact on a pv
        assertEquals(1d, result.getBusVoltageSensitivityValue("g2", "b2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V); // 1 on itself
        assertEquals(0.3423d, result.getBusVoltageSensitivityValue("g2", "b3", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V); // value obtained by running two loadflow with a very small difference on targetV for bus2
        assertEquals(0d, result.getBusVoltageSensitivityValue("g2", "b4", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testFilterVoltageVoltageSensitivityValues() {
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.setVoltageVoltageSensitivityValueThreshold(0.5);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "g2"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(1d, result.getBusVoltageSensitivityValue("g2", "b2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V); // 1 on itself
    }

    @Test
    void testBusVoltagePerTargetVTwt() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();

        Substation substation4 = network.newSubstation()
                .setId("SUBSTATION4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(33.0)
                .setLowVoltageLimit(0.0)
                .setHighVoltageLimit(100.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl4.getBusBreakerView().newBus()
                .setId("BUS_4")
                .add();
        vl4.newLoad()
                .setId("LOAD_4")
                .setBus("BUS_4")
                .setQ0(0)
                .setP0(10)
                .add();
        network.newLine()
                .setId("LINE_34")
                .setBus1("BUS_3")
                .setBus2("BUS_4")
                .setR(1.05)
                .setX(10.0)
                .setG1(0.0000005)
                .add();

        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("T2wT");
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VL_1_0", true);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "T2wT"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0d, result.getBusVoltageSensitivityValue("T2wT", "BUS_1", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0.035205d, result.getBusVoltageSensitivityValue("T2wT", "BUS_2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(1d, result.getBusVoltageSensitivityValue("T2wT", "BUS_3", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(1.055117d, result.getBusVoltageSensitivityValue("T2wT", "BUS_4", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);

        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(3)
                .setRegulationTerminal(t2wt.getTerminal1()) // control will be disabled.
                .setTargetV(135.0);

        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result2.getValues().size());
        assertEquals(0d, result2.getBusVoltageSensitivityValue("T2wT", "BUS_1", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0d, result2.getBusVoltageSensitivityValue("T2wT", "BUS_2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0d, result2.getBusVoltageSensitivityValue("T2wT", "BUS_3", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0d, result2.getBusVoltageSensitivityValue("T2wT", "BUS_4", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);

        assertEquals(135.0, result2.getBusVoltageFunctionReferenceValue("BUS_1"), LoadFlowAssert.DELTA_V);
        assertEquals(133.77, result2.getBusVoltageFunctionReferenceValue("BUS_2"), LoadFlowAssert.DELTA_V);
        assertEquals(25.88, result2.getBusVoltageFunctionReferenceValue("BUS_3"), LoadFlowAssert.DELTA_V);
        assertEquals(25.16, result2.getBusVoltageFunctionReferenceValue("BUS_4"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetVVsc() {
        Network network = HvdcNetworkFactory.createVsc();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "cs2"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(0d, result.getBusVoltageSensitivityValue("cs2", "b1", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(1d, result.getBusVoltageSensitivityValue("cs2", "b2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTarget3wt() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT3wt();
        ThreeWindingsTransformer t3wt = network.getThreeWindingsTransformer("T3wT");
        t3wt.getLeg2().getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t3wt.getLeg2().getTerminal())
                .setTargetV(28.);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VL_1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "T3wT"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0d, result.getBusVoltageSensitivityValue("T3wT", "BUS_1", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0d, result.getBusVoltageSensitivityValue("T3wT", "BUS_2", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(1d, result.getBusVoltageSensitivityValue("T3wT", "BUS_3", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
        assertEquals(0d, result.getBusVoltageSensitivityValue("T3wT", "BUS_4", SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetVFunctionRef() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLGEN_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "GEN"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        Function<String, Double> getV = busId -> network.getBusView().getBus(busId).getV();
        assertEquals(getV.apply("VLGEN_0"), result.getBusVoltageFunctionReferenceValue("NGEN"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLHV1_0"), result.getBusVoltageFunctionReferenceValue("NHV1"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLHV2_0"), result.getBusVoltageFunctionReferenceValue("NHV2"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLLOAD_0"), result.getBusVoltageFunctionReferenceValue("NLOAD"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testAdditionnalFactors() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(0.2296d, result.getBranchFlow1FunctionReferenceValue("l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3154d, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2296d, result.getBranchFlow1FunctionReferenceValue("l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4549d, result.getBranchFlow1FunctionReferenceValue("l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.3154d, result.getBranchFlow1FunctionReferenceValue("l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testInjectionNotFoundAdditionalFactor() {
        testInjectionNotFoundAdditionalFactor(false);
    }

    @Test
    void testTargetVOnPqNode() {
        // asking a target v on a load should crash
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "d3"))
                .collect(Collectors.toList());

        List<Contingency> contingencies = Collections.emptyList();
        List<SensitivityVariableSet> variableSets = Collections.emptyList();
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Regulating terminal for 'd3' not found", e.getCause().getMessage());
    }

    @Test
    void testTargetVOnAbsentTerminal() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "a"))
                .collect(Collectors.toList());

        List<Contingency> contingencies = Collections.emptyList();

        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Regulating terminal for 'a' not found", e.getCause().getMessage());
    }

    @Test
    void testTargetVOnNotRegulatingTwt() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "l23"))
                .collect(Collectors.toList());

        List<Contingency> contingencies = Collections.emptyList();

        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Regulating terminal for 'l23' not found", e.getCause().getMessage());
    }

    @Test
    void testBusVoltageOnAbsentBus() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = Collections.singletonList(createBusVoltagePerTargetV("id", "g2"));

        List<Contingency> contingencies = Collections.emptyList();

        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));

        assertTrue(e.getCause() instanceof PowsyblException);

        assertEquals("The bus ref for 'id' cannot be resolved.", e.getCause().getMessage());
    }

    @Test
    void testHvdcSensi() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);

        // test active power setpoint increase on an HVDC line
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + SENSI_CHANGE);

        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(Identifiable::getId)
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network1.getLine(line).getTerminal1().getP() - network.getLine(line).getTerminal1().getP()) / SENSI_CHANGE
            ));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l12", "l13", "l23"),
                                                                         SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                                                                         false, ContingencyContext.all());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getBranchFlow1SensitivityValue("hvdc34", "l12", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getBranchFlow1SensitivityValue("hvdc34", "l13", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getBranchFlow1SensitivityValue("hvdc34", "l23", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithLCCs() {
        // test active power setpoint increase on a HVDC line
        // FIXME
        // Note that in case of LCC converter stations, in AC, an increase of the setpoint of the HDVC line is not equivalent to
        // running two LFs and comparing the differences as we don't change Q at LCCs when we change P.
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        runLf(network, sensiParameters.getLoadFlowParameters());

        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + SENSI_CHANGE);

        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(Identifiable::getId)
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network1.getLine(line).getTerminal1().getP() - network.getLine(line).getTerminal1().getP()) / SENSI_CHANGE
            ));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l12", "l13", "l23"),
                                                                         SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                                                                         false, ContingencyContext.all());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(0.36218, loadFlowDiff.get("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35967, loadFlowDiff.get("l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.61191, loadFlowDiff.get("l23"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.341889, result.getBranchFlow1SensitivityValue("hvdc34", "l12", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.341889, result.getBranchFlow1SensitivityValue("hvdc34", "l13", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.63611, result.getBranchFlow1SensitivityValue("hvdc34", "l23", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithBothSides() {
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        runLf(network, sensiParameters.getLoadFlowParameters());

        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + SENSI_CHANGE);
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(Identifiable::getId)
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network1.getLine(line).getTerminal1().getP() - network.getLine(line).getTerminal1().getP()) / SENSI_CHANGE
            ));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                                                                         SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                                                                         false, ContingencyContext.all());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getBranchFlow1SensitivityValue("hvdc34", "l12", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getBranchFlow1SensitivityValue("hvdc34", "l13", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getBranchFlow1SensitivityValue("hvdc34", "l23", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l25"), result.getBranchFlow1SensitivityValue("hvdc34", "l25", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l45"), result.getBranchFlow1SensitivityValue("hvdc34", "l45", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l46"), result.getBranchFlow1SensitivityValue("hvdc34", "l46", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l56"), result.getBranchFlow1SensitivityValue("hvdc34", "l56", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithBothSidesDistributed() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.01);

        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        runLf(network, sensiParameters.getLoadFlowParameters());

        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
        network1.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + SENSI_CHANGE);
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(Identifiable::getId)
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network1.getLine(line).getTerminal1().getP() - network.getLine(line).getTerminal1().getP()) / SENSI_CHANGE
            ));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                                                                         SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                                                                         false, ContingencyContext.all());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getBranchFlow1SensitivityValue("hvdc34", "l12", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getBranchFlow1SensitivityValue("hvdc34", "l13", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getBranchFlow1SensitivityValue("hvdc34", "l23", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l25"), result.getBranchFlow1SensitivityValue("hvdc34", "l25", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l45"), result.getBranchFlow1SensitivityValue("hvdc34", "l45", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l46"), result.getBranchFlow1SensitivityValue("hvdc34", "l46", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l56"), result.getBranchFlow1SensitivityValue("hvdc34", "l56", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithBothSidesDistributed2() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.01);

        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        runLf(network, sensiParameters.getLoadFlowParameters());

        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
        network1.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        network1.getGenerator("g1").setTargetP(network1.getGenerator("g1").getTargetP() + SENSI_CHANGE);
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(Identifiable::getId)
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network1.getLine(line).getTerminal1().getP() - network.getLine(line).getTerminal1().getP()) / SENSI_CHANGE
            ));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("g1"),
                false, ContingencyContext.all());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getBranchFlow1SensitivityValue("g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getBranchFlow1SensitivityValue("g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getBranchFlow1SensitivityValue("g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l25"), result.getBranchFlow1SensitivityValue("g1", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l45"), result.getBranchFlow1SensitivityValue("g1", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l46"), result.getBranchFlow1SensitivityValue("g1", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l56"), result.getBranchFlow1SensitivityValue("g1", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcInjectionNotFound() {
        testHvdcInjectionNotFound(false);
    }

    @Test
    void disconnectedGeneratorShouldBeSkipped() {
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        //Disconnect g4 generator
        network.getGenerator("g4").getTerminal().disconnect();

        // FIXME: using getBusBreakerView in AbstractSensitivityAnalysis.checkBus -> make this test passed

        List<SensitivityFactor> factors = Collections.singletonList(createBusVoltagePerTargetV("b1", "g4"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0d, result.getBusVoltageSensitivityValue("g4", "b1", SensitivityVariableType.BUS_TARGET_VOLTAGE));
    }

    @Test
    void testBranchFunctionOutsideMainComponent() {
        testBranchFunctionOutsideMainComponent(false);
    }

    @Test
    void testInjectionOutsideMainComponent() {
        testInjectionOutsideMainComponent(false);
    }

    @Test
    void testPhaseShifterOutsideMainComponent() {
        testPhaseShifterOutsideMainComponent(false);
    }

    @Test
    void testGlskOutsideMainComponent() {
        testGlskOutsideMainComponent(false);
    }

    @Test
    void testGlskAndLineOutsideMainComponent() {
        testGlskAndLineOutsideMainComponent(false);
    }

    @Test
    void testGlskPartiallyOutsideMainComponent() {
        testGlskPartiallyOutsideMainComponent(false);
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

    @Test
    void testDanglingLineSensi() {
        Network network = BoundaryFactory.createWithLoad();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0");
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "dl1"),
                createBranchFlowPerInjectionIncrease("dl1", "dl1"),
                createBranchIntensityPerInjectionIncrease("dl1", "load3"));

        // dangling line is connected
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(-0.903d, result.getBranchFlow1SensitivityValue("dl1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(91.293, result.getBranchFlow1FunctionReferenceValue("dl1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.001d, result.getBranchFlow1SensitivityValue("dl1", "dl1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(260.51, result.getBranchCurrent1FunctionReferenceValue("dl1"), LoadFlowAssert.DELTA_I);

        // dangling line is connected on base case but will be disconnected by a contingency => 0
        List<Contingency> contingencies = List.of(new Contingency("c", new DanglingLineContingency("dl1")));
        result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(-0.903d, result.getBranchFlow1SensitivityValue("dl1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("c", "dl1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        // dangling line is disconnected on base case => 0
        network.getDanglingLine("dl1").getTerminal().disconnect();
        result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("dl1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithHvdcAcEmulation() {
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        parameters.setHvdcAcEmulation(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);

        SensitivityAnalysisParameters sensiParameters = createParameters(false);
        sensiParameters.setLoadFlowParameters(parameters);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l25", "d2"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(0.442, result.getBranchFlow1SensitivityValue("d2", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithPhaseControlOn() {
        Network network = PhaseControlFactory.createNetworkWithT2wt();
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("PS1");
        t2wt.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(t2wt.getTerminal1())
                .setRegulationValue(83);
        SensitivityAnalysisParameters sensiParameters = createParameters(false);
        sensiParameters.getLoadFlowParameters().setPhaseShifterRegulationOn(true);
        LoadFlow.run(network, sensiParameters.getLoadFlowParameters());
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                List.of("L1", "L2", "PS1"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("G1"), false, ContingencyContext.none());
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), result.getBranchFlow1FunctionReferenceValue(null, "PS1"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testInjectionIncreaseWithPhaseControlOnInTheNetwork() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VL2_0", false);
        sensiParameters.getLoadFlowParameters().setPhaseShifterRegulationOn(true);

        Network network = PhaseControlFactory.createNetworkWithT2wt();
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("PS1");
        t2wt.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(t2wt.getTerminal1())
                .setRegulationValue(83);
        runLf(network, sensiParameters.getLoadFlowParameters());

        Network network1 = PhaseControlFactory.createNetworkWithT2wt();
        TwoWindingsTransformer t2wt1 = network1.getTwoWindingsTransformer("PS1");
        t2wt1.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(t2wt1.getTerminal1())
                .setRegulationValue(83);
        network1.getGenerator("G1").setTargetP(network1.getGenerator("G1").getTargetP() + SENSI_CHANGE);
        runLf(network1, sensiParameters.getLoadFlowParameters());

        Map<String, Double> loadFlowDiff = network.getLineStream().map(Identifiable::getId)
                .collect(Collectors.toMap(
                    lineId -> lineId,
                    line -> (network1.getLine(line).getTerminal1().getP() - network.getLine(line).getTerminal1().getP()) / SENSI_CHANGE
                ));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("L1", "L2"),
                SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("G1"),
                false, ContingencyContext.all());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("L1"), result.getBranchFlow1SensitivityValue("G1", "L1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("L2"), result.getBranchFlow1SensitivityValue("G1", "L2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void lineWithDifferentNominalVoltageTest() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0", false);
        Network network = EurostagTutorialExample1Factory.create();

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("NHV1_NHV2_1", "NHV1_NHV2_2"),
                SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GEN"),
                false, ContingencyContext.all());

        runLf(network, sensiParameters.getLoadFlowParameters());
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(0.499, result.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.499, result.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(303.166, result.getFunctionReferenceValue("NHV1_NHV2_1", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(303.166, result.getFunctionReferenceValue("NHV1_NHV2_2", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(303.166, network.getLine("NHV1_NHV2_1").getTerminal1().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(303.166, network.getLine("NHV1_NHV2_2").getTerminal1().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(602.290, network.getTwoWindingsTransformer("NHV2_NLOAD").getTerminal1().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-601.419, network.getTwoWindingsTransformer("NHV2_NLOAD").getTerminal2().getP(), LoadFlowAssert.DELTA_POWER);

        network.getVoltageLevel("VLHV2").setNominalV(360);
        runLf(network, sensiParameters.getLoadFlowParameters());
        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(0.499, result2.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.499, result2.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(303.170, result2.getFunctionReferenceValue("NHV1_NHV2_1", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(303.170, result2.getFunctionReferenceValue("NHV1_NHV2_2", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(303.170, network.getLine("NHV1_NHV2_1").getTerminal1().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(303.170, network.getLine("NHV1_NHV2_2").getTerminal1().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(602.302, network.getTwoWindingsTransformer("NHV2_NLOAD").getTerminal1().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-601.430, network.getTwoWindingsTransformer("NHV2_NLOAD").getTerminal2().getP(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithPvPqSwitch() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.createBaseNetwork();
        network.getLoad("d2").setQ0(0.4);
        network.getLoad("d3").setQ0(1.6);
        network.getGenerator("g4").newMinMaxReactiveLimits().setMinQ(-0.5).setMaxQ(0.5).add();

        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = List.of(createBusVoltagePerTargetV("b3", "g4"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());

        assertEquals(0.0, result.getBusVoltageSensitivityValue("g4", "b3", SensitivityVariableType.BUS_TARGET_VOLTAGE));
    }

    @Test
    void testNullBusInjection() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getLoad("LOAD").getTerminal().disconnect();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLGEN_0");

        List<SensitivityFactor> factors = List.of(createBranchIntensityPerInjectionIncrease("NHV1_NHV2_1", "LOAD"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(92.0836, result.getBranchCurrent1FunctionReferenceValue("NHV1_NHV2_1"), LoadFlowAssert.DELTA_I);
    }

    @Test
    void testThreeWindingsTransformerAsFunction() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Network network = VoltageControlNetworkFactory.createNetworkWithT3wt();

        SensitivityFactor factorActivePower1Twt = createTransformerLegFlowPerInjectionIncrease("T3wT", "LOAD_3", ThreeWindingsTransformer.Side.ONE);
        SensitivityFactor factorActivePower2Twt = createTransformerLegFlowPerInjectionIncrease("T3wT", "LOAD_3", ThreeWindingsTransformer.Side.TWO);
        SensitivityFactor factorActivePower3Twt = createTransformerLegFlowPerInjectionIncrease("T3wT", "LOAD_3", ThreeWindingsTransformer.Side.THREE);

        SensitivityFactor factorCurrent1 = createTransformerLegIntensityPerInjectionIncrease("T3wT", "LOAD_3", ThreeWindingsTransformer.Side.ONE);
        SensitivityFactor factorCurrent2 = createTransformerLegIntensityPerInjectionIncrease("T3wT", "LOAD_3", ThreeWindingsTransformer.Side.TWO);
        SensitivityFactor factorCurrent3 = createTransformerLegIntensityPerInjectionIncrease("T3wT", "LOAD_3", ThreeWindingsTransformer.Side.THREE);

        List<SensitivityFactor> factors = List.of(factorActivePower1Twt, factorActivePower2Twt, factorActivePower3Twt,
                factorCurrent1, factorCurrent2, factorCurrent3);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(6, result.getValues().size());

        assertEquals(43.03, result.getBranchCurrent1FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_I);
        assertEquals(83.91, result.getBranchCurrent2FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_I);
        assertEquals(279.70, result.getBranchCurrent3FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_I);
        assertEquals(10.007, result.getBranchFlow1FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4.999, result.getBranchFlow2FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4.999, result.getBranchFlow3FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-4.309, result.getBranchCurrent1SensitivityValue("LOAD_3", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-16.796, result.getBranchCurrent2SensitivityValue("LOAD_3", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-0.033, result.getBranchCurrent3SensitivityValue("LOAD_3", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-1.001, result.getBranchFlow1SensitivityValue("LOAD_3", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.000, result.getBranchFlow2SensitivityValue("LOAD_3", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.000, result.getBranchFlow3SensitivityValue("LOAD_3", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testThreeWindingsTransformerAsVariable() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.setAngleFlowSensitivityValueThreshold(0.1);
        Network network = PhaseControlFactory.createNetworkWithT3wt();

        //Add phase tap changer to leg1 and leg3 of the twt for testing purpose
        ThreeWindingsTransformer twt = network.getThreeWindingsTransformer("PS1");
        twt.getLeg1().newPhaseTapChanger()
                .setTapPosition(1)
                .setRegulationTerminal(twt.getLeg1().getTerminal())
                .setRegulationMode(PhaseTapChanger.RegulationMode.FIXED_TAP)
                .setRegulationValue(200)
                .beginStep()
                .setAlpha(-5.0)
                .endStep()
                .beginStep()
                .setAlpha(0.0)
                .endStep()
                .beginStep()
                .setAlpha(5)
                .endStep()
                .add();
        twt.getLeg3().newPhaseTapChanger()
                .setTapPosition(1)
                .setRegulationTerminal(twt.getLeg3().getTerminal())
                .setRegulationMode(PhaseTapChanger.RegulationMode.FIXED_TAP)
                .setRegulationValue(200)
                .beginStep()
                .setAlpha(-5.0)
                .endStep()
                .beginStep()
                .setAlpha(0.0)
                .endStep()
                .beginStep()
                .setAlpha(5)
                .endStep()
                .add();

        SensitivityFactor factorPhase1 = createBranchFlowPerTransformerLegPSTAngle("L1", "PS1", ThreeWindingsTransformer.Side.ONE);
        SensitivityFactor factorPhase2 = createBranchFlowPerTransformerLegPSTAngle("L1", "PS1", ThreeWindingsTransformer.Side.TWO);
        SensitivityFactor factorPhase3 = createBranchFlowPerTransformerLegPSTAngle("L1", "PS1", ThreeWindingsTransformer.Side.THREE);
        List<SensitivityFactor> factors = List.of(factorPhase1, factorPhase2, factorPhase3);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(2, result.getValues().size());
        assertEquals(-5.421, result.getBranchFlow1SensitivityValue("PS1", "L1", SensitivityVariableType.TRANSFORMER_PHASE_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(5.421, result.getBranchFlow1SensitivityValue("PS1", "L1", SensitivityVariableType.TRANSFORMER_PHASE_2), LoadFlowAssert.DELTA_POWER);
        //Sensitivity value at phase 3 is filtered because it is 0
    }

    @Test
    void testThreeWindingsTransformerNoPhaseShifter() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Network network = VoltageControlNetworkFactory.createNetworkWithT3wt();
        SensitivityFactor factorPhase1 = createBranchFlowPerTransformerLegPSTAngle("LINE_12", "T3wT", ThreeWindingsTransformer.Side.ONE);
        List<SensitivityFactor> factors = List.of(factorPhase1);
        List<Contingency> contingencies = Collections.emptyList();
        List<SensitivityVariableSet> variableSets = Collections.emptyList();
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Three windings transformer 'T3wT' leg on side 'TRANSFORMER_PHASE_1' has no phase tap changer", e.getCause().getMessage());
    }

    @Test
    void testThreeWindingsTransformerError() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Network network = VoltageControlNetworkFactory.createNetworkWithT3wt();
        SensitivityFactor factorPhase1 = createBranchFlowPerTransformerLegPSTAngle("LINE_12", "transfo", ThreeWindingsTransformer.Side.ONE);
        List<SensitivityFactor> factors = List.of(factorPhase1);
        List<Contingency> contingencies = Collections.emptyList();
        List<SensitivityVariableSet> variableSets = Collections.emptyList();
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Three windings transformer 'transfo' not found", e.getCause().getMessage());
    }
}
