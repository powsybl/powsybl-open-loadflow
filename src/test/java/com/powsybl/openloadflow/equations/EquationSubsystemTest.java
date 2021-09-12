/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.network.LfNetwork;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class EquationSubsystemTest {

    @Test
    void test() {
        Network network = EurostagTutorialExample1Factory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network);
        LfNetwork lfNetwork = lfNetworks.get(0);

        var equationSystem = AcEquationSystem.create(lfNetwork);
        var equationSubsystem = EquationSubsystem.filterBuses(equationSystem, Set.of(1));

    }
}