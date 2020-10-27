/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.EmptyContingencyListProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSensitivityAnalysisProviderTest {

    private final DenseMatrixFactory matrixFactory = new DenseMatrixFactory();

    private static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(dc);
        OpenLoadFlowParameters lfParametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new NameSlackBusSelector(slackBusId));
        lfParameters.addExtension(OpenLoadFlowParameters.class, lfParametersExt);
        return sensiParameters;
    }

    @Test
    void testDc() {
        Network network = EurostagTutorialExample1Factory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VLLOAD_0");
        OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider(matrixFactory);
        BranchFlowPerInjectionIncrease factor = new BranchFlowPerInjectionIncrease(new BranchFlow("NHV1_NHV2_1", "NHV1_NHV2_1", "NHV1_NHV2_1"),
                                                                                   new InjectionIncrease("GEN", "GEN", "GEN"));
        SensitivityFactorsProvider factorsProvider = network1 -> Collections.singletonList(factor);
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(),
                                                             sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(1, result.getSensitivityValues().size());
        SensitivityValue sensiValue = result.getSensitivityValue(factor);
        assertEquals(0.5d, sensiValue.getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    public void testAc() {
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
