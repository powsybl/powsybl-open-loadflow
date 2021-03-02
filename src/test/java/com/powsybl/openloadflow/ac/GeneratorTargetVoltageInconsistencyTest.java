/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.util.ParameterConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class GeneratorTargetVoltageInconsistencyTest {

    @Test
    void localTest() {
        Network network = Network.create("generatorLocalInconsistentTargetVoltage", "code");
        Substation s = network.newSubstation()
                .setId("s")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();

        vl1.newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(23)
                .setVoltageRegulatorOn(true)
                .add();
        vl1.newGenerator()
                .setId("g2")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(22)
                .setVoltageRegulatorOn(true)
                .add();

        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setP0(99.9)
                .setQ0(80)
                .add();
        network.newLine()
                .setId("l1")
                .setVoltageLevel1("vl1")
                .setConnectableBus1("b1")
                .setBus1("b1")
                .setVoltageLevel2("vl2")
                .setConnectableBus2("b2")
                .setBus2("b2")
                .setR(1)
                .setX(1)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();

        FirstSlackBusSelector slackBusSelector = new FirstSlackBusSelector();
        PowsyblException exception = assertThrows(PowsyblException.class, () -> LfNetwork.load(network, slackBusSelector));
        assertEquals("Generators [g1, g2] are connected to the same bus 'vl1_0' with a different target voltages: 22.0 and 23.0", exception.getMessage());
    }

    @Test
    void remoteTest() {
        Network network = Network.create("generatorRemoteInconsistentTargetVoltage", "code");
        Substation s = network.newSubstation()
                .setId("s")
                .add();

        VoltageLevel vl3 = s.newVoltageLevel()
                .setId("vl3")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        Load ld = vl3.newLoad()
                .setId("ld")
                .setBus("b3")
                .setConnectableBus("b3")
                .setP0(99.9)
                .setQ0(80)
                .add();

        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(ld.getTerminal())
                .add();

        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        Generator g2 = vl2.newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(225)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(ld.getTerminal())
                .add();

        network.newLine()
                .setId("l1")
                .setVoltageLevel1("vl1")
                .setConnectableBus1("b1")
                .setBus1("b1")
                .setVoltageLevel2("vl3")
                .setConnectableBus2("b3")
                .setBus2("b3")
                .setR(1)
                .setX(1)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        network.newLine()
                .setId("l2")
                .setVoltageLevel1("vl2")
                .setConnectableBus1("b2")
                .setBus1("b2")
                .setVoltageLevel2("vl3")
                .setConnectableBus2("b3")
                .setBus2("b3")
                .setR(1)
                .setX(1)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();

        FirstSlackBusSelector slackBusSelector = new FirstSlackBusSelector();
        LfNetworkParameters parameters = new LfNetworkParameters(slackBusSelector, true, false, false, false, ParameterConstants.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE, false);
        PowsyblException exception = assertThrows(PowsyblException.class, () -> LfNetwork.load(network, parameters));
        assertEquals("LfGeneratorImpl 'g2' has an inconsistent target voltage: 0.5625 pu", exception.getMessage());
    }

    @Test
    void remoteAndLocalTest() {
        Network network = Network.create("generatorRemoteAndLocalInconsistentTargetVoltage", "code");
        Substation s = network.newSubstation()
                .setId("s")
                .add();

        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        Generator g2 = vl2.newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413)
                .setVoltageRegulatorOn(true)
                .add();

        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(412)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(g2.getTerminal())
                .add();

        network.newLine()
                .setId("l1")
                .setVoltageLevel1("vl1")
                .setConnectableBus1("b1")
                .setBus1("b1")
                .setVoltageLevel2("vl2")
                .setConnectableBus2("b2")
                .setBus2("b2")
                .setR(1)
                .setX(1)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();

        VoltageLevel vl3 = s.newVoltageLevel()
                .setId("vl3")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        vl3.newLoad()
                .setId("ld")
                .setBus("b3")
                .setConnectableBus("b3")
                .setP0(99.9)
                .setQ0(80)
                .add();

        network.newLine()
                .setId("l2")
                .setVoltageLevel1("vl2")
                .setConnectableBus1("b2")
                .setBus1("b2")
                .setVoltageLevel2("vl3")
                .setConnectableBus2("b3")
                .setBus2("b3")
                .setR(1)
                .setX(1)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();

        FirstSlackBusSelector slackBusSelector = new FirstSlackBusSelector();
        LfNetworkParameters parameters = new LfNetworkParameters(slackBusSelector, true, false, false, false, ParameterConstants.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE, false);
        PowsyblException exception = assertThrows(PowsyblException.class, () -> LfNetwork.load(network, parameters));
        assertEquals("Bus 'vl2_0' controlled by bus 'vl1_0' has also a local voltage control with a different value: 413.0 and 412.0", exception.getMessage());
    }
}
