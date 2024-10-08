/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.knitro.tofix;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class LfBranchDisconnectionTest {

    private static final double DELTA = 1E-5;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;
    private Line line1;

    @Test
    void test() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters()).get(0);
        LfBranch lfl1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        line1 = network.getLine("NHV1_NHV2_1");

        lfl1.setDisconnectionAllowedSide1(true);
        lfl1.setDisconnectionAllowedSide2(true);

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAcSolverType(AcSolverType.KNITRO);

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertActivePowerEquals(302.444, line1.getTerminal1());
        assertReactivePowerEquals(98.740,line1.getTerminal1());
        assertActivePowerEquals(-300.433, line1.getTerminal2());
        assertReactivePowerEquals(-137.188, line1.getTerminal2());

        lfl1.setConnectedSide1(false);
        var resultSide1disconnected = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, resultSide1disconnected.getComponentResults().get(0).getStatus());
        assertActivePowerEquals(0, line1.getTerminal1());
        assertReactivePowerEquals(0,line1.getTerminal1());
        assertActivePowerEquals(0.01597, line1.getTerminal2());
        assertReactivePowerEquals(-54.32, line1.getTerminal2());

//        assertEquals(AcSolverStatus.CONVERGED, resultSide1disconnected.getSolverStatus());
//        assertEquals(0, lfl1.getP1().eval(), 0);
//        assertEquals(0, lfl1.getQ1().eval(), 0);
//        assertEquals(1.587E-4, lfl1.getP2().eval(), DELTA);
//        assertEquals(-0.5432, lfl1.getQ2().eval(), DELTA);
//
//        lfl1.setConnectedSide1(true);
//        resultSide1connected = new AcloadFlowEngine(context)
//                .run();
//        assertEquals(AcSolverStatus.CONVERGED, resultSide1connected.getSolverStatus());
//        assertEquals(3.02444, lfl1.getP1().eval(), DELTA);
//        assertEquals(0.98739, lfl1.getQ1().eval(), DELTA);
//        assertEquals(-3.00434, lfl1.getP2().eval(), DELTA);
//        assertEquals(-1.37187, lfl1.getQ2().eval(), DELTA);
//
//        lfl1.setConnectedSide2(false);
//        resultSide2disconnected = new AcloadFlowEngine(context)
//                    .run();
//        assertEquals(AcSolverStatus.CONVERGED, resultSide2disconnected.getSolverStatus());
//        assertEquals(1.752e-4, lfl1.getP1().eval(), DELTA);
//        assertEquals(-0.61995, lfl1.getQ1().eval(), DELTA);
//        assertEquals(0, lfl1.getP2().eval(), 0);
//        assertEquals(0, lfl1.getQ2().eval(), 0);
//
//        lfl1.setConnectedSide2(true);
//        resultSide2connected = new AcloadFlowEngine(context)
//                    .run();
//        assertEquals(AcSolverStatus.CONVERGED, resultSide2connected.getSolverStatus());
//        assertEquals(3.02444, lfl1.getP1().eval(), DELTA);
//        assertEquals(0.98739, lfl1.getQ1().eval(), DELTA);
//        assertEquals(-3.00434, lfl1.getP2().eval(), DELTA);
//        assertEquals(-1.37187, lfl1.getQ2().eval(), DELTA);
//
//        lfl1.setConnectedSide1(false);
//        lfl1.setConnectedSide2(false);
//        resultSides1and2Disconnected = new AcloadFlowEngine(context)
//                .run();
//        assertEquals(AcSolverStatus.CONVERGED, resultSides1and2Disconnected.getSolverStatus());
//        assertTrue(Double.isNaN(lfl1.getP1().eval()));
//        assertTrue(Double.isNaN(lfl1.getQ1().eval()));
//        assertTrue(Double.isNaN(lfl1.getP2().eval()));
//        assertTrue(Double.isNaN(lfl1.getQ2().eval()));
    }
}
