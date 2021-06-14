/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.*;
import com.powsybl.iidm.network.IdBasedBusRef;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.*;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.BusVoltagePerTargetV;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BusVoltage;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import com.powsybl.sensitivity.factors.variables.TargetVoltage;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcSensitivityAnalysisContingenciesTest extends AbstractSensitivityAnalysisTest {

    @Test
    void test4BusesSensi() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        List<Contingency> contingencyList = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(15, result.getSensitivityValues().size());
        List<SensitivityValue> contingencyResult = result.getSensitivityValuesContingencies().get("l23");
        assertEquals(-0.5409d, getContingencyValue(contingencyResult, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, getContingencyValue(contingencyResult, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyResult, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, getContingencyValue(contingencyResult, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, getContingencyValue(contingencyResult, "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, getContingencyValue(contingencyResult, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, getContingencyValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getContingencyValue(contingencyResult, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getContingencyValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, getContingencyValue(contingencyResult, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6000d, getContingencyValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getContingencyValue(contingencyResult, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getContingencyValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4BusesSensiAdditionalFactor() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network, String contingencyId) {
                return createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                    network.getBranchStream().collect(Collectors.toList()));
            }
        };
        List<Contingency> contingencyList = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        List<SensitivityValue> contingencyResult = result.getSensitivityValuesContingencies().get("l23");
        assertEquals(15, contingencyResult.size());

        assertEquals(-0.5409d, getContingencyValue(contingencyResult, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, getContingencyValue(contingencyResult, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyResult, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, getContingencyValue(contingencyResult, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, getContingencyValue(contingencyResult, "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, getContingencyValue(contingencyResult, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, getContingencyValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getContingencyValue(contingencyResult, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getContingencyValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, getContingencyValue(contingencyResult, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6000d, getContingencyValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getContingencyValue(contingencyResult, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getContingencyValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4BusesFunctionReference() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        List<Contingency> contingencyList = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        List<SensitivityValue> contingencyResult = result.getSensitivityValuesContingencies().get("l23");

        assertEquals(0.6761d, getFunctionReference(contingencyResult, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.0d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.676d, getFunctionReference(contingencyResult, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.324d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesLoosingATransfo() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        List<Contingency> contingencyList = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();
        assertEquals(15, result.getSensitivityValues().size());
        List<SensitivityValue> contingencyResult = result.getSensitivityValuesContingencies().get("l23");
        assertEquals(-0.5409d, getValue(contingencyResult, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.400d, getValue(contingencyResult, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, getValue(contingencyResult, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.2591d, getValue(contingencyResult, "g4", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, getValue(contingencyResult, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4000d, getValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getValue(contingencyResult, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1352d, getValue(contingencyResult, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.600d, getValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getValue(contingencyResult, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesLoosingATransfoFunctionRef() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        List<Contingency> contingencyList = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();
        assertEquals(15, result.getSensitivityValues().size());
        List<SensitivityValue> contingencyResult = result.getSensitivityValuesContingencies().get("l23");
        assertEquals(0.6761d, getFunctionReference(contingencyResult, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.0d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.676d, getFunctionReference(contingencyResult, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.324d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesPhaseShift() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
            .map(AcSensitivityAnalysisContingenciesTest::createBranchFlow)
            .map(branchFlow -> new BranchFlowPerPSTAngle(branchFlow, new PhaseTapChangerAngle("l23", "l23", "l23"))).collect(Collectors.toList());
        List<Contingency> contingencyList = Collections.singletonList(new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(5, result.getSensitivityValues().size());
        List<SensitivityValue> contingencyValues = result.getSensitivityValuesContingencies().get("l34");
        assertEquals(0d, getContingencyValue(contingencyValues, "l23", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0564d, getContingencyValue(contingencyValues, "l23", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "l23", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.0564d, getContingencyValue(contingencyValues, "l23", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0564d, getContingencyValue(contingencyValues, "l23", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesFunctionReferenceWithTransformer() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
            .map(AcSensitivityAnalysisContingenciesTest::createBranchFlow)
            .map(branchFlow -> new BranchFlowPerPSTAngle(branchFlow, new PhaseTapChangerAngle("l23", "l23", "l23"))).collect(Collectors.toList());
        List<Contingency> contingencyList = Collections.singletonList(new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(5, result.getSensitivityValues().size());
        List<SensitivityValue> contingencyValues = result.getSensitivityValuesContingencies().get("l34");
        assertEquals(-1.0d, getFunctionReference(contingencyValues, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.7319d, getFunctionReference(contingencyValues, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyValues, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.268d, getFunctionReference(contingencyValues, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.7319d, getFunctionReference(contingencyValues, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesFunctionReferenceWithTransformerInAdditionalFactors() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network, String contingencyId) {
                return network.getBranchStream()
                    .map(AcSensitivityAnalysisContingenciesTest::createBranchFlow)
                    .map(branchFlow -> new BranchFlowPerPSTAngle(branchFlow, new PhaseTapChangerAngle("l23", "l23", "l23"))).collect(Collectors.toList());
            }
        };

        List<Contingency> contingencyList = Collections.singletonList(new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        List<SensitivityValue> contingencyValues = result.getSensitivityValuesContingencies().get("l34");
        assertEquals(5, contingencyValues.size());
        assertEquals(-1.0d, getFunctionReference(contingencyValues, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.7319d, getFunctionReference(contingencyValues, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyValues, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.268d, getFunctionReference(contingencyValues, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.7319d, getFunctionReference(contingencyValues, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testMultipleContingencies() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(Collections.singletonList(network.getGenerator("g2")),
            network.getBranchStream().collect(Collectors.toList()));
        List<Contingency> contingencyList = List.of(new Contingency("l23", new BranchContingency("l23")), new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        List<SensitivityValue> l34values = result.getSensitivityValuesContingencies().get("l34");
        List<SensitivityValue> l23values = result.getSensitivityValuesContingencies().get("l23");
        assertEquals(0.1352d, getValue(l23values, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.600d, getValue(l23values, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(l23values, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getValue(l23values, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0648d, getValue(l23values, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.2d, getValue(l34values, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.4056d, getValue(l34values, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1944d, getValue(l34values, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(l34values, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1944d, getValue(l34values, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testMultipleContingenciesAdditionalFactors() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());
        InjectionIncrease injectionIncrease = new InjectionIncrease("g2", "g2", "g2");
        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.singletonList(new BranchFlowPerInjectionIncrease(
                    new BranchFlow("l14", "l14", "l14"),
                    injectionIncrease
                ));
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network, String contingencyId) {
                List<SensitivityFactor> factors = new ArrayList<>();
                if (contingencyId.equals("l23")) {
                    factors.add(new BranchFlowPerInjectionIncrease(new BranchFlow("l23", "l23", "l23"), injectionIncrease));
                    factors.add(new BranchFlowPerInjectionIncrease(new BranchFlow("l12", "l12", "l12"), injectionIncrease));
                } else if (contingencyId.equals("l34")) {
                    factors.add(new BranchFlowPerInjectionIncrease(new BranchFlow("l23", "l23", "l23"), injectionIncrease));
                    factors.add(new BranchFlowPerInjectionIncrease(new BranchFlow("l34", "l34", "l34"), injectionIncrease));
                    factors.add(new BranchFlowPerInjectionIncrease(new BranchFlow("l12", "l12", "l12"), injectionIncrease));
                }
                return factors;
            }
        };
        List<Contingency> contingencyList = List.of(new Contingency("l23", new BranchContingency("l23")), new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        List<SensitivityValue> l34values = result.getSensitivityValuesContingencies().get("l34");
        List<SensitivityValue> l23values = result.getSensitivityValuesContingencies().get("l23");
        assertEquals(4, l34values.size());
        assertEquals(3, l23values.size());

        assertEquals(0.1352d, getValue(l23values, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.600d, getValue(l23values, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(l23values, "g2", "l23"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.2d, getValue(l34values, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.4056d, getValue(l34values, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1944d, getValue(l34values, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(l34values, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyWithMultipleBranches() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(Collections.singletonList(network.getGenerator("g2")),
            network.getBranchStream().collect(Collectors.toList()));
        List<Contingency> contingencyList = List.of(new Contingency("l23+l34", new BranchContingency("l23"),  new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        List<SensitivityValue> contingencyResult = result.getSensitivityValuesContingencies().get("l23+l34");
        assertEquals(0.2d, getValue(contingencyResult, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.600d, getValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnSingleLine() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorOnOneSide();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(14, result.getSensitivityValuesContingencies().get("l34").size());
        assertEquals(-0.1324d, getContingencyValue(result, "l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2676d, getContingencyValue(result, "l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1324d, getContingencyValue(result, "l34", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1986d, getContingencyValue(result, "l34", "g3", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.4013d, getContingencyValue(result, "l34", "g3", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1986d, getContingencyValue(result, "l34", "g3", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g3", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g3", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g3", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g3", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyMultipleLinesBreaksOneContingency() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLinesWithAdditionnalGens();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<Contingency> contingencies = List.of(new Contingency("l24+l35", new BranchContingency("l24"), new BranchContingency("l35")));
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(24, result.getSensitivityValuesContingencies().get("l24+l35").size());
        List<SensitivityValue> contingencyResult = result.getSensitivityValuesContingencies().get("l24+l35");
        assertEquals(-0.1331d, getValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2669d, getValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1331d, getValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.1997d, getValue(contingencyResult, "g3", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.4003d, getValue(contingencyResult, "g3", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1997d, getValue(contingencyResult, "g3", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g3", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g3", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g3", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g3", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g3", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getValue(contingencyResult, "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g6", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g6", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getValue(contingencyResult, "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getValue(contingencyResult, "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getValue(contingencyResult, "g6", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskRescale() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        Map<String, Float> glskMap = new HashMap<>();
        glskMap.put("g2", 0.4f);
        glskMap.put("g6", 0.6f);
        LinearGlsk glsk = new LinearGlsk("glsk", "glsk", glskMap);
        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
            .map(AbstractSensitivityAnalysisTest::createBranchFlow)
            .map(branchFlow -> new BranchFlowPerLinearGlsk(branchFlow, glsk))
            .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(7, result.getSensitivityValuesContingencies().get("l34").size());
        assertEquals(-0.5d, getContingencyValue(result, "l34", "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getContingencyValue(result, "l34", "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskRescaleAdditionalFactor() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        Map<String, Float> glskMap = new HashMap<>();
        glskMap.put("g2", 0.4f);
        glskMap.put("g6", 0.6f);
        LinearGlsk glsk = new LinearGlsk("glsk", "glsk", glskMap);
        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network, String contingencyId) {
                return network.getBranchStream()
                    .map(AbstractSensitivityAnalysisTest::createBranchFlow)
                    .map(branchFlow -> new BranchFlowPerLinearGlsk(branchFlow, glsk))
                    .collect(Collectors.toList());
            }
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(7, result.getSensitivityValuesContingencies().get("l34").size());
        assertEquals(-0.5d, getContingencyValue(result, "l34", "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getContingencyValue(result, "l34", "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l56"), LoadFlowAssert.DELTA_POWER);
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
        List<String> busIds = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            busIds.add("b" + i);
        }
        List<Contingency> contingencies = Collections.singletonList(new Contingency("l45", new BranchContingency("l45")));
        TargetVoltage targetVoltage = new TargetVoltage("g2", "g2", "g2");
        SensitivityFactorsProvider factorsProvider = n -> busIds.stream()
            .map(bus -> new BusVoltage(bus, bus, new IdBasedBusRef(bus)))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        List<SensitivityValue> contingencyValue = result.getSensitivityValuesContingencies().get("l45");
        assertEquals(0.916d, getValue(contingencyValue, "g2", busIds.get(0)), LoadFlowAssert.DELTA_V); // 0 on the slack
        assertEquals(1d, getValue(contingencyValue, "g2", busIds.get(1)), LoadFlowAssert.DELTA_V); // 1 on itself
        assertEquals(0.8133d, getValue(contingencyValue, "g2", busIds.get(2)), LoadFlowAssert.DELTA_V);
        assertEquals(0.512d, getValue(contingencyValue, "g2", busIds.get(3)), LoadFlowAssert.DELTA_V);
        assertEquals(0d, getValue(contingencyValue, "g2", busIds.get(4)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0d, getValue(contingencyValue, "g2", busIds.get(5)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0d, getValue(contingencyValue, "g2", busIds.get(6)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0.209d, getValue(contingencyValue, "g2", busIds.get(7)), LoadFlowAssert.DELTA_V);
        assertEquals(0.1062d, getValue(contingencyValue, "g2", busIds.get(8)), LoadFlowAssert.DELTA_V);
        assertEquals(0d, getValue(contingencyValue, "g2", busIds.get(9)), LoadFlowAssert.DELTA_V); // no impact on a pv
    }

    @Test
    void testBusVoltagePerTargetVFunctionRef() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<String> busIds = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            busIds.add("b" + i);
        }
        List<Contingency> contingencies = Collections.singletonList(new Contingency("l45", new BranchContingency("l45")));
        TargetVoltage targetVoltage = new TargetVoltage("g2", "g2", "g2");
        SensitivityFactorsProvider factorsProvider = n -> busIds.stream()
            .map(bus -> new BusVoltage(bus, bus, new IdBasedBusRef(bus)))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        List<SensitivityValue> contingencyValue = result.getSensitivityValuesContingencies().get("l45");
        assertEquals(0.993d, getFunctionReference(contingencyValue, busIds.get(0)), LoadFlowAssert.DELTA_V);
        assertEquals(1d, getFunctionReference(contingencyValue, busIds.get(1)), LoadFlowAssert.DELTA_V);
        assertEquals(0.992d, getFunctionReference(contingencyValue, busIds.get(2)), LoadFlowAssert.DELTA_V);
        assertEquals(0.988d, getFunctionReference(contingencyValue, busIds.get(3)), LoadFlowAssert.DELTA_V);
        assertEquals(0d, getFunctionReference(contingencyValue, busIds.get(4)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0d, getFunctionReference(contingencyValue, busIds.get(5)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0d, getFunctionReference(contingencyValue, busIds.get(6)), LoadFlowAssert.DELTA_V); // disconnected
        assertEquals(0.987d, getFunctionReference(contingencyValue, busIds.get(7)), LoadFlowAssert.DELTA_V);
        assertEquals(0.989d, getFunctionReference(contingencyValue, busIds.get(8)), LoadFlowAssert.DELTA_V);
        assertEquals(1d, getFunctionReference(contingencyValue, busIds.get(9)), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testHvdcSensiRescale() {
        double sensiChange = 10e-4;
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
        network2.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + sensiChange);
        network2.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        network2.getLine("l25").getTerminal1().disconnect();
        network2.getLine("l25").getTerminal2().disconnect();
        runLf(network2, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network1.getLine(line).getTerminal1().getP() - network2.getLine(line).getTerminal1().getP()) / sensiChange
            ));

        ContingencyContext contingencyContext = ContingencyContext.createAllContingencyContext();
        List<SensitivityFactor2> factors = SensitivityFactor2.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                                                                           SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("b1_vl_0"); // the most meshed bus selected in the loadflow
        SensitivityAnalysisResult2 result = sensiProvider.run(network, Collections.singletonList(new Contingency("l25", new BranchContingency("l25"))), Collections.emptyList(),
                sensiParameters, factors);

        // FIXME
//        assertEquals(loadFlowDiff.get("l12"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l12"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l13"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l13"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l23"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l23"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(0d, hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l25"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l45"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l45"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l46"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l46"), "l25"), LoadFlowAssert.DELTA_POWER);
//        assertEquals(loadFlowDiff.get("l56"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l56"), "l25"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        SensitivityFactorsProvider factorsProvider = n -> {
            return createFactorMatrix(List.of("g1", "g2").stream().map(network::getGenerator).collect(Collectors.toList()),
                List.of("l12", "l13", "l23").stream().map(network::getBranch).collect(Collectors.toList()));
        };

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));
        CompletionException e = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join());
        assertTrue(e.getCause() instanceof NotImplementedException);
        assertEquals("Contingencies on a DC line are not yet supported in AC mode.", e.getCause().getMessage());
    }

    @Test
    void testContingencyPropagationLfSwitch() {
        Network network = NodeBreakerNetworkFactory.create3Bars();
        SensitivityAnalysisParameters sensiParameters = createParameters(false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        List<Contingency> contingencyList = Collections.singletonList(new Contingency("L2", new BranchContingency("L2")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencyList,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        //Flow is around 200 on all lines
        result.getSensitivityValues()
            .forEach(v -> assertEquals(200, v.getFunctionReference(), 5));

        // Propagating contingency on L2 encounters a coupler, which is not (yet) supported in sensitivity analysis
        assertTrue(result.getSensitivityValuesContingencies().isEmpty());
    }

    @Test
    void testDanglingLineContingencyDistributedSlackOnLoads() {
        Network network = DanglingLineFactory.createWithLoad();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl3_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        SensitivityFactorsProvider factorsProvider = n -> List.of(new BranchFlowPerInjectionIncrease(new BranchFlow("l1", "l1", "l1"),
                new InjectionIncrease("g1", "g1", "g1")));
        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(1, result.getSensitivityValues().size());
        assertEquals(0.3697, getValue(result, "g1", "l1"), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(75.272, getFunctionReference(result, "l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3695, getContingencyValue(result, "dl1", "g1", "l1"), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(36.794, getContingencyFunctionReference(result, "l1", "dl1"), LoadFlowAssert.DELTA_POWER);

        network.getDanglingLine("dl1").getTerminal().disconnect();
        Line l1 = network.getLine("l1");
        LoadFlowParameters parameters = sensiParameters.getLoadFlowParameters();
        parameters.getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001);
        runLf(network, parameters, Reporter.NO_OP);
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
        Network network = DanglingLineFactory.createWithLoad();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        SensitivityFactorsProvider factorsProvider2 = n -> List.of(new BranchFlowPerInjectionIncrease(new BranchFlow("l1", "l1", "l1"),
                new InjectionIncrease("load3", "load3", "load3")));
        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider2, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(1, result.getSensitivityValues().size());
        assertEquals(-0.3704, getValue(result, "load3", "l1"), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(75.336, getFunctionReference(result, "l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.3704, getContingencyValue(result, "dl1", "load3", "l1"), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(3.0071, getContingencyFunctionReference(result, "l1", "dl1"), LoadFlowAssert.DELTA_POWER);

        network.getDanglingLine("dl1").getTerminal().disconnect();
        Line l1 = network.getLine("l1");
        LoadFlowParameters parameters = sensiParameters.getLoadFlowParameters();
        parameters.getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001);
        runLf(network, parameters, Reporter.NO_OP);
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
        Network network = EurostagTutorialExample1Factory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLGEN_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("NHV2_NLOAD", "NHV2_NLOAD", "NHV2_NLOAD");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
                .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
                .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
                .collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("NHV1_NHV2_2", new BranchContingency("NHV1_NHV2_2")));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(4, result.getSensitivityValues().size());
        assertEquals(0d, getValue(result, "NHV2_NLOAD", "NGEN"), LoadFlowAssert.DELTA_V);
        assertEquals(0.035205d,  getValue(result, "NHV2_NLOAD", "NHV1"), LoadFlowAssert.DELTA_V);
        assertEquals(0.077194d,  getValue(result, "NHV2_NLOAD", "NHV2"), LoadFlowAssert.DELTA_V);
        assertEquals(1.0d,  getValue(result, "NHV2_NLOAD", "NLOAD"), LoadFlowAssert.DELTA_V);

        assertEquals(0d, getContingencyValue(result, "NHV1_NHV2_2", "NHV2_NLOAD", "NGEN"), LoadFlowAssert.DELTA_V);
        assertEquals(0.026329d,  getContingencyValue(result, "NHV1_NHV2_2", "NHV2_NLOAD", "NHV1"), LoadFlowAssert.DELTA_V);
        assertEquals(0.103981d,  getContingencyValue(result, "NHV1_NHV2_2", "NHV2_NLOAD", "NHV2"), LoadFlowAssert.DELTA_V);
        assertEquals(1.0d,  getContingencyValue(result, "NHV1_NHV2_2", "NHV2_NLOAD", "NLOAD"), LoadFlowAssert.DELTA_V);
    }
}
