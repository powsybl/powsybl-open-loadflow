/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

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

class FastDecoupledTest {

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
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED).setAcSolverType(FastDecoupledFactory.NAME);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(405, bus1);
        LoadFlowAssert.assertAngleEquals(0, bus1);
        assertVoltageEquals(235.132, bus2);
        LoadFlowAssert.assertAngleEquals(-2.259235, bus2);
        assertVoltageEquals(20.834, bus3);
        LoadFlowAssert.assertAngleEquals(-2.721880, bus3);
        assertActivePowerEquals(161.095, twt.getLeg1().getTerminal());
        assertReactivePowerEquals(81.884, twt.getLeg1().getTerminal());
        assertActivePowerEquals(-161, twt.getLeg2().getTerminal());
        assertReactivePowerEquals(-74, twt.getLeg2().getTerminal());
        assertActivePowerEquals(0, twt.getLeg3().getTerminal());
        assertReactivePowerEquals(0, twt.getLeg3().getTerminal());
    }
}
