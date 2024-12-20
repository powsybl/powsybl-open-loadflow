/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;

/**
 * <p>2 bus test network:</p>
 *<pre>
 *      0 MW, 0 MVar
 *   1 ===== 1pu
 *       |
 *       |
 *       | 0.1j
 *       |
 *       |
 *   2 ===== 1pu
 *      200 MW, 100 MVar
 *</pre>
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class TwoBusNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network create() {
        Network network = Network.create("2-bus", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b2, "l1", 2, 1);
        createLine(network, b1, b2, "l12", 0.1f);
        return network;
    }

    public static Network createZeroImpedanceToShuntCompensator() {
        Network network = create();
        Bus b2 = network.getBusBreakerView().getBus("b2");
        Bus b3 = createOtherBus(network, "b3", b2.getVoltageLevel().getId());
        createLine(network, b2, b3, "l23", 0.0); // zero impedance
        b3.getVoltageLevel().newShuntCompensator()
                .setId("sc")
                .setName("sc")
                .setConnectableBus(b3.getId())
                .setBus(b3.getId())
                .setSectionCount(1)
                .newLinearModel()
                .setBPerSection(-2.0)
                .setMaximumSectionCount(1)
                .add()
                .add();
        return network;
    }

    public static Network createWithAThirdBus() {
        Network network = create();
        // On a different and higher voltage level then b1 and b2
        Bus b3 = createBus(network, "b3", 1.5);
        Bus b2 = network.getBusBreakerView().getBus("b2");
        createLine(network, b2, b3, "l23", 0.1f);
        return network;
    }
}
