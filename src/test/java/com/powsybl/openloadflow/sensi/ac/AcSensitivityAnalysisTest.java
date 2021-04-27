/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.sensi.SensitivityFactorReader;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.BranchIntensityPerPSTAngle;
import com.powsybl.sensitivity.factors.BusVoltagePerTargetV;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BusVoltage;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import com.powsybl.sensitivity.factors.variables.TargetVoltage;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
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
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
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
    void testBusVoltagePerTargetVRemoteControl() {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("g1", "g1", "g1");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
                .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
                .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(4, result.getSensitivityValues().size());
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

        TargetVoltage targetVoltage = new TargetVoltage("g2", "g2", "g2");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
                .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
                .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(4, result.getSensitivityValues().size());
        assertEquals(0d, getValue(result, "g2", "b1"), LoadFlowAssert.DELTA_V); // no impact on a pv
        assertEquals(1d,  getValue(result, "g2", "b2"), LoadFlowAssert.DELTA_V); // 1 on itself
        assertEquals(0.3423d,  getValue(result, "g2", "b3"), LoadFlowAssert.DELTA_V); // value obtained by running two loadflow with a very small difference on targetV for bus2
        assertEquals(0d,  getValue(result, "g2", "b4"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTargetVTwt() {
        Network network = FourBusNetworkFactory.createWithTransfoRatioChanger();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("l23", "l23", "l23");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
            .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join());

        assertTrue(e.getCause() instanceof NotImplementedException);

        assertEquals("[l23] Bus target voltage on two windings transformer is not managed yet", e.getCause().getMessage());
    }

    @Test
    void testBusVoltagePerTargetVVsc() {
        Network network = HvdcNetworkFactory.createVsc();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        TargetVoltage targetVoltage = new TargetVoltage("cs2", "cs2", "cs2");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
            .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(0d, getValue(result, "cs2", "b1"), LoadFlowAssert.DELTA_V);
        assertEquals(1d, getValue(result, "cs2", "b2"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testBusVoltagePerTarget3wt() {
        Network network = T3wtFactory.createWithRatioChanger();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "vl1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("3wt", "3wt", "3wt");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
            .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join());

        assertTrue(e.getCause() instanceof NotImplementedException);

        assertEquals("[3wt] Bus target voltage on three windings transformer is not managed yet", e.getCause().getMessage());
    }

    @Test
    void testBusVoltagePerTargetVFunctionRef() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("g2", "g2", "g2");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
            .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();
        runLf(network, sensiParameters.getLoadFlowParameters());
        Function<String, Double> getV = busId -> network.getBusView().getBus(busId).getV();
        assertEquals(getV.apply("b1_vl_0"), getFunctionReference(result, "b1"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("b2_vl_0"), getFunctionReference(result, "b2"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("b3_vl_0"), getFunctionReference(result, "b3"), LoadFlowAssert.DELTA_V);
        assertEquals(getV.apply("b4_vl_0"), getFunctionReference(result, "b4"), LoadFlowAssert.DELTA_V);
    }

    @Test
    void testAdditionnalFactors() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        runLf(network, sensiParameters.getLoadFlowParameters());
        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network) {
                return network.getBranchStream()
                    .map(AcSensitivityAnalysisTest::createBranchFlow)
                    .map(branchFlow -> new BranchFlowPerPSTAngle(branchFlow, new PhaseTapChangerAngle("l23", "l23", "l23"))).collect(Collectors.toList());
            }
        };

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
    void testInjectionNotFoundAdditionalFactor() {
        testInjectionNotFoundAdditionalFactor(false);
    }

    @Test
    void testTargetVOnPqNode() {
        // asking a target v on a load should crash
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("d3", "d3", "d3");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
            .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join());

        assertTrue(e.getCause() instanceof PowsyblException);

        assertEquals("Regulating terminal for 'd3' not found", e.getCause().getMessage());
    }

    @Test
    void testTargetVOnAbsentTerminal() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("a", "a", "a");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
            .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join());

        assertTrue(e.getCause() instanceof PowsyblException);

        assertEquals("Regulating terminal for 'a' not found", e.getCause().getMessage());
    }

    @Test
    void testTargetVOnNotRegulatingTwt() {
        Network network = FourBusNetworkFactory.createWithTransfoCompensed();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("l23", "l23", "l23");
        SensitivityFactorsProvider factorsProvider = n -> network.getBusBreakerView().getBusStream()
            .map(bus -> new BusVoltage(bus.getId(), bus.getId(), new IdBasedBusRef(bus.getId())))
            .map(busVoltage -> new BusVoltagePerTargetV(busVoltage, targetVoltage))
            .collect(Collectors.toList());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join());

        assertTrue(e.getCause() instanceof PowsyblException);

        assertEquals("Regulating terminal for 'l23' not found", e.getCause().getMessage());
    }

    @Test
    void testBusVoltageOnAbsentBus() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TargetVoltage targetVoltage = new TargetVoltage("g2", "g2", "g2");
        BusVoltage busVoltage = new BusVoltage("id", "id", new IdBasedBusRef("id"));
        SensitivityFactorsProvider factorsProvider = n -> Collections.singletonList(new BusVoltagePerTargetV(busVoltage, targetVoltage));
        CompletionException e = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join());

        assertTrue(e.getCause() instanceof PowsyblException);

        assertEquals("The bus ref for 'id' cannot be resolved.", e.getCause().getMessage());
    }

    @Test
    void testHvdcSensi() {
        double sensiChange = 10e-4;
        // test active power setpoint increase on an HVDC line
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusSelector(new MostMeshedSlackBusSelector());
        List<Pair<String, String>> variableAndFunction = List.of(
            Pair.of("hvdc34", "l12"),
            Pair.of("hvdc34", "l13"),
            Pair.of("hvdc34", "l23")
        );
        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + sensiChange);

        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network.getLine(line).getTerminal1().getP() - network1.getLine(line).getTerminal1().getP()) / sensiChange
            ));

        HvdcWriter hvdcWriter = HvdcWriter.create();
        SensitivityFactorReader reader = createHvdcReader(variableAndFunction);
        sensiProvider.run(HvdcNetworkFactory.createNetworkWithGenerators2(), VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, reader, hvdcWriter);

        assertEquals(loadFlowDiff.get("l12"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l12")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l13")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l23")), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithLCCs() {
        double sensiChange = 10e-4;
        // test active power setpoint increase on a HVDC line
        // FIXME
        // Note that in case of LCC converter stations, in AC, an increase of the setpoint of the HDVC line is not equivalent to
        // running two LFs and comparing the differences as we don't change Q at LCCs when we change P.
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusSelector(new MostMeshedSlackBusSelector());
        List<Pair<String, String>> variableAndFunction = List.of(
                Pair.of("hvdc34", "l12"),
                Pair.of("hvdc34", "l13"),
                Pair.of("hvdc34", "l23")
        );
        HvdcWriter hvdcWriter = HvdcWriter.create();
        SensitivityFactorReader reader = createHvdcReader(variableAndFunction);
        sensiProvider.run(HvdcNetworkFactory.createNetworkWithGenerators(), VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
                sensiParameters, reader, hvdcWriter);

        assertEquals(-0.346002, hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l12")), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.346002, hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l13")), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.642998, hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l23")), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithBothSides() {
        double sensiChange = 10e-4;
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", false);

        List<Pair<String, String>> variableAndFunction = List.of(
            Pair.of("hvdc34", "l12"),
            Pair.of("hvdc34", "l13"),
            Pair.of("hvdc34", "l23"),
            Pair.of("hvdc34", "l25"),
            Pair.of("hvdc34", "l45"),
            Pair.of("hvdc34", "l46"),
            Pair.of("hvdc34", "l56")
        );
        runLf(network, sensiParameters.getLoadFlowParameters());
        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators2();
        network1.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + sensiChange);
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network.getLine(line).getTerminal1().getP() - network1.getLine(line).getTerminal1().getP()) / sensiChange
            ));

        HvdcWriter hvdcWriter = HvdcWriter.create();
        SensitivityFactorReader reader = createHvdcReader(variableAndFunction);
        sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, reader, hvdcWriter);

        assertEquals(loadFlowDiff.get("l12"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l12")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l13")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l23")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l25"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l25")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l45"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l45")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l46"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l46")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l56"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l56")), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiWithBothSidesDistributed() {
        double sensiChange = 10e-4;
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.01);

        List<Pair<String, String>> variableAndFunction = List.of(
            Pair.of("hvdc34", "l12"),
            Pair.of("hvdc34", "l13"),
            Pair.of("hvdc34", "l23"),
            Pair.of("hvdc34", "l25"),
            Pair.of("hvdc34", "l45"),
            Pair.of("hvdc34", "l46"),
            Pair.of("hvdc34", "l56")
        );
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
        HvdcWriter hvdcWriter = HvdcWriter.create();
        SensitivityFactorReader reader = createHvdcReader(variableAndFunction);
        sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, Collections.emptyList(),
            sensiParameters, reader, hvdcWriter);

        assertEquals(loadFlowDiff.get("l12"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l12")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l13")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l23")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l25"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l25")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l45"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l45")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l46"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l46")), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l56"), hvdcWriter.getSensitivityValue(Pair.of("hvdc34", "l56")), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcInjectionNotFound() {
        testHvdcInjectionNotFound(false);
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
