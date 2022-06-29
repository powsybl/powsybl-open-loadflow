/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfInternalConnection extends AbstractLfSwitch {

    public LfInternalConnection(LfNetwork network, LfBus bus1, LfBus bus2) {
        super(network, bus1, bus2);
    }

    @Override
    public String getId() {
        return "InternalConnection-" + getBus1().getId() + "-" + getBus2().getId();
    }
}
