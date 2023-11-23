/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.openloadflow.network.impl.extensions.SubstationAutomationSystemsAdder;

import java.time.ZonedDateTime;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class AutomationSystemNetworkFactory extends AbstractLoadFlowNetworkFactory {

    private AutomationSystemNetworkFactory() {
    }

    /**
     * b1 and b2 are the HV network
     * b3 and b4 are the LV network
     * when breaker br1 is closed, the network is operated "coupled" and the LV line l34 have a very high intensity
     * opening the breaker br1 allow reducing the intensity of line l34 (even if network is in that case less robust
     * to any contingency)
     * g1
     * |      l12
     * b1 ====-------------==== b2
     * |                |
     * 8 tr1            8 tr2
     * |   b3p  l34    |
     * b3 ====*====--------==== b4
     * br1 |        |
     * ld3      ld4
     */
    public static Network create() {
        Network network = Network.create("OverloadManagementSystemTestCase", "code");
        network.setCaseDate(ZonedDateTime.parse("2020-04-05T14:11:00.000+01:00"));
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
        Substation s1 = network.getSubstation("s1");
        s1.newExtension(SubstationAutomationSystemsAdder.class)
            .newOverloadManagementSystem()
                .withMonitoredLineId("l34")
                .withThreshold(300)
                .withSwitchIdToOperate("br1")
                .withSwitchOpen(true)
            .add()
            .add();
        return network;
    }
}
