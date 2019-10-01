/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ActivePowerControl;

/**
 * <p>4 bus test network adapted to distributed slack bus:</p>
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DistributedSlackNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network create() {
        Network network = Network.create("distributed-slack-bus", "code");
        Bus b1 = createBus(network, "b1", 400);
        Bus b2 = createBus(network, "b2", 400);
        Bus b3 = createBus(network, "b3", 400);
        Bus b4 = createBus(network, "b4", 400);
        Generator g1 = b1.getVoltageLevel()
                .newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(400)
                .setVoltageRegulatorOn(true)
                .add();
        g1.addExtension(ActivePowerControl.class, new ActivePowerControl<>(g1, true, 4));
        Generator g2 = b2.getVoltageLevel()
                .newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(100)
                .setMaxP(300)
                .setTargetP(200)
                .setTargetQ(300)
                .setVoltageRegulatorOn(false)
                .add();
        g2.addExtension(ActivePowerControl.class, new ActivePowerControl<>(g2, true, 2));
        Generator g3 = b3.getVoltageLevel()
                .newGenerator()
                .setId("g3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(50)
                .setMaxP(150)
                .setTargetP(90)
                .setTargetQ(130)
                .setVoltageRegulatorOn(false)
                .add();
        g3.addExtension(ActivePowerControl.class, new ActivePowerControl<>(g3, true, 3));
        Generator g4 = b3.getVoltageLevel()
                .newGenerator()
                .setId("g4")
                .setBus("b3")
                .setConnectableBus("b3")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(50)
                .setMaxP(150)
                .setTargetP(90)
                .setTargetQ(130)
                .setVoltageRegulatorOn(false)
                .add();
        g4.addExtension(ActivePowerControl.class, new ActivePowerControl<>(g4, true, 1));
        createLoad(b4, "l1", 600, 400);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b2, b4, "l24", 0.15f);
        createLine(network, b3, b4, "l34", 0.12f);
        return network;
    }
}
