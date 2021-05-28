/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.DanglingLineContingency;
import com.powsybl.contingency.LineContingency;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.DanglingLineFactory;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.sensi.*;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.BranchIntensityPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(2, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(15, result.getSensitivityValues().size());
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
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(15, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(10, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(10, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(15, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(10, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g1")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(5, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(10, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(15, result.getSensitivityValues().size());
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

        Map<String, Float> glskMap = new HashMap<>();
        glskMap.put("d2", 30f);
        glskMap.put("g2", 10f);
        glskMap.put("d3", 50f);
        glskMap.put("g1", 10f);
        LinearGlsk linearGlsk = new LinearGlsk("glsk", "glsk", glskMap);

        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream().map(branch -> new BranchFlowPerLinearGlsk(new BranchFlow(branch.getId(), branch.getId(), branch.getId()), linearGlsk)).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(5, result.getSensitivityValues().size());
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

        Map<String, Float> glskMap = new HashMap<>();
        glskMap.put("d2", 30f);
        glskMap.put("g2", 10f);
        glskMap.put("d3", 50f);
        glskMap.put("g1", 10f);
        LinearGlsk linearGlsk = new LinearGlsk("glsk", "glsk", glskMap);

        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream().map(branch -> new BranchFlowPerLinearGlsk(new BranchFlow(branch.getId(), branch.getId(), branch.getId()), linearGlsk)).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(5, result.getSensitivityValues().size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(0.0d, getValue(result, "d2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testVscInjection() {
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createVsc();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0", false);
        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
                .map(branch -> new BranchFlowPerInjectionIncrease(createBranchFlow(branch), createInjectionIncrease(network.getVscConverterStation("cs2"))))
                .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(-1d, getValue(result, "cs2", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLccInjection() {
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createLcc();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0", false);
        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
                .map(branch -> new BranchFlowPerInjectionIncrease(createBranchFlow(branch), createInjectionIncrease(network.getLccConverterStation("cs2"))))
                .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
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
        List<SensitivityFactor2> factors = SensitivityFactor2.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23"),
                SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);
        SensitivityAnalysisResult2 result = sensiProvider.run(network, Collections.emptyList(), Collections.emptyList(), sensiParameters, factors);
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
        List<SensitivityFactor2> factors = SensitivityFactor2.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
            SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);
        SensitivityAnalysisResult2 result = sensiProvider.run(network, Collections.emptyList(), Collections.emptyList(), sensiParameters, factors);

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
        List<SensitivityFactor2> factors = SensitivityFactor2.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
            SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);

        SensitivityAnalysisResult2 result = sensiProvider.run(network, Collections.emptyList(), Collections.emptyList(), sensiParameters, factors);

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
        List<SensitivityFactor2> factors = SensitivityFactor2.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23"),
            SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);

        SensitivityAnalysisResult2 result = sensiProvider.run(network, Collections.emptyList(), Collections.emptyList(), sensiParameters, factors);

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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        CompletionException exception = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join());
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
        SensitivityFactorsProvider factorsProvider
            = n -> Collections.singletonList(new BranchFlowPerPSTAngle(createBranchFlow(l1),
            new PhaseTapChangerAngle(ps1.getId(), ps1.getNameOrId(), ps1.getId())));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();
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
        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.singletonList(new BranchFlowPerInjectionIncrease(
                    new BranchFlow("l12", "l12", "l12"),
                    new InjectionIncrease("g1", "g1", "g1")
                ));
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network) {
                return Collections.singletonList(new BranchFlowPerInjectionIncrease(
                    new BranchFlow("l13", "l13", "l13"),
                    new InjectionIncrease("g2", "g2", "g2")
                ));
            }
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(2, result.getSensitivityValues().size());
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
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
            .map(AbstractSensitivityAnalysisTest::createBranchIntensity)
            .map(branchIntensity -> new BranchIntensityPerPSTAngle(branchIntensity, new PhaseTapChangerAngle("l23", "l23", "l23"))).collect(Collectors.toList());
        CompletableFuture<SensitivityAnalysisResult> result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault());

        CompletionException e = assertThrows(CompletionException.class, () -> result.join());
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
    void testOldApiAdapter() {
        Network network = PhaseShifterTestCaseFactory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL2_0");
        List<Contingency> contingencies = List.of(new Contingency("def", new LineContingency("L1")));
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("set", List.of(new WeightedSensitivityVariable("G1", 100))));
        List<SensitivityFactor2> factors = List.of(new SensitivityFactor2(SensitivityFunctionType.BRANCH_ACTIVE_POWER, "L1",
                SensitivityVariableType.TRANSFORMER_PHASE, "PS1",
                false, ContingencyContext.none()),
            new SensitivityFactor2(SensitivityFunctionType.BRANCH_ACTIVE_POWER, "L1",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, "set",
                true, ContingencyContext.all()));
        SensitivityFactorsProviderAdapter factorsProvider = new SensitivityFactorsProviderAdapter(factors, variableSets);
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        List<SensitivityValue2> values = factorsProvider.getValues(result);
        assertEquals(3, values.size());
        assertEquals(0.5d, values.get(0).getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50, values.get(0).getFunctionReference(), LoadFlowAssert.DELTA_POWER);
        assertNull(values.get(0).getContingencyId());
        assertEquals(-6.3d, values.get(1).getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(50, values.get(1).getFunctionReference(), LoadFlowAssert.DELTA_POWER);
        assertNull(values.get(1).getContingencyId());
        assertEquals(0d, values.get(2).getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, values.get(2).getFunctionReference(), LoadFlowAssert.DELTA_POWER);
        assertNotNull(values.get(2).getContingencyId());
    }

    @Test
    void testDanglingLineSensi() {
        Network network = DanglingLineFactory.createWithLoad();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");
        List<SensitivityFactor2> factors = List.of(new SensitivityFactor2(SensitivityFunctionType.BRANCH_ACTIVE_POWER, "l1",
            SensitivityVariableType.INJECTION_ACTIVE_POWER, "dl1",
            false, ContingencyContext.all()));
        // dangling line is connected
        SensitivityAnalysisResult2 result = sensiProvider.run(network, Collections.emptyList(), Collections.emptyList(), sensiParameters, factors);
        assertEquals(-0.812d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);

        // dangling line is connected on base case but will be disconnected by a contingency => 0
        List<Contingency> contingencies = List.of(new Contingency("c", new DanglingLineContingency("dl1")));
        result = sensiProvider.run(network, contingencies, Collections.emptyList(), sensiParameters, factors);
        assertEquals(-0.812d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("c", "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);

        // dangling line is disconnected on base case => 0
        network.getDanglingLine("dl1").getTerminal().disconnect();
        result = sensiProvider.run(network, Collections.emptyList(), Collections.emptyList(), sensiParameters, factors);
        assertEquals(0d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);
    }
}
