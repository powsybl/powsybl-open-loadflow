/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LfBranchDisableModeTest {

    private static Network calculateLine1OpenSide1Ref() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getLine("NHV1_NHV2_1").getTerminal1().disconnect();
        new OpenLoadFlowProvider(new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>())
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, new LoadFlowParameters())
                .join();
        return network;
    }

    @Test
    void test() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters()).get(0);
        LfBranch lfl1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        lfl1.getSupportedDisableModes().add(LfBranchDisableMode.SIDE_1);

        AcLoadFlowParameters acParameters = new AcLoadFlowParameters(new LfNetworkParameters(),
                                                                     new AcEquationSystemCreationParameters(),
                                                                     new NewtonRaphsonParameters(),
                                                                     Collections.emptyList(),
                                                                     AcLoadFlowParameters.DEFAULT_MAX_OUTER_LOOP_ITERATIONS,
                                                                     new DenseMatrixFactory(),
                                                                     new UniformValueVoltageInitializer(),
                                                                     false);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            AcLoadFlowResult result = new AcloadFlowEngine(context)
                    .run();
            assertEquals(NewtonRaphsonStatus.CONVERGED, result.getNewtonRaphsonStatus());
            lfNetwork.updateState(new LfNetworkStateUpdateParameters(true, false, false, false, true, false, false));

            var l1 = network.getLine("NHV1_NHV2_1");
            assertActivePowerEquals(302.444, l1.getTerminal1());
            assertReactivePowerEquals(98.739, l1.getTerminal1());
            assertActivePowerEquals(-300.434, l1.getTerminal2());
            assertReactivePowerEquals(-137.187, l1.getTerminal2());
        }

        Network networkRef = calculateLine1OpenSide1Ref();

        lfl1.setDisableMode(LfBranchDisableMode.SIDE_1);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            AcLoadFlowResult result = new AcloadFlowEngine(context)
                    .run();
            assertEquals(NewtonRaphsonStatus.CONVERGED, result.getNewtonRaphsonStatus());
            lfNetwork.updateState(new LfNetworkStateUpdateParameters(true, false, false, false, true, false, false));

            var l1 = network.getLine("NHV1_NHV2_1");
        }
    }
}
