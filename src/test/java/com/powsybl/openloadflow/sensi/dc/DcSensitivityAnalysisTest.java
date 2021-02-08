/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.dc;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.EmptyContingencyListProvider;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
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
                factorsProvider, new EmptyContingencyListProvider(), sensiParameters, LocalComputationManager.getDefault()).join();
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
                factorsProvider, new EmptyContingencyListProvider(), sensiParameters, LocalComputationManager.getDefault()).join();
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
                factorsProvider, new EmptyContingencyListProvider(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(-1d, getValue(result, "cs2", "l12"), LoadFlowAssert.DELTA_POWER);
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
                factorsProvider, new EmptyContingencyListProvider(), sensiParameters, LocalComputationManager.getDefault()).join());
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(-6.3d, getValue(result, "PS1", "L1"), LoadFlowAssert.DELTA_POWER);
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
}
