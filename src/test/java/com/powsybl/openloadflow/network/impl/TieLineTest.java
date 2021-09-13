/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.TieLineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class TieLineTest {

    @Test
    void test() {
        Network network = TieLineFactory.create();
        LfNetwork lfNetwork = LfNetwork.load(network).get(0);
        assertEquals(3, lfNetwork.getBuses().size());
    }
}
