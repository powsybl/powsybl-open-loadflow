/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NetworkState {

    private final List<BusState> busStates;

    private final List<BranchState> branchStates;

    protected NetworkState(List<BusState> busStates, List<BranchState> branchStates) {
        this.busStates = Objects.requireNonNull(busStates);
        this.branchStates = Objects.requireNonNull(branchStates);
    }

    public static NetworkState save(LfNetwork network) {
        Objects.requireNonNull(network);
        List<BusState> busStates = ElementState.save(network.getBuses(), BusState::save);
        List<BranchState> branchStates = ElementState.save(network.getBranches(), BranchState::save);
        return new NetworkState(busStates, branchStates);
    }

    public void restore() {
        ElementState.restore(busStates);
        ElementState.restore(branchStates);
    }
}
