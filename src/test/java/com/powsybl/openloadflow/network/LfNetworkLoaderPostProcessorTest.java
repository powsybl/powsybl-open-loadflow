/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class LfNetworkLoaderPostProcessorTest {

    private static final String PP_1 = "PP1";
    private static final String PP_2 = "PP2";

    private Network network;
    private LfNetworkParameters parameters;

    private LfNetworkLoaderPostProcessor pp1;
    private LfNetworkLoaderPostProcessor pp2;

    private boolean pp1Activated;
    private boolean pp2Activated;

    @BeforeEach
    void setUp() {
        network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        parameters = new LfNetworkParameters();

        pp1 = new AbstractLfNetworkLoaderPostProcessor() {
            @Override
            public String getName() {
                return PP_1;
            }

            @Override
            public void onBusAdded(Object element, LfBus lfBus) {
                pp1Activated = true;
            }
        };

        pp2 = new AbstractLfNetworkLoaderPostProcessor() {
            @Override
            public String getName() {
                return PP_2;
            }

            @Override
            public LoadingPolicy getLoadingPolicy() {
                return LoadingPolicy.SELECTION;
            }

            @Override
            public void onBusAdded(Object element, LfBus lfBus) {
                pp2Activated = true;
            }
        };

        pp1Activated = false;
        pp2Activated = false;
    }

    @Test
    void test1() {
        LfNetwork.load(network, new LfNetworkLoaderImpl(() -> List.of(pp1, pp2)), parameters);
        assertTrue(pp1Activated);
        assertFalse(pp2Activated);
    }

    @Test
    void test2() {
        parameters.setLoaderPostProcessorSelection(Set.of(PP_2));
        LfNetwork.load(network, new LfNetworkLoaderImpl(() -> List.of(pp1, pp2)), parameters);
        assertTrue(pp1Activated);
        assertTrue(pp2Activated);
    }
}
