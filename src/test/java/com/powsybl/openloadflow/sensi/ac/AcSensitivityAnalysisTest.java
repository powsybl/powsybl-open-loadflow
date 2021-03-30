/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.T3wtFactory;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.sensi.SensitivityFactorReader;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.BranchIntensityPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    void test4busesFunctionReference() {
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

        assertEquals(0.2512d, getFunctionReference(result, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2512d, getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.2512d, getFunctionReference(result, "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2512d, getFunctionReference(result, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4976d, getFunctionReference(result, "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesFunctionReferenceWithTransformer() {
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

        assertEquals(0.2296d, getFunctionReference(result, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3154d, getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2296d, getFunctionReference(result, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4549d, getFunctionReference(result, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.3154d, getFunctionReference(result, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesPhaseShiftIntensity() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
            .map(AcSensitivityAnalysisTest::createBranchIntensity)
            .map(branchIntensity -> new BranchIntensityPerPSTAngle(branchIntensity, new PhaseTapChangerAngle("l23", "l23", "l23"))).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(5, result.getSensitivityValues().size());

        assertEquals(37.6799d, getValue(result, "l23", "l23"), LoadFlowAssert.DELTA_I);
        assertEquals(-12.5507d, getValue(result, "l23", "l14"), LoadFlowAssert.DELTA_I);
        assertEquals(37.3710d, getValue(result, "l23", "l12"), LoadFlowAssert.DELTA_I);
        assertEquals(-12.6565d, getValue(result, "l23", "l34"), LoadFlowAssert.DELTA_I);
        assertEquals(-25.0905d, getValue(result, "l23", "l13"), LoadFlowAssert.DELTA_I);
    }

    @Test
    void test4busesPhaseShiftIntensityFunctionReference() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream()
            .map(AcSensitivityAnalysisTest::createBranchIntensity)
            .map(branchIntensity -> new BranchIntensityPerPSTAngle(branchIntensity, new PhaseTapChangerAngle("l23", "l23", "l23"))).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(5, result.getSensitivityValues().size());
        assertEquals(766.4654d, getFunctionReference(result, "l23"), LoadFlowAssert.DELTA_I);
        assertEquals(132.5631d, getFunctionReference(result, "l14"), LoadFlowAssert.DELTA_I);
        assertEquals(182.1272d, getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_I);
        assertEquals(716.5036d, getFunctionReference(result, "l34"), LoadFlowAssert.DELTA_I);
        assertEquals(847.8542d, getFunctionReference(result, "l13"), LoadFlowAssert.DELTA_I);
    }

    @Test
    void testBusVoltagePerTargetV() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Pair<String, String> g2b1 = Pair.of("g2", "b1_vl_0");
        Pair<String, String> g2b2 = Pair.of("g2", "b2_vl_0");
        Pair<String, String> g2b3 = Pair.of("g2", "b3_vl_0");
        Pair<String, String> g2b4 = Pair.of("g2", "b4_vl_0");
        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(g2b1, g2b2, g2b3, g2b4));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter);

        assertEquals(0d, factorWriter.getSensitivityValue(g2b1), LoadFlowAssert.DELTA_V); // no impact on a pv
        assertEquals(1d, factorWriter.getSensitivityValue(g2b2), LoadFlowAssert.DELTA_V); // 1 on itself
        assertEquals(0.3423d, factorWriter.getSensitivityValue(g2b3), LoadFlowAssert.DELTA_V); // value obtained by running two loadflow with a very small difference on targetV for bus2
        assertEquals(0d, factorWriter.getSensitivityValue(g2b4), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetVTwt() {
        Network network = FourBusNetworkFactory.createWithTransfoRatioChanger();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Pair<String, String> g2b1 = Pair.of("l23", "b1_vl_0");
        Pair<String, String> g2b2 = Pair.of("l23", "b2_vl_0");
        Pair<String, String> g2b3 = Pair.of("l23", "b3_vl_0");
        Pair<String, String> g2b4 = Pair.of("l23", "b4_vl_0");
        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(g2b1, g2b2, g2b3, g2b4));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter);

        assertEquals(0d, factorWriter.getSensitivityValue(g2b1), LoadFlowAssert.DELTA_V); // no impact on a pv
        assertEquals(1d, factorWriter.getSensitivityValue(g2b2), LoadFlowAssert.DELTA_V); // 1 on itself
        assertEquals(0.3087d, factorWriter.getSensitivityValue(g2b3), LoadFlowAssert.DELTA_V); // value obtained by running two loadflow with a very small difference on targetV for bus2
        assertEquals(0d, factorWriter.getSensitivityValue(g2b4), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetVVsc() {
        Network network = HvdcNetworkFactory.createVsc();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Pair<String, String> cs2vl1 = Pair.of("cs2", "vl1_0");
        Pair<String, String> cs2vl2 = Pair.of("cs2", "vl2_0");
        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(cs2vl1, cs2vl2));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter);

        assertEquals(0d, factorWriter.getSensitivityValue(cs2vl1), LoadFlowAssert.DELTA_V);
        assertEquals(1d, factorWriter.getSensitivityValue(cs2vl2), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTarget3wt() {
        Network network = T3wtFactory.createWithRatioChanger();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Pair<String, String> t3wtvl1 = Pair.of("3wt", "vl1_0");
        Pair<String, String> t3wtvl2 = Pair.of("3wt", "vl2_0");
        Pair<String, String> t3wtvl3 = Pair.of("3wt", "vl3_0");
        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(t3wtvl1, t3wtvl2, t3wtvl3));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        NotImplementedException e = assertThrows(NotImplementedException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter));

        assertEquals("[3wt] Bus voltage on three windings transformer is not managed yet", e.getMessage());
    }

    @Test
    void testBusVoltagePerTargetVFunctionRef() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        Pair<String, String> g2b1 = Pair.of("g2", "b1_vl_0");
        Pair<String, String> g2b2 = Pair.of("g2", "b2_vl_0");
        Pair<String, String> g2b3 = Pair.of("g2", "b3_vl_0");
        Pair<String, String> g2b4 = Pair.of("g2", "b4_vl_0");
        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(g2b1, g2b2, g2b3, g2b4));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter);
        runLf(network, sensiParameters.getLoadFlowParameters());
        Function<String, Double> getV = busId -> network.getBusView().getBus(busId).getV();
        assertEquals(getV.apply("b1_vl_0"), factorWriter.getFunctionRef(g2b1), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("b2_vl_0"), factorWriter.getFunctionRef(g2b2), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("b3_vl_0"), factorWriter.getFunctionRef(g2b3), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("b4_vl_0"), factorWriter.getFunctionRef(g2b4), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testTargetVOnPqNode() {
        // asking a target v on a load should crash
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(Pair.of("d3", "b4_vl_0")));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        PowsyblException e = assertThrows(PowsyblException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter));
        assertEquals("Regulating terminal for 'd3' not found", e.getMessage());
    }

    @Test
    void testTargetVOnAbsentTerminal() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(Pair.of("a", "b4_vl_0")));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        PowsyblException e = assertThrows(PowsyblException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter));
        assertEquals("Regulating terminal for 'a' not found", e.getMessage());
    }

    @Test
    void testTargetVOnNotRegulatingTwt() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(Pair.of("l23", "b4_vl_0")));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        PowsyblException e = assertThrows(PowsyblException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter));
        assertEquals("Regulating terminal for 'l23' not found", e.getMessage());
    }

    @Test
    void testBusVoltageOnAbsentBus() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        SensitivityFactorReader factorReader = createBusVoltageReader(List.of(Pair.of("g2", "id")));
        BusVoltageWriter factorWriter = createBusVoltageWriter();
        PowsyblException e = assertThrows(PowsyblException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, factorReader, factorWriter));
        assertEquals("Bus 'id' not found", e.getMessage());
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
}
