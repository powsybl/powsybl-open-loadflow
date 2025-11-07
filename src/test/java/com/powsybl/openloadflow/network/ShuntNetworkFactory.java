/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class ShuntNetworkFactory extends AbstractLoadFlowNetworkFactory {

    private ShuntNetworkFactory() {
    }

    public static Network create() {
        Network network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("S2")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setTargetP(101.3664)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(101)
                .setQ0(150)
                .add();
        VoltageLevel vl3 = s2.newVoltageLevel()
                .setId("vl3")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        vl3.newShuntCompensator()
                .setId("SHUNT")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(1e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(3e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();
        network.newLine()
                .setId("l1")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();
        network.newLine()
                .setId("l2")
                .setBus1("b3")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .add();
        return network;
    }

    public static Network createWithTwoShuntCompensators() {
        Network network = create();
        VoltageLevel vl3 = network.getVoltageLevel("vl3");
        vl3.newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(0)
                .setVoltageRegulatorOn(false)
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(1e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(3e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();
        return network;
    }

    public static Network createWithGeneratorAndShunt() {
        Network network = create();
        VoltageLevel vl3 = network.getVoltageLevel("vl3");
        vl3.newGenerator()
                .setId("g2")
                .setConnectableBus("b3")
                .setBus("b3")
                .setTargetP(0)
                .setTargetV(393)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        return network;
    }

    public static Network createWithGeneratorAndShuntNonImpedant() {
        Network network = createWithGeneratorAndShunt();
        VoltageLevel vl3 = network.getVoltageLevel("vl3");
        vl3.getBusBreakerView().newBus().setId("b4").add();
        vl3.getBusBreakerView().newSwitch().setBus1("b3").setBus2("b4").setId("switch").add();
        vl3.newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b4")
                .setConnectableBus("b4")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setTargetV(393)
                .setTargetDeadband(2.0)
                .newNonLinearModel()
                .beginSection()
                .setB(1e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(3e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();
        return network;
    }

    /**
     * Two shunt compensators with many sections, in same electrical vicinity and providing equivalent voltage support
     * <pre>
     * 400 kV nominal V
     *
     * 200 MW, 390 kV                 200 MW
     * g1                             ld3
     * |                              |
     * b1 --- 30Ω ----- b2---- 5Ω ----b3
     *                 /  \
     *                5Ω   5Ω
     *               /      \
     *              b4      b5
     *              |       |
     *              s4      s5
     *    s4 and s5 identical shunts:
     *      initially at section 0
     *      20 max sections (16 MVar per section at nominal V)
     *      410 kV setpoint at own terminal bus (b4 and b5 respectively)
     * </pre>
     * @return network
     */
    public static Network createTwinShuntCompensators() {
        Network network = Network.create("twinShuntCompensators", "code");
        Bus b1 = createBus(network, "b1", 400.);
        Bus b2 = createBus(network, "b2", 400.);
        Bus b3 = createBus(network, "b3", 400.);
        Bus b4 = createBus(network, "b4", 400.);
        Bus b5 = createBus(network, "b5", 400.);
        createGenerator(b1, "g1", 200., 390.);
        createLoad(b3, "ld3", 200, 20);
        createLine(network, b1, b2, "l12", 30.);
        createLine(network, b2, b3, "l23", 5.);
        createLine(network, b2, b4, "l24", 5.);
        createLine(network, b2, b5, "l25", 5.);
        createFixedShuntCompensator(b4, "s4", 0., 1e-4, 20)
                .setSectionCount(0)
                .setTargetV(410.)
                .setTargetDeadband(2.)
                .setVoltageRegulatorOn(true);
        createFixedShuntCompensator(b5, "s5", 0., 1e-4, 20)
                .setSectionCount(0)
                .setTargetV(410.)
                .setTargetDeadband(2.)
                .setVoltageRegulatorOn(true);
        return network;
    }
}
