/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class GeneratorTargetVoltageInconsistencyTest {

    @Test
    void localTest() throws IOException {
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
                .setConnectableBus1("b1")
                .setBus1("b1")
                .setConnectableBus2("b2")
                .setBus2("b2")
                .setR(1)
                .setX(1)
                .add();

        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters()
                .setSlackBusSelector(new FirstSlackBusSelector());
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("testReport", "Test Report")
                .build();
        List<LfNetwork> lfNetworks = Networks.load(network, lfNetworkParameters, reportNode);
        assertEquals(1, lfNetworks.size());

        LfNetwork lfNetwork = lfNetworks.get(0);
        LfBus controlledBus = lfNetwork.getBusById("vl1_0");
        assertNotNull(controlledBus);

        Optional<GeneratorVoltageControl> vc = controlledBus.getGeneratorVoltageControl();
        assertTrue(vc.isPresent());
        assertEquals(23, vc.get().getTargetValue() * controlledBus.getNominalV());
        LoadFlowAssert.assertReportEquals("/notUniqueTargetVControllerBusReport.txt", reportNode);
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
                .setConnectableBus1("b1")
                .setBus1("b1")
                .setConnectableBus2("b3")
                .setBus2("b3")
                .setR(1)
                .setX(1)
                .add();
        network.newLine()
                .setId("l2")
                .setConnectableBus1("b2")
                .setBus1("b2")
                .setConnectableBus2("b3")
                .setBus2("b3")
                .setR(1)
                .setX(1)
                .add();

        LfNetworkParameters parameters = new LfNetworkParameters()
                .setGeneratorVoltageRemoteControl(true);

        Generator g = network.getGenerator("g2");
        assertEquals(0.5625, g.getTargetV() / g.getTerminal().getVoltageLevel().getNominalV());

        List<LfNetwork> networkList = Networks.load(network, parameters);
        LfNetwork mainNetwork = networkList.get(0);
        LfGenerator generator = mainNetwork.getBusById("vl2_0").getGenerators().get(0);
        assertEquals("g2", generator.getId());
        assertTrue(Double.isNaN(generator.getTargetV()));
    }

    @Test
    void remoteAndLocalTest() throws IOException {
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
                .setConnectableBus1("b1")
                .setBus1("b1")
                .setConnectableBus2("b2")
                .setBus2("b2")
                .setR(1)
                .setX(1)
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
                .setConnectableBus1("b2")
                .setBus1("b2")
                .setConnectableBus2("b3")
                .setBus2("b3")
                .setR(1)
                .setX(1)
                .add();

        LfNetworkParameters parameters = new LfNetworkParameters()
                .setGeneratorVoltageRemoteControl(true);

        assertEquals(412, network.getGenerator("g1").getTargetV());
        assertEquals(413, g2.getTargetV());
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("testReport", "Test Report")
                .build();
        List<LfNetwork> networkList = Networks.load(network, parameters, reportNode);
        LfNetwork mainNetwork = networkList.get(0);
        Optional<GeneratorVoltageControl> sharedVoltageControl = mainNetwork.getBusById("vl2_0").getGeneratorVoltageControl();
        assertTrue(sharedVoltageControl.isPresent());

        assertEquals(413 / g2.getTerminal().getVoltageLevel().getNominalV(), sharedVoltageControl.get().getTargetValue());
        LoadFlowAssert.assertReportEquals("/busAlreadyControlledWithDifferentTargetVReport.txt", reportNode);
    }
}
