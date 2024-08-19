/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.T3wtFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcLoadFlow3wtTest {

    private Network network;
    private Substation s;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private ThreeWindingsTransformer twt;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = T3wtFactory.create();
        s = network.getSubstation("s");
        bus1 = network.getBusBreakerView().getBus("b1");
        bus2 = network.getBusBreakerView().getBus("b2");
        bus3 = network.getBusBreakerView().getBus("b3");
        twt = network.getThreeWindingsTransformer("3wt");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(405, bus1);
        LoadFlowAssert.assertAngleEquals(0, bus1);
        assertVoltageEquals(235.132, bus2);
        LoadFlowAssert.assertAngleEquals(-2.259241, bus2);
        assertVoltageEquals(20.834, bus3);
        LoadFlowAssert.assertAngleEquals(-2.721885, bus3);
        assertActivePowerEquals(161.095, twt.getLeg1().getTerminal());
        assertReactivePowerEquals(81.884, twt.getLeg1().getTerminal());
        assertActivePowerEquals(-161, twt.getLeg2().getTerminal());
        assertReactivePowerEquals(-74, twt.getLeg2().getTerminal());
        assertActivePowerEquals(0, twt.getLeg3().getTerminal());
        assertReactivePowerEquals(0, twt.getLeg3().getTerminal());
    }

    @Test
    void testWithRatioTapChangers() {
        // create a ratio tap changer on leg 1 and check that voltages on leg 2 and 3 have changed compare to previous
        // test
        twt.getLeg1().newRatioTapChanger()
                .setLoadTapChangingCapabilities(false)
                .setTapPosition(0)
                .beginStep()
                    .setR(5)
                    .setX(10)
                    .setRho(0.9)
                .endStep()
            .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(405, bus1);
        assertVoltageEquals(209.886, bus2);
        assertVoltageEquals(18.582, bus3);
    }

    @Test
    void testWithPhaseTapChangers() {
        // create a phase tap changer at leg 2 with a zero phase shifting
        PhaseTapChanger ptc = twt.getLeg2().newPhaseTapChanger()
                .setTapPosition(0)
                .beginStep()
                .setAlpha(0)
                .endStep()
                .add();
        // create a transformer between bus 1 / bus2 in parallel of leg1 / leg2
        TwoWindingsTransformer twtParallel = s.newTwoWindingsTransformer()
                .setId("2wt")
                .setBus1("b1")
                .setConnectableBus1("b1")
                .setBus2("b2")
                .setConnectableBus2("b2")
                .setRatedU1(390)
                .setRatedU2(220)
                .setR(4)
                .setX(80)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(21.97, twtParallel.getTerminal1());
        assertActivePowerEquals(-139.088, twt.getLeg2().getTerminal());

        // set the phase shifting to 10 degree and check active flow change
        ptc.getStep(0).setAlpha(10);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(121.691, twtParallel.getTerminal1());
        assertActivePowerEquals(-40.451, twt.getLeg2().getTerminal());
    }

    @Test
    void testSplitShuntAdmittance() {
        parameters.setTwtSplitShuntAdmittance(false);
        twt.getLeg1().setB(0.00004);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(405, bus1);
        LoadFlowAssert.assertAngleEquals(0, bus1);
        assertVoltageEquals(235.132, bus2);
        LoadFlowAssert.assertAngleEquals(-2.259241, bus2);
        assertVoltageEquals(20.834, bus3);
        LoadFlowAssert.assertAngleEquals(-2.721885, bus3);
        assertActivePowerEquals(161.095, twt.getLeg1().getTerminal());
        assertReactivePowerEquals(75.323, twt.getLeg1().getTerminal());
        assertActivePowerEquals(-161, twt.getLeg2().getTerminal());
        assertReactivePowerEquals(-74, twt.getLeg2().getTerminal());
        assertActivePowerEquals(0, twt.getLeg3().getTerminal());
        assertReactivePowerEquals(0, twt.getLeg3().getTerminal());

        parameters.setTwtSplitShuntAdmittance(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(405, bus1);
        LoadFlowAssert.assertAngleEquals(0, bus1);
        assertVoltageEquals(235.358, bus2);
        LoadFlowAssert.assertAngleEquals(-2.257583, bus2);
        assertVoltageEquals(20.854, bus3);
        LoadFlowAssert.assertAngleEquals(-2.719334, bus3);
        assertActivePowerEquals(161.095, twt.getLeg1().getTerminal());
        assertReactivePowerEquals(75.314, twt.getLeg1().getTerminal());
        assertActivePowerEquals(-161, twt.getLeg2().getTerminal());
        assertReactivePowerEquals(-74, twt.getLeg2().getTerminal());
        assertActivePowerEquals(0, twt.getLeg3().getTerminal());
        assertReactivePowerEquals(0, twt.getLeg3().getTerminal());
    }
}
