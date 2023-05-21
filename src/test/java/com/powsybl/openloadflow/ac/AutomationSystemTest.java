/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.AbstractLoadFlowNetworkFactory;
import com.powsybl.openloadflow.network.extensions.SubstationAutomationSystemsAdder;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertCurrentEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AutomationSystemTest extends AbstractLoadFlowNetworkFactory {

    /**
     * b1 and b2 are the HV network
     * b3 and b4 are the LV network
     * when breaker br1 is closed, the network is operated "coupled" and the LV line l34 have a very high intensity
     * opening the breaker br1 allow reducing the intensity of line l34 (even if network is in that case less robust
     * to any contingency)
     *      g1
     *      |      l12
     * b1 ====-------------==== b2
     *      |                |
     *      8 tr1            8 tr2
     *      |   b3p  l34    |
     * b3 ====*====--------==== b4
     *      br1 |        |
     *         ld3      ld4
     */
    private static Network createNetwork() {
        Network network = Network.create("OverloadManagementSystemTestCase", "code");
        Bus b1 = createBus(network, "s1", "b1", 225);
        Bus b2 = createBus(network, "s2", "b2", 225);
        Bus b3 = createBus(network, "s1", "b3", 63);
        Bus b3p = b3.getVoltageLevel().getBusBreakerView().newBus()
                .setId("b3p")
                .add();
        Bus b4 = createBus(network, "s2", "b4", 63);
        createGenerator(b1, "g1", 100, 230);
        createLoad(b3p, "ld3", 3, 2);
        createLoad(b4, "ld4", 90, 60);
        createLine(network, b1, b2, "l12", 0.1, 3);
        createLine(network, b3p, b4, "l34", 0.05, 3.2);
        b3.getVoltageLevel().getBusBreakerView().newSwitch()
                .setId("br1")
                .setBus1("b3")
                .setBus2("b3p")
                .setOpen(false)
                .add();
        createTransformer(network, "s1", b1, b3, "tr1", 0.2, 2, 1);
        createTransformer(network, "s2", b2, b4, "tr2", 0.3, 3, 1);
        return network;
    }

    @Test
    void test() {
        Network network = createNetwork();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSimulateAutomatons(true);
        Substation s1 = network.getSubstation("s1");
        s1.newExtension(SubstationAutomationSystemsAdder.class)
                .newOverloadManagementSystem()
                    .withLineIdToMonitor("l34")
                    .withThreshold(300)
                    .withSwitchIdToOperate("br1")
                    .withSwitchOpen(true)
                .add()
            .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        Line l12 = network.getLine("l12");
        Line l34 = network.getLine("l34");
        assertCurrentEquals(298.953, l12.getTerminal1());
        assertCurrentEquals(34.333, l34.getTerminal1()); // no more loop in LV network
    }
}
