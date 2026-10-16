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
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public abstract class AbsractLfDcNetworkTest {

    private final LfNetworkParameters networkParameters = new LfNetworkParameters().setAcDcNetwork(true);

    // --- getNumDC ---

    @Test
    void testGetNumScReturnsTheExpectedValue() {
        Network network = AcDcNetworkFactory.createAcDcNetworkTwoDcSubNetworks();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        assertEquals(0, lfNetwork.getDcNetworks().get(0).getNumDC());
        assertEquals(1, lfNetwork.getDcNetworks().get(1).getNumDC());
    }

    // --- getAcDcConverters ---

    @Test
    void getAcDcConvertersReturnsOnlyTheConvertersInTheDcComponent() {
        Network network = AcDcNetworkFactory.createAcDcNetworkTwoDcSubNetworks();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        List<LfAcDcConverter> converters1 = lfNetwork.getDcNetworks().get(0).getAcDcConverters();
        assertEquals(List.of("conv12", "conv36"), converters1.stream().map(LfAcDcConverter::getId).toList());

        List<LfAcDcConverter> converters2 = lfNetwork.getDcNetworks().get(1).getAcDcConverters();
        assertEquals(List.of("conv14", "conv56"), converters2.stream().map(LfAcDcConverter::getId).toList());
    }

    // --- resolve ---

    @Test
    void dcSubComponentAlreadyResolvedByVDcConverterNeedsNoPromotion() {
        // conv23 (P_PCC) and conv45 (V_DC) share the dn3/dn4 subcomponent: conv45 already settles its DC voltage
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();
        List<LfAcDcConverter> convertersToSetInVdcMode = lfNetwork.getDcNetworks().getFirst().resolve();

        assertTrue(convertersToSetInVdcMode.isEmpty());
    }

    @Test
    void droopModeIsAlsoConsideredAsControllingVoltage() {
        // conv23 (P_PCC) and conv45 (V_DC) share the dn3/dn4 subcomponent: conv45 already settles its DC voltage
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        network.getVoltageSourceConverter("conv45")
                .setControlMode(AcDcConverter.ControlMode.DC_DROOP)
                .setTargetP(50)
                .newDroopCurve()
                .beginSegment().setMinV(-400).setMaxV(400).setK(0.01).endSegment()
                .add();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();
        List<LfAcDcConverter> convertersToSetInVdcMode = lfNetwork.getDcNetworks().getFirst().resolve();

        assertTrue(convertersToSetInVdcMode.isEmpty());
    }

    @Test
    void allPccConverterArePromotedWhenTwoShareAnDcSubComponent() {
        Network network = AcDcNetworkFactory.createAcDcNetworkTwoPccConvertersWithoutVdcReference();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();
        List<LfAcDcConverter> convertersToSetInVdcMode = lfNetwork.getDcNetworks().getFirst().resolve();

        assertEquals(2, convertersToSetInVdcMode.size());
    }

    @Test
    void converterWithNoDcBusReachingGroundIsRejected() {
        // DC node dn3 is not grounded
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        network.getDcGround("dg3").disconnectDc();

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        PowsyblException exception = assertThrows(PowsyblException.class,
                () -> lfNetwork.getDcNetworks().getFirst().resolve());
        assertTrue(exception.getMessage().contains("not indirectly connected to a DC ground"));
    }

}
