/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageSourceConverter;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
class LfDcComponentValidatorTest {
    private final LfNetworkParameters networkParameters = new LfNetworkParameters().setAcDcNetwork(true);

    // --- resolve ---
    // LfDcComponentValidator.resolve() is called during network loading, so we test it the same way as this is how
    // end-user witness its effect.

    Set<String> getConvertersSwitchedToVdcMode(LfNetwork lfNetwork) {
        return lfNetwork.getVoltageSourceConverters().stream()
                .filter(vsc -> vsc.getControlMode() == AcDcConverter.ControlMode.V_DC
                        && vsc.getInitialControlMode() != AcDcConverter.ControlMode.V_DC)
                .map(LfAcDcConverter::getId)
                .collect(Collectors.toSet());
    }

    @Test
    void dcSubComponentAlreadyResolvedByVDcConverterNeedsNoPromotion() {
        // conv23 (P_PCC) and conv45 (V_DC) share the dn3/dn4 subcomponent: conv45 already settles its DC voltage
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        assertTrue(getConvertersSwitchedToVdcMode(lfNetwork).isEmpty());
    }

    @Test
    void droopModeIsAlsoConsideredAsControllingVoltage() {
        // conv23 (P_PCC) and conv45 (DC_DROOP) share the dn3/dn4 subcomponent: conv45 already settles its DC voltage
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        network.getVoltageSourceConverter("conv45")
                .setControlMode(AcDcConverter.ControlMode.DC_DROOP)
                .setTargetP(50)
                .newDroopCurve()
                .beginSegment().setMinV(-400).setMaxV(400).setK(0.01).endSegment()
                .add();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        assertTrue(getConvertersSwitchedToVdcMode(lfNetwork).isEmpty());
    }

    @Test
    void disconnectedConvertersAreNotConsidered() {
        // conv23 (P_PCC) and conv45 (V_DC) share the dn3/dn4 subcomponent: conv45 already settles its DC voltage but is
        // disconnected. it cannot control DC voltage anymore
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");

        // AC disconnection
        conv45.disconnect();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();
        assertEquals(Set.of("conv23"), getConvertersSwitchedToVdcMode(lfNetwork));

        // DC disconnection
        conv45.connect();
        conv45.disconnectDc();
        LfNetwork lfNetwork2 = Networks.load(network, networkParameters).getFirst();
        assertEquals(Set.of("conv23"), getConvertersSwitchedToVdcMode(lfNetwork2));

    }

    @Test
    void allPccConverterArePromotedWhenTwoShareAnDcSubComponent() {
        // No converters control V_DC.
        Network network = AcDcNetworkFactory.createAcDcNetworkTwoPccConvertersWithoutVdcReference();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        assertEquals(Set.of("conv23", "conv45"), getConvertersSwitchedToVdcMode(lfNetwork));
    }

    @Test
    void dcSubComponentLinkedByResistiveDcSwitchNeedsNoPromotion() {
        // conv23 (P_PCC, on dn3) reaches conv45 (V_DC, on dn4) through the resistive switch sw3b then the line dl3b4:
        // dn3 and dn3b are distinct DC buses, so the switch must be followed to find conv45
        Network network = AcDcNetworkFactory.createAcDcNetworkWithDcSwitchAndDcLine(0.5, 0.5);
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();
        assertTrue(getConvertersSwitchedToVdcMode(lfNetwork).isEmpty());
    }

    @Test
    void converterWithNoDcBusReachingGroundIsRejected() {
        // Converter conv23 negative pole is not grounded
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        network.getDcGround("dg3").disconnectDc();

        PowsyblException exception = assertThrows(PowsyblException.class,
                () -> Networks.load(network, networkParameters));
        assertTrue(exception.getMessage().contains("not indirectly connected to a DC ground"));
    }
}
