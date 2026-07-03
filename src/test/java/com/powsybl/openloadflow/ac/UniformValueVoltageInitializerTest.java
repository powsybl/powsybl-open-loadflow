/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
class UniformValueVoltageInitializerTest {

    /**
     * This test purpose is to ensure that
     * - Two DC buses connected to the same converter have a different initial voltage value.
     * - DC buses which are not connected to any converters are initialised at 1 pu
     */
    @Test
    void testDcBusesVoltageInitialisation() {
        // Create network
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModel();
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters().setAcDcNetwork(true)).getFirst();

        UniformValueVoltageInitializer initializer = new UniformValueVoltageInitializer();

        // Calling prepare, it shall store initial voltage of DC buses connected to a converter
        initializer.prepare(lfNetwork, ReportNode.NO_OP);

        // Get DC buses
        LfDcBus dn3p = lfNetwork.getDcBusById("dn3p_dcBus");
        LfDcBus dn3r = lfNetwork.getDcBusById("dn3r_dcBus");
        LfDcBus dn3n = lfNetwork.getDcBusById("dn3n_dcBus");
        LfDcBus dn4p = lfNetwork.getDcBusById("dn4p_dcBus");
        LfDcBus dn4r = lfNetwork.getDcBusById("dn4r_dcBus");
        LfDcBus dn4n = lfNetwork.getDcBusById("dn4n_dcBus");
        LfDcBus dnGr = lfNetwork.getDcBusById("dnGr_dcBus");

        // Check initial voltages
        assertNotEquals(initializer.getDcVoltage(dn3p), initializer.getDcVoltage(dn3r));
        assertNotEquals(initializer.getDcVoltage(dn3n), initializer.getDcVoltage(dn3r));
        assertNotEquals(initializer.getDcVoltage(dn4p), initializer.getDcVoltage(dn4r));
        assertNotEquals(initializer.getDcVoltage(dn4n), initializer.getDcVoltage(dn4r));
        assertEquals(1d, initializer.getDcVoltage(dnGr));
    }
}
