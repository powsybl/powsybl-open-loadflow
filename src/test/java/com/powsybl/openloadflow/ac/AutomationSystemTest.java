/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.AutomationSystemNetworkFactory;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertCurrentEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AutomationSystemTest {

    @Test
    void test() {
        Network network = AutomationSystemNetworkFactory.create();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSimulateAutomationSystems(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        Line l12 = network.getLine("l12");
        Line l34 = network.getLine("l34");
        assertCurrentEquals(298.953, l12.getTerminal1());
        assertCurrentEquals(34.333, l34.getTerminal1()); // no more loop in LV network
    }

    @Test
    void testNotSupportedTripping() {
        Network network = AutomationSystemNetworkFactory.create();
        network.getOverloadManagementSystem("l34_opens_br1")
                .setEnabled(false);
        Substation s1 = network.getSubstation("s1");
        s1.newOverloadManagementSystem()
                .setId("l34_opens_l34")
                .setEnabled(true)
                .setMonitoredElementId("l34")
                .setMonitoredElementSide(ThreeSides.ONE)
                .newBranchTripping()
                .setKey("l34 key")
                .setBranchToOperateId("l34")
                .setSideToOperate(TwoSides.ONE)
                .setCurrentLimit(200.)
                .add()
                .add();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSimulateAutomationSystems(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        Line l12 = network.getLine("l12");
        Line l34 = network.getLine("l34");
        assertCurrentEquals(175.411, l12.getTerminal1());
        assertCurrentEquals(378.397, l34.getTerminal1());
    }
}
