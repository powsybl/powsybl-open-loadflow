/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;

/**
 * <p>4 bus test networks adapted to distributed slack bus:</p>
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DistributedSlackNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network create() {
        Network network = Network.create("distributed-generation-slack-bus", "code");
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
        g1.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .withDroop(4)
                .add();
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
        g2.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .withDroop(2)
                .add();
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
        g3.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .withDroop(3)
                .add();
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
        g4.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .withDroop(1)
                .add();
        createLoad(b4, "l1", 600, 400);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b2, b4, "l24", 0.15f);
        createLine(network, b3, b4, "l34", 0.12f);
        return network;
    }

    public static Network createNetworkWithLoads() {
        Network network = Network.create("distributed-load-slack-bus", "code");
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
                .setMaxP(400)
                .setTargetP(100)
                .setTargetV(400)
                .setVoltageRegulatorOn(true)
                .add();
        Generator g2 = b2.getVoltageLevel()
                .newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(400)
                .setTargetP(200)
                .setTargetQ(300)
                .setVoltageRegulatorOn(false)
                .add();
        createLoad(b1, "l1", 30, 30);
        createLoad(b2, "l2", 60, 40);
        createLoad(b3, "l3", 50, 35);
        createLoad(b4, "l4", 140, 100);
        createLoad(b4, "l5", 10, 100);
        createLoad(b4, "l6", -50, 100);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b2, b4, "l24", 0.15f);
        createLine(network, b3, b4, "l34", 0.12f);
        return network;
    }

    public static Network createWithBattery() {
        Network network = create();
        Battery bat1 = network.getBusBreakerView().getBus("b1")
                .getVoltageLevel()
                .newBattery()
                .setId("bat1")
                .setMinP(-10)
                .setMaxP(10)
                .setTargetP(2)
                .setBus("b1")
                .setConnectableBus("b1")
                .setTargetQ(0)
                .add();
        bat1.newExtension(ActivePowerControlAdder.class)
                .withParticipate(false)
                .withDroop(0)
                .withParticipationFactor(0)
                .add();
        Battery bat2 = network.getBusBreakerView().getBus("b2")
                .getVoltageLevel()
                .newBattery()
                .setId("bat2")
                .setMinP(-20)
                .setMaxP(20)
                .setTargetP(-5)
                .setBus("b2")
                .setConnectableBus("b2")
                .setTargetQ(0)
                .add();
        bat2.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .withDroop(3)
                .withParticipationFactor(0.5)
                .add();
        return network;
    }
}
