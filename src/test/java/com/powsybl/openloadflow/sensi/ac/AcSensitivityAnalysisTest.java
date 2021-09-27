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
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
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
import java.util.concurrent.CompletableFuture;
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
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(2, result.getValues().size());
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

        List<SensitivityFactor> factors = createFactorMatrix(Collections.singletonList(network.getGenerator("g4")),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

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

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());

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

        List<WeightedSensitivityVariable> variables = new ArrayList<>();
        variables.add(new WeightedSensitivityVariable("g1", 0.25f));
        variables.add(new WeightedSensitivityVariable("g4", 0.25f));
        variables.add(new WeightedSensitivityVariable("d2", 0.5f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream()
            .map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk"))
            .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), variableSets, sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(-0.044d, getValue(result, "glsk", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.069d, getValue(result, "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.031d, getValue(result, "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.006d, getValue(result, "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.037d, getValue(result, "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesWithTransfoInjection() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());

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
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBranchStream()
            .map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

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

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(15, result.getValues().size());

        assertEquals(0.2512d, getFunctionReference(result, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.2512d, getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.2512d, getFunctionReference(result, "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2512d, getFunctionReference(result, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4976d, getFunctionReference(result, "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesFunctionReferenceWithTransformer() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        List<SensitivityFactor> factors = network.getBranchStream()
            .map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(0.2296d, getFunctionReference(result, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3154d, getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2296d, getFunctionReference(result, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4549d, getFunctionReference(result, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.3154d, getFunctionReference(result, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void test4busesPhaseShiftIntensity() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBranchStream()
            .map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(37.6799d, getValue(result, "l23", "l23"), LoadFlowAssert.DELTA_I);
        assertEquals(-12.5507d, getValue(result, "l23", "l14"), LoadFlowAssert.DELTA_I);
        assertEquals(37.3710d, getValue(result, "l23", "l12"), LoadFlowAssert.DELTA_I);
        assertEquals(-12.6565d, getValue(result, "l23", "l34"), LoadFlowAssert.DELTA_I);
        assertEquals(-25.0905d, getValue(result, "l23", "l13"), LoadFlowAssert.DELTA_I);
    }

    @Test
    void test4busesPhaseShiftIntensityFunctionReference() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBranchStream()
            .map(branch -> createBranchIntensityPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());
        assertEquals(766.4654d, getFunctionReference(result, "l23"), LoadFlowAssert.DELTA_I);
        assertEquals(132.5631d, getFunctionReference(result, "l14"), LoadFlowAssert.DELTA_I);
        assertEquals(182.1272d, getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_I);
        assertEquals(716.5036d, getFunctionReference(result, "l34"), LoadFlowAssert.DELTA_I);
        assertEquals(847.8542d, getFunctionReference(result, "l13"), LoadFlowAssert.DELTA_I);
    }

    @Test
    void testBusVoltagePerTargetVRemoteControl() {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "g1"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0.04997d, getValue(result, "g1", "b1"), LoadFlowAssert.DELTA_V);
        assertEquals(0.0507d,  getValue(result, "g1", "b2"), LoadFlowAssert.DELTA_V);
        assertEquals(0.0525d,  getValue(result, "g1", "b3"), LoadFlowAssert.DELTA_V);
        assertEquals(1d,  getValue(result, "g1", "b4"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetV() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "g2"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0d, getValue(result, "g2", "b1"), LoadFlowAssert.DELTA_V); // no impact on a pv
        assertEquals(1d,  getValue(result, "g2", "b2"), LoadFlowAssert.DELTA_V); // 1 on itself
        assertEquals(0.3423d,  getValue(result, "g2", "b3"), LoadFlowAssert.DELTA_V); // value obtained by running two loadflow with a very small difference on targetV for bus2
        assertEquals(0d,  getValue(result, "g2", "b4"), LoadFlowAssert.DELTA_V);
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
                .setVoltageLevel1("VL_3")
                .setVoltageLevel2("VL_4")
                .setBus1("BUS_3")
                .setBus2("BUS_4")
                .setR(1.05)
                .setX(10.0)
                .setG1(0.0000005)
                .setG2(0.)
                .setB1(0.)
                .setB2(0.)
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

        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0d, getValue(result, "T2wT", "BUS_1"), LoadFlowAssert.DELTA_V);
        assertEquals(0.035205d,  getValue(result, "T2wT", "BUS_2"), LoadFlowAssert.DELTA_V);
        assertEquals(1d,  getValue(result, "T2wT", "BUS_3"), LoadFlowAssert.DELTA_V);
        assertEquals(1.055117d,  getValue(result, "T2wT", "BUS_4"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetVVsc() {
        Network network = HvdcNetworkFactory.createVsc();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "cs2"))
                .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(0d, getValue(result, "cs2", "b1"), LoadFlowAssert.DELTA_V);
        assertEquals(1d, getValue(result, "cs2", "b2"), LoadFlowAssert.DELTA_V);
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
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getValues().size());
        assertEquals(0d, getValue(result, "T3wT", "BUS_1"), LoadFlowAssert.DELTA_V);
        assertEquals(0d,  getValue(result, "T3wT", "BUS_2"), LoadFlowAssert.DELTA_V);
        assertEquals(1d,  getValue(result, "T3wT", "BUS_3"), LoadFlowAssert.DELTA_V);
        assertEquals(0d,  getValue(result, "T3wT", "BUS_4"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetVFunctionRef() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLGEN_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = network.getBusBreakerView().getBusStream()
                .map(bus -> createBusVoltagePerTargetV(bus.getId(), "GEN"))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        runLf(network, sensiParameters.getLoadFlowParameters());
        Function<String, Double> getV = busId -> network.getBusView().getBus(busId).getV();
        assertEquals(getV.apply("VLGEN_0"), getFunctionReference(result, "NGEN"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLHV1_0"), getFunctionReference(result, "NHV1"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLHV2_0"), getFunctionReference(result, "NHV2"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("VLLOAD_0"), getFunctionReference(result, "NLOAD"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testAdditionnalFactors() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getValues().size());

        assertEquals(0.2296d, getFunctionReference(result, "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3154d, getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.2296d, getFunctionReference(result, "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4549d, getFunctionReference(result, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.3154d, getFunctionReference(result, "l23"), LoadFlowAssert.DELTA_POWER);
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
        CompletableFuture<SensitivityAnalysisResult> task = sensiRunner.runAsync(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> task.join());

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
        CompletableFuture<SensitivityAnalysisResult> task = sensiRunner.runAsync(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> task.join());

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
        CompletableFuture<SensitivityAnalysisResult> task = sensiRunner.runAsync(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> task.join());

        assertTrue(e.getCause() instanceof PowsyblException);

        assertEquals("Regulating terminal for 'l23' not found", e.getCause().getMessage());
    }

    @Test
    void testBusVoltageOnAbsentBus() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = Collections.singletonList(createBusVoltagePerTargetV("id", "g2"));

        CompletableFuture<SensitivityAnalysisResult> task = sensiRunner.runAsync(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        CompletionException e = assertThrows(CompletionException.class, () -> task.join());

        assertTrue(e.getCause() instanceof PowsyblException);

        assertEquals("The bus ref for 'id' cannot be resolved.", e.getCause().getMessage());
    }

    @Test
    void testHvdcSensi() {
        double sensiChange = 10e-4;
        // test active power setpoint increase on an HVDC line
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
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
        SensitivityAnalysisResult result = sensiRunner.run(HvdcNetworkFactory.createNetworkWithGenerators2(), VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getValue(null, "l12", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getValue(null, "l13", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getValue(null, "l23", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithLCCs() {
        // test active power setpoint increase on a HVDC line
        // FIXME
        // Note that in case of LCC converter stations, in AC, an increase of the setpoint of the HDVC line is not equivalent to
        // running two LFs and comparing the differences as we don't change Q at LCCs when we change P.
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        ContingencyContext contingencyContext = ContingencyContext.all();
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23"),
                                                                           SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);

        SensitivityAnalysisResult result = sensiRunner.run(HvdcNetworkFactory.createNetworkWithGenerators(), VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(-0.346002, result.getValue(null, "l12", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.346002, result.getValue(null, "l13", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.642998, result.getValue(null, "l23", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithBothSides() {
        double sensiChange = 10e-4;
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
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
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.01);

        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
        network1.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
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
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(1, result.getValues().size());
        assertEquals(0d, getValue(result, "g4", "b1"));
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
        Network network = DanglingLineFactory.createWithLoad();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0");
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "dl1"));

        // dangling line is connected
        SensitivityAnalysisResult result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(-0.903d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);

        // dangling line is connected on base case but will be disconnected by a contingency => 0
        List<Contingency> contingencies = List.of(new Contingency("c", new DanglingLineContingency("dl1")));
        result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(-0.903d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("c", "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);

        // dangling line is disconnected on base case => 0
        network.getDanglingLine("dl1").getTerminal().disconnect();
        result = sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);
        assertEquals(0d, result.getValue(null, "l1", "dl1").getValue(), LoadFlowAssert.DELTA_POWER);
    }
}
