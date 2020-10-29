/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.EmptyContingencyListProvider;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSensitivityAnalysisProviderTest {

    private final DenseMatrixFactory matrixFactory = new DenseMatrixFactory();

    private final OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider(matrixFactory);

    private static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(dc);
        OpenLoadFlowParameters lfParametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new NameSlackBusSelector(slackBusId));
        lfParameters.addExtension(OpenLoadFlowParameters.class, lfParametersExt);
        return sensiParameters;
    }

    private void runAcLf(Network network) {
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, new LoadFlowParameters())
                .join();
        if (!result.isOk()) {
            throw new PowsyblException("AC LF diverged");
        }
    }

    @Test
    void testDc() {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VLLOAD_0");
        InjectionIncrease genIncrease = new InjectionIncrease("GEN", "GEN", "GEN");
        BranchFlowPerInjectionIncrease factor1 = new BranchFlowPerInjectionIncrease(new BranchFlow("NHV1_NHV2_1", "NHV1_NHV2_1", "NHV1_NHV2_1"),
                                                                                    genIncrease);
        BranchFlowPerInjectionIncrease factor2 = new BranchFlowPerInjectionIncrease(new BranchFlow("NHV1_NHV2_2", "NHV1_NHV2_2", "NHV1_NHV2_2"),
                                                                                    genIncrease);
        SensitivityFactorsProvider factorsProvider = n -> Arrays.asList(factor1, factor2);
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
                                                             sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(2, result.getSensitivityValues().size());
        SensitivityValue sensiValue1 = result.getSensitivityValue(factor1);
        assertEquals(0.5d, sensiValue1.getValue(), LoadFlowAssert.DELTA_POWER);
        SensitivityValue sensiValue2 = result.getSensitivityValue(factor2);
        assertEquals(0.5d, sensiValue2.getValue(), LoadFlowAssert.DELTA_POWER);
    }

    private static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches) {
        Objects.requireNonNull(injections);
        Objects.requireNonNull(branches);
        return injections.stream().flatMap(injection -> {
            InjectionIncrease injIncrease = new InjectionIncrease(injection.getId(), injection.getId(), injection.getId());
            return branches.stream().map(branch -> new BranchFlowPerInjectionIncrease(new BranchFlow(branch.getId(), branch.getNameOrId(), branch.getId()), injIncrease));
        }).collect(Collectors.toList());
    }

    private static double getValue(SensitivityAnalysisResult result, String variableId, String functionId) {
        return result.getSensitivityValues().stream().filter(value -> value.getFactor().getVariable().getId().equals(variableId) && value.getFactor().getFunction().getId().equals(functionId))
                .findFirst()
                .map(SensitivityValue::getValue)
                .orElse(Double.NaN);
    }

    @Test
    void testDc4buses() {
        Network network = FourBusNetworkFactory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0");
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                                             network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
                                                             sensiParameters, LocalComputationManager.getDefault())
                .join();

        assertEquals(0.25d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.125d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.375d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.625d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.125d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0.375d, getValue(result, "g4", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.125d, getValue(result, "g4", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.125d, getValue(result, "g4", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.625d, getValue(result, "g4", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g4", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testAc() {
        Network network = EurostagTutorialExample1Factory.create();

        DenseMatrixFactory matrixFactory = new DenseMatrixFactory();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(matrixFactory));
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new FirstSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        loadFlowRunner.run(network, parameters);

        List<String> branchIds = Arrays.asList("NHV1_NHV2_1", "NHV1_NHV2_2");
        new OpenSensitivityAnalysisProvider(matrixFactory)
                .runAc(network, branchIds);
    }
}
