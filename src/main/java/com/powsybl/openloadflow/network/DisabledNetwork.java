/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Collections;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public record DisabledNetwork(Set<LfBus> buses, Set<LfBranch> branches, Set<LfHvdc> hvdcs) {

    public DisabledNetwork() {
        this(Collections.emptySet(), Collections.emptySet());
    }

    public DisabledNetwork(Set<LfBus> buses, Set<LfBranch> branches) {
        this(buses, branches, Collections.emptySet());
    }
}
