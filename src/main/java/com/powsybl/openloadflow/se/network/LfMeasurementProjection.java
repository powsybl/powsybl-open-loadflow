/**
 * Copyright (c) 2026, Kickstage (https://kickstage.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.network;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the location of a meter onto the elements of an {@link LfNetwork}.
 *
 * <p>Elements are keyed by identity, never by their pair of end buses: parallel circuits
 * share a bus pair, and keying on the pair silently collapses them onto whichever branch
 * was indexed last.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public class LfMeasurementProjection {

    private final LfNetwork network;

    private final Map<String, LfBus> busesById = new HashMap<>();

    private final Map<String, LfBranch> branchesById = new HashMap<>();

    public LfMeasurementProjection(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
        for (LfBus bus : network.getBuses()) {
            busesById.put(bus.getId(), bus);
        }
        for (LfBranch branch : network.getBranches()) {
            branchesById.put(branch.getId(), branch);
        }
    }

    public LfNetwork getNetwork() {
        return network;
    }

    /**
     * The bus carrying the given identifier, empty if it is not part of this network:
     * it may belong to another island, or be out of service.
     */
    public Optional<LfBus> findBus(String busId) {
        return Optional.ofNullable(busesById.get(Objects.requireNonNull(busId)));
    }

    public Optional<LfBranch> findBranch(String branchId) {
        return Optional.ofNullable(branchesById.get(Objects.requireNonNull(branchId)));
    }

    /**
     * Which side of the branch faces the given bus: 1 or 2.
     *
     * <p>A branch's orientation in the computational model need not match the orientation
     * the measurement was recorded with, so the side is resolved rather than assumed.</p>
     *
     * @throws IllegalArgumentException if the bus is not an end of the branch
     */
    public static int sideOf(LfBranch branch, LfBus bus) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(bus);
        LfBus bus1 = branch.getBus1();
        if (bus1 != null && bus1.getNum() == bus.getNum()) {
            return 1;
        }
        LfBus bus2 = branch.getBus2();
        if (bus2 != null && bus2.getNum() == bus.getNum()) {
            return 2;
        }
        throw new IllegalArgumentException("Bus " + bus.getId() + " is not an end of branch " + branch.getId());
    }
}
