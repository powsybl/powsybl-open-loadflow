/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.DanglingLineContingency;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.DanglingLineFactory;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class DcSensitivityAnalysisTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testEsgTuto() {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VLLOAD_0");
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(2, result.getValues().size());
        assertEquals(0.5d, getValue(result, "GEN", "NHV1_NHV2_1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getValue(result, "GEN", "NHV1_NHV2_2"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4buses() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        Map<String, Double> functionReferenceByLine = new HashMap<>();
        for (Line line : network.getLines()) {
            functionReferenceByLine.put(line.getId(), line.getTerminal1().getP());
        }

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0");
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());
        assertEquals(0.25d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.25d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.125d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.375d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.625d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.125d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.375d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.125d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.125d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.625d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        for (Line line : network.getLines()) {
            assertEquals(functionReferenceByLine.get(line.getId()), getFunctionReference(result, line.getId()), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testGeneratorInjection4busesDistributed() {
        // The factors are generators injections
        Network network = FourBusNetworkFactory.create();
        for (Generator generator : network.getGenerators()) {
            generator.setMaxP(generator.getTargetP() + 0.5);
        }
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(0.192d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.269d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.115d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.038d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.154d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.067d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.356d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.260d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.163d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.096d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.433d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.144d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.24d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.337d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.096d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGeneratorInjection4busesDistributed2() {
        // The factors are generators injections
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());
        assertEquals(0.175d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.275d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.125d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.025d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.450d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.25d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLoadInjection4busesDistributed() {
        // test injection increase on loads
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(10, result.getValues().size());
        assertEquals(0.05d, getValue(result, "d2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "d2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "d2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "d2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "d2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.075d, getValue(result, "d3", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.025d, getValue(result, "d3", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.375d, getValue(result, "d3", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.275d, getValue(result, "d3", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "d3", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSeveralGeneratorsConnectedToTheSameBus() {
        // test injection increase on loads
        Network network = FourBusNetworkFactory.createWithTwoGeneratorsAtBus2();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(10, result.getValues().size());
        assertEquals(0.045d, getValue(result, "d2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.318d, getValue(result, "d2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.227d, getValue(result, "d2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.136d, getValue(result, "d2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.09d, getValue(result, "d2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGeneratorInjection4busesDistributedOnLoad() {
        // test injection increase on loads
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());
        assertEquals(0.225d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.325d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.125d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.225d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.45d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.3d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.4d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLoadInjection4busesDistributedOnLoad() {
        // test injection increase on loads
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<SensitivityFactor> factors = createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(10, result.getValues().size());
        assertEquals(0.1d, getValue(result, "d2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.3d, getValue(result, "d2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getValue(result, "d2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "d2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2d, getValue(result, "d2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.025d, getValue(result, "d3", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.075d, getValue(result, "d3", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.125d, getValue(result, "d3", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.025d, getValue(result, "d3", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.05d, getValue(result, "d3", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesDistributedPartialFactors() {
        // test that the sensitivity computation does not make assumption about the presence of all factors
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g1")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());
        assertEquals(0.175d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.275d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.125d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.025d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLoadInjectionWithoutGenerator() {
        // test injection increase on loads
        Network network = FourBusNetworkFactory.createBaseNetwork();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(10, result.getValues().size());
        assertEquals(0.083d, getValue(result, "d2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.583d, getValue(result, "d2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.417d, getValue(result, "d2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "d2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.167d, getValue(result, "d2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.0416d, getValue(result, "d3", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2083d, getValue(result, "d3", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2083d, getValue(result, "d3", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.375d, getValue(result, "d3", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.416d, getValue(result, "d3", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLoadInjectionOnSlackBusDistributed() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());
        assertEquals(0.175d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.275d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.125d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.025d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-0.450d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.25d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGLSK() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("d2", 30f),
                new WeightedSensitivityVariable("g2", 10f),
                new WeightedSensitivityVariable("d3", 50f),
                new WeightedSensitivityVariable("g1", 10f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk")).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), variableSets, sensiParameters);

        assertEquals(5, result.getValues().size());
        assertEquals(-7d / 40d, getValue(result, "glsk", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-3d / 8d, getValue(result, "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 40d, getValue(result, "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(7d / 40d, getValue(result, "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-7d / 20d, getValue(result, "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOnSlackBusDistributed() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("d2", 30f),
                new WeightedSensitivityVariable("g2", 10f),
                new WeightedSensitivityVariable("d3", 50f),
                new WeightedSensitivityVariable("g1", 10f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk")).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), variableSets, sensiParameters);

        assertEquals(5, result.getValues().size());
        assertEquals(0d, getValue(result, "glsk", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 10d, getValue(result, "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 10d, getValue(result, "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 5d, getValue(result, "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 5d, getValue(result, "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLoadInjectionOnSlackBus() {
        // test injection increase on loads
        Network network = FourBusNetworkFactory.createBaseNetwork();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b2_vl_0", false);
        List<SensitivityFactor> factors = createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(0.0d, getValue(result, "d2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testVscInjection() {
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createVsc();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0", false);
        List<SensitivityFactor> factors = network.getBranchStream()
                .map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), network.getVscConverterStation("cs2").getId()))
                .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(-1d, getValue(result, "cs2", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLccInjection() {
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createLcc();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0", false);
        List<SensitivityFactor> factors = network.getBranchStream()
                .map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), network.getLccConverterStation("cs2").getId()))
                .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(-1d, getValue(result, "cs2", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensi() {
        double sensiChange = 10e-4;
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcWithGenerators();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createTwoCcLinkedByAHvdcWithGenerators();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + sensiChange);
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network.getLine(line).getTerminal1().getP() - network1.getLine(line).getTerminal1().getP()) / sensiChange
            ));
        ContingencyContext contingencyContext = ContingencyContext.all();
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23"),
                SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(loadFlowDiff.get("l12"), result.getValue(null, "l12", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getValue(null, "l13", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getValue(null, "l23", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithBothSides() {
        double sensiChange = 10e-4;
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + sensiChange);
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network.getLine(line).getTerminal1().getP() - network1.getLine(line).getTerminal1().getP()) / sensiChange
            ));

        ContingencyContext contingencyContext = ContingencyContext.all();
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
            SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getValue(null, "l12", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getValue(null, "l13", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getValue(null, "l23", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l25"), result.getValue(null, "l25", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l45"), result.getValue(null, "l45", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l46"), result.getValue(null, "l46", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l56"), result.getValue(null, "l56", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithBothSidesDistributed() {
        double sensiChange = 10e-4;
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + sensiChange);
        network1.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network.getLine(line).getTerminal1().getP() - network1.getLine(line).getTerminal1().getP()) / sensiChange
            ));

        ContingencyContext contingencyContext = ContingencyContext.all();
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
            SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);

        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getValue(null, "l12", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getValue(null, "l13", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getValue(null, "l23", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l25"), result.getValue(null, "l25", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l45"), result.getValue(null, "l45", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l46"), result.getValue(null, "l46", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l56"), result.getValue(null, "l56", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiVsc() {
        double sensiChange = 10e-4;
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + sensiChange);
        network1.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network.getLine(line).getTerminal1().getP() - network1.getLine(line).getTerminal1().getP()) / sensiChange
            ));

        ContingencyContext contingencyContext = ContingencyContext.all();
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23"),
            SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);

        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getValue(null, "l12", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getValue(null, "l13", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getValue(null, "l23", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcInjectionNotFound() {
        testHvdcInjectionNotFound(true);
    }

    @Test
    void testBalanceTypeNotSupported() {
        // test injection increase on loads
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        List<SensitivityFactor> factors =  createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        CompletionException exception = assertThrows(CompletionException.class, () -> sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factors, Collections.emptyList(), Collections.emptyList(), sensiParameters));
        assertTrue(exception.getCause() instanceof UnsupportedOperationException);
        assertEquals("Unsupported balance type mode: PROPORTIONAL_TO_CONFORM_LOAD", exception.getCause().getMessage());
    }

    @Test
    void testPhaseShifter() {
        Network network = PhaseShifterTestCaseFactory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL2_0");
        Branch<?> l1 = network.getBranch("L1");
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        List<SensitivityFactor> factors = Collections.singletonList(createBranchFlowPerPSTAngle(l1.getId(), ps1.getId()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(-6.3d, getValue(result, "PS1", "L1"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testAdditionalFactors() {
        // test injection increase on loads
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        Map<String, Double> functionReferenceByLine = new HashMap<>();
        for (Line line : network.getLines()) {
            functionReferenceByLine.put(line.getId(), line.getTerminal1().getP());
        }
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = new ArrayList<>();
        factors.add(createBranchFlowPerInjectionIncrease("l12", "g1"));
        factors.add(createBranchFlowPerInjectionIncrease("l13", "g2"));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(2, result.getValues().size());
        assertEquals(0.325d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(functionReferenceByLine.get("l12"), getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(functionReferenceByLine.get("l13"), getFunctionReference(result, "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testInjectionNotFoundAdditionalFactor() {
        testInjectionNotFoundAdditionalFactor(true);
    }

    @Test
    void testIntensityCrash() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = network.getBranchStream()
            .map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Only variables of type TRANSFORMER_PHASE or INJECTION_ACTIVE_POWER, and functions of type BRANCH_ACTIVE_POWER are yet supported in DC", e.getCause().getMessage());
    }

    @Test
    void testBranchFunctionOutsideMainComponent() {
        testBranchFunctionOutsideMainComponent(true);
    }

    @Test
    void testInjectionOutsideMainComponent() {
        testInjectionOutsideMainComponent(true);
    }

    @Test
    void testPhaseShifterOutsideMainComponent() {
        testPhaseShifterOutsideMainComponent(true);
    }

    @Test
    void testGlskOutsideMainComponent() {
        testGlskOutsideMainComponent(true);
    }

    @Test
    void testGlskAndLineOutsideMainComponent() {
        testGlskAndLineOutsideMainComponent(true);
    }

    @Test
    void testGlskPartiallyOutsideMainComponent() {
        testGlskPartiallyOutsideMainComponent(true);
    }

    @Test
    void testInjectionNotFound() {
        testInjectionNotFound(true);
    }

    @Test
    void testBranchNotFound() {
        testBranchNotFound(true);
    }

    @Test
    void testEmptyFactors() {
        testEmptyFactors(true);
    }

    @Test
    void testGlskNotFound() {
        testGlskInjectionNotFound(true);
    }

    @Test
    void testDanglingLineSensi() {
        Network network = DanglingLineFactory.createWithLoad();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "dl1"));
        // dangling line is connected
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(-0.812d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);

        // dangling line is connected on base case but will be disconnected by a contingency => 0
        List<Contingency> contingencies = List.of(new Contingency("c", new DanglingLineContingency("dl1")));
        result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(-0.812d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("c", "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);

        // dangling line is disconnected on base case => 0
        network.getDanglingLine("dl1").getTerminal().disconnect();
        result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(0d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnOpenLine() {
        Network network = NodeBreakerNetworkFactory.create();
        List<Contingency> contingencies = List.of(
            new Contingency("c1", new BranchContingency("L1"))
        );

        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSensitivityAnalysis(network, contingencies);
        assertEquals(1, propagatedContingencies.size());
    }
}
