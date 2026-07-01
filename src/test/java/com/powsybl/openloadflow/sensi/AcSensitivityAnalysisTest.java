/*
 * Copyright (c) 2020-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.action.Action;
import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.PowsyblTestReportResourceBundle;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.*;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.VoltageRegulation;
import com.powsybl.iidm.network.extensions.VoltageRegulationAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AcSensitivityAnalysisTest extends AbstractSensitivityAnalysisTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcSensitivityAnalysisTest.class);

    AcSensitivityAnalysisTest(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    @Test
    void testEsgTuto() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getLineStream().collect(Collectors.toList()));

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(2, result.getValues().size());
        assertEquals(0.498d, result.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.498d, result.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testEsgTutoMT() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");

        OpenSensitivityAnalysisParameters openSensitivityAnalysisParameters = OpenSensitivityAnalysisParameters.getOrDefault(sensiParameters)
                .setThreadCount(2);
        sensiParameters.addExtension(OpenSensitivityAnalysisParameters.class, openSensitivityAnalysisParameters);

        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);

        List<Contingency> contingencies = new ArrayList<>();
        // Create a large list of contingencies
        for (int i = 0; i < 100; i++) {
            final String suffix = "-" + i;
            contingencies.addAll(network.getLineStream().map(l -> new Contingency(l.getId() + suffix, new LineContingency(l.getId()))).toList());
        }

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies);

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(402, result.getValues().size()); // (Base case + 200 contingencies) * 2 factors = 402 values
        assertEquals(0.498d, result.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.498d, result.getBranchFlow1SensitivityValue("GEN", "NHV1_NHV2_2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        for (int i = 0; i < 100; i++) {
            assertEquals(0,
                    result.getBranchFlow1SensitivityValue("NHV1_NHV2_1-" + i, "GEN", "NHV1_NHV2_1", SensitivityVariableType.INJECTION_ACTIVE_POWER),
                    LoadFlowAssert.DELTA_POWER);
            assertEquals(0.997,
                    result.getBranchFlow1SensitivityValue("NHV1_NHV2_1-" + i, "GEN", "NHV1_NHV2_2", SensitivityVariableType.INJECTION_ACTIVE_POWER),
                    LoadFlowAssert.DELTA_POWER);
            assertEquals(0.997,
                    result.getBranchFlow1SensitivityValue("NHV1_NHV2_2-" + i, "GEN", "NHV1_NHV2_1", SensitivityVariableType.INJECTION_ACTIVE_POWER),
                    LoadFlowAssert.DELTA_POWER);
            assertEquals(0,
                    result.getBranchFlow1SensitivityValue("NHV1_NHV2_2-" + i, "GEN", "NHV1_NHV2_2", SensitivityVariableType.INJECTION_ACTIVE_POWER),
                    LoadFlowAssert.DELTA_POWER);
        }

        // Test number of calls to the writer
        SensitivityFactorReader factorReader = new SensitivityFactorModelReader(factors, network);
        AtomicInteger valueCallCount = new AtomicInteger(0);
        AtomicInteger statusCallCount = new AtomicInteger(0);
        SensitivityResultWriter resultWriter = new SensitivityResultWriter() {

            public void writeSensitivityValue(int factorIndex, int contingencyIndex, int operatorStrategyIndex, double value, double functionReference) {
                valueCallCount.incrementAndGet();
            }

            @Override
            public void writeStateStatus(int contingencyIndex, int operatorStrategyIndex, SensitivityAnalysisResult.Status status) {
                statusCallCount.incrementAndGet();
            }
        };

        runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        sensiRunner.run(network, network.getVariantManager().getWorkingVariantId(),
                factorReader,
                resultWriter,
                runParameters);

        assertEquals(0, statusCallCount.get()); // Not called for the case case
        assertEquals(factors.size(), valueCallCount.get());

        // now check call count with contingencies, and report
        statusCallCount.set(0);
        valueCallCount.set(0);

        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("test")
                .build();

        runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies)
                .setReportNode(reportNode);
        sensiRunner.run(network, network.getVariantManager().getWorkingVariantId(),
                factorReader,
                resultWriter,
                runParameters);

        assertEquals(200, statusCallCount.get()); // 200 contingencies
        assertEquals(402, valueCallCount.get()); // (base case + 200 contingences) * 2 factors = 402

        assertReportEquals("/sensiMtReport.txt", reportNode);

    }

    @Test
    void testEsgTutoMTWithSpecificContingencyContexts() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");

        OpenSensitivityAnalysisParameters openSensitivityAnalysisParameters = OpenSensitivityAnalysisParameters.getOrDefault(sensiParameters)
                .setThreadCount(2);
        sensiParameters.addExtension(OpenSensitivityAnalysisParameters.class, openSensitivityAnalysisParameters);

        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);

        List<Contingency> contingencies = new ArrayList<>();
        // Create a large list of contingencies
        for (int i = 0; i < 100; i++) {
            final String suffix = "-" + i;
            contingencies.addAll(network.getLineStream().map(l -> new Contingency(l.getId() + suffix, new LineContingency(l.getId()))).toList());
        }

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()), contingencies.get(0).getId(), TwoSides.ONE);

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies);

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        // IN MT mode, factors with specific contingencies are not reported as invalid for the other contingencies. They are just not written.
        assertEquals(2, result.getValues().size()); // (1 contingency) * 2 factors = 2 values

        assertEquals(0,
                result.getBranchFlow1SensitivityValue("NHV1_NHV2_1-0", "GEN", "NHV1_NHV2_1", SensitivityVariableType.INJECTION_ACTIVE_POWER),
                LoadFlowAssert.DELTA_POWER);
        assertEquals(0.997,
                result.getBranchFlow1SensitivityValue("NHV1_NHV2_1-0", "GEN", "NHV1_NHV2_2", SensitivityVariableType.INJECTION_ACTIVE_POWER),
                LoadFlowAssert.DELTA_POWER);
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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(-0.632d, result.getBranchFlow1SensitivityValue("g4", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.122d, result.getBranchFlow1SensitivityValue("g4", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.122d, result.getBranchFlow1SensitivityValue("g4", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.368d, result.getBranchFlow1SensitivityValue("g4", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.245d, result.getBranchFlow1SensitivityValue("g4", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testUnsupportedParameters() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        // These are all unsupported parameters that should be overriden
        sensiParameters.getLoadFlowParameters()
                .setComponentMode(LoadFlowParameters.ComponentMode.ALL_CONNECTED)
                .getExtension(OpenLoadFlowParameters.class)
                    .setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_ZERO_IMPEDANCE_LINE)
                    .setNetworkCacheEnabled(true)
                    .setReferenceBusSelectionMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY);

        Network network = FourBusNetworkFactory.createBaseNetwork();
        runLf(network, sensiParameters.getLoadFlowParameters());
        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g4")),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        // Results should be the same (and also warn log messages should appear)
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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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
                network.getBranchStream().collect(Collectors.toList()), null, TwoSides.TWO);

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setVariableSets(variableSets)
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(-0.044d, result.getBranchFlow1SensitivityValue("glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.069d, result.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.031d, result.getBranchFlow1SensitivityValue("glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.006d, result.getBranchFlow1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.037d, result.getBranchFlow1SensitivityValue("glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDuplicateVariableSet() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        // this network has no G or B, so we should be very close to DC results
        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<WeightedSensitivityVariable> variables1 = List.of(new WeightedSensitivityVariable("g1", 0.25f),
                new WeightedSensitivityVariable("g4", 0.25f),
                new WeightedSensitivityVariable("d2", 0.5f));

        List<WeightedSensitivityVariable> variables2 = List.of(new WeightedSensitivityVariable("g1", 0.50f),
                new WeightedSensitivityVariable("g4", 0.50f));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", variables1), new SensitivityVariableSet("glsk", variables2));

        List<SensitivityFactor> factors = network.getBranchStream()
                .map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk"))
                .collect(Collectors.toList());

        SensitivityAnalysisRunParameters sensitivityAnalysisRunParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setVariableSets(variableSets);

        CompletionException ex = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, sensitivityAnalysisRunParameters));
        assertEquals("com.powsybl.commons.PowsyblException: Variable set ID 'glsk' is duplicated", ex.getMessage());

    }

    @Test
    void testDuplicateContingency() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.create();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<Contingency> contingencies = List.of(new Contingency("foo", new LineContingency("l12")), new Contingency("foo", new LineContingency("l14")));

        List<SensitivityFactor> factors = Collections.singletonList(createBranchFlowPerInjectionIncrease("l12", "g1"));

        SensitivityAnalysisRunParameters sensitivityAnalysisRunParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies);

        CompletionException ex = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, sensitivityAnalysisRunParameters));
        assertEquals("com.powsybl.commons.PowsyblException: Contingency ID 'foo' is duplicated", ex.getMessage());

    }

    @Test
    void testGlskWithBranchCurrentFunction() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.create();

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("g1", 0.25f),
                new WeightedSensitivityVariable("g4", 0.25f),
                new WeightedSensitivityVariable("d2", 0.5f));
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream()
                .map(branch -> new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1,
                        branch.getId(),
                        SensitivityVariableType.INJECTION_ACTIVE_POWER,
                        "glsk",
                        true,
                        ContingencyContext.none()))
                .toList();

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setVariableSets(variableSets)
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(-25.36d, result.getBranchCurrent1SensitivityValue("glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-39.919d, result.getBranchCurrent1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(17.942d, result.getBranchCurrent1SensitivityValue("glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(3.515d, result.getBranchCurrent1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-21.609d, result.getBranchCurrent1SensitivityValue("glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
    }

    @Test
    void test4busesWithTransfoInjection() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_ANGLE);

        network.getBranch("l23").getTerminal1().connect();
        network.getBranch("l23").getTerminal2().disconnect();

        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_ANGLE);

        network.getBranch("l23").getTerminal1().connect();
        network.getBranch("l23").getTerminal2().disconnect();

        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        List<SensitivityFactor> factorsSide1 = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23", TwoSides.ONE)).toList();
        List<SensitivityFactor> factorsSide2 = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23", TwoSides.TWO)).toList();

        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(factorsSide1);
        factors.addAll(factorsSide2);

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        List<SensitivityFactor> factorsSide1 = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23", TwoSides.ONE)).collect(Collectors.toList());
        List<SensitivityFactor> factorsSide2 = network.getBranchStream().map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23", TwoSides.TWO)).collect(Collectors.toList());

        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(factorsSide1);
        factors.addAll(factorsSide2);

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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
    void testBusVoltagePerTargetV() {
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "g2"))
                .collect(Collectors.toList());

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class)
                .setTransformerVoltageControlMode(OpenLoadFlowParameters.TransformerVoltageControlMode.WITH_GENERATOR_VOLTAGE_CONTROL);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "T2wT"))
                .collect(Collectors.toList());

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        Function<String, Double> getV = busId -> network.getBusView().getBus(busId).getV();
        assertEquals(getV.apply("VLGEN_0"), result.getBusVoltageFunctionReferenceValue("NGEN"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLHV1_0"), result.getBusVoltageFunctionReferenceValue("NHV1"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLHV2_0"), result.getBusVoltageFunctionReferenceValue("NHV2"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLLOAD_0"), result.getBusVoltageFunctionReferenceValue("NLOAD"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetQGen() {
        Network network = ReactiveInjectionNetworkFactory.createTwoGensOneLoad();

        List<SensitivityFactor> factors = Arrays.asList(createBusVoltagePerTargetQ("b3", "g2", null),
                createBusVoltagePerTargetQ("b2", "g2", null),
                createBusVoltagePerTargetQ("b1", "g2", null));

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b2", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001).setNewtonRaphsonConvEpsPerEq(0.0001);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
        assertEquals(0d, result.getBusVoltageSensitivityValue("g2", "b1", SensitivityVariableType.INJECTION_REACTIVE_POWER), 1e-5);
        assertEquals(0.00963d, result.getBusVoltageSensitivityValue("g2", "b3", SensitivityVariableType.INJECTION_REACTIVE_POWER), 1e-5);
        assertEquals(0.01926d, result.getBusVoltageSensitivityValue("g2", "b2", SensitivityVariableType.INJECTION_REACTIVE_POWER), 1e-5);

        // sensitivty of V to Q of PVBus should be exactly 0
        factors = Arrays.asList(new SensitivityFactor[] {
                createBusVoltagePerTargetQ("b3", "g1", null),
                createBusVoltagePerTargetQ("b2", "g1", null),
                createBusVoltagePerTargetQ("b1", "g1", null)});
        result = sensiRunner.run(network, factors, runParameters);
        assertEquals(0d, result.getBusVoltageSensitivityValue("g1", "b1", SensitivityVariableType.INJECTION_REACTIVE_POWER), 0);
        assertEquals(0d, result.getBusVoltageSensitivityValue("g1", "b3", SensitivityVariableType.INJECTION_REACTIVE_POWER), 0);
        assertEquals(0d, result.getBusVoltageSensitivityValue("g1", "b2", SensitivityVariableType.INJECTION_REACTIVE_POWER), 0);
        assertEquals(3, result.getValues().size());

        sensiParameters.setFlowVoltageSensitivityValueThreshold(0.1);

        result = sensiRunner.run(network, factors, runParameters);
        assertEquals(0, result.getValues().size());
    }

    @Test
    void testInjectionQPerTargetV() {
        Network network = ReactiveInjectionNetworkFactory.createTwoGensOneLoad();

        List<SensitivityFactor> factors = Arrays.asList(createTargetQPerTargetV("b1", "g1", null),
                createTargetQPerTargetV("b2", "g1", null),
                createTargetQPerTargetV("b3", "g1", null));

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b2", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.00001).setNewtonRaphsonConvEpsPerEq(0.000001);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
        assertEquals(0.0741, result.getSensitivityValue("g1", "b1", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE), 1e-3);
        // Other sensi should be null
        assertEquals(0, result.getSensitivityValue("g1", "b2", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE), 1e-6);
        assertEquals(0, result.getSensitivityValue("g1", "b3", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE), 1e-6);
        assertEquals(3, result.getValues().size());

        sensiParameters.setFlowVoltageSensitivityValueThreshold(0.1);
        result = sensiRunner.run(network, factors, runParameters);
        assertEquals(0, result.getValues().size());

        sensiParameters.setFlowVoltageSensitivityValueThreshold(0.05);
        result = sensiRunner.run(network, factors, runParameters);
        assertEquals(1, result.getValues().size());
    }

    @Test
    void testInjectionQPerTargetVWithShunt() {
        Network network = ShuntNetworkFactory.create();
        network.getShuntCompensator("SHUNT").setSectionCount(1); // non null b

        List<SensitivityFactor> factors = Arrays.asList(createTargetQPerTargetV("b1", "g1", null),
                createTargetQPerTargetV("b2", "g1", null),
                createTargetQPerTargetV("b3", "g1", null));

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b2", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.00001).setNewtonRaphsonConvEpsPerEq(0.000001);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(0.788, result.getSensitivityValue("g1", "b1", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE), 1e-3);
        assertEquals(-0.789, result.getSensitivityValue("g1", "b3", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE), 1e-3);
        // Other sensi should be null
        assertEquals(0, result.getSensitivityValue("g1", "b2", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE), 1e-6);
    }

    @Test
    void testInjectionQPerTargetVCornerCases() {
        // Tests mainly created to satisfy code coverage criteria
        Network network = ShuntNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b2", false);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        // Test a combination that is not supported yet
        SensitivityFactor notSupportedYetAndSonarWantsToSeeItInAction =
                new SensitivityFactor(SensitivityFunctionType.BUS_REACTIVE_POWER, "b1",
                        SensitivityVariableType.INJECTION_ACTIVE_POWER, "b2", false,
                        ContingencyContext.all());
        Throwable thrown = assertThrows(CompletionException.class,
                () -> sensiRunner.run(network, Arrays.asList(notSupportedYetAndSonarWantsToSeeItInAction), runParameters));
        assertTrue(thrown.getMessage().contains("Variable type INJECTION_ACTIVE_POWER not supported with function type BUS_REACTIVE_POWER"));

        // Test sensitivity for a bus that does not exist in the LfNetwork
        network.getVoltageLevel("vl1").getBusBreakerView().newBus().setId("NotConnected").add();
        SensitivityFactor injectionBusDoesNotExist =
                new SensitivityFactor(SensitivityFunctionType.BUS_REACTIVE_POWER, "NotConnected",
                        SensitivityVariableType.BUS_TARGET_VOLTAGE, "g1", false,
                        ContingencyContext.all());
        SensitivityAnalysisResult result = sensiRunner.run(network, Arrays.asList(injectionBusDoesNotExist), runParameters);
        assertEquals(0,
                result.getSensitivityValue("g1", "NotConnected", SensitivityFunctionType.BUS_REACTIVE_POWER, SensitivityVariableType.BUS_TARGET_VOLTAGE),
                0);
    }

    @Test
    void testAdditionnalFactors() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setVariableSets(variableSets)
                .setContingencies(contingencies)
                .setParameters(sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
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
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setVariableSets(variableSets)
                .setContingencies(contingencies)
                .setParameters(sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
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
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setVariableSets(variableSets)
                .setContingencies(contingencies)
                .setParameters(sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
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
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setVariableSets(variableSets)
                .setContingencies(contingencies)
                .setParameters(sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getBranchFlow1SensitivityValue("hvdc34", "l12", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getBranchFlow1SensitivityValue("hvdc34", "l13", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getBranchFlow1SensitivityValue("hvdc34", "l23", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiCurrent() {
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
                        line -> (network1.getLine(line).getTerminal1().getI() - network.getLine(line).getTerminal1().getI()) / SENSI_CHANGE
                ));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_CURRENT_1, List.of("l12", "l13", "l23"),
                SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                false, ContingencyContext.all());
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getBranchCurrent1SensitivityValue("hvdc34", "l12", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(loadFlowDiff.get("l13"), result.getBranchCurrent1SensitivityValue("hvdc34", "l13", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(loadFlowDiff.get("l23"), result.getBranchCurrent1SensitivityValue("hvdc34", "l23", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testHvdcSensiAcEmulationNotSupported(boolean acEmulationParameter) {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters()
                .setHvdcAcEmulation(acEmulationParameter);

        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_CURRENT_1, List.of("l12", "l13", "l23"),
                SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                false, ContingencyContext.all());
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        if (acEmulationParameter) {
            CompletionException exception = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
            assertEquals("HVDC line hvdc34 has AC emulation enabled, HVDC_LINE_ACTIVE_POWER sensitivity is not supported", exception.getCause().getMessage());
        } else {
            // If parameter hvdcAcEmulation is false, no problem
            SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
            assertEquals(3, result.getValues().size());
        }
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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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
        runLf(network, sensiParameters.getLoadFlowParameters());

        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

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
    void testBoundaryLineSensi() {
        Network network = BoundaryFactory.createWithLoad();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0");
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "bl1"),
                createBranchFlowPerInjectionIncrease("bl1", "bl1"),
                createBranchIntensityPerInjectionIncrease("bl1", "load3"));
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);

        // boundary line is connected
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
        assertEquals(-0.903d, result.getBranchFlow1SensitivityValue("bl1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(91.293, result.getBranchFlow1FunctionReferenceValue("bl1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.001d, result.getBranchFlow1SensitivityValue("bl1", "bl1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(260.51, result.getBranchCurrent1FunctionReferenceValue("bl1"), LoadFlowAssert.DELTA_I);

        // boundary line is connected on base case but will be disconnected by a contingency => 0
        List<Contingency> contingencies = List.of(new Contingency("c", new BoundaryLineContingency("bl1")));
        runParameters.setContingencies(contingencies);
        result = sensiRunner.run(network, factors, runParameters);
        assertEquals(-0.903d, result.getBranchFlow1SensitivityValue("bl1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("c", "bl1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        // boundary line is disconnected on base case => 0
        network.getBoundaryLine("bl1").getTerminal().disconnect();
        runParameters.setContingencies(Collections.emptyList());
        result = sensiRunner.run(network, factors, runParameters);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("bl1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testBatterySensi() {
        Network network = DistributedSlackNetworkFactory.createWithBattery();

        SensitivityAnalysisParameters sensiParameters = createParameters(false);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l14", "bat1"), createBranchFlowPerInjectionIncrease("l14", "b1"));

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        // The battery should be found and return the same sensitivity than to an injection at its bus
        assertEquals(result.getBranchFlow1SensitivityValue("bat1", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER),
                result.getBranchFlow1SensitivityValue("b1", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER));

    }

    @Test
    void testBatteryVoltageControlSensi() {
        Network network = DistributedSlackNetworkFactory.createWithBattery();
        network.getBattery("bat1").newExtension(VoltageRegulationAdder.class)
                .withTargetV(400)
                .withVoltageRegulatorOn(false)
                .add();

        SensitivityAnalysisParameters sensiParameters = createParameters(false);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(Collections.emptyList());

        // Computing sensitivity per target V of generator g1

        List<SensitivityFactor> factors = List.of(createBranchReactivePowerPerTargetV("l14", "g1"));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
        assertEquals(result.getSensitivityValue("g1", "l14", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityVariableType.BUS_TARGET_VOLTAGE),
                -0.001288, DELTA_SENSITIVITY_VALUE);

        // Setting battery voltage control and computing sensitivity per target V of battery bat1 -> Result should be the same
        network.getBattery("bat1").getExtension(VoltageRegulation.class)
                .setVoltageRegulatorOn(true);
        network.getGenerator("g1").setTargetQ(0).setVoltageRegulatorOn(false);

        factors = List.of(createBranchReactivePowerPerTargetV("l14", "bat1"));
        result = sensiRunner.run(network, factors, runParameters);
        assertEquals(result.getSensitivityValue("bat1", "l14", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityVariableType.BUS_TARGET_VOLTAGE),
                -0.00128, DELTA_SENSITIVITY_VALUE);
    }

    @Test
    void testBatteryNoVoltageControlExtension() {
        Network network = DistributedSlackNetworkFactory.createWithBattery();
        // Battery 'bat1' has no VoltageRegulation extension

        SensitivityAnalysisParameters sensiParameters = createParameters(false);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(Collections.emptyList());

        List<SensitivityFactor> factors = List.of(createBranchReactivePowerPerTargetV("l14", "bat1"));
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
        assertInstanceOf(PowsyblException.class, e.getCause());
        assertEquals("Regulating terminal for 'bat1' not found", e.getCause().getMessage());
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

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
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
        loadFlowRunner.run(network, sensiParameters.getLoadFlowParameters());
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                List.of("L1", "L2", "PS1"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("G1"), false, ContingencyContext.none());
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
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
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(loadFlowDiff.get("L1"), result.getBranchFlow1SensitivityValue("G1", "L1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("L2"), result.getBranchFlow1SensitivityValue("G1", "L2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void lineWithDifferentNominalVoltageTest() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0", false);
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("NHV1_NHV2_1", "NHV1_NHV2_2"),
                SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GEN"),
                false, ContingencyContext.all());

        runLf(network, sensiParameters.getLoadFlowParameters());
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
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
        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, runParameters);
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
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(1, result.getValues().size());

        assertEquals(0.0, result.getBusVoltageSensitivityValue("g4", "b3", SensitivityVariableType.BUS_TARGET_VOLTAGE));
    }

    @Test
    void testNullBusInjection() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getLoad("LOAD").getTerminal().disconnect();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLGEN_0");

        List<SensitivityFactor> factors = List.of(createBranchIntensityPerInjectionIncrease("NHV1_NHV2_1", "LOAD"));
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(92.0836, result.getBranchCurrent1FunctionReferenceValue("NHV1_NHV2_1"), LoadFlowAssert.DELTA_I);
    }

    @Test
    void testThreeWindingsTransformerAsFunction() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Network network = VoltageControlNetworkFactory.createNetworkWithT3wt();

        // Choosing variable LOAD_4 which is the one connected to the leg 3 of the transformer
        SensitivityFactor factorActivePower1Twt = createTransformerLegFlowPerInjectionIncrease("T3wT", "LOAD_4", ThreeSides.ONE);
        SensitivityFactor factorActivePower2Twt = createTransformerLegFlowPerInjectionIncrease("T3wT", "LOAD_4", ThreeSides.TWO);
        SensitivityFactor factorActivePower3Twt = createTransformerLegFlowPerInjectionIncrease("T3wT", "LOAD_4", ThreeSides.THREE);

        SensitivityFactor factorCurrent1 = createTransformerLegIntensityPerInjectionIncrease("T3wT", "LOAD_4", ThreeSides.ONE);
        SensitivityFactor factorCurrent2 = createTransformerLegIntensityPerInjectionIncrease("T3wT", "LOAD_4", ThreeSides.TWO);
        SensitivityFactor factorCurrent3 = createTransformerLegIntensityPerInjectionIncrease("T3wT", "LOAD_4", ThreeSides.THREE);

        SensitivityFactor factorReactivePower1Twt = createTransformerLegReactiveFlowPerTargetQ("T3wT", "LOAD_4", ThreeSides.ONE);
        SensitivityFactor factorReactivePower2Twt = createTransformerLegReactiveFlowPerTargetQ("T3wT", "LOAD_4", ThreeSides.TWO);
        SensitivityFactor factorReactivePower3Twt = createTransformerLegReactiveFlowPerTargetQ("T3wT", "LOAD_4", ThreeSides.THREE);

        List<SensitivityFactor> factors = List.of(factorActivePower1Twt, factorActivePower2Twt, factorActivePower3Twt,
                factorCurrent1, factorCurrent2, factorCurrent3, factorReactivePower1Twt, factorReactivePower2Twt, factorReactivePower3Twt);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        runAcLf(network);
        // Storing all function reference values
        double pLeg1Before = network.getThreeWindingsTransformer("T3wT").getLeg1().getTerminal().getP();
        double qLeg1Before = network.getThreeWindingsTransformer("T3wT").getLeg1().getTerminal().getQ();
        double iLeg1Before = network.getThreeWindingsTransformer("T3wT").getLeg1().getTerminal().getI();
        double pLeg2Before = network.getThreeWindingsTransformer("T3wT").getLeg2().getTerminal().getP();
        double qLeg2Before = network.getThreeWindingsTransformer("T3wT").getLeg2().getTerminal().getQ();
        double iLeg2Before = network.getThreeWindingsTransformer("T3wT").getLeg2().getTerminal().getI();
        double pLeg3Before = network.getThreeWindingsTransformer("T3wT").getLeg3().getTerminal().getP();
        double qLeg3Before = network.getThreeWindingsTransformer("T3wT").getLeg3().getTerminal().getQ();
        double iLeg3Before = network.getThreeWindingsTransformer("T3wT").getLeg3().getTerminal().getI();

        // Storing all p and i values to check sensitivities to injection active power
        network.getLoad("LOAD_4").setP0(network.getLoad("LOAD_4").getP0() - 0.1); // increasing injection active power (decreasing load consumption)
        runAcLf(network);
        double pLeg1After = network.getThreeWindingsTransformer("T3wT").getLeg1().getTerminal().getP();
        double iLeg1After = network.getThreeWindingsTransformer("T3wT").getLeg1().getTerminal().getI();
        double pLeg2After = network.getThreeWindingsTransformer("T3wT").getLeg2().getTerminal().getP();
        double iLeg2After = network.getThreeWindingsTransformer("T3wT").getLeg2().getTerminal().getI();
        double pLeg3After = network.getThreeWindingsTransformer("T3wT").getLeg3().getTerminal().getP();
        double iLeg3After = network.getThreeWindingsTransformer("T3wT").getLeg3().getTerminal().getI();

        // Storing all q values to check sensitivities to injection reactive power
        network.getLoad("LOAD_4").setP0(network.getLoad("LOAD_4").getP0() + 0.1); // Back to previous P0
        network.getLoad("LOAD_4").setQ0(network.getLoad("LOAD_4").getQ0() - 0.1); // increasing injection reactive power
        runAcLf(network);
        double qLeg1After = network.getThreeWindingsTransformer("T3wT").getLeg1().getTerminal().getQ();
        double qLeg2After = network.getThreeWindingsTransformer("T3wT").getLeg2().getTerminal().getQ();
        double qLeg3After = network.getThreeWindingsTransformer("T3wT").getLeg3().getTerminal().getQ();
        assertEquals(9, result.getValues().size());

        // checking numerically computed function reference values
        assertEquals(43.03, iLeg1Before, LoadFlowAssert.DELTA_I);
        assertEquals(83.91, iLeg2Before, LoadFlowAssert.DELTA_I);
        assertEquals(279.70, iLeg3Before, LoadFlowAssert.DELTA_I);
        assertEquals(10.007, pLeg1Before, LoadFlowAssert.DELTA_POWER);
        assertEquals(-4.999, pLeg2Before, LoadFlowAssert.DELTA_POWER);
        assertEquals(-4.999, pLeg3Before, LoadFlowAssert.DELTA_POWER);
        assertEquals(0.035, qLeg1Before, LoadFlowAssert.DELTA_POWER);
        assertEquals(0., qLeg2Before, LoadFlowAssert.DELTA_POWER);
        assertEquals(0., qLeg3Before, LoadFlowAssert.DELTA_POWER);

        // checking that they are the same as function reference values from sensitivity analysis
        assertEquals(43.03, result.getBranchCurrent1FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_I);
        assertEquals(83.91, result.getBranchCurrent2FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_I);
        assertEquals(279.70, result.getBranchCurrent3FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_I);
        assertEquals(10.007, result.getBranchFlow1FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4.999, result.getBranchFlow2FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4.999, result.getBranchFlow3FunctionReferenceValue("T3wT"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.035, result.getFunctionReferenceValue("T3wT", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(0., result.getFunctionReferenceValue("T3wT", SensitivityFunctionType.BRANCH_REACTIVE_POWER_2), LoadFlowAssert.DELTA_POWER);
        assertEquals(0., result.getFunctionReferenceValue("T3wT", SensitivityFunctionType.BRANCH_REACTIVE_POWER_3), LoadFlowAssert.DELTA_POWER);

        // checking numerically computed sensitivities
        assertEquals(-4.309, (iLeg1After - iLeg1Before) / 0.1, LoadFlowAssert.DELTA_I);
        assertEquals(-0.010, (iLeg2After - iLeg2Before) / 0.1, LoadFlowAssert.DELTA_I);
        assertEquals(-55.987, (iLeg3After - iLeg3Before) / 0.1, LoadFlowAssert.DELTA_I);
        assertEquals(-1.001, (pLeg1After - pLeg1Before) / 0.1, LoadFlowAssert.DELTA_POWER);
        assertEquals(0., (pLeg2After - pLeg2Before) / 0.1, LoadFlowAssert.DELTA_POWER);
        assertEquals(1., (pLeg3After - pLeg3Before) / 0.1, LoadFlowAssert.DELTA_POWER);
        assertEquals(-1., (qLeg1After - qLeg1Before) / 0.1, LoadFlowAssert.DELTA_POWER);
        assertEquals(0., (qLeg2After - qLeg2Before) / 0.1, LoadFlowAssert.DELTA_POWER);
        assertEquals(1., (qLeg3After - qLeg3Before) / 0.1, LoadFlowAssert.DELTA_POWER);

        // checking that they are the same as sensitivity values from sentivitiy analysis
        assertEquals(-4.309, result.getBranchCurrent1SensitivityValue("LOAD_4", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-0.010, result.getBranchCurrent2SensitivityValue("LOAD_4", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-55.987, result.getBranchCurrent3SensitivityValue("LOAD_4", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-1.001, result.getBranchFlow1SensitivityValue("LOAD_4", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0., result.getBranchFlow2SensitivityValue("LOAD_4", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1., result.getBranchFlow3SensitivityValue("LOAD_4", "T3wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1., result.getSensitivityValue("LOAD_4", "T3wT", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityVariableType.INJECTION_REACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0., result.getSensitivityValue("LOAD_4", "T3wT", SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, SensitivityVariableType.INJECTION_REACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1., result.getSensitivityValue("LOAD_4", "T3wT", SensitivityFunctionType.BRANCH_REACTIVE_POWER_3, SensitivityVariableType.INJECTION_REACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
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
                .setRegulationMode(PhaseTapChanger.RegulationMode.CURRENT_LIMITER)
                .setRegulating(false)
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
                .setRegulationMode(PhaseTapChanger.RegulationMode.CURRENT_LIMITER)
                .setRegulating(false)
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

        SensitivityFactor factorPhase1 = createBranchFlowPerTransformerLegPSTAngle("L1", "PS1", ThreeSides.ONE);
        SensitivityFactor factorPhase2 = createBranchFlowPerTransformerLegPSTAngle("L1", "PS1", ThreeSides.TWO);
        SensitivityFactor factorPhase3 = createBranchFlowPerTransformerLegPSTAngle("L1", "PS1", ThreeSides.THREE);
        List<SensitivityFactor> factors = List.of(factorPhase1, factorPhase2, factorPhase3);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
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
        SensitivityFactor factorPhase1 = createBranchFlowPerTransformerLegPSTAngle("LINE_12", "T3wT", ThreeSides.ONE);
        List<SensitivityFactor> factors = List.of(factorPhase1);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Three windings transformer 'T3wT' leg on side 'TRANSFORMER_PHASE_1' has no phase tap changer", e.getCause().getMessage());
    }

    @Test
    void testThreeWindingsTransformerError() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Network network = VoltageControlNetworkFactory.createNetworkWithT3wt();
        SensitivityFactor factorPhase1 = createBranchFlowPerTransformerLegPSTAngle("LINE_12", "transfo", ThreeSides.ONE);
        List<SensitivityFactor> factors = List.of(factorPhase1);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Three windings transformer 'transfo' not found", e.getCause().getMessage());
    }

    @Test
    void testGenericSensitivityThresholdFiltering() {

        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();
        parameters.setAngleFlowSensitivityValueThreshold(1.0);
        parameters.setFlowFlowSensitivityValueThreshold(1.0);
        parameters.setFlowVoltageSensitivityValueThreshold(1.0);
        parameters.setVoltageVoltageSensitivityValueThreshold(1.0);

        //Angle Flow
        assertTrue(AbstractSensitivityAnalysis.filterSensitivityValue(0.0, SensitivityVariableType.TRANSFORMER_PHASE,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, parameters));
        assertFalse(AbstractSensitivityAnalysis.filterSensitivityValue(2.0, SensitivityVariableType.TRANSFORMER_PHASE,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, parameters));
        assertFalse(AbstractSensitivityAnalysis.filterSensitivityValue(0.0, SensitivityVariableType.TRANSFORMER_PHASE,
                SensitivityFunctionType.BUS_VOLTAGE, parameters));

        //Flow Flow
        assertTrue(AbstractSensitivityAnalysis.filterSensitivityValue(0.0, SensitivityVariableType.INJECTION_ACTIVE_POWER,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, parameters));
        assertFalse(AbstractSensitivityAnalysis.filterSensitivityValue(2.0, SensitivityVariableType.INJECTION_ACTIVE_POWER,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, parameters));
        assertFalse(AbstractSensitivityAnalysis.filterSensitivityValue(0.0, SensitivityVariableType.INJECTION_ACTIVE_POWER,
                SensitivityFunctionType.BUS_VOLTAGE, parameters));

        //Flow Voltage
        assertTrue(AbstractSensitivityAnalysis.filterSensitivityValue(0.0, SensitivityVariableType.BUS_TARGET_VOLTAGE,
                SensitivityFunctionType.BRANCH_CURRENT_1, parameters));
        assertFalse(AbstractSensitivityAnalysis.filterSensitivityValue(2.0, SensitivityVariableType.BUS_TARGET_VOLTAGE,
                SensitivityFunctionType.BRANCH_CURRENT_1, parameters));

        //Voltage Voltage
        assertTrue(AbstractSensitivityAnalysis.filterSensitivityValue(0.0, SensitivityVariableType.BUS_TARGET_VOLTAGE,
                SensitivityFunctionType.BUS_VOLTAGE, parameters));
        assertFalse(AbstractSensitivityAnalysis.filterSensitivityValue(2.0, SensitivityVariableType.BUS_TARGET_VOLTAGE,
                SensitivityFunctionType.BUS_VOLTAGE, parameters));

        //Reactive power based function and variable are not filtered
        assertFalse(AbstractSensitivityAnalysis.filterSensitivityValue(0.0, SensitivityVariableType.INJECTION_REACTIVE_POWER,
                SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, parameters));
        assertFalse(AbstractSensitivityAnalysis.filterSensitivityValue(0.0, SensitivityVariableType.BUS_TARGET_VOLTAGE,
                SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, parameters));
    }

    @Test
    void testWithTieLines() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Network network = BoundaryFactory.createWithTieLine();
        List<SensitivityFactor> factors = network.getTieLineStream().map(line -> createBranchFlowPerInjectionIncrease(line.getId(), "g1")).toList();
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
        assertEquals(1, result.getValues().size());
        assertEquals(35.000, result.getBranchFlow1FunctionReferenceValue("t12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testWithTieLinesAreaInterchangeControl() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        LoadFlowParameters parameters = sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        OpenLoadFlowParameters.create(parameters).setAreaInterchangeControl(true);
        Network network = BoundaryFactory.createWithTieLine();
        List<SensitivityFactor> factors = network.getTieLineStream().map(line -> createBranchFlowPerInjectionIncrease(line.getId(), "g1", null, TwoSides.TWO)).toList();
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
        assertEquals(1, result.getValues().size());
        assertEquals(-35.000, result.getBranchFlow2FunctionReferenceValue("t12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testReactivePowerAndCurrentPerTargetVSensi() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "NLOAD");

        List<SensitivityFactor> factors = List.of(
                new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_1,
                                      "NHV2_NLOAD",
                                      SensitivityVariableType.BUS_TARGET_VOLTAGE,
                                      "GEN",
                                      false,
                                      ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_2,
                                      "NHV2_NLOAD",
                                      SensitivityVariableType.BUS_TARGET_VOLTAGE,
                                      "GEN",
                                      false,
                                      ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1,
                                      "NHV2_NLOAD",
                                      SensitivityVariableType.BUS_TARGET_VOLTAGE,
                                      "GEN",
                                      false,
                                      ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2,
                                      "NHV2_NLOAD",
                                      SensitivityVariableType.BUS_TARGET_VOLTAGE,
                                      "GEN",
                                      false,
                                      ContingencyContext.all())
        );
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(-7.9596, result.getSensitivityValue("GEN", "NHV2_NLOAD", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.0, result.getSensitivityValue("GEN", "NHV2_NLOAD", SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-52.3300, result.getSensitivityValue("GEN", "NHV2_NLOAD", SensitivityFunctionType.BRANCH_CURRENT_1, SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-132.3927, result.getSensitivityValue("GEN", "NHV2_NLOAD", SensitivityFunctionType.BRANCH_CURRENT_2, SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        runAcLf(network);

        // check reference flows are consistents with LF ones
        var twt = network.getTwoWindingsTransformer("NHV2_NLOAD");
        assertReactivePowerEquals(result.getFunctionReferenceValue("NHV2_NLOAD", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1),
                                  twt.getTerminal1());
        assertReactivePowerEquals(result.getFunctionReferenceValue("NHV2_NLOAD", SensitivityFunctionType.BRANCH_REACTIVE_POWER_2),
                                  twt.getTerminal2());
        assertCurrentEquals(result.getFunctionReferenceValue("NHV2_NLOAD", SensitivityFunctionType.BRANCH_CURRENT_1),
                            twt.getTerminal1());
        assertCurrentEquals(result.getFunctionReferenceValue("NHV2_NLOAD", SensitivityFunctionType.BRANCH_CURRENT_2),
                            twt.getTerminal2());

        // check sensi values looks consistent with 2 LF diff
        double q1Before = twt.getTerminal1().getQ();
        double q2Before = twt.getTerminal2().getQ();
        double i1Before = twt.getTerminal1().getI();
        double i2Before = twt.getTerminal2().getI();

        Generator gen = network.getGenerator("GEN");
        gen.setTargetV(gen.getTargetV() + 0.01);

        runAcLf(network);

        // for asserts, accepting 3 significant digits precision to compare with sensitivity values of the sensitivity analysis
        assertEquals(-7.96, (twt.getTerminal1().getQ() - q1Before) / 0.01, 0.01);
        assertEquals(0.0, (twt.getTerminal2().getQ() - q2Before) / 0.01, 0.01);
        assertEquals(-52.3, (twt.getTerminal1().getI() - i1Before) / 0.01, 0.1);
        assertEquals(-132, (twt.getTerminal2().getI() - i2Before) / 0.01, 1.);
    }

    @Test
    void testReactivePowerAndCurrentPerTargetQSensi() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "NLOAD");

        List<SensitivityFactor> factors = List.of(
                createBranchReactivePowerPerTargetQ("NHV1_NHV2_1", "NLOAD", TwoSides.ONE),
                createBranchReactivePowerPerTargetQ("NHV1_NHV2_1", "NLOAD", TwoSides.TWO),
                createBranchIntensityPerTargetQ("NHV1_NHV2_1", "NLOAD", TwoSides.ONE),
                createBranchIntensityPerTargetQ("NHV1_NHV2_1", "NLOAD", TwoSides.TWO)
        );
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
        assertEquals(4, result.getValues().size());
        assertEquals(-0.6260, result.getSensitivityValue("NLOAD", "NHV1_NHV2_1", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityVariableType.INJECTION_REACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.5701, result.getSensitivityValue("NLOAD", "NHV1_NHV2_1", SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, SensitivityVariableType.INJECTION_REACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.3245, result.getSensitivityValue("NLOAD", "NHV1_NHV2_1", SensitivityFunctionType.BRANCH_CURRENT_1, SensitivityVariableType.INJECTION_REACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.4610, result.getSensitivityValue("NLOAD", "NHV1_NHV2_1", SensitivityFunctionType.BRANCH_CURRENT_2, SensitivityVariableType.INJECTION_REACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        runAcLf(network);

        // check reference flows are consistents with LF ones
        var line = network.getLine("NHV1_NHV2_1");
        assertReactivePowerEquals(result.getFunctionReferenceValue("NHV1_NHV2_1", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1),
                line.getTerminal1());
        assertReactivePowerEquals(result.getFunctionReferenceValue("NHV1_NHV2_1", SensitivityFunctionType.BRANCH_REACTIVE_POWER_2),
                line.getTerminal2());
        assertCurrentEquals(result.getFunctionReferenceValue("NHV1_NHV2_1", SensitivityFunctionType.BRANCH_CURRENT_1),
                line.getTerminal1());
        assertCurrentEquals(result.getFunctionReferenceValue("NHV1_NHV2_1", SensitivityFunctionType.BRANCH_CURRENT_2),
                line.getTerminal2());

        // check sensi values looks consistent with 2 LF diff
        double q1Before = line.getTerminal1().getQ();
        double q2Before = line.getTerminal2().getQ();
        double i1Before = line.getTerminal1().getI();
        double i2Before = line.getTerminal2().getI();

        Load load = network.getLoad("LOAD");
        load.setQ0(load.getQ0() - 0.1);
        runAcLf(network);

        assertEquals(-0.6260, (line.getTerminal1().getQ() - q1Before) / 0.1, LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.5701, (line.getTerminal2().getQ() - q2Before) / 0.1, LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.3245, (line.getTerminal1().getI() - i1Before) / 0.1, LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.4610, (line.getTerminal2().getI() - i2Before) / 0.1, LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
    }

    @Test
    void testUnsupportedVariablesSensiV() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "NLOAD");

        List<SensitivityFactor> factors = List.of(new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_1,
                                                                        "NHV2_NLOAD",
                                                                        SensitivityVariableType.INJECTION_ACTIVE_POWER,
                                                                        "GEN",
                                                                        false,
                                                                        ContingencyContext.all()));

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
        assertEquals("Variable type INJECTION_ACTIVE_POWER not supported with function type BRANCH_REACTIVE_POWER_1", e.getCause().getMessage());

        List<SensitivityFactor> factors2 = List.of(new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE,
                                                  "NLOAD",
                                                  SensitivityVariableType.INJECTION_ACTIVE_POWER,
                                                  "GEN",
                                                  false,
                                                  ContingencyContext.all()));

        e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors2, runParameters));
        assertEquals("Variable type INJECTION_ACTIVE_POWER not supported with function type BUS_VOLTAGE", e.getCause().getMessage());
    }

    @Test
    void testWithTieLinesSpecifiedByBoundaryLines() {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Network network = BoundaryFactory.createWithTieLine();
        //BRANCH_ACTIVE_POWER
        List<SensitivityFactor> factors = network.getBoundaryLineStream().map(line -> createBranchFlowPerInjectionIncrease(line.getId(), "g1")).collect(Collectors.toList());
        factors.add(createBranchFlowPerInjectionIncrease("t12", "g1", TwoSides.ONE)); // Adding tie line BRANCH_ACTIVE_POWER_1
        factors.add(createBranchFlowPerInjectionIncrease("t12", "g1", null, TwoSides.TWO)); // Adding tie line BRANCH_ACTIVE_POWER_2
        //BRANCH_CURRENT
        factors.addAll(network.getBoundaryLineStream().map(line -> createBranchIntensityPerInjectionIncrease(line.getId(), "g1")).toList());
        factors.add(createBranchIntensityPerInjectionIncrease("t12", "g1", TwoSides.ONE)); // Adding tie line BRANCH_CURRENT_1
        factors.add(createBranchIntensityPerInjectionIncrease("t12", "g1", TwoSides.TWO)); // Adding tie line BRANCH_CURRENT_2
        //BRANCH_REACTIVE_POWER
        factors.addAll(network.getBoundaryLineStream().map(line -> createBranchReactivePowerPerTargetV(line.getId(), "g1")).toList());
        factors.add(createBranchReactivePowerPerTargetV("t12", "g1", TwoSides.ONE)); // Adding tie line BRANCH_CURRENT_1
        factors.add(createBranchReactivePowerPerTargetV("t12", "g1", TwoSides.TWO)); // Adding tie line BRANCH_CURRENT_2

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, runParameters);
        assertEquals(12, result.getValues().size());

        // Boundary line h1 side 1 and Tie line t12 side 1 should represent the same sensitivity values
        assertEquals(35.0, result.getBranchFlow1FunctionReferenceValue("h1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(35.0, result.getBranchFlow1FunctionReferenceValue("t12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5, result.getBranchFlow1SensitivityValue("g1", "h1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5, result.getBranchFlow1SensitivityValue("g1", "t12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.518, result.getBranchCurrent1FunctionReferenceValue("h1"), LoadFlowAssert.DELTA_I);
        assertEquals(50.518, result.getBranchCurrent1FunctionReferenceValue("t12"), LoadFlowAssert.DELTA_I);
        assertEquals(0.5, result.getBranchCurrent1SensitivityValue("g1", "h1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(0.5, result.getBranchCurrent1SensitivityValue("g1", "t12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(0.0038, result.getFunctionReferenceValue("h1", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0038, result.getFunctionReferenceValue("t12", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(338.983, result.getSensitivityValue("g1", "h1", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_POWER);
        assertEquals(338.983, result.getSensitivityValue("g1", "t12", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_POWER);

        // Boundary line h2 side 1 and Tie line t12 side 2 should represent the same sensitivity values
        assertEquals(-35.0, result.getBranchFlow1FunctionReferenceValue("h2"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-35.0, result.getBranchFlow2FunctionReferenceValue("t12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.5, result.getBranchFlow1SensitivityValue("g1", "h2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.5, result.getBranchFlow2SensitivityValue("g1", "t12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(50.518, result.getBranchCurrent1FunctionReferenceValue("h2"), LoadFlowAssert.DELTA_I);
        assertEquals(50.518, result.getBranchCurrent2FunctionReferenceValue("t12"), LoadFlowAssert.DELTA_I);
        assertEquals(0.5, result.getBranchCurrent1SensitivityValue("g1", "h2", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(0.5, result.getBranchCurrent2SensitivityValue("g1", "t12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_I);
        assertEquals(-0.0024, result.getFunctionReferenceValue("h2", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.0024, result.getFunctionReferenceValue("t12", SensitivityFunctionType.BRANCH_REACTIVE_POWER_2), LoadFlowAssert.DELTA_POWER);
        assertEquals(-338.983, result.getSensitivityValue("g1", "h2", SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_POWER);
        assertEquals(-338.983, result.getSensitivityValue("g1", "t12", SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, SensitivityVariableType.BUS_TARGET_VOLTAGE), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFailingLf() {
        Network network = TwoBusNetworkFactory.create();
        network.getLoad("l1").setP0(3.0);
        List<SensitivityFactor> factors = List.of(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                "l12",
                SensitivityVariableType.INJECTION_ACTIVE_POWER,
                "g1",
                false,
                ContingencyContext.all()));
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0");
        sensiParameters.getLoadFlowParameters().setDistributedSlack(true);

        OpenLoadFlowParameters olfParameters = sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class);
        olfParameters.setMaxNewtonRaphsonIterations(1);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
        assertEquals("Initial load flow of base situation ended with solver status MAX_ITERATION_REACHED", e.getCause().getMessage());

        olfParameters.setMaxNewtonRaphsonIterations(10)
                .setSlackBusPMaxMismatch(0.00001)
                .setMaxOuterLoopIterations(1);
        e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParameters));
        assertEquals("Initial load flow of base situation ended with outer loop status UNSTABLE", e.getCause().getMessage());
    }

    @Test
    void testUnsupportedSensitivityOperatorStrategy() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters()
                .setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.CONTINGENCIES_AND_OPERATOR_STRATEGIES);

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));
        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")), network.getBranchStream().toList());
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("open l14", ContingencyContext.all(), new TrueCondition(), List.of("open l14")));
        List<Action> actions = List.of(new TerminalsConnectionAction("open l14", "l14", true));

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setContingencies(contingencies)
                .setParameters(sensiParameters)
                .setOperatorStrategies(operatorStrategies)
                .setActions(actions)));
        assertEquals("AC sensitivity analysis does not support operator strategies", e.getCause().getMessage());
    }

    @Test
    void testComputationInterrupted() {
        Network network = BoundaryFactory.createWithLoad();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "bl1"),
            createBranchFlowPerInjectionIncrease("bl1", "load3"));

        List<Contingency> contingencies = List.of(new Contingency("c", new BoundaryLineContingency("bl1")));
        AcSensitivityAnalysis analysis = new AcSensitivityAnalysis(new SparseMatrixFactory(),
            new EvenShiloachGraphDecrementalConnectivityFactory<>(),
            sensiParameters);
        SensitivityFactorReader factorReader = new SensitivityFactorModelReader(factors, network);
        SensitivityResultModelWriter resultWriter = new SensitivityResultModelWriter(contingencies, Collections.emptyList());

        LoadFlowParameters loadFlowParameters = sensiParameters.getLoadFlowParameters();
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
            .setContingencyPropagation(false)
            .setShuntCompensatorVoltageControlOn(!loadFlowParameters.isDc() && loadFlowParameters.isShuntCompensatorVoltageControlOn())
            .setSlackDistributionOnConformLoad(loadFlowParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
            .setHvdcAcEmulation(!loadFlowParameters.isDc() && loadFlowParameters.isHvdcAcEmulation());

        Thread.currentThread().interrupt();
        String variantId = network.getVariantManager().getWorkingVariantId();
        Executor executor = LocalComputationManager.getDefault().getExecutor();
        List<SensitivityVariableSet> noVar = Collections.emptyList();
        OpenSensitivityAnalysisParameters openSensitivityAnalysisParameters = OpenSensitivityAnalysisParameters.getOrDefault(sensiParameters);
        List<OperatorStrategy> operatorStrategies = Collections.emptyList();
        List<Action> actions = Collections.emptyList();
        assertThrows(PowsyblException.class, () -> analysis.analyse(network, variantId,
                contingencies, operatorStrategies, actions, creationParameters, noVar, factorReader, resultWriter, ReportNode.NO_OP,
                openSensitivityAnalysisParameters, executor));
    }

    @Test
    void testRunSyncIsCallable() {
        OpenSensitivityAnalysisProvider p = new OpenSensitivityAnalysisProvider(new SparseMatrixFactory());
        // Make sur that runSync is a Callable. Only CompletableFutureTask.runAsync(Callable) handles correctly thread cancel. Not CompletableFutureTask.runAsync(Runnable)
        Callable t = () -> p.runSync(null, null, null, null, null, null, null, null, null, null, null);
        assertFalse(t instanceof Runnable);
    }

    @Test
    void testShuntBSensi() {
        Network network = ShuntNetworkFactory.create();
        String shuntId = "SHUNT";
        ShuntCompensator shunt = network.getShuntCompensator(shuntId);
        Bus bus1 = network.getBusBreakerView().getBus("b1");
        Bus bus2 = network.getBusBreakerView().getBus("b2");
        Bus bus3 = network.getBusBreakerView().getBus("b3");

        List<Bus> monitoredBuses = List.of(bus2, bus3);

        shunt.setSectionCount(0);
        runAcLf(network);
        Map<Bus, Double> vBefore = monitoredBuses.stream().collect(Collectors.toMap(b -> b, Bus::getV));

        shunt.setSectionCount(1);
        runAcLf(network);
        Map<Bus, Double> vAfter = monitoredBuses.stream().collect(Collectors.toMap(b -> b, Bus::getV));

        shunt.setVoltageRegulatorOn(true);
        shunt.setSectionCount(0);
        runAcLf(network);

        // Test with a bus view id vl3_0 and and busbar section ids
        List<SensitivityFactor> factors = List.of(
                createBusVoltagePerShuntB("vl3_0", shuntId),
                createBusVoltagePerShuntB(bus2.getId(), shuntId),
                createBusVoltagePerShuntB(bus1.getId(), shuntId));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(createParameters(false, bus2.getId(), false)));

        // Sensi in kV/S — multiply sensitivity by delta B to get delta V
        double deltaB = shunt.getB(1) - shunt.getB(0);
        SensitivityVariableType varType = SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE;
        double sVl3 = result.getBusVoltageSensitivityValue(shuntId, "vl3_0", varType);
        double sB2 = result.getBusVoltageSensitivityValue(shuntId, bus2.getId(), varType);
        double sB1 = result.getBusVoltageSensitivityValue(shuntId, bus1.getId(), varType);

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testShuntBSensi (shunt ").append(shuntId).append(", deltaB=").append(deltaB).append(") ===").append(System.lineSeparator());
        out.append(String.format("  %-7s sensi=%+14.4f  pred(sensi*deltaB)=%+10.5f  actual dV=%+10.5f%n",
                "vl3_0", sVl3, sVl3 * deltaB, vAfter.get(bus3) - vBefore.get(bus3)));
        out.append(String.format("  %-7s sensi=%+14.4f  pred(sensi*deltaB)=%+10.5f  actual dV=%+10.5f%n",
                bus2.getId(), sB2, sB2 * deltaB, vAfter.get(bus2) - vBefore.get(bus2)));
        out.append(String.format("  %-7s sensi=%+14.4f  pred(sensi*deltaB)=%+10.5f  (reference bus, expected 0)%n",
                bus1.getId(), sB1, sB1 * deltaB));
        LOGGER.info("{}", out);

        assertEquals(vAfter.get(bus3) - vBefore.get(bus3), sVl3 * deltaB, 1e-1);
        assertEquals(vAfter.get(bus2) - vBefore.get(bus2), sB2 * deltaB, 1e-1);
        assertEquals(0.0, sB1 * deltaB, 1e-3);
        assertEquals(2334.586, sVl3, DELTA_V);
        assertEquals(1168.842, sB2, DELTA_V);
        assertEquals(0.0, sB1, DELTA_V);

        SensitivityAnalysisRunParameters runParametersDc = new SensitivityAnalysisRunParameters()
                .setParameters(createParameters(true, bus2.getId(), false));
        CompletionException ex = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, runParametersDc));
        assertEquals("com.powsybl.commons.PowsyblException: Only variables of type TRANSFORMER_PHASE, TRANSFORMER_PHASE_1, TRANSFORMER_PHASE_2, TRANSFORMER_PHASE_3, INJECTION_ACTIVE_POWER and HVDC_LINE_ACTIVE_POWER, and functions of type BRANCH_ACTIVE_POWER_1, BRANCH_ACTIVE_POWER_2 and BRANCH_ACTIVE_POWER_3 are yet supported in DC", ex.getMessage());
    }

    @Test
    void testShuntBSensiBis() {
        Network network = ShuntNetworkFactory.create();
        String shuntId = "SHUNT";
        ShuntCompensator shunt = network.getShuntCompensator(shuntId);
        List<Branch> monitoredBranches = List.of("l1", "l2").stream()
                .map(network::getBranch)
                .toList();
        List<Bus> monitoredBuses = List.of("b1", "b2", "b3").stream()
                .map(id -> network.getBusBreakerView().getBus(id))
                .toList();

        // Snapshot before: shunt at section 0
        shunt.setSectionCount(0);
        runAcLf(network);
        Map<Bus, Double> qBefore = monitoredBuses.stream().collect(Collectors.toMap(b -> b, Bus::getQ));
        Map<Branch, double[]> branchBefore = monitoredBranches.stream().collect(Collectors.toMap(b -> b, b -> new double[]{
                b.getTerminal1().getP(), b.getTerminal2().getP(),
                b.getTerminal1().getI(), b.getTerminal2().getI()}));

        // Snapshot after: shunt at section 1
        shunt.setSectionCount(1);
        runAcLf(network);
        Map<Bus, Double> qAfter = monitoredBuses.stream().collect(Collectors.toMap(b -> b, Bus::getQ));
        Map<Branch, double[]> branchAfter = monitoredBranches.stream().collect(Collectors.toMap(b -> b, b -> new double[]{
                b.getTerminal1().getP(), b.getTerminal2().getP(),
                b.getTerminal1().getI(), b.getTerminal2().getI()}));

        // Reset for sensitivity analysis
        shunt.setVoltageRegulatorOn(true);
        shunt.setSectionCount(0);
        runAcLf(network);

        // Build sensitivity factors
        List<SensitivityFactor> factors = new ArrayList<>();
        for (Bus bus : monitoredBuses) {
            factors.add(createBusReactivePowerPerShuntB(bus.getId(), shuntId));
        }
        for (Branch branch : monitoredBranches) {
            factors.add(createBranchFlowPerShuntB(branch.getId(), shuntId, TwoSides.ONE));
            factors.add(createBranchFlowPerShuntB(branch.getId(), shuntId, TwoSides.TWO));
            factors.add(createBranchIntensityPerShuntB(branch.getId(), shuntId, TwoSides.ONE));
            factors.add(createBranchIntensityPerShuntB(branch.getId(), shuntId, TwoSides.TWO));
        }

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(createParameters(false, "b1", false)));

        double deltaB = shunt.getB(1) - shunt.getB(0);
        SensitivityVariableType varType = SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE;

        // Assert exact sensitivity values to catch drift
        assertEquals(151898.236, result.getSensitivityValue(shuntId, "b1", SensitivityFunctionType.BUS_REACTIVE_POWER, varType), DELTA_POWER);
        assertEquals(-150995.536, result.getSensitivityValue(shuntId, "b3", SensitivityFunctionType.BUS_REACTIVE_POWER, varType), DELTA_POWER);
        assertEquals(-300.900, result.getBranchFlow2SensitivityValue(shuntId, "l1", varType), DELTA_POWER);
        assertEquals(-186566.452, result.getBranchCurrent1SensitivityValue(shuntId, "l1", varType), DELTA_I);
        assertEquals(-186566.452, result.getBranchCurrent2SensitivityValue(shuntId, "l1", varType), DELTA_I);
        assertEquals(0.0, result.getBranchFlow1SensitivityValue(shuntId, "l2", varType), DELTA_POWER);
        assertEquals(0.0, result.getBranchFlow2SensitivityValue(shuntId, "l2", varType), DELTA_POWER);

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testShuntBSensiBis (shunt ").append(shuntId).append(", deltaB=").append(deltaB).append(") ===").append(System.lineSeparator());

        // Assert bus reactive power sensitivities
        for (Bus bus : monitoredBuses) {
            double diff = qAfter.get(bus) - qBefore.get(bus);
            double scaled = result.getSensitivityValue(shuntId, bus.getId(), SensitivityFunctionType.BUS_REACTIVE_POWER, varType) * deltaB;
            out.append(String.format("  bus %-3s Q  : pred(sensi*deltaB)=%+14.4f  actual dQ=%+14.4f%n", bus.getId(), scaled, diff));
            assertEquals(diff, scaled, 2.0);
        }

        // Assert branch P and I sensitivities
        // l2 is directly connected to the shunt bus — P and I sensitivities have known issues:
        //   p1_l2, p2_l2: scaled is 0 while diff is near-zero (shunt on same bus)
        //   i1_l2: sign mismatch due to current non-linearity
        for (Branch branch : monitoredBranches) {
            double[] before = branchBefore.get(branch);
            double[] after = branchAfter.get(branch);
            String branchId = branch.getId();

            double diffP1 = after[0] - before[0];
            double scaledP1 = result.getBranchFlow1SensitivityValue(shuntId, branchId, varType) * deltaB;
            out.append(String.format("  branch %-3s P1: pred(sensi*deltaB)=%+14.4f  actual dP1=%+14.4f%n", branchId, scaledP1, diffP1));
            assertEquals(diffP1, scaledP1, 1.0);

            double diffP2 = after[1] - before[1];
            double scaledP2 = result.getBranchFlow2SensitivityValue(shuntId, branchId, varType) * deltaB;
            out.append(String.format("  branch %-3s P2: pred(sensi*deltaB)=%+14.4f  actual dP2=%+14.4f%n", branchId, scaledP2, diffP2));
            assertEquals(diffP2, scaledP2, 1.0);

            double diffI1 = after[2] - before[2];
            double scaledI1 = result.getBranchCurrent1SensitivityValue(shuntId, branchId, varType) * deltaB;
            out.append(String.format("  branch %-3s I1: pred(sensi*deltaB)=%+14.4f  actual dI1=%+14.4f  sameSign=%b%n",
                    branchId, scaledI1, diffI1, Math.signum(diffI1) == Math.signum(scaledI1)));
            assertTrue(Math.signum(diffI1) == Math.signum(scaledI1), "I1 sign mismatch on " + branchId);

            double diffI2 = after[3] - before[3];
            double scaledI2 = result.getBranchCurrent2SensitivityValue(shuntId, branchId, varType) * deltaB;
            out.append(String.format("  branch %-3s I2: pred(sensi*deltaB)=%+14.4f  actual dI2=%+14.4f  sameSign=%b%n",
                    branchId, scaledI2, diffI2, Math.signum(diffI2) == Math.signum(scaledI2)));
            assertTrue(Math.signum(diffI2) == Math.signum(scaledI2), "I2 sign mismatch on " + branchId);
        }
        LOGGER.info("{}", out);
    }

    private Network createShuntTransformerNetwork(double bPerSection) {
        Network network = Network.create("shunt-trafo-test", "test");
        Substation s1 = network.newSubstation().setId("S1").add();
        Substation s2 = network.newSubstation().setId("S2").add();

        VoltageLevel vl1 = s1.newVoltageLevel().setId("vl1").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl1.getBusBreakerView().newBus().setId("b1").add();
        vl1.newGenerator().setId("g1").setBus("b1").setConnectableBus("b1")
                .setTargetP(10).setTargetV(400).setMinP(0).setMaxP(500).setVoltageRegulatorOn(true).add();

        VoltageLevel vl2 = s1.newVoltageLevel().setId("vl2").setNominalV(20).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl2.getBusBreakerView().newBus().setId("b2").add();
        vl2.newLoad().setId("ld1").setBus("b2").setConnectableBus("b2").setP0(10).setQ0(3).add();

        VoltageLevel vl3 = s2.newVoltageLevel().setId("vl3").setNominalV(20).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl3.getBusBreakerView().newBus().setId("b3").add();
        vl3.newShuntCompensator().setId("SHUNT").setBus("b3").setConnectableBus("b3")
                .setSectionCount(0).setVoltageRegulatorOn(false)
                .newLinearModel().setBPerSection(bPerSection).setMaximumSectionCount(1).add()
                .add();

        s1.newTwoWindingsTransformer().setId("tr1")
                .setVoltageLevel1("vl1").setBus1("b1").setConnectableBus1("b1")
                .setVoltageLevel2("vl2").setBus2("b2").setConnectableBus2("b2")
                .setRatedU1(400).setRatedU2(20).setR(0.5).setX(10).add();

        network.newLine().setId("l1").setBus1("b2").setBus2("b3").setR(0.1).setX(1).add();
        return network;
    }

    @Test
    void testShuntBSensiWithTransformer() {
        // Small synthetic network: g1 (400kV) --transformer--> b2 (20kV) --line--> b3 (20kV, shunt)
        // Sweep across increasing bPerSection values to see when linearization diverges
        String shuntId = "SHUNT";
        List<String> branchIds = List.of("tr1", "l1");
        SensitivityVariableType varType = SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE;

        // Run sensitivity on the base network (bPerSection doesn't matter for sensitivity computation)
        Network baseNetwork = createShuntTransformerNetwork(1e-4);
        baseNetwork.getShuntCompensator(shuntId).setSectionCount(0);
        runAcLf(baseNetwork);

        List<SensitivityFactor> factors = new ArrayList<>();
        for (String branchId : branchIds) {
            factors.add(createBranchIntensityPerShuntB(branchId, shuntId, TwoSides.ONE));
            factors.add(createBranchIntensityPerShuntB(branchId, shuntId, TwoSides.TWO));
        }
        SensitivityAnalysisResult result = sensiRunner.run(baseNetwork, factors, new SensitivityAnalysisRunParameters()
                .setParameters(createParameters(false, "b1", false)));

        // Assert exact sensitivity values to catch any drift
        assertEquals(-364.498, result.getBranchCurrent1SensitivityValue(shuntId, "tr1", varType), DELTA_I);
        assertEquals(-7289.966, result.getBranchCurrent2SensitivityValue(shuntId, "tr1", varType), DELTA_I);
        assertEquals(9877.586, result.getBranchCurrent1SensitivityValue(shuntId, "l1", varType), DELTA_I);
        assertEquals(9877.586, result.getBranchCurrent2SensitivityValue(shuntId, "l1", varType), DELTA_I);

        // Sweep bPerSection values — accumulate rows to avoid interleaving with OLF console output
        double[] bSteps = {1e-5, 1e-4, 1e-3, 5e-3, 1e-2};
        List<String> sweepRows = new ArrayList<>();
        sweepRows.add("bPerSection | branch | I1 diff      | I1 scaled    | I1 sign ok | I2 diff      | I2 scaled    | I2 sign ok");
        for (double bStep : bSteps) {
            Network net = createShuntTransformerNetwork(bStep);
            ShuntCompensator shunt = net.getShuntCompensator(shuntId);

            shunt.setSectionCount(0);
            runAcLf(net);
            double[] i1Before = {net.getBranch("tr1").getTerminal1().getI(), net.getBranch("l1").getTerminal1().getI()};
            double[] i2Before = {net.getBranch("tr1").getTerminal2().getI(), net.getBranch("l1").getTerminal2().getI()};

            shunt.setSectionCount(1);
            runAcLf(net);
            double[] i1After = {net.getBranch("tr1").getTerminal1().getI(), net.getBranch("l1").getTerminal1().getI()};
            double[] i2After = {net.getBranch("tr1").getTerminal2().getI(), net.getBranch("l1").getTerminal2().getI()};

            for (int i = 0; i < branchIds.size(); i++) {
                String branchId = branchIds.get(i);
                double diffI1 = i1After[i] - i1Before[i];
                double scaledI1 = result.getBranchCurrent1SensitivityValue(shuntId, branchId, varType) * bStep;
                boolean signOkI1 = diffI1 == 0 || Math.signum(diffI1) == Math.signum(scaledI1);
                double diffI2 = i2After[i] - i2Before[i];
                double scaledI2 = result.getBranchCurrent2SensitivityValue(shuntId, branchId, varType) * bStep;
                boolean signOkI2 = diffI2 == 0 || Math.signum(diffI2) == Math.signum(scaledI2);
                sweepRows.add(String.format("%-11.0e | %-6s | %+12.6f | %+12.6f | %-10s | %+12.6f | %+12.6f | %s",
                        bStep, branchId, diffI1, scaledI1, signOkI1, diffI2, scaledI2, signOkI2));
            }
        }

        // Assert that small steps (1e-4) have correct signs and close values
        Network smallNet = createShuntTransformerNetwork(1e-4);
        ShuntCompensator shuntSmall = smallNet.getShuntCompensator(shuntId);
        shuntSmall.setSectionCount(0);
        runAcLf(smallNet);
        Map<String, double[]> smallBefore = branchIds.stream().collect(Collectors.toMap(id -> id, id -> new double[]{
                smallNet.getBranch(id).getTerminal1().getI(), smallNet.getBranch(id).getTerminal2().getI()}));
        shuntSmall.setSectionCount(1);
        runAcLf(smallNet);
        double smallDeltaB = 1e-4;
        for (String branchId : branchIds) {
            Branch branch = smallNet.getBranch(branchId);
            double diffI1 = branch.getTerminal1().getI() - smallBefore.get(branchId)[0];
            double scaledI1 = result.getBranchCurrent1SensitivityValue(shuntId, branchId, varType) * smallDeltaB;
            double diffI2 = branch.getTerminal2().getI() - smallBefore.get(branchId)[1];
            double scaledI2 = result.getBranchCurrent2SensitivityValue(shuntId, branchId, varType) * smallDeltaB;
            assertTrue(Math.signum(diffI1) == Math.signum(scaledI1), "I1 sign mismatch on " + branchId);
            assertTrue(Math.signum(diffI2) == Math.signum(scaledI2), "I2 sign mismatch on " + branchId);
        }

        LOGGER.info("=== testShuntBSensiWithTransformer : I1/I2 diff vs scaled sensitivity over bPerSection sweep ==={}{}",
                System.lineSeparator(), String.join(System.lineSeparator(), sweepRows));
    }

    @Test
    void testShuntBSensi2() {
        Network network = IeeeCdfNetworkFactory.create14();
        String shuntId = "B9-SH";
        ShuntCompensator shunt = network.getShuntCompensator(shuntId);

        List<Bus> monitoredBuses = List.of("B9", "B10", "B7", "B4", "B14").stream()
                .map(id -> network.getBusBreakerView().getBus(id))
                .toList();
        List<Branch> monitoredBranches = List.of("L9-10-1", "L9-14-1", "L7-9-1", "T4-9-1", "T4-7-1", "T5-6-1").stream()
                .map(network::getBranch)
                .toList();

        // Snapshot before: shunt disconnected (section count 0)
        shunt.setSectionCount(0);
        runAcLf(network);
        Map<Bus, Double> vBefore = monitoredBuses.stream().collect(Collectors.toMap(b -> b, Bus::getV));
        Map<Bus, Double> qBefore = monitoredBuses.stream().collect(Collectors.toMap(b -> b, Bus::getQ));
        Map<Branch, double[]> branchBefore = monitoredBranches.stream().collect(Collectors.toMap(b -> b, b -> new double[]{
                b.getTerminal1().getP(), b.getTerminal2().getP(),
                b.getTerminal1().getI(), b.getTerminal2().getI()}));

        // Snapshot after: shunt connected (section count 1)
        shunt.setSectionCount(1);
        runAcLf(network);
        Map<Bus, Double> vAfter = monitoredBuses.stream().collect(Collectors.toMap(b -> b, Bus::getV));
        Map<Bus, Double> qAfter = monitoredBuses.stream().collect(Collectors.toMap(b -> b, Bus::getQ));
        Map<Branch, double[]> branchAfter = monitoredBranches.stream().collect(Collectors.toMap(b -> b, b -> new double[]{
                b.getTerminal1().getP(), b.getTerminal2().getP(),
                b.getTerminal1().getI(), b.getTerminal2().getI()}));

        // Reset for sensitivity analysis
        shunt.setSectionCount(0);
        runAcLf(network);

        // Build sensitivity factors
        List<SensitivityFactor> factors = new ArrayList<>();
        for (Bus bus : monitoredBuses) {
            factors.add(createBusVoltagePerShuntB(bus.getId(), shuntId));
            factors.add(createBusReactivePowerPerShuntB(bus.getId(), shuntId));
        }
        for (Branch branch : monitoredBranches) {
            factors.add(createBranchFlowPerShuntB(branch.getId(), shuntId, TwoSides.ONE));
            factors.add(createBranchFlowPerShuntB(branch.getId(), shuntId, TwoSides.TWO));
            factors.add(createBranchIntensityPerShuntB(branch.getId(), shuntId, TwoSides.ONE));
            factors.add(createBranchIntensityPerShuntB(branch.getId(), shuntId, TwoSides.TWO));
        }

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(createParameters(false, "B1", false)));

        double deltaB = shunt.getB(1) - shunt.getB(0);
        SensitivityVariableType varType = SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE;

        // Assert exact sensitivity values to catch drift
        assertEquals(2.5347, result.getBusVoltageSensitivityValue(shuntId, "B9", varType), DELTA_V);
        assertEquals(2.1022, result.getBusVoltageSensitivityValue(shuntId, "B10", varType), DELTA_V);
        assertEquals(2.1065, result.getBusVoltageSensitivityValue(shuntId, "B7", varType), DELTA_V);
        assertEquals(5.4944, result.getBusVoltageSensitivityValue(shuntId, "B4", varType), DELTA_V);
        assertEquals(1.6228, result.getBusVoltageSensitivityValue(shuntId, "B14", varType), DELTA_V);
        assertEquals(-153.728, result.getSensitivityValue(shuntId, "B9", SensitivityFunctionType.BUS_REACTIVE_POWER, varType), DELTA_POWER);
        assertEquals(6.8015, result.getBranchFlow1SensitivityValue(shuntId, "L9-10-1", varType), DELTA_POWER);
        assertEquals(-6.7951, result.getBranchFlow2SensitivityValue(shuntId, "L9-10-1", varType), DELTA_POWER);
        assertEquals(6.4563, result.getBranchFlow1SensitivityValue(shuntId, "L9-14-1", varType), DELTA_POWER);
        assertEquals(8.4005, result.getBranchFlow1SensitivityValue(shuntId, "L7-9-1", varType), DELTA_POWER);
        assertEquals(4.8572, result.getBranchFlow1SensitivityValue(shuntId, "T4-9-1", varType), DELTA_POWER);
        assertEquals(111.079, result.getBranchCurrent1SensitivityValue(shuntId, "L9-10-1", varType), DELTA_I);
        assertEquals(111.079, result.getBranchCurrent2SensitivityValue(shuntId, "L9-10-1", varType), DELTA_I);
        assertEquals(310.880, result.getBranchCurrent1SensitivityValue(shuntId, "L9-14-1", varType), DELTA_I);
        assertEquals(-1003.505, result.getBranchCurrent1SensitivityValue(shuntId, "L7-9-1", varType), DELTA_I);
        assertEquals(-1170.756, result.getBranchCurrent2SensitivityValue(shuntId, "L7-9-1", varType), DELTA_I);
        assertEquals(-8.800, result.getBranchCurrent1SensitivityValue(shuntId, "T4-9-1", varType), DELTA_I);
        assertEquals(-95.934, result.getBranchCurrent2SensitivityValue(shuntId, "T4-9-1", varType), DELTA_I);
        assertEquals(75.942, result.getBranchCurrent1SensitivityValue(shuntId, "T4-7-1", varType), DELTA_I);
        assertEquals(716.188, result.getBranchCurrent2SensitivityValue(shuntId, "T4-7-1", varType), DELTA_I);
        assertEquals(-56.359, result.getBranchCurrent1SensitivityValue(shuntId, "T5-6-1", varType), DELTA_I);
        assertEquals(-590.923, result.getBranchCurrent2SensitivityValue(shuntId, "T5-6-1", varType), DELTA_I);

        // Sensitivity is a linear approximation; with a large delta B step (0.132 S), expect significant linearization error
        double voltageTol = 5e-1;
        double powerTol = 1.0;

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testShuntBSensi2 (shunt ").append(shuntId).append(", deltaB=").append(deltaB).append(") ===").append(System.lineSeparator());

        // Assert bus voltage sensitivities — same sign and close values
        for (Bus bus : monitoredBuses) {
            double diff = vAfter.get(bus) - vBefore.get(bus);
            double scaled = result.getBusVoltageSensitivityValue(shuntId, bus.getId(), varType) * deltaB;
            out.append(String.format("  bus %-4s V : pred(sensi*deltaB)=%+10.5f  actual dV=%+10.5f  sameSign=%b%n",
                    bus.getId(), scaled, diff, Math.signum(diff) == Math.signum(scaled)));
            assertEquals(diff, scaled, voltageTol);
            assertTrue(Math.signum(diff) == Math.signum(scaled), "V sign mismatch on " + bus.getId());
        }

        // Assert bus reactive power sensitivities — same sign and close values
        for (Bus bus : monitoredBuses) {
            double diff = qAfter.get(bus) - qBefore.get(bus);
            double scaled = result.getSensitivityValue(shuntId, bus.getId(), SensitivityFunctionType.BUS_REACTIVE_POWER, varType) * deltaB;
            out.append(String.format("  bus %-4s Q : pred(sensi*deltaB)=%+10.4f  actual dQ=%+10.4f%n", bus.getId(), scaled, diff));
            assertEquals(diff, scaled, powerTol);
        }

        // Assert branch active power sensitivities — same sign and close values
        // Current sensitivities have known sign mismatches on T4-9-1 due to non-linearity
        Set<String> currentSignExceptions = Set.of("T4-9-1");
        for (Branch branch : monitoredBranches) {
            double[] before = branchBefore.get(branch);
            double[] after = branchAfter.get(branch);
            String branchId = branch.getId();
            // P1
            double diffP1 = after[0] - before[0];
            double scaledP1 = result.getBranchFlow1SensitivityValue(shuntId, branchId, varType) * deltaB;
            out.append(String.format("  branch %-7s P1: pred(sensi*deltaB)=%+10.4f  actual dP1=%+10.4f  sameSign=%b%n",
                    branchId, scaledP1, diffP1, Math.signum(diffP1) == Math.signum(scaledP1)));
            assertEquals(diffP1, scaledP1, powerTol);
            assertTrue(Math.signum(diffP1) == Math.signum(scaledP1), "P1 sign mismatch on " + branchId);
            // P2
            double diffP2 = after[1] - before[1];
            double scaledP2 = result.getBranchFlow2SensitivityValue(shuntId, branchId, varType) * deltaB;
            out.append(String.format("  branch %-7s P2: pred(sensi*deltaB)=%+10.4f  actual dP2=%+10.4f  sameSign=%b%n",
                    branchId, scaledP2, diffP2, Math.signum(diffP2) == Math.signum(scaledP2)));
            assertEquals(diffP2, scaledP2, powerTol);
            assertTrue(Math.signum(diffP2) == Math.signum(scaledP2), "P2 sign mismatch on " + branchId);
            // I1
            double diffI1 = after[2] - before[2];
            double scaledI1 = result.getBranchCurrent1SensitivityValue(shuntId, branchId, varType) * deltaB;
            out.append(String.format("  branch %-7s I1: pred(sensi*deltaB)=%+10.4f  actual dI1=%+10.4f  sameSign=%b%s%n",
                    branchId, scaledI1, diffI1, Math.signum(diffI1) == Math.signum(scaledI1),
                    currentSignExceptions.contains(branchId) ? " (expected mismatch)" : ""));
            if (currentSignExceptions.contains(branchId)) {
                // Known sign mismatch due to current non-linearity with large delta B
                assertNotEquals(Math.signum(diffI1), Math.signum(scaledI1), "Expected sign mismatch on I1 " + branchId);
            } else {
                assertTrue(Math.signum(diffI1) == Math.signum(scaledI1), "I1 sign mismatch on " + branchId);
            }
            // I2
            double diffI2 = after[3] - before[3];
            double scaledI2 = result.getBranchCurrent2SensitivityValue(shuntId, branchId, varType) * deltaB;
            out.append(String.format("  branch %-7s I2: pred(sensi*deltaB)=%+10.4f  actual dI2=%+10.4f  sameSign=%b%s%n",
                    branchId, scaledI2, diffI2, Math.signum(diffI2) == Math.signum(scaledI2),
                    currentSignExceptions.contains(branchId) ? " (expected mismatch)" : ""));
            if (currentSignExceptions.contains(branchId)) {
                assertNotEquals(Math.signum(diffI2), Math.signum(scaledI2), "Expected sign mismatch on I2 " + branchId);
            } else {
                assertTrue(Math.signum(diffI2) == Math.signum(scaledI2), "I2 sign mismatch on " + branchId);
            }
        }
    }

    private static final List<String> RXY_FUNCTION_LABELS = List.of("P1", "P2", "Q1", "Q2", "I1", "I2", "V");

    /**
     * Solve an AC load flow on {@code network} and capture the monitored functions in the same order
     * as {@link #RXY_FUNCTION_LABELS}: P1, P2, Q1, Q2, I1, I2 on {@code branchId} and V on {@code busId}.
     */
    private double[] solveAndCaptureBranchFunctions(Network network, String branchId, String busId, LoadFlowParameters lfParameters) {
        runLf(network, lfParameters);
        Branch<?> branch = network.getBranch(branchId);
        Terminal t1 = branch.getTerminal1();
        Terminal t2 = branch.getTerminal2();
        double v = network.getBusBreakerView().getBus(busId).getV();
        return new double[] {t1.getP(), t2.getP(), t1.getQ(), t2.getQ(), t1.getI(), t2.getI(), v};
    }

    /**
     * Build the 7 sensitivity factors (P1, P2, Q1, Q2, I1, I2 and V) of the monitored functions w.r.t. a single
     * branch parameter {@code varType} of {@code variableBranchId}.
     */
    private static List<SensitivityFactor> createBranchParameterFactors(String monitoredBranchId, String monitoredBusId,
                                                                        String variableBranchId, SensitivityVariableType varType) {
        return List.of(
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, monitoredBranchId, varType, variableBranchId, false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, monitoredBranchId, varType, variableBranchId, false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, monitoredBranchId, varType, variableBranchId, false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, monitoredBranchId, varType, variableBranchId, false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, monitoredBranchId, varType, variableBranchId, false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, monitoredBranchId, varType, variableBranchId, false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, monitoredBusId, varType, variableBranchId, false, ContingencyContext.all()));
    }

    /**
     * Read the 7 analytic sensitivities (same order as {@link #RXY_FUNCTION_LABELS}) of the monitored functions
     * w.r.t. parameter {@code varType} of {@code variableBranchId}.
     */
    private static double[] readBranchParameterSensitivities(SensitivityAnalysisResult result, String monitoredBranchId,
                                                             String monitoredBusId, String variableBranchId, SensitivityVariableType varType) {
        return new double[] {
                result.getBranchFlow1SensitivityValue(variableBranchId, monitoredBranchId, varType),
                result.getBranchFlow2SensitivityValue(variableBranchId, monitoredBranchId, varType),
                result.getSensitivityValue(variableBranchId, monitoredBranchId, SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, varType),
                result.getSensitivityValue(variableBranchId, monitoredBranchId, SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, varType),
                result.getBranchCurrent1SensitivityValue(variableBranchId, monitoredBranchId, varType),
                result.getBranchCurrent2SensitivityValue(variableBranchId, monitoredBranchId, varType),
                result.getBusVoltageSensitivityValue(variableBranchId, monitoredBusId, varType)};
    }

    private static double[] finiteDifference(double[] base, double[] perturbed, double step) {
        double[] fd = new double[base.length];
        for (int i = 0; i < base.length; i++) {
            fd[i] = (perturbed[i] - base[i]) / step;
        }
        return fd;
    }

    private static void appendSensiVsFd(StringBuilder out, String header, double[] analytic, double[] fd) {
        out.append(header).append(System.lineSeparator());
        for (int i = 0; i < RXY_FUNCTION_LABELS.size(); i++) {
            out.append(String.format("  %-2s  analytic = %+14.6f   fd = %+14.6f   |diff| = %.3e%n",
                    RXY_FUNCTION_LABELS.get(i), analytic[i], fd[i], Math.abs(analytic[i] - fd[i])));
        }
    }

    /**
     * Assert that every analytic sensitivity matches its finite-difference estimate, within a relative
     * tolerance (with a small absolute floor to tolerate near-zero quantities).
     */
    private static void assertSensiCloseToFd(double[] analytic, double[] fd, double relTol, double absFloor) {
        for (int i = 0; i < RXY_FUNCTION_LABELS.size(); i++) {
            double tol = Math.max(Math.abs(fd[i]) * relTol, absFloor);
            String label = RXY_FUNCTION_LABELS.get(i);
            assertEquals(fd[i], analytic[i], tol, "Mismatch on " + label + " analytic vs finite difference");
        }
    }

    /**
     * Self-sensitivity of a line: monitor every function on the very branch whose R / X / Y is perturbed.
     * This exercises both the direct partial term (variable branch == monitored branch) and the indirect
     * term flowing through the Jacobian, cross-checked against a finite-difference re-solve on a real line.
     */
    @Test
    void testBranchRXYSelfSensitivity() {
        String branchId = "L2-3-1";
        String busId = "B4";
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        // Baseline operating point.
        Network nBase = IeeeCdfNetworkFactory.create14();
        double[] base = solveAndCaptureBranchFunctions(nBase, branchId, busId, lfParameters);
        Line line = nBase.getLine(branchId);
        double rBase = line.getR();
        double xBase = line.getX();
        double yBase = 1.0 / Math.hypot(rBase, xBase);

        double rel = 1e-4;
        double dR = rel * rBase;
        double dX = rel * xBase;
        double dY = rel * yBase;

        // Finite difference on R.
        Network nR = IeeeCdfNetworkFactory.create14();
        nR.getLine(branchId).setR(rBase + dR);
        double[] fdR = finiteDifference(base, solveAndCaptureBranchFunctions(nR, branchId, busId, lfParameters), dR);

        // Finite difference on X.
        Network nX = IeeeCdfNetworkFactory.create14();
        nX.getLine(branchId).setX(xBase + dX);
        double[] fdX = finiteDifference(base, solveAndCaptureBranchFunctions(nX, branchId, busId, lfParameters), dX);

        // Finite difference on Y at constant ksi: scale R and X so y goes from yBase to yBase + dY.
        Network nY = IeeeCdfNetworkFactory.create14();
        double scaleY = yBase / (yBase + dY); // z scaled by this factor -> y scaled by 1/scaleY
        nY.getLine(branchId).setR(rBase * scaleY).setX(xBase * scaleY);
        double[] fdY = finiteDifference(base, solveAndCaptureBranchFunctions(nY, branchId, busId, lfParameters), dY);

        // Analytic sensitivities.
        Network network = IeeeCdfNetworkFactory.create14();
        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(createBranchParameterFactors(branchId, busId, branchId, SensitivityVariableType.BRANCH_RESISTANCE));
        factors.addAll(createBranchParameterFactors(branchId, busId, branchId, SensitivityVariableType.BRANCH_REACTANCE));
        factors.addAll(createBranchParameterFactors(branchId, busId, branchId, SensitivityVariableType.BRANCH_ADMITTANCE));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        double[] sR = readBranchParameterSensitivities(result, branchId, busId, branchId, SensitivityVariableType.BRANCH_RESISTANCE);
        double[] sX = readBranchParameterSensitivities(result, branchId, busId, branchId, SensitivityVariableType.BRANCH_REACTANCE);
        double[] sY = readBranchParameterSensitivities(result, branchId, busId, branchId, SensitivityVariableType.BRANCH_ADMITTANCE);

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testBranchRXYSelfSensitivity (branch ").append(branchId).append(", bus ").append(busId).append(") ===").append(System.lineSeparator());
        out.append("baseline R=").append(rBase).append(" X=").append(xBase).append(" Y=").append(yBase)
                .append(" ; finite-difference steps dR=").append(dR).append(" dX=").append(dX).append(" dY=").append(dY)
                .append(" ; functions [P1,P2,Q1,Q2,I1,I2,V] = ").append(Arrays.toString(base)).append(System.lineSeparator());
        appendSensiVsFd(out, "d{func}/dR :", sR, fdR);
        appendSensiVsFd(out, "d{func}/dX :", sX, fdX);
        appendSensiVsFd(out, "d{func}/dY :", sY, fdY);
        LOGGER.info("{}", out);

        assertSensiCloseToFd(sR, fdR, 2e-2, 1e-3);
        assertSensiCloseToFd(sX, fdX, 2e-2, 1e-3);
        assertSensiCloseToFd(sY, fdY, 2e-2, 1e-1);
    }

    /**
     * Side-3 branch function types ({@code BRANCH_*_3}) on a two-winding branch / line. Such a branch has no third
     * side, so {@code getFunctionEquationTerm} maps side 3 to the side-1 quantities; the R / X / Y direct partial
     * must do the same. This asserts that the side-3 self-sensitivities equal the side-1 ones for active power,
     * reactive power and current, across all three branch parameters.
     */
    @Test
    void testBranchRXYSide3MatchesSide1() {
        String branchId = "L2-3-1";
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        Network network = IeeeCdfNetworkFactory.create14();

        List<SensitivityFunctionType> side1Types = List.of(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, SensitivityFunctionType.BRANCH_CURRENT_1);
        List<SensitivityFunctionType> side3Types = List.of(SensitivityFunctionType.BRANCH_ACTIVE_POWER_3,
                SensitivityFunctionType.BRANCH_REACTIVE_POWER_3, SensitivityFunctionType.BRANCH_CURRENT_3);
        List<SensitivityVariableType> varTypes = List.of(SensitivityVariableType.BRANCH_RESISTANCE,
                SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE);

        List<SensitivityFactor> factors = new ArrayList<>();
        for (SensitivityVariableType varType : varTypes) {
            for (SensitivityFunctionType functionType : side1Types) {
                factors.add(new SensitivityFactor(functionType, branchId, varType, branchId, false, ContingencyContext.all()));
            }
            for (SensitivityFunctionType functionType : side3Types) {
                factors.add(new SensitivityFactor(functionType, branchId, varType, branchId, false, ContingencyContext.all()));
            }
        }
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        for (SensitivityVariableType varType : varTypes) {
            for (int i = 0; i < side1Types.size(); i++) {
                double side1 = result.getSensitivityValue(branchId, branchId, side1Types.get(i), varType);
                double side3 = result.getSensitivityValue(branchId, branchId, side3Types.get(i), varType);
                assertEquals(side1, side3, 1e-9,
                        "Side-3 sensitivity should match side 1 for " + side1Types.get(i) + " w.r.t. " + varType);
            }
        }
    }

    /**
     * Cross-sensitivity between two lines: perturb R / X / Y of one line and monitor the functions on a
     * <em>different</em> line (plus a bus voltage). Here the direct partial term is zero, so this checks the
     * indirect term flowing through the Jacobian, again cross-checked against a finite-difference re-solve.
     */
    @Test
    void testBranchRXYCrossSensitivity() {
        String variableBranchId = "L1-2-1";
        String monitoredBranchId = "L2-3-1";
        String busId = "B4";
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        Network nBase = IeeeCdfNetworkFactory.create14();
        double[] base = solveAndCaptureBranchFunctions(nBase, monitoredBranchId, busId, lfParameters);
        Line line = nBase.getLine(variableBranchId);
        double rBase = line.getR();
        double xBase = line.getX();
        double yBase = 1.0 / Math.hypot(rBase, xBase);

        double rel = 1e-4;
        double dR = rel * rBase;
        double dX = rel * xBase;
        double dY = rel * yBase;

        Network nR = IeeeCdfNetworkFactory.create14();
        nR.getLine(variableBranchId).setR(rBase + dR);
        double[] fdR = finiteDifference(base, solveAndCaptureBranchFunctions(nR, monitoredBranchId, busId, lfParameters), dR);

        Network nX = IeeeCdfNetworkFactory.create14();
        nX.getLine(variableBranchId).setX(xBase + dX);
        double[] fdX = finiteDifference(base, solveAndCaptureBranchFunctions(nX, monitoredBranchId, busId, lfParameters), dX);

        Network nY = IeeeCdfNetworkFactory.create14();
        double scaleY = yBase / (yBase + dY);
        nY.getLine(variableBranchId).setR(rBase * scaleY).setX(xBase * scaleY);
        double[] fdY = finiteDifference(base, solveAndCaptureBranchFunctions(nY, monitoredBranchId, busId, lfParameters), dY);

        Network network = IeeeCdfNetworkFactory.create14();
        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_RESISTANCE));
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_REACTANCE));
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_ADMITTANCE));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        double[] sR = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_RESISTANCE);
        double[] sX = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_REACTANCE);
        double[] sY = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_ADMITTANCE);

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testBranchRXYCrossSensitivity (perturb ").append(variableBranchId)
                .append(", monitor ").append(monitoredBranchId).append(" and bus ").append(busId).append(") ===").append(System.lineSeparator());
        out.append("baseline R=").append(rBase).append(" X=").append(xBase).append(" Y=").append(yBase)
                .append(" ; finite-difference steps dR=").append(dR).append(" dX=").append(dX).append(" dY=").append(dY)
                .append(" ; functions [P1,P2,Q1,Q2,I1,I2,V] = ").append(Arrays.toString(base)).append(System.lineSeparator());
        appendSensiVsFd(out, "d{func}/dR :", sR, fdR);
        appendSensiVsFd(out, "d{func}/dX :", sX, fdX);
        appendSensiVsFd(out, "d{func}/dY :", sY, fdY);
        LOGGER.info("{}", out);

        assertSensiCloseToFd(sR, fdR, 3e-2, 1e-3);
        assertSensiCloseToFd(sX, fdX, 3e-2, 1e-3);
        assertSensiCloseToFd(sY, fdY, 3e-2, 1e-1);
    }

    /**
     * Branch R / X / Y self-sensitivity on a transformer rather than a line. IEEE-14 transformers have a
     * zero resistance, so the R step is sized relative to X (and ksi = atan2(R, X) = 0 at the base point).
     */
    @Test
    void testBranchRXYSensitivityWithTransformer() {
        String branchId = "T4-7-1";
        String busId = "B7";
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        Network nBase = IeeeCdfNetworkFactory.create14();
        double[] base = solveAndCaptureBranchFunctions(nBase, branchId, busId, lfParameters);
        TwoWindingsTransformer twt = nBase.getTwoWindingsTransformer(branchId);
        double rBase = twt.getR();
        double xBase = twt.getX();
        double yBase = 1.0 / Math.hypot(rBase, xBase);

        double rel = 1e-4;
        // R may be zero on this transformer: size the R step relative to |X| so it stays meaningful.
        double dR = rel * Math.max(Math.abs(rBase), Math.abs(xBase));
        double dX = rel * xBase;
        double dY = rel * yBase;

        Network nR = IeeeCdfNetworkFactory.create14();
        nR.getTwoWindingsTransformer(branchId).setR(rBase + dR);
        double[] fdR = finiteDifference(base, solveAndCaptureBranchFunctions(nR, branchId, busId, lfParameters), dR);

        Network nX = IeeeCdfNetworkFactory.create14();
        nX.getTwoWindingsTransformer(branchId).setX(xBase + dX);
        double[] fdX = finiteDifference(base, solveAndCaptureBranchFunctions(nX, branchId, busId, lfParameters), dX);

        Network nY = IeeeCdfNetworkFactory.create14();
        double scaleY = yBase / (yBase + dY);
        nY.getTwoWindingsTransformer(branchId).setR(rBase * scaleY).setX(xBase * scaleY);
        double[] fdY = finiteDifference(base, solveAndCaptureBranchFunctions(nY, branchId, busId, lfParameters), dY);

        Network network = IeeeCdfNetworkFactory.create14();
        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(createBranchParameterFactors(branchId, busId, branchId, SensitivityVariableType.BRANCH_RESISTANCE));
        factors.addAll(createBranchParameterFactors(branchId, busId, branchId, SensitivityVariableType.BRANCH_REACTANCE));
        factors.addAll(createBranchParameterFactors(branchId, busId, branchId, SensitivityVariableType.BRANCH_ADMITTANCE));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        double[] sR = readBranchParameterSensitivities(result, branchId, busId, branchId, SensitivityVariableType.BRANCH_RESISTANCE);
        double[] sX = readBranchParameterSensitivities(result, branchId, busId, branchId, SensitivityVariableType.BRANCH_REACTANCE);
        double[] sY = readBranchParameterSensitivities(result, branchId, busId, branchId, SensitivityVariableType.BRANCH_ADMITTANCE);

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testBranchRXYSensitivityWithTransformer (branch ").append(branchId).append(", bus ").append(busId).append(") ===").append(System.lineSeparator());
        out.append("baseline R=").append(rBase).append(" X=").append(xBase).append(" Y=").append(yBase)
                .append(" ; finite-difference steps dR=").append(dR).append(" dX=").append(dX).append(" dY=").append(dY)
                .append(" ; functions [P1,P2,Q1,Q2,I1,I2,V] = ").append(Arrays.toString(base)).append(System.lineSeparator());
        appendSensiVsFd(out, "d{func}/dR :", sR, fdR);
        appendSensiVsFd(out, "d{func}/dX :", sX, fdX);
        appendSensiVsFd(out, "d{func}/dY :", sY, fdY);
        LOGGER.info("{}", out);

        assertSensiCloseToFd(sR, fdR, 5e-2, 1e-2);
        assertSensiCloseToFd(sX, fdX, 5e-2, 1e-2);
        assertSensiCloseToFd(sY, fdY, 5e-2, 1e-1);
    }

    private static double branchR(Network network, String branchId) {
        Branch<?> branch = network.getBranch(branchId);
        if (branch instanceof Line line) {
            return line.getR();
        } else if (branch instanceof TwoWindingsTransformer twt) {
            return twt.getR();
        }
        throw new IllegalArgumentException("Unsupported branch type for " + branchId);
    }

    private static double branchX(Network network, String branchId) {
        Branch<?> branch = network.getBranch(branchId);
        if (branch instanceof Line line) {
            return line.getX();
        } else if (branch instanceof TwoWindingsTransformer twt) {
            return twt.getX();
        }
        throw new IllegalArgumentException("Unsupported branch type for " + branchId);
    }

    private static void setBranchR(Network network, String branchId, double r) {
        Branch<?> branch = network.getBranch(branchId);
        if (branch instanceof Line line) {
            line.setR(r);
        } else if (branch instanceof TwoWindingsTransformer twt) {
            twt.setR(r);
        } else {
            throw new IllegalArgumentException("Unsupported branch type for " + branchId);
        }
    }

    private static void setBranchX(Network network, String branchId, double x) {
        Branch<?> branch = network.getBranch(branchId);
        if (branch instanceof Line line) {
            line.setX(x);
        } else if (branch instanceof TwoWindingsTransformer twt) {
            twt.setX(x);
        } else {
            throw new IllegalArgumentException("Unsupported branch type for " + branchId);
        }
    }

    /**
     * Generic finite-difference cross-sensitivity check on IEEE-14: perturb R / X / Y of {@code variableBranchId}
     * (a line or a transformer) and validate the analytic sensitivities of the functions monitored on a
     * <em>different</em> branch {@code monitoredBranchId} (plus the voltage at {@code busId}) against a
     * finite-difference re-solve. The direct term is zero here, so this exercises the indirect term flowing through
     * the Jacobian for every line/transformer combination.
     */
    private void assertBranchRXYCrossSensitivity(String label, String variableBranchId, String monitoredBranchId,
                                                 String busId, double relTol, double floorRX, double floorY) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        Network nBase = IeeeCdfNetworkFactory.create14();
        double[] base = solveAndCaptureBranchFunctions(nBase, monitoredBranchId, busId, lfParameters);
        double rBase = branchR(nBase, variableBranchId);
        double xBase = branchX(nBase, variableBranchId);
        double yBase = 1.0 / Math.hypot(rBase, xBase);

        double rel = 1e-4;
        // Size the R step relative to |X| too, so it stays meaningful on transformers with a zero resistance.
        double dR = rel * Math.max(Math.abs(rBase), Math.abs(xBase));
        double dX = rel * xBase;
        double dY = rel * yBase;

        Network nR = IeeeCdfNetworkFactory.create14();
        setBranchR(nR, variableBranchId, rBase + dR);
        double[] fdR = finiteDifference(base, solveAndCaptureBranchFunctions(nR, monitoredBranchId, busId, lfParameters), dR);

        Network nX = IeeeCdfNetworkFactory.create14();
        setBranchX(nX, variableBranchId, xBase + dX);
        double[] fdX = finiteDifference(base, solveAndCaptureBranchFunctions(nX, monitoredBranchId, busId, lfParameters), dX);

        Network nY = IeeeCdfNetworkFactory.create14();
        double scaleY = yBase / (yBase + dY);
        setBranchR(nY, variableBranchId, rBase * scaleY);
        setBranchX(nY, variableBranchId, xBase * scaleY);
        double[] fdY = finiteDifference(base, solveAndCaptureBranchFunctions(nY, monitoredBranchId, busId, lfParameters), dY);

        Network network = IeeeCdfNetworkFactory.create14();
        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_RESISTANCE));
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_REACTANCE));
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_ADMITTANCE));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        double[] sR = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_RESISTANCE);
        double[] sX = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_REACTANCE);
        double[] sY = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_ADMITTANCE);

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== ").append(label).append(" (perturb ").append(variableBranchId)
                .append(", monitor ").append(monitoredBranchId).append(" and bus ").append(busId).append(") ===").append(System.lineSeparator());
        out.append("baseline R=").append(rBase).append(" X=").append(xBase).append(" Y=").append(yBase)
                .append(" ; finite-difference steps dR=").append(dR).append(" dX=").append(dX).append(" dY=").append(dY)
                .append(" ; functions [P1,P2,Q1,Q2,I1,I2,V] = ").append(Arrays.toString(base)).append(System.lineSeparator());
        appendSensiVsFd(out, "d{func}/dR :", sR, fdR);
        appendSensiVsFd(out, "d{func}/dX :", sX, fdX);
        appendSensiVsFd(out, "d{func}/dY :", sY, fdY);
        LOGGER.info("{}", out);

        assertSensiCloseToFd(sR, fdR, relTol, floorRX);
        assertSensiCloseToFd(sX, fdX, relTol, floorRX);
        assertSensiCloseToFd(sY, fdY, relTol, floorY);
    }

    /** Cross-sensitivity with a transformer as the perturbed branch and a line as the monitored branch. */
    @Test
    void testBranchRXYCrossSensitivityTransformerVariable() {
        assertBranchRXYCrossSensitivity("testBranchRXYCrossSensitivityTransformerVariable",
                "T4-7-1", "L4-5-1", "B5", 3e-2, 1e-3, 1e-1);
    }

    /** Cross-sensitivity with a line as the perturbed branch and a transformer as the monitored branch. */
    @Test
    void testBranchRXYCrossSensitivityTransformerMonitored() {
        assertBranchRXYCrossSensitivity("testBranchRXYCrossSensitivityTransformerMonitored",
                "L4-5-1", "T4-7-1", "B7", 3e-2, 1e-3, 1e-1);
    }

    /** Cross-sensitivity between two transformers sharing a bus. */
    @Test
    void testBranchRXYCrossSensitivityTransformerToTransformer() {
        assertBranchRXYCrossSensitivity("testBranchRXYCrossSensitivityTransformerToTransformer",
                "T4-9-1", "T4-7-1", "B7", 3e-2, 1e-3, 1e-1);
    }

    /**
     * Net reactive power injected at a bus, matching the BUS_REACTIVE_POWER function convention: the opposite of
     * the reactive power flowing from the bus into its incident branches.
     */
    private static double busReactiveInjection(Network network, String busId) {
        return -network.getBusBreakerView().getBus(busId).getConnectedTerminalStream()
                .filter(t -> t.getConnectable() instanceof Branch)
                .mapToDouble(Terminal::getQ)
                .sum();
    }

    /**
     * Sensitivity of a bus reactive power injection (BUS_REACTIVE_POWER) to a branch R / X / Y. Perturb a line
     * incident to two generator buses (where the reactive injection is free) and cross-check the analytic
     * sensitivity against a finite-difference re-solve. Exercises both the direct term (perturbed branch incident
     * to the monitored bus) and the indirect term (a generator bus elsewhere).
     */
    @Test
    void testBranchRXYBusReactivePowerSensitivity() {
        String branchId = "L2-3-1"; // incident to generator buses B2 and B3
        // BUS_REACTIVE_POWER function ids (generators map to their bus): B2/B3 are incident, B6 is not.
        List<String> generators = List.of("B2-G", "B3-G", "B6-G");
        List<String> buses = List.of("B2", "B2", "B6"); // bus hosting each generator (B3-G is at B3)
        List<String> genBus = List.of("B2", "B3", "B6");
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        Network nBase = IeeeCdfNetworkFactory.create14();
        runLf(nBase, lfParameters);
        Map<String, Double> qBase = new LinkedHashMap<>();
        genBus.forEach(b -> qBase.put(b, busReactiveInjection(nBase, b)));
        Line line = nBase.getLine(branchId);
        double rBase = line.getR();
        double xBase = line.getX();
        double yBase = 1.0 / Math.hypot(rBase, xBase);
        double rel = 1e-4;
        double dR = rel * rBase;
        double dX = rel * xBase;
        double dY = rel * yBase;

        Network nR = IeeeCdfNetworkFactory.create14();
        nR.getLine(branchId).setR(rBase + dR);
        runLf(nR, lfParameters);
        Network nX = IeeeCdfNetworkFactory.create14();
        nX.getLine(branchId).setX(xBase + dX);
        runLf(nX, lfParameters);
        Network nY = IeeeCdfNetworkFactory.create14();
        double scaleY = yBase / (yBase + dY);
        nY.getLine(branchId).setR(rBase * scaleY).setX(xBase * scaleY);
        runLf(nY, lfParameters);

        Network network = IeeeCdfNetworkFactory.create14();
        List<SensitivityFactor> factors = new ArrayList<>();
        for (String gen : generators) {
            for (SensitivityVariableType v : List.of(SensitivityVariableType.BRANCH_RESISTANCE,
                    SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE)) {
                factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_REACTIVE_POWER, gen, v, branchId, false, ContingencyContext.all()));
            }
        }
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testBranchRXYBusReactivePowerSensitivity (perturb ").append(branchId).append(") ===").append(System.lineSeparator());
        SensitivityVariableType[] vars = {SensitivityVariableType.BRANCH_RESISTANCE, SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE};
        double[] steps = {dR, dX, dY};
        Network[] perturbed = {nR, nX, nY};
        String[] varNames = {"R", "X", "Y"};
        for (int g = 0; g < generators.size(); g++) {
            String gen = generators.get(g);
            String bus = genBus.get(g);
            for (int v = 0; v < vars.length; v++) {
                double analytic = result.getSensitivityValue(branchId, gen, SensitivityFunctionType.BUS_REACTIVE_POWER, vars[v]);
                double fd = (busReactiveInjection(perturbed[v], bus) - qBase.get(bus)) / steps[v];
                out.append(String.format("  Q(%-3s)/d%s : analytic = %+12.4f   fd = %+12.4f%n", bus, varNames[v], analytic, fd));
            }
        }
        LOGGER.info("{}", out);

        for (int g = 0; g < generators.size(); g++) {
            String gen = generators.get(g);
            String bus = genBus.get(g);
            for (int v = 0; v < vars.length; v++) {
                double analytic = result.getSensitivityValue(branchId, gen, SensitivityFunctionType.BUS_REACTIVE_POWER, vars[v]);
                double fd = (busReactiveInjection(perturbed[v], bus) - qBase.get(bus)) / steps[v];
                assertEquals(fd, analytic, Math.max(1.0, Math.abs(fd) * 2e-2), "dQ(" + bus + ")/d" + varNames[v]);
            }
        }
    }

    private static final double[] RXY_SWEEP_RELATIVE_STEPS = {1e-4, 1e-3, 1e-2, 5e-2, 1e-1, 2e-1, 5e-1, 1.0, 2.0};

    /**
     * Sweep an increasing perturbation of one branch parameter and report, for the monitored P1, how the
     * finite-difference slope drifts away from the (fixed) analytic sensitivity evaluated at the base point.
     * Returns the relative P1 error per step. The "point of rupture" is where this error first becomes large.
     */
    private double[] sweepBranchParameterLinearity(StringBuilder out, String paramName, String variableBranchId, String monitoredBranchId,
                                                   String busId, LoadFlowParameters lfParameters, double[] base, double[] analytic) {
        Network n0 = IeeeCdfNetworkFactory.create14();
        double rBase = branchR(n0, variableBranchId);
        double xBase = branchX(n0, variableBranchId);
        double yBase = 1.0 / Math.hypot(rBase, xBase);

        out.append(String.format("  perturbing %s :%n", paramName));
        out.append(String.format("    %-9s %-14s %-18s %-18s %-10s%n",
                "rel", "step", "pred dP1(sensi*step)", "fd dP1(after-base)", "relErr%"));

        double[] relErrP1 = new double[RXY_SWEEP_RELATIVE_STEPS.length];
        Double ruptureRel = null;
        for (int k = 0; k < RXY_SWEEP_RELATIVE_STEPS.length; k++) {
            double rel = RXY_SWEEP_RELATIVE_STEPS[k];
            Network net = IeeeCdfNetworkFactory.create14();
            double step;
            switch (paramName) {
                case "R" -> {
                    step = rel * Math.max(Math.abs(rBase), Math.abs(xBase));
                    setBranchR(net, variableBranchId, rBase + step);
                }
                case "X" -> {
                    step = rel * xBase;
                    setBranchX(net, variableBranchId, xBase + step);
                }
                case "Y" -> {
                    step = rel * yBase;
                    double scaleY = yBase / (yBase + step);
                    setBranchR(net, variableBranchId, rBase * scaleY);
                    setBranchX(net, variableBranchId, xBase * scaleY);
                }
                default -> throw new IllegalArgumentException(paramName);
            }
            double[] perturbed = solveAndCaptureBranchFunctions(net, monitoredBranchId, busId, lfParameters);
            double actualDeltaP1 = perturbed[0] - base[0];      // finite-difference change of P1
            double predictedDeltaP1 = analytic[0] * step;       // first-order predicted change = sensi * step
            relErrP1[k] = Math.abs(predictedDeltaP1 - actualDeltaP1) / Math.max(Math.abs(actualDeltaP1), 1e-9);
            out.append(String.format("    %-9.0e %-14.6g %+-18.5f %+-18.5f %-10.4f%n",
                    rel, step, predictedDeltaP1, actualDeltaP1, relErrP1[k] * 100));
            if (ruptureRel == null && relErrP1[k] > 0.01) {
                ruptureRel = rel;
            }
        }
        out.append(String.format("    -> P1 rupture (relErr > 1%%) first reached at rel = %s%n", ruptureRel == null ? "never in range" : ruptureRel));
        return relErrP1;
    }

    private void runBranchRXYLinearitySweep(StringBuilder out, String label, String variableBranchId, String monitoredBranchId, String busId) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        Network nBase = IeeeCdfNetworkFactory.create14();
        double[] base = solveAndCaptureBranchFunctions(nBase, monitoredBranchId, busId, lfParameters);

        Network network = IeeeCdfNetworkFactory.create14();
        List<SensitivityFactor> factors = new ArrayList<>();
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_RESISTANCE));
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_REACTANCE));
        factors.addAll(createBranchParameterFactors(monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_ADMITTANCE));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));
        double[] sR = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_RESISTANCE);
        double[] sX = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_REACTANCE);
        double[] sY = readBranchParameterSensitivities(result, monitoredBranchId, busId, variableBranchId, SensitivityVariableType.BRANCH_ADMITTANCE);

        out.append(String.format("=== linearity sweep : %s (perturb %s, monitor %s, bus %s) ===%n", label, variableBranchId, monitoredBranchId, busId));
        double[] errR = sweepBranchParameterLinearity(out, "R", variableBranchId, monitoredBranchId, busId, lfParameters, base, sR);
        double[] errX = sweepBranchParameterLinearity(out, "X", variableBranchId, monitoredBranchId, busId, lfParameters, base, sX);
        double[] errY = sweepBranchParameterLinearity(out, "Y", variableBranchId, monitoredBranchId, busId, lfParameters, base, sY);

        // Anchor: at the smallest step the analytic sensitivity matches the finite difference closely...
        int last = RXY_SWEEP_RELATIVE_STEPS.length - 1;
        for (double[] err : List.of(errR, errX, errY)) {
            assertTrue(err[0] < 0.01, "analytic and finite difference should agree at the smallest step");
            // ...and the gap grows with the step size (the rupture this sweep is meant to expose).
            assertTrue(err[last] > err[0], "linearization error should grow as the perturbation grows");
        }
    }

    /**
     * Linearity sweep on lines: increase the R / X / Y perturbation step and watch the first-order sensitivity
     * peel away from the finite-difference slope, for both self- and cross-sensitivities. Diagnostic / printed.
     * All output is buffered and printed once at the end so the table is not interleaved with the load-flow logs.
     */
    @Test
    void testBranchRXYLinearitySweep() {
        StringBuilder out = new StringBuilder("\n");
        out.append("Columns: step    = parameter change applied (dR / dX / dY);\n");
        out.append("         pred dP1 = first-order predicted change of P1 = sensi * step (sensi = engine dP1/dparam at the base point);\n");
        out.append("         fd dP1   = actual change of P1 (after - base) measured by re-solving at this step - the truth;\n");
        out.append("         relErr%  = 100*|pred - fd| / |fd|. Tiny step: pred ~ fd. As step grows, the linear prediction drifts.\n");
        runBranchRXYLinearitySweep(out, "self", "L2-3-1", "L2-3-1", "B4");
        runBranchRXYLinearitySweep(out, "cross", "L1-2-1", "L2-3-1", "B4");
        LOGGER.info("{}", out);
    }

    private static final List<String> RXY_FLOW_LABELS = List.of("P1", "P2", "Q1", "Q2", "I1", "I2");

    /** Capture the 6 flows [P1, P2, Q1, Q2, I1, I2] of a branch at the current operating point. */
    private static double[] captureBranchFlows(Network network, String branchId) {
        Branch<?> branch = network.getBranch(branchId);
        Terminal t1 = branch.getTerminal1();
        Terminal t2 = branch.getTerminal2();
        return new double[] {t1.getP(), t2.getP(), t1.getQ(), t2.getQ(), t1.getI(), t2.getI()};
    }

    /** Read the 6 flow sensitivities of a monitored branch w.r.t. a variable branch parameter. */
    private static double[] readBranchFlowSensitivities(SensitivityAnalysisResult result, String variableBranchId,
                                                        String monitoredBranchId, SensitivityVariableType varType) {
        return new double[] {
                result.getBranchFlow1SensitivityValue(variableBranchId, monitoredBranchId, varType),
                result.getBranchFlow2SensitivityValue(variableBranchId, monitoredBranchId, varType),
                result.getSensitivityValue(variableBranchId, monitoredBranchId, SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, varType),
                result.getSensitivityValue(variableBranchId, monitoredBranchId, SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, varType),
                result.getBranchCurrent1SensitivityValue(variableBranchId, monitoredBranchId, varType),
                result.getBranchCurrent2SensitivityValue(variableBranchId, monitoredBranchId, varType)};
    }

    /**
     * Use the branch parameter sensitivities to predict the <em>direction</em> (sign) of the flow
     * redistribution that occurs when a line is physically disconnected, mirroring the sign comparison of
     * the testShuntBSensi* family.
     * <p>
     * Disconnecting a line drives its series admittance to zero, i.e. ΔY = −Y_base for the
     * {@code BRANCH_ADMITTANCE} variable. The first-order prediction of the change of a monitored quantity F
     * on a neighbouring branch is therefore {@code dF/dY · (−Y_base)}, whose sign should match the sign of
     * the actual change (F_after − F_before) measured by disconnecting the line and re-solving.
     * <p>
     * We assert the admittance-based direction, which is the cleanest representation of a disconnection since
     * ΔY = −Y_base is exact (the impedance picture R, X → ∞ is not a finite step). As in the shunt tests, this
     * is a sign/direction check — the magnitude is not expected to match for such a large, non-linear perturbation.
     */
    @Test
    void testBranchDisconnectionDirectionFromSensitivity() {
        String disconnectedLineId = "L2-3-1";
        List<String> monitoredBranchIds = List.of("L1-2-1", "L2-4-1", "L2-5-1", "L3-4-1");
        String busId = "B4";
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        // Snapshot before: line connected.
        Network nBefore = IeeeCdfNetworkFactory.create14();
        runLf(nBefore, lfParameters);
        Map<String, double[]> flowsBefore = new LinkedHashMap<>();
        monitoredBranchIds.forEach(id -> flowsBefore.put(id, captureBranchFlows(nBefore, id)));
        double vBefore = nBefore.getBusBreakerView().getBus(busId).getV();

        // Snapshot after: line physically disconnected at both ends.
        Network nAfter = IeeeCdfNetworkFactory.create14();
        nAfter.getLine(disconnectedLineId).getTerminal1().disconnect();
        nAfter.getLine(disconnectedLineId).getTerminal2().disconnect();
        runLf(nAfter, lfParameters);
        Map<String, double[]> flowsAfter = new LinkedHashMap<>();
        monitoredBranchIds.forEach(id -> flowsAfter.put(id, captureBranchFlows(nAfter, id)));
        double vAfter = nAfter.getBusBreakerView().getBus(busId).getV();

        // Baseline (connected) for the sensitivity analysis.
        Network network = IeeeCdfNetworkFactory.create14();
        Line disconnectedLine = network.getLine(disconnectedLineId);
        double yBase = 1.0 / Math.hypot(disconnectedLine.getR(), disconnectedLine.getX());
        double deltaY = -yBase; // disconnection drives the series admittance to zero

        List<SensitivityFactor> factors = new ArrayList<>();
        for (String monitoredBranchId : monitoredBranchIds) {
            for (SensitivityVariableType varType : List.of(SensitivityVariableType.BRANCH_RESISTANCE,
                    SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE)) {
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, monitoredBranchId, varType, disconnectedLineId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, monitoredBranchId, varType, disconnectedLineId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, monitoredBranchId, varType, disconnectedLineId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, monitoredBranchId, varType, disconnectedLineId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, monitoredBranchId, varType, disconnectedLineId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, monitoredBranchId, varType, disconnectedLineId, false, ContingencyContext.all()));
            }
        }
        for (SensitivityVariableType varType : List.of(SensitivityVariableType.BRANCH_RESISTANCE,
                SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE)) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, busId, varType, disconnectedLineId, false, ContingencyContext.all()));
        }

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testBranchDisconnectionDirectionFromSensitivity (disconnect ").append(disconnectedLineId).append(") ===").append(System.lineSeparator());
        out.append("disconnected line baseline Y=").append(yBase).append(" ; ΔY (full disconnection) = ").append(deltaY).append(System.lineSeparator());

        // Direction check on each monitored branch flow: sign(dF/dY · ΔY) must match sign(F_after − F_before).
        // Active power is the robust quantity; reactive power / current can flip sign because Y holds the line
        // charging fixed whereas a real disconnection also removes it. We assert P1/P2 and print everything.
        for (String monitoredBranchId : monitoredBranchIds) {
            double[] before = flowsBefore.get(monitoredBranchId);
            double[] after = flowsAfter.get(monitoredBranchId);
            double[] sY = readBranchFlowSensitivities(result, disconnectedLineId, monitoredBranchId, SensitivityVariableType.BRANCH_ADMITTANCE);

            out.append("monitored branch ").append(monitoredBranchId).append(" :").append(System.lineSeparator());
            for (int i = 0; i < RXY_FLOW_LABELS.size(); i++) {
                double actual = after[i] - before[i];
                double predictedY = sY[i] * deltaY;
                out.append(String.format("  %-2s  Δ(actual) = %+12.5f   pred(dF/dY·ΔY) = %+12.5f   sameSignY = %b%n",
                        RXY_FLOW_LABELS.get(i), actual, predictedY,
                        Math.signum(actual) == Math.signum(predictedY)));
            }

            // Assert the active-power redistribution direction (P1, P2) is correctly predicted.
            for (int i = 0; i <= 1; i++) {
                double actual = after[i] - before[i];
                double predictedY = sY[i] * deltaY;
                if (Math.abs(actual) > 1e-3) {
                    assertEquals(Math.signum(actual), Math.signum(predictedY),
                            RXY_FLOW_LABELS.get(i) + " direction mismatch on " + monitoredBranchId
                                    + " (actual=" + actual + ", predicted=" + predictedY + ")");
                }
            }
        }

        // Bus voltage direction.
        double vActual = vAfter - vBefore;
        double vPredictedY = result.getBusVoltageSensitivityValue(disconnectedLineId, busId, SensitivityVariableType.BRANCH_ADMITTANCE) * deltaY;
        out.append(String.format("bus %s voltage : Δ(actual) = %+.6f   pred(dV/dY·ΔY) = %+.6f   sameSign = %b%n",
                busId, vActual, vPredictedY, Math.signum(vActual) == Math.signum(vPredictedY)));
        LOGGER.info("{}", out);
    }

    /**
     * Complement of {@link #testBranchDisconnectionDirectionFromSensitivity}: predict the <em>direction</em> of the
     * flow redistribution produced by <em>connecting</em> a line, using the branch admittance sensitivity.
     * <p>
     * The natural linearization point for a connection is the out-of-service state, where an open branch has no
     * admittance sensitivity. We therefore use a high-impedance stand-in: the candidate line is kept in service but
     * with its impedance scaled up by a large factor, so it carries ≈ 0 (electrically ≈ disconnected) yet still has a
     * defined {@code BRANCH_ADMITTANCE} sensitivity. Connecting then raises the series admittance from this small
     * value y_weak up to the nominal y, i.e. ΔY = y − y_weak &gt; 0, and the first-order prediction dF/dY · ΔY (with
     * dF/dY evaluated at the weak state) should match the sign of the actual change measured by restoring the line.
     * As for disconnection, this is a sign/direction check; the magnitude is not expected to match for such a large,
     * non-linear step.
     */
    @Test
    void testBranchConnectionDirectionFromSensitivity() {
        String connectedLineId = "L2-3-1";
        List<String> monitoredBranchIds = List.of("L1-2-1", "L2-4-1", "L2-5-1", "L3-4-1");
        String busId = "B4";
        double weakImpedanceFactor = 1000.0; // impedance ×1000 -> admittance /1000 ≈ open, but still in service
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();

        double rFull = IeeeCdfNetworkFactory.create14().getLine(connectedLineId).getR();
        double xFull = IeeeCdfNetworkFactory.create14().getLine(connectedLineId).getX();
        double yFull = 1.0 / Math.hypot(rFull, xFull);
        double yWeak = 1.0 / Math.hypot(rFull * weakImpedanceFactor, xFull * weakImpedanceFactor);
        double deltaY = yFull - yWeak; // connection raises the series admittance to nominal

        // Snapshot before: line present but high-impedance (electrically ≈ out of service). This is also the
        // linearization point for the sensitivity.
        Network nBefore = IeeeCdfNetworkFactory.create14();
        nBefore.getLine(connectedLineId).setR(rFull * weakImpedanceFactor).setX(xFull * weakImpedanceFactor);
        runLf(nBefore, lfParameters);
        Map<String, double[]> flowsBefore = new LinkedHashMap<>();
        monitoredBranchIds.forEach(id -> flowsBefore.put(id, captureBranchFlows(nBefore, id)));
        double vBefore = nBefore.getBusBreakerView().getBus(busId).getV();

        // Snapshot after: line restored to its nominal impedance (connected).
        Network nAfter = IeeeCdfNetworkFactory.create14();
        runLf(nAfter, lfParameters);
        Map<String, double[]> flowsAfter = new LinkedHashMap<>();
        monitoredBranchIds.forEach(id -> flowsAfter.put(id, captureBranchFlows(nAfter, id)));
        double vAfter = nAfter.getBusBreakerView().getBus(busId).getV();

        // Sensitivity at the weak (≈ out-of-service) state.
        Network network = IeeeCdfNetworkFactory.create14();
        network.getLine(connectedLineId).setR(rFull * weakImpedanceFactor).setX(xFull * weakImpedanceFactor);
        List<SensitivityFactor> factors = new ArrayList<>();
        for (String m : monitoredBranchIds) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, m, SensitivityVariableType.BRANCH_ADMITTANCE, connectedLineId, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, m, SensitivityVariableType.BRANCH_ADMITTANCE, connectedLineId, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, m, SensitivityVariableType.BRANCH_ADMITTANCE, connectedLineId, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_2, m, SensitivityVariableType.BRANCH_ADMITTANCE, connectedLineId, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, m, SensitivityVariableType.BRANCH_ADMITTANCE, connectedLineId, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, m, SensitivityVariableType.BRANCH_ADMITTANCE, connectedLineId, false, ContingencyContext.all()));
        }
        factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, busId, SensitivityVariableType.BRANCH_ADMITTANCE, connectedLineId, false, ContingencyContext.all()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters));

        StringBuilder out = new StringBuilder(System.lineSeparator());
        out.append("=== testBranchConnectionDirectionFromSensitivity (connect ").append(connectedLineId).append(") ===").append(System.lineSeparator());
        out.append("y_weak=").append(yWeak).append(" (≈ out of service) -> y_nominal=").append(yFull).append(" ; ΔY (connection) = ").append(deltaY).append(System.lineSeparator());

        for (String monitoredBranchId : monitoredBranchIds) {
            double[] before = flowsBefore.get(monitoredBranchId);
            double[] after = flowsAfter.get(monitoredBranchId);
            double[] sY = readBranchFlowSensitivities(result, connectedLineId, monitoredBranchId, SensitivityVariableType.BRANCH_ADMITTANCE);

            out.append("monitored branch ").append(monitoredBranchId).append(" :").append(System.lineSeparator());
            for (int i = 0; i < RXY_FLOW_LABELS.size(); i++) {
                double actual = after[i] - before[i];
                double predictedY = sY[i] * deltaY;
                out.append(String.format("  %-2s  Δ(actual) = %+12.5f   pred(dF/dY·ΔY) = %+12.5f   sameSignY = %b%n",
                        RXY_FLOW_LABELS.get(i), actual, predictedY, Math.signum(actual) == Math.signum(predictedY)));
            }

            // Assert the active-power redistribution direction (P1, P2) is correctly predicted.
            for (int i = 0; i <= 1; i++) {
                double actual = after[i] - before[i];
                double predictedY = sY[i] * deltaY;
                if (Math.abs(actual) > 1e-3) {
                    assertEquals(Math.signum(actual), Math.signum(predictedY),
                            RXY_FLOW_LABELS.get(i) + " direction mismatch on " + monitoredBranchId
                                    + " (actual=" + actual + ", predicted=" + predictedY + ")");
                }
            }
        }

        double vActual = vAfter - vBefore;
        double vPredictedY = result.getBusVoltageSensitivityValue(connectedLineId, busId, SensitivityVariableType.BRANCH_ADMITTANCE) * deltaY;
        out.append(String.format("bus %s voltage : Δ(actual) = %+.6f   pred(dV/dY·ΔY) = %+.6f   sameSign = %b%n",
                busId, vActual, vPredictedY, Math.signum(vActual) == Math.signum(vPredictedY)));
        LOGGER.info("{}", out);
    }

    /**
     * Builds a network with a dead-end line: a generator at b1 feeds, through line {@code l1}, a bus b2 that has no
     * load, generator or shunt. With no downstream consumption and no line shunt admittance, {@code l1} carries
     * (numerically) zero current at both ends.
     */
    private static Network createDeadEndLineNetwork() {
        Network network = Network.create("dead-end-line", "test");
        Substation s1 = network.newSubstation().setId("S1").add();
        VoltageLevel vl1 = s1.newVoltageLevel().setId("vl1").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl1.getBusBreakerView().newBus().setId("b1").add();
        vl1.newGenerator().setId("g1").setBus("b1").setConnectableBus("b1")
                .setTargetP(0).setTargetV(400).setMinP(0).setMaxP(100).setVoltageRegulatorOn(true).add();
        VoltageLevel vl2 = s1.newVoltageLevel().setId("vl2").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl2.getBusBreakerView().newBus().setId("b2").add();
        // No load / generator / shunt at b2, and no line shunt admittance -> l1 carries zero current.
        network.newLine().setId("l1").setBus1("b1").setBus2("b2").setR(0.1).setX(1.0).add();
        return network;
    }

    /**
     * Current-magnitude self-sensitivity (∂|I|/∂{R,X,Y}) of a branch that carries zero current. There the
     * direct partial of {@code |I| = |S| / v} is a 0/0 limit; the implementation returns 0 below the
     * zero-current threshold. This exercises that branch and asserts the sensitivities are a clean 0 (not
     * NaN / infinity from a division by a vanishing apparent power).
     */
    @Test
    void testBranchRXYCurrentSelfSensitivityZeroCurrent() {
        Network network = createDeadEndLineNetwork();
        runAcLf(network);

        // Sanity check: the dead-end line indeed carries ~0 current.
        assertEquals(0.0, network.getLine("l1").getTerminal1().getI(), 1e-3);
        assertEquals(0.0, network.getLine("l1").getTerminal2().getI(), 1e-3);

        String branchId = "l1";
        List<SensitivityFactor> factors = new ArrayList<>();
        for (SensitivityVariableType varType : List.of(SensitivityVariableType.BRANCH_RESISTANCE,
                SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE)) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, branchId, varType, branchId, false, ContingencyContext.all()));
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, branchId, varType, branchId, false, ContingencyContext.all()));
        }

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(createParameters(false, "b1")));

        for (SensitivityVariableType varType : List.of(SensitivityVariableType.BRANCH_RESISTANCE,
                SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE)) {
            double sI1 = result.getBranchCurrent1SensitivityValue(branchId, branchId, varType);
            double sI2 = result.getBranchCurrent2SensitivityValue(branchId, branchId, varType);
            assertTrue(Double.isFinite(sI1), "I1 sensitivity must be finite for " + varType);
            assertTrue(Double.isFinite(sI2), "I2 sensitivity must be finite for " + varType);
            assertEquals(0.0, sI1, DELTA_I, "I1 self-sensitivity must be 0 at zero current for " + varType);
            assertEquals(0.0, sI2, DELTA_I, "I2 self-sensitivity must be 0 at zero current for " + varType);
        }
    }

    /**
     * A branch R / X / Y variable that does not resolve to an in-service two-bus branch must yield a (zero)
     * predefined result rather than an error. This covers the three ways the variable element resolves to
     * {@code null}: an unknown branch id, a branch open at side 1, and a branch open at side 2, across every
     * function-type family (branch active power / current, bus voltage, branch reactive power, bus reactive
     * power) that supports branch-parameter variables.
     */
    @Test
    void testBranchRXYVariableOnDisconnectedOrUnknownBranch() {
        Network network = IeeeCdfNetworkFactory.create14();
        // Open a line at side 1 and another at side 2 (kept in network, but no longer a two-bus branch).
        network.getLine("L1-5-1").getTerminal1().disconnect();
        network.getLine("L3-4-1").getTerminal2().disconnect();

        String monitoredBranchId = "L2-3-1";
        String monitoredBusId = "B4";
        List<String> badVariableBranchIds = List.of("UNKNOWN-BRANCH", "L1-5-1", "L3-4-1");

        List<SensitivityFactor> factors = new ArrayList<>();
        for (String variableId : badVariableBranchIds) {
            for (SensitivityVariableType varType : List.of(SensitivityVariableType.BRANCH_RESISTANCE,
                    SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE)) {
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, monitoredBranchId, varType, variableId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, monitoredBranchId, varType, variableId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, monitoredBranchId, varType, variableId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, monitoredBusId, varType, variableId, false, ContingencyContext.all()));
                factors.add(new SensitivityFactor(SensitivityFunctionType.BUS_REACTIVE_POWER, monitoredBusId, varType, variableId, false, ContingencyContext.all()));
            }
        }

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(createParameters(false, "B1")));

        // Every factor whose variable does not resolve to an in-service branch must yield exactly 0.
        for (String variableId : badVariableBranchIds) {
            for (SensitivityVariableType varType : List.of(SensitivityVariableType.BRANCH_RESISTANCE,
                    SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE)) {
                assertEquals(0.0, result.getSensitivityValue(variableId, monitoredBranchId, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, varType), 0.0);
                assertEquals(0.0, result.getSensitivityValue(variableId, monitoredBranchId, SensitivityFunctionType.BRANCH_CURRENT_1, varType), 0.0);
                assertEquals(0.0, result.getSensitivityValue(variableId, monitoredBranchId, SensitivityFunctionType.BRANCH_REACTIVE_POWER_1, varType), 0.0);
                assertEquals(0.0, result.getSensitivityValue(variableId, monitoredBusId, SensitivityFunctionType.BUS_VOLTAGE, varType), 0.0);
                assertEquals(0.0, result.getSensitivityValue(variableId, monitoredBusId, SensitivityFunctionType.BUS_REACTIVE_POWER, varType), 0.0);
            }
        }
    }
}
