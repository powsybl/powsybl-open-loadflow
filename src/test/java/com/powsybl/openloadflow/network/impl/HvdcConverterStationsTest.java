/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class HvdcConverterStationsTest {

    @Test
    void testIsVsc() {
        Network networkVsc = HvdcTestNetwork.createVsc();
        assertFalse(HvdcConverterStations.isVsc(networkVsc.getVoltageLevel("VL1")));
        assertTrue(HvdcConverterStations.isVsc(networkVsc.getVscConverterStation("C1")));
        Network networkLcc = HvdcTestNetwork.createLcc();
        assertFalse(HvdcConverterStations.isVsc(networkLcc.getLccConverterStation("C1")));
    }
}
