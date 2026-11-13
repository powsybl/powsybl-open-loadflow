/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
class AcDcConverterStateTest {

    @Test
    void testExpectedAttributesAreRestored() {
        // Save the converter state, then modify the targetP and controlMode of the converter.
        // Restoring the saved state sets the two attributes to their initial value
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters().setAcDcNetwork(true)).getFirst();
        LfAcDcConverter converter = lfNetwork.getVoltageSourceConverter(0);

        // Check initial state and save it
        assertEquals(AcDcConverter.ControlMode.P_PCC, converter.getControlMode());
        assertEquals(0.5, converter.getTargetP());
        AcDcConverterState state = AcDcConverterState.save(converter);

        // Modify the LfAcDcConverter
        converter.setControlMode(AcDcConverter.ControlMode.V_DC);
        converter.setTargetP(0.3);
        assertEquals(AcDcConverter.ControlMode.V_DC, converter.getControlMode());
        assertEquals(0.3, converter.getTargetP());

        // Restore state and check LfAcDcConverter attributes
        state.restore();
        assertEquals(AcDcConverter.ControlMode.P_PCC, converter.getControlMode());
        assertEquals(0.5, converter.getTargetP());
    }
}
