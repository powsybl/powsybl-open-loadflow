/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class NetworkStateTest {

    @Test
    void restoreDoesNotShareTheSavedLoadDisablingStatus() {
        LfNetwork lfNetwork = Networks.load(EurostagTutorialExample1Factory.create(), new MostMeshedSlackBusSelector()).getFirst();
        LfLoad load = lfNetwork.getBuses().stream()
                .flatMap(b -> b.getLoads().stream())
                .filter(l -> l.getOriginalIds().contains("LOAD"))
                .findFirst().orElseThrow();
        NetworkState state = NetworkState.save(lfNetwork);
        for (int contingency = 1; contingency <= 2; contingency++) {
            load.setOriginalLoadDisabled("LOAD", true);
            state.restore();
            assertFalse(load.isOriginalLoadDisabled("LOAD"), "original load still disabled after restore " + contingency);
        }
    }
}
