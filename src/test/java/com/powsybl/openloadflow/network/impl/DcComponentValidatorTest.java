/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
class DcComponentValidatorTest {

    private static final int NUM_DCC = 1;

    @Test
    void islandAlreadyResolvedByVDcConverterNeedsNoPromotion() {
        // conv23 (P_PCC) and conv45 (V_DC) share the dn3/dn4 island: conv45 already settles its DC voltage
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        List<AcDcConverter<?>> convertersToSetInVdcMode = DcComponentValidator.resolveDcComponent(
            allDcBuses(network),
            List.of(network.getVoltageSourceConverter("conv23"), network.getVoltageSourceConverter("conv45")),
            NUM_DCC);

        assertTrue(convertersToSetInVdcMode.isEmpty());
    }

    @Test
    void droopModeIsAlsoConsideredAsControllingVoltage() {
        // conv23 (P_PCC) and conv45 (V_DC) share the dn3/dn4 island: conv45 already settles its DC voltage
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        network.getVoltageSourceConverter("conv45").setControlMode(AcDcConverter.ControlMode.P_PCC_DROOP);
        List<AcDcConverter<?>> convertersToSetInVdcMode = DcComponentValidator.resolveDcComponent(
                allDcBuses(network),
                List.of(network.getVoltageSourceConverter("conv23"), network.getVoltageSourceConverter("conv45")),
                NUM_DCC);

        assertTrue(convertersToSetInVdcMode.isEmpty());
    }

    @Test
    void disconnectedConvertersAreNotConsidered() {
        // conv23 (P_PCC) and conv45 (V_DC) share the dn3/dn4 island: conv45 already settles its DC voltage but is
        // disconnected. it cannot control DC voltage anymore
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");

        // AC disconnection
        conv45.disconnect();
        List<AcDcConverter<?>> convertersToSetInVdcMode = DcComponentValidator.resolveDcComponent(
                allDcBuses(network),
                List.of(network.getVoltageSourceConverter("conv23"), network.getVoltageSourceConverter("conv45")),
                NUM_DCC);

        assertEquals(1, convertersToSetInVdcMode.size());
        assertEquals(network.getVoltageSourceConverter("conv23"), convertersToSetInVdcMode.getFirst());

        // DC disconnection
        conv45.connect();
        conv45.disconnectDc();
        List<AcDcConverter<?>> convertersToSetInVdcMode2 = DcComponentValidator.resolveDcComponent(
                allDcBuses(network),
                List.of(network.getVoltageSourceConverter("conv23"), network.getVoltageSourceConverter("conv45")),
                NUM_DCC);

        assertEquals(1, convertersToSetInVdcMode2.size());
        assertEquals(network.getVoltageSourceConverter("conv23"), convertersToSetInVdcMode2.getFirst());
    }

    @Test
    void allPccConverterArePromotedWhenTwoShareAnIsland() {
        Network network = AcDcNetworkFactory.createAcDcNetworkTwoPccConvertersWithoutVdcReference();

        List<AcDcConverter<?>> convertersToSetInVdcMode = DcComponentValidator.resolveDcComponent(
            allDcBuses(network),
            List.of(network.getVoltageSourceConverter("conv23"), network.getVoltageSourceConverter("conv45")),
            NUM_DCC);

        assertEquals(2, convertersToSetInVdcMode.size());
    }

    @Test
    void islandWithNoConverterAndNoGroundIsRejected() {
        // Restricting the resolution to dn3/dn4 only (ignoring the grounded dnDummy3/dnDummy4 buses) simulates a
        // pocket of DC buses reachable only through DC lines, with no converter and no ground at all
        Network network = AcDcNetworkFactory.createBaseNetwork();
        List<DcBus> deadIsland = List.of(network.getDcNode("dn3").getDcBus(), network.getDcNode("dn4").getDcBus());

        PowsyblException exception = assertThrows(PowsyblException.class,
            () -> DcComponentValidator.resolveDcComponent(deadIsland, List.of(), NUM_DCC));
        assertTrue(exception.getMessage().contains("no AC-DC converter able to settle the DC voltage"));
    }

    @Test
    void converterWithNoTerminalReachingGroundIsRejected() {
        // A converter whose two DC terminals are only linked to each other, with no DC ground anywhere: promoting
        // it to V_DC would only add a relative constraint, never anchoring an absolute voltage
        Network network = Network.create("dc-validator-ungrounded-test", "test");
        Substation s = network.newSubstation().setId("S").add();
        VoltageLevel vl = s.newVoltageLevel().setId("vl").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl.getBusBreakerView().newBus().setId("b").add();
        network.newDcNode().setId("dnA").setNominalV(400.).add();
        network.newDcNode().setId("dnB").setNominalV(400.).add();
        vl.newVoltageSourceConverter()
            .setIdleLoss(0.5)
            .setSwitchingLoss(0.001)
            .setResistiveLoss(1)
            .setControlMode(AcDcConverter.ControlMode.P_PCC)
            .setTargetP(10.)
            .setId("conv")
            .setBus1("b")
            .setDcNode1("dnA")
            .setDcNode2("dnB")
            .setVoltageRegulatorOn(false)
            .setReactivePowerSetpoint(0.0)
            .add();

        List<AcDcConverter<?>> converters = List.of(network.getVoltageSourceConverter("conv"));
        PowsyblException exception = assertThrows(PowsyblException.class,
            () -> DcComponentValidator.resolveDcComponent(allDcBuses(network), converters, NUM_DCC));
        assertTrue(exception.getMessage().contains("not indirectly connected to a DC ground"));
    }

    private static List<DcBus> allDcBuses(Network network) {
        return network.getDcBusStream().toList();
    }
}
