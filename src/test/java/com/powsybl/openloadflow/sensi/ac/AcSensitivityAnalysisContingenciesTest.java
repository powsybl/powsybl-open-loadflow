/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.*;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcSensitivityAnalysisContingenciesTest extends AbstractSensitivityAnalysisTest {

    @Test
    void test4BusesSensi() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l23");

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());
        assertEquals(-0.5409d, result.getBranchFlow1SensitivityValue("l23", "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, result.getBranchFlow1SensitivityValue("l23", "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, result.getBranchFlow1SensitivityValue("l23", "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, result.getBranchFlow1SensitivityValue("l23", "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, result.getBranchFlow1SensitivityValue("l23", "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, result.getBranchFlow1SensitivityValue("l23", "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, result.getBranchFlow1SensitivityValue("l23", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6000d, result.getBranchFlow1SensitivityValue("l23", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4BusesSensiAdditionalFactor() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues("l23").size());

        assertEquals(-0.5409d, result.getBranchFlow1SensitivityValue("l23", "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, result.getBranchFlow1SensitivityValue("l23", "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, result.getBranchFlow1SensitivityValue("l23", "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, result.getBranchFlow1SensitivityValue("l23", "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, result.getBranchFlow1SensitivityValue("l23", "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, result.getBranchFlow1SensitivityValue("l23", "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, result.getBranchFlow1SensitivityValue("l23", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6000d, result.getBranchFlow1SensitivityValue("l23", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4BusesFunctionReference() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(0.6761d, result.getBranchFlow1FunctionReferenceValue("l23", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.0d, result.getBranchFlow1FunctionReferenceValue("l23", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l23", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.676d, result.getBranchFlow1FunctionReferenceValue("l23", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.324d, result.getBranchFlow1FunctionReferenceValue("l23", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesLoosingATransfo() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l23");

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());
        assertEquals(-0.5409d, result.getBranchFlow1SensitivityValue("l23", "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.400d, result.getBranchFlow1SensitivityValue("l23", "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, result.getBranchFlow1SensitivityValue("l23", "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, result.getBranchFlow1SensitivityValue("l23", "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, result.getBranchFlow1SensitivityValue("l23", "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, result.getBranchFlow1SensitivityValue("l23", "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, result.getBranchFlow1SensitivityValue("l23", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.600d, result.getBranchFlow1SensitivityValue("l23", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesLoosingATransfoFunctionRef() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l23");

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());
        assertEquals(0.6761d, result.getBranchFlow1FunctionReferenceValue("l23", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.0d, result.getBranchFlow1FunctionReferenceValue("l23", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l23", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.676d, result.getBranchFlow1FunctionReferenceValue("l23", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.324d, result.getBranchFlow1FunctionReferenceValue("l23", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesPhaseShift() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23", "l34", Branch.Side.ONE)).collect(Collectors.toList());

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "l23", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0564d, result.getBranchFlow1SensitivityValue("l34", "l23", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "l23", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.0564d, result.getBranchFlow1SensitivityValue("l34", "l23", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0564d, result.getBranchFlow1SensitivityValue("l34", "l23", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesFunctionReferenceWithTransformer() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23", "l34", Branch.Side.ONE)).collect(Collectors.toList());

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());
        assertEquals(-1.0d, result.getBranchFlow1FunctionReferenceValue("l34", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.7319d, result.getBranchFlow1FunctionReferenceValue("l34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l34", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.268d, result.getBranchFlow1FunctionReferenceValue("l34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.7319d, result.getBranchFlow1FunctionReferenceValue("l34", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesFunctionReferenceWithTransformerInAdditionalFactors() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues("l34").size());
        assertEquals(-1.0d, result.getBranchFlow1FunctionReferenceValue("l34", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.7319d, result.getBranchFlow1FunctionReferenceValue("l34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l34", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.268d, result.getBranchFlow1FunctionReferenceValue("l34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.7319d, result.getBranchFlow1FunctionReferenceValue("l34", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testMultipleContingencies() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                                                             network.getBranchStream().collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")),
                                                  new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(0.1352d, result.getBranchFlow1SensitivityValue("l23", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.600d, result.getBranchFlow1SensitivityValue("l23", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, result.getBranchFlow1SensitivityValue("l23", "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.2d, result.getBranchFlow1SensitivityValue("l34", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.4056d, result.getBranchFlow1SensitivityValue("l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1944d, result.getBranchFlow1SensitivityValue("l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1944d, result.getBranchFlow1SensitivityValue("l34", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testMultipleContingenciesAdditionalFactors() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l14", "g2"),
                                                  createBranchFlowPerInjectionIncrease("l23", "g2", "l23"),
                                                  createBranchFlowPerInjectionIncrease("l12", "g2", "l23"),
                                                  createBranchFlowPerInjectionIncrease("l23", "g2", "l34"),
                                                  createBranchFlowPerInjectionIncrease("l34", "g2", "l34"),
                                                  createBranchFlowPerInjectionIncrease("l12", "g2", "l34"));

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")),
                                                  new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues("l34").size());
        assertEquals(3, result.getValues("l23").size());

        assertEquals(0.1352d, result.getBranchFlow1SensitivityValue("l23", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.600d, result.getBranchFlow1SensitivityValue("l23", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g2", "l23"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.2d, result.getBranchFlow1SensitivityValue("l34", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.4056d, result.getBranchFlow1SensitivityValue("l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1944d, result.getBranchFlow1SensitivityValue("l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyWithMultipleBranches() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                                                             network.getBranchStream().collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("l23+l34", new BranchContingency("l23"),  new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(0.2d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.600d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnSingleLine() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorOnOneSide();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(14, result.getValues("l34").size());
        assertEquals(-0.1324d, result.getBranchFlow1SensitivityValue("l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2676d, result.getBranchFlow1SensitivityValue("l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1324d, result.getBranchFlow1SensitivityValue("l34", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1986d, result.getBranchFlow1SensitivityValue("l34", "g3", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.4013d, result.getBranchFlow1SensitivityValue("l34", "g3", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1986d, result.getBranchFlow1SensitivityValue("l34", "g3", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g3", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g3", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g3", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g3", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-1.662, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.066, result.getBranchFlow1FunctionReferenceValue("l34", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyMultipleLinesBreaksOneContingency() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLinesWithAdditionnalGens();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l24+l35", new BranchContingency("l24"), new BranchContingency("l35")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l24+l35");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(24, result.getValues("l24+l35").size());
        assertEquals(-0.1331d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2669d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1331d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1997d, result.getBranchFlow1SensitivityValue("l24+l35", "g3", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.4003d, result.getBranchFlow1SensitivityValue("l24+l35", "g3", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1997d, result.getBranchFlow1SensitivityValue("l24+l35", "g3", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g3", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g3", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g3", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g3", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g3", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskRescale() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("g2", 0.4f),
                                                              new WeightedSensitivityVariable("g6", 0.6f));
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream()
                                                 .map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk", "l34", Branch.Side.ONE))
                                                 .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        assertEquals(7, result.getValues("l34").size());
        assertEquals(-0.5d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "glsk", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "glsk", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "glsk", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskRescaleAdditionalFactor() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("g2", 0.4f),
                                                              new WeightedSensitivityVariable("g6", 0.6f));
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream()
                                                 .map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk", "l34", Branch.Side.ONE))
                                                 .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        assertEquals(7, result.getValues("l34").size());
        assertEquals(-0.5d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "glsk", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "glsk", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "glsk", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testInjectionNotFoundAdditionalFactorContingency() {
        testInjectionNotFoundAdditionalFactorContingency(false);
    }

    @Test
    void testBusVoltagePerTargetV() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l45", new BranchContingency("l45")));

        List<String> busIds = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            busIds.add("b" + i);
        }
        List<SensitivityFactor> factors = busIds.stream()
                                                .map(bus -> createBusVoltagePerTargetV(bus, "g2"))
                                                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(0.916d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(0)), LoadFlowAssert.DELTA_V); // 0 on the slack
        assertEquals(1d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(1)), LoadFlowAssert.DELTA_V); // 1 on itself
        assertEquals(0.8133d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(2)), LoadFlowAssert.DELTA_V);
        assertEquals(0.512d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(3)), LoadFlowAssert.DELTA_V);
        assertEquals(0d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(4)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(5)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(6)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0.209d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(7)), LoadFlowAssert.DELTA_V);
        assertEquals(0.1062d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(8)), LoadFlowAssert.DELTA_V);
        assertEquals(0d, result.getBusVoltageSensitivityValue("l45", "g2", busIds.get(9)), LoadFlowAssert.DELTA_V); // no impact on a pv
    }

    @Test
    void testBusVoltagePerTargetVFunctionRef() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l45", new BranchContingency("l45")));

        List<String> busIds = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            busIds.add("b" + i);
        }
        List<SensitivityFactor> factors = busIds.stream()
                                                .map(bus -> createBusVoltagePerTargetV(bus, "g2"))
                                                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(0.993d, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(0)), LoadFlowAssert.DELTA_V);
        assertEquals(1d, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(1)), LoadFlowAssert.DELTA_V);
        assertEquals(0.992d, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(2)), LoadFlowAssert.DELTA_V);
        assertEquals(0.988d, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(3)), LoadFlowAssert.DELTA_V);
        assertEquals(Double.NaN, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(4)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(Double.NaN, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(5)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(Double.NaN, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(6)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0.987d, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(7)), LoadFlowAssert.DELTA_V);
        assertEquals(0.989d, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(8)), LoadFlowAssert.DELTA_V);
        assertEquals(1d, result.getBusVoltageFunctionReferenceValue("l45", busIds.get(9)), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testHvdcSensiRescale() {
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);

        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators();
        network1.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        network1.getLine("l25").getTerminal1().disconnect();
        network1.getLine("l25").getTerminal2().disconnect();
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Network network2 = HvdcNetworkFactory.createNetworkWithGenerators();
        network2.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + SENSI_CHANGE);
        network2.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        network2.getLine("l25").getTerminal1().disconnect();
        network2.getLine("l25").getTerminal2().disconnect();
        runLf(network2, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
                                                  .collect(Collectors.toMap(
                                                      lineId -> lineId,
                                                      line -> (network1.getLine(line).getTerminal1().getP() - network2.getLine(line).getTerminal1().getP()) / SENSI_CHANGE
                                                  ));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                                                                         SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                                                                         false, ContingencyContext.all());

        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class)
                       .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                       .setSlackBusId("b1_vl_0"); // the most meshed bus selected in the loadflow

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.singletonList(new Contingency("l25", new BranchContingency("l25"))), Collections.emptyList(), sensiParameters);

        // FIXME
//        assertEquals(loadFlowDiff.get("l12"), hvdcWriter.getBranchFlow1SensitivityValue(Pair.of("hvdc34", "l12"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l13"), hvdcWriter.getBranchFlow1SensitivityValue(Pair.of("hvdc34", "l13"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l23"), hvdcWriter.getBranchFlow1SensitivityValue(Pair.of("hvdc34", "l23"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(0d, hvdcWriter.getBranchFlow1SensitivityValue(Pair.of("hvdc34", "l25"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l45"), hvdcWriter.getBranchFlow1SensitivityValue(Pair.of("hvdc34", "l45"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l46"), hvdcWriter.getBranchFlow1SensitivityValue(Pair.of("hvdc34", "l46"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l56"), hvdcWriter.getBranchFlow1SensitivityValue(Pair.of("hvdc34", "l56"), "l25"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1", "g2").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23").map(network::getBranch).collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof NotImplementedException);
        assertEquals("Contingencies on a DC line are not yet supported in AC mode.", e.getCause().getMessage());
    }

    @Test
    void testContingencyPropagationLfSwitch() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = NodeBreakerNetworkFactory.create3Bars();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "L2");

        List<Contingency> contingencies = List.of(new Contingency("L2", new BranchContingency("L2")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        //Flow is around 200 on all lines
        result.getValues().forEach(v -> assertEquals(200, v.getFunctionReference(), 5));

        // Propagating contingency on L2 encounters a coupler, which is not (yet) supported in sensitivity analysis
        assertTrue(result.getValues("L2").isEmpty());
    }

    @Test
    void testDanglingLineContingencyDistributedSlackOnLoads() {
        Network network = BoundaryFactory.createWithLoad();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl3_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "g1"));

        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getPreContingencyValues().size());
        assertEquals(0.3697, result.getBranchFlow1SensitivityValue("g1", "l1"), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(75.272, result.getBranchFlow1FunctionReferenceValue("l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3695, result.getBranchFlow1SensitivityValue("dl1", "g1", "l1"), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(36.794, result.getBranchFlow1FunctionReferenceValue("dl1", "l1"), LoadFlowAssert.DELTA_POWER);

        network.getDanglingLine("dl1").getTerminal().disconnect();

        LoadFlowParameters parameters = sensiParameters.getLoadFlowParameters();
        parameters.getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001);
        runLf(network, parameters, Reporter.NO_OP);

        Line l1 = network.getLine("l1");
        double initialP = l1.getTerminal1().getP();
        assertEquals(36.795, initialP, LoadFlowAssert.DELTA_POWER);

        network.getGenerator("g1").setTargetP(network.getGenerator("g1").getTargetP() + 1);

        runLf(network, sensiParameters.getLoadFlowParameters(), Reporter.NO_OP);

        double finalP = l1.getTerminal1().getP();
        assertEquals(37.164, finalP, LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3695, finalP - initialP, LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
    }

    @Test
    void testDanglingLineContingencyDistributedSlackOnGenerators() {
        Network network = BoundaryFactory.createWithLoad();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "load3"));

        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getPreContingencyValues().size());
        assertEquals(-0.3704, result.getBranchFlow1SensitivityValue("load3", "l1"), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(75.336, result.getBranchFlow1FunctionReferenceValue("l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.3704, result.getBranchFlow1SensitivityValue("dl1", "load3", "l1"), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(3.0071, result.getBranchFlow1FunctionReferenceValue("dl1", "l1"), LoadFlowAssert.DELTA_POWER);

        network.getDanglingLine("dl1").getTerminal().disconnect();

        LoadFlowParameters parameters = sensiParameters.getLoadFlowParameters();
        parameters.getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001);
        runLf(network, parameters, Reporter.NO_OP);

        Line l1 = network.getLine("l1");
        double initialP = l1.getTerminal1().getP();
        assertEquals(3.0071, initialP, LoadFlowAssert.DELTA_POWER);

        network.getLoad("load3").setP0(network.getLoad("load3").getP0() + 1);
        runLf(network, sensiParameters.getLoadFlowParameters(), Reporter.NO_OP);

        double finalP = l1.getTerminal1().getP();
        assertEquals(3.3775, finalP, LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.3704, initialP - finalP, LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
    }

    @Test
    void testEurostagFactory() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLGEN_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                                                 .map(bus -> createBusVoltagePerTargetV(bus.getId(), "NHV2_NLOAD"))
                                                 .collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("NHV1_NHV2_2", new BranchContingency("NHV1_NHV2_2")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getPreContingencyValues().size());
        assertEquals(0d, result.getBusVoltageSensitivityValue("NHV2_NLOAD", "NGEN"), LoadFlowAssert.DELTA_V);
        assertEquals(0.035205d, result.getBusVoltageSensitivityValue("NHV2_NLOAD", "NHV1"), LoadFlowAssert.DELTA_V);
        assertEquals(0.077194d, result.getBusVoltageSensitivityValue("NHV2_NLOAD", "NHV2"), LoadFlowAssert.DELTA_V);
        assertEquals(1.0d, result.getBusVoltageSensitivityValue("NHV2_NLOAD", "NLOAD"), LoadFlowAssert.DELTA_V);

        assertEquals(0d, result.getBusVoltageSensitivityValue("NHV1_NHV2_2", "NHV2_NLOAD", "NGEN"), LoadFlowAssert.DELTA_V);
        assertEquals(0.026329d,  result.getBusVoltageSensitivityValue("NHV1_NHV2_2", "NHV2_NLOAD", "NHV1"), LoadFlowAssert.DELTA_V);
        assertEquals(0.103981d,  result.getBusVoltageSensitivityValue("NHV1_NHV2_2", "NHV2_NLOAD", "NHV2"), LoadFlowAssert.DELTA_V);
        assertEquals(1.0d,  result.getBusVoltageSensitivityValue("NHV1_NHV2_2", "NHV2_NLOAD", "NLOAD"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testGlskOutsideMainComponentWithContingency() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("l12", "glsk"));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("g6", 1f),
                                                                                                       new WeightedSensitivityVariable("g3", 2f))));

        List<Contingency> contingencies = List.of(new Contingency("additionnalline_0", new BranchContingency("additionnalline_0")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        assertEquals(2, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("glsk", "l12"), LoadFlowAssert.DELTA_POWER);

        assertEquals(100.080, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(100.080, result.getBranchFlow1FunctionReferenceValue("additionnalline_0", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesPhaseShiftIntensityFunctionReference() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(Double.NaN, result.getFunctionReferenceValue("l23", "l23", SensitivityFunctionType.BRANCH_CURRENT_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, result.getBranchCurrent1SensitivityValue("l23", "l23", "l12"), LoadFlowAssert.DELTA_POWER);
    }
}
