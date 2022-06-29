/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Switch;
import com.powsybl.openloadflow.network.AbstractLfSwitch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfSwitch extends AbstractLfSwitch {

    private final Switch aSwitch;

    public LfSwitch(LfNetwork network, LfBus bus1, LfBus bus2, Switch aSwitch) {
        super(network, bus1, bus2);
        this.aSwitch = Objects.requireNonNull(aSwitch);
    }

    @Override
    public String getId() {
        return aSwitch.getId();
    }
}
