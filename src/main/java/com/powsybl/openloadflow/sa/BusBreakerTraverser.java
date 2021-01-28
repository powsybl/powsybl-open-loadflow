/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.VoltageLevel;

import java.util.Set;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 * @author Mathieu Bague <mathieu.bague at rte-france.com>
 */
class BusBreakerTraverser implements VoltageLevel.TopologyTraverser {

    private final Set<Terminal> traversedTerminals;

    BusBreakerTraverser(Set<Terminal> traversedTerminals) {
        this.traversedTerminals = traversedTerminals;
    }

    @Override
    public boolean traverse(Terminal terminal, boolean connected) {
        // we have no idea what kind of switch it was in the initial node/breaker topology
        // so to keep things simple we do not propagate the fault
        if (connected) {
            traversedTerminals.add(terminal);
        }
        return false;
    }

    @Override
    public boolean traverse(Switch aSwitch) {
        throw new AssertionError("Should not be called");
    }

}
