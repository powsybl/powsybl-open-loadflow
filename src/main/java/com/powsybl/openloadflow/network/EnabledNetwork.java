/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class EnabledNetwork {

    private final Set<LfBus> buses;
    private final Set<LfBranch> branches;

    public EnabledNetwork(Set<LfBus> buses, Set<LfBranch> branches) {
        this.buses = Objects.requireNonNull(buses);
        this.branches = Objects.requireNonNull(branches);
    }

    public EnabledNetwork() {
        this(Collections.emptySet(), Collections.emptySet());
    }

    public Set<LfBus> getBuses() {
        return buses;
    }

    public Set<LfBranch> getBranches() {
        return branches;
    }

    public void apply() {
        for (LfBus bus : buses) {
            bus.setDisabled(false);
        }
        for (LfBranch branch : branches) {
            branch.setDisabled(false);
        }
    }
}
