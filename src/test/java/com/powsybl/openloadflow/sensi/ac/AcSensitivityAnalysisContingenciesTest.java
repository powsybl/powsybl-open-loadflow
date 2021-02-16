/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.ac;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
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
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
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
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
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
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
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
    void testMultipleContingencies() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
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
    void testContingencyWithMultipleBranches() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
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
}
