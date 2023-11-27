/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class LfBranchDisconnectionTest {

    private static final double DELTA = 1E-5;

    @Test
    void test() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters()).get(0);
        LfBranch lfl1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        lfl1.setDisconnectionAllowedSide1(true);
        lfl1.setDisconnectionAllowedSide2(true);

        AcLoadFlowParameters acParameters = new AcLoadFlowParameters(new LfNetworkParameters(),
                                                                     new AcEquationSystemCreationParameters(),
                                                                     new NewtonRaphsonParameters(),
                                                                     Collections.emptyList(),
                                                                     AcLoadFlowParameters.DEFAULT_MAX_OUTER_LOOP_ITERATIONS,
                                                                     new DenseMatrixFactory(),
                                                                     new UniformValueVoltageInitializer(),
                                                                     false,
                                                                     OpenLoadFlowParameters.SlackDistributionFailureBehavior.LEAVE_ON_SLACK_BUS);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            AcLoadFlowResult result = new AcloadFlowEngine(context)
                    .run();
            assertEquals(NewtonRaphsonStatus.CONVERGED, result.getNewtonRaphsonStatus());
            assertEquals(3.02444, lfl1.getP1().eval(), DELTA);
            assertEquals(0.98739, lfl1.getQ1().eval(), DELTA);
            assertEquals(-3.00434, lfl1.getP2().eval(), DELTA);
            assertEquals(-1.37187, lfl1.getQ2().eval(), DELTA);

            lfl1.setConnectedSide1(false);
            result = new AcloadFlowEngine(context)
                    .run();
            assertEquals(NewtonRaphsonStatus.CONVERGED, result.getNewtonRaphsonStatus());
            assertTrue(Double.isNaN(lfl1.getP1().eval()));
            assertTrue(Double.isNaN(lfl1.getQ1().eval()));
            assertEquals(1.752e-4, lfl1.getP2().eval(), DELTA);
            assertEquals(-0.61068, lfl1.getQ2().eval(), DELTA);

            lfl1.setConnectedSide1(true);
            result = new AcloadFlowEngine(context)
                    .run();
            assertEquals(NewtonRaphsonStatus.CONVERGED, result.getNewtonRaphsonStatus());
            assertEquals(3.02444, lfl1.getP1().eval(), DELTA);
            assertEquals(0.98739, lfl1.getQ1().eval(), DELTA);
            assertEquals(-3.00434, lfl1.getP2().eval(), DELTA);
            assertEquals(-1.37187, lfl1.getQ2().eval(), DELTA);

            lfl1.setConnectedSide2(false);
            result = new AcloadFlowEngine(context)
                    .run();
            assertEquals(NewtonRaphsonStatus.CONVERGED, result.getNewtonRaphsonStatus());
            assertEquals(1.752e-4, lfl1.getP1().eval(), DELTA);
            assertEquals(-0.5811, lfl1.getQ1().eval(), DELTA);
            assertTrue(Double.isNaN(lfl1.getP2().eval()));
            assertTrue(Double.isNaN(lfl1.getQ2().eval()));

            lfl1.setConnectedSide2(true);
            result = new AcloadFlowEngine(context)
                    .run();
            assertEquals(NewtonRaphsonStatus.CONVERGED, result.getNewtonRaphsonStatus());
            assertEquals(3.02444, lfl1.getP1().eval(), DELTA);
            assertEquals(0.98739, lfl1.getQ1().eval(), DELTA);
            assertEquals(-3.00434, lfl1.getP2().eval(), DELTA);
            assertEquals(-1.37187, lfl1.getQ2().eval(), DELTA);

            lfl1.setConnectedSide1(false);
            lfl1.setConnectedSide2(false);
            result = new AcloadFlowEngine(context)
                    .run();
            assertEquals(NewtonRaphsonStatus.CONVERGED, result.getNewtonRaphsonStatus());
            assertTrue(Double.isNaN(lfl1.getP1().eval()));
            assertTrue(Double.isNaN(lfl1.getQ1().eval()));
            assertTrue(Double.isNaN(lfl1.getP2().eval()));
            assertTrue(Double.isNaN(lfl1.getQ2().eval()));
        }
    }
}
