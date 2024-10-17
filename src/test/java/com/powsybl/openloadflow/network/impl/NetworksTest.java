/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class NetworksTest {

    @Test
    void testGetRegulatingTerminal() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        assertFalse(Networks.getEquipmentRegulatingTerminal(network, "LD1").isPresent());
        assertSame(network.getTwoWindingsTransformer("TWT").getTerminal1(), Networks.getEquipmentRegulatingTerminal(network, "TWT").orElseThrow());
        assertSame(network.getTwoWindingsTransformer("TWT").getTerminal1(), Networks.getEquipmentRegulatingTerminal(network, "TWT").orElseThrow());
        assertSame(network.getGenerator("GH1").getTerminal(), Networks.getEquipmentRegulatingTerminal(network, "GH1").orElseThrow());
        assertSame(network.getShuntCompensator("SHUNT").getTerminal(), Networks.getEquipmentRegulatingTerminal(network, "SHUNT").orElseThrow());
        assertSame(network.getVscConverterStation("VSC1").getTerminal(), Networks.getEquipmentRegulatingTerminal(network, "VSC1").orElseThrow());
        assertFalse(Networks.getEquipmentRegulatingTerminal(network, "LCC1").isPresent());
        assertSame(network.getStaticVarCompensator("SVC").getTerminal(), Networks.getEquipmentRegulatingTerminal(network, "SVC").orElseThrow());

        Network network3wt = ThreeWindingsTransformerNetworkFactory.create();
        assertSame(network3wt.getLoad("LOAD_33").getTerminal(), Networks.getEquipmentRegulatingTerminal(network3wt, "3WT").orElseThrow());

        Network network3wtWithoutTapChanger = EurostagTutorialExample1Factory.createWith3wTransformer();
        assertFalse(Networks.getEquipmentRegulatingTerminal(network3wtWithoutTapChanger, "NGEN_V2_NHV1").isPresent());
    }
}
