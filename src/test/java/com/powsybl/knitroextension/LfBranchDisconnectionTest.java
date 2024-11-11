/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.knitroextension;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.Test;

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

        AcLoadFlowParameters acParameters = new AcLoadFlowParameters()
                .setMatrixFactory(new DenseMatrixFactory());
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            AcLoadFlowResult result = new AcloadFlowEngine(context, new KnitroSolverFactory())
                    .run();
            assertEquals(AcSolverStatus.CONVERGED, result.getSolverStatus());
            assertEquals(3.02444, lfl1.getP1().eval(), DELTA);
            assertEquals(0.98740, lfl1.getQ1().eval(), DELTA);
            assertEquals(-3.00434, lfl1.getP2().eval(), DELTA);
            assertEquals(-1.37188, lfl1.getQ2().eval(), DELTA);

            lfl1.setConnectedSide1(false);
            result = new AcloadFlowEngine(context, new KnitroSolverFactory())
                    .run();
            assertEquals(AcSolverStatus.CONVERGED, result.getSolverStatus());
            assertEquals(0, lfl1.getP1().eval(), 0);
            assertEquals(0, lfl1.getQ1().eval(), 0);
            assertEquals(1.587E-4, lfl1.getP2().eval(), DELTA);
            assertEquals(-0.5432, lfl1.getQ2().eval(), DELTA);

            lfl1.setConnectedSide1(true);
            result = new AcloadFlowEngine(context, new KnitroSolverFactory())
                    .run();
            assertEquals(AcSolverStatus.CONVERGED, result.getSolverStatus());
            assertEquals(3.02444, lfl1.getP1().eval(), DELTA);
            assertEquals(0.98740, lfl1.getQ1().eval(), DELTA);
            assertEquals(-3.00434, lfl1.getP2().eval(), DELTA);
            assertEquals(-1.37188, lfl1.getQ2().eval(), DELTA);

            lfl1.setConnectedSide2(false);
            result = new AcloadFlowEngine(context, new KnitroSolverFactory())
                    .run();
            assertEquals(AcSolverStatus.CONVERGED, result.getSolverStatus());
            assertEquals(1.752e-4, lfl1.getP1().eval(), DELTA);
            assertEquals(-0.61995, lfl1.getQ1().eval(), DELTA);
            assertEquals(0, lfl1.getP2().eval(), 0);
            assertEquals(0, lfl1.getQ2().eval(), 0);

            lfl1.setConnectedSide2(true);
            result = new AcloadFlowEngine(context, new KnitroSolverFactory())
                    .run();
            assertEquals(AcSolverStatus.CONVERGED, result.getSolverStatus());
            assertEquals(3.02444, lfl1.getP1().eval(), DELTA);
            assertEquals(0.98740, lfl1.getQ1().eval(), DELTA);
            assertEquals(-3.00434, lfl1.getP2().eval(), DELTA);
            assertEquals(-1.37188, lfl1.getQ2().eval(), DELTA);

            lfl1.setConnectedSide1(false);
            lfl1.setConnectedSide2(false);
            result = new AcloadFlowEngine(context, new KnitroSolverFactory())
                    .run();
            assertEquals(AcSolverStatus.CONVERGED, result.getSolverStatus());
            assertTrue(Double.isNaN(lfl1.getP1().eval()));
            assertTrue(Double.isNaN(lfl1.getQ1().eval()));
            assertTrue(Double.isNaN(lfl1.getP2().eval()));
            assertTrue(Double.isNaN(lfl1.getQ2().eval()));
        }
    }
}
