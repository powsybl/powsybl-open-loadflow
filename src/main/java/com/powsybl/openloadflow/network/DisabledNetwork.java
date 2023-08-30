/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class DisabledNetwork {

    private final Set<LfBus> buses;
    private final Set<LfBranch> branches;
    private final Set<LfHvdc> hvdcs;

    public DisabledNetwork(Set<LfBus> buses, Set<LfBranch> branches, Set<LfHvdc> hvdcs) {
        this.buses = Objects.requireNonNull(buses);
        this.branches = Objects.requireNonNull(branches);
        this.hvdcs = Objects.requireNonNull(hvdcs);
    }

    public DisabledNetwork() {
        this(Collections.emptySet(), Collections.emptySet());
    }

    public DisabledNetwork(Set<LfBus> buses, Set<LfBranch> branches) {
        this(buses, branches, Collections.emptySet());
    }

    public Set<LfBus> getBuses() {
        return buses;
    }

    public Set<LfBranch> getBranches() {
        return branches;
    }

    public Set<LfHvdc> getHvdcs() {
        return hvdcs;
    }
}
