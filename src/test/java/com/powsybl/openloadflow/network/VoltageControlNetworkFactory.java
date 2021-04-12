/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

/**
 * @author Anne Tilloy <anne.tilloy@rte-france.com>
 */
public class VoltageControlNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network createWithGeneratorRemoteControl() {
        Network network = Network.create("generator-remote-control-test", "code");
        Substation s = network.newSubstation()
                .setId("s")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        VoltageLevel vl3 = s.newVoltageLevel()
                .setId("vl3")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b3 = vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        VoltageLevel vl4 = s.newVoltageLevel()
                .setId("vl4")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b4 = vl4.getBusBreakerView().newBus()
                .setId("b4")
                .add();
        Load l4 = vl4.newLoad()
                .setId("l4")
                .setBus("b4")
                .setConnectableBus("b4")
                .setP0(299.6)
                .setQ0(200)
                .add();
        Generator g1 = b1.getVoltageLevel()
                .newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4) // 22 413.4
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        Generator g2 = b2.getVoltageLevel()
                .newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        Generator g3 = b3.getVoltageLevel()
                .newGenerator()
                .setId("g3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        TwoWindingsTransformer tr1 = s.newTwoWindingsTransformer()
                .setId("tr1")
                .setVoltageLevel1(b1.getVoltageLevel().getId())
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .setG(0)
                .setB(0)
                .add();
        TwoWindingsTransformer tr2 = s.newTwoWindingsTransformer()
                .setId("tr2")
                .setVoltageLevel1(b2.getVoltageLevel().getId())
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.2)
                .setRatedU2(398)
                .setR(1)
                .setX(36)
                .setG(0)
                .setB(0)
                .add();
        TwoWindingsTransformer tr3 = s.newTwoWindingsTransformer()
                .setId("tr3")
                .setVoltageLevel1(b3.getVoltageLevel().getId())
                .setBus1(b3.getId())
                .setConnectableBus1(b3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(21.3)
                .setRatedU2(397)
                .setR(2)
                .setX(50)
                .setG(0)
                .setB(0)
                .add();

        return network;
    }
}
