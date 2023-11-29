/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A LfLoad is an aggregation of several classical network loads. The loss of a network load is modeled by a power
 * shift, that is a partial shift of the full LfLoad.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfLostLoad {

    private final PowerShift powerShift = new PowerShift();

    private final Set<String> ids = new LinkedHashSet<>();

    public PowerShift getPowerShift() {
        return powerShift;
    }

    public Set<String> getOriginalIds() {
        return ids;
    }

    @Override
    public String toString() {
        return "LfLostLoad(" +
                "ids=" + ids +
                ", powerShift=" + powerShift +
                ')';
    }
}
