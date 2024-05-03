/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class DisabledNetwork {

    private final Set<LfBus> buses;
    private final Map<LfBranch, DisabledBranchStatus> branchesStatus;
    private final Set<LfHvdc> hvdcs;

    public DisabledNetwork(Set<LfBus> buses, Map<LfBranch, DisabledBranchStatus> branchesStatus, Set<LfHvdc> hvdcs) {
        this.buses = Objects.requireNonNull(buses);
        this.branchesStatus = Objects.requireNonNull(branchesStatus);
        this.hvdcs = Objects.requireNonNull(hvdcs);
    }

    public DisabledNetwork(Set<LfBus> buses, Set<LfBranch> branches, Set<LfHvdc> hvdcs) {
        this(buses, Objects.requireNonNull(branches).stream().collect(Collectors.toMap(Function.identity(), branch -> DisabledBranchStatus.BOTH_SIDES)), hvdcs);
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
        return branchesStatus.keySet();
    }

    public Map<LfBranch, DisabledBranchStatus> getBranchesStatus() {
        return branchesStatus;
    }

    public Set<LfHvdc> getHvdcs() {
        return hvdcs;
    }
}
