/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.knitro;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcLoadFlowLccTest {

    @Test
    void test() {
        Network network = HvdcNetworkFactory.createLcc();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setGradientComputationModeKnitro(2)
                .setAcSolverType(AcSolverType.KNITRO);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus bus1 = network.getBusView().getBus("vl1_0");
        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);

        Bus bus2 = network.getBusView().getBus("vl2_0");
        assertVoltageEquals(389.3763, bus2);
        assertAngleEquals(-0.095268, bus2);

        Bus bus3 = network.getBusView().getBus("vl3_0");
        assertVoltageEquals(380, bus3);
        assertAngleEquals(0, bus3);

        LccConverterStation cs2 = network.getLccConverterStation("cs2");
        assertActivePowerEquals(50.00, cs2.getTerminal());
        assertReactivePowerEquals(37.499, cs2.getTerminal());

        LccConverterStation cs3 = network.getLccConverterStation("cs3");
        assertActivePowerEquals(-49.399, cs3.getTerminal());
        assertReactivePowerEquals(37.049, cs3.getTerminal());
    }
}
