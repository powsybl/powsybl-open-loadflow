/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSensitivityAnalysisTest {

    @Test
    public void test() {
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
        new OpenSensitivityAnalysis(network, matrixFactory)
                .runAc(branchIds);
    }
}
