/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class NetworkActivations {
    private final DisabledNetwork disabledNetwork;
    private final EnabledNetwork enabledNetwork;

    public NetworkActivations(DisabledNetwork disabledNetwork, EnabledNetwork enabledNetwork) {
        this.disabledNetwork = Objects.requireNonNull(disabledNetwork);
        this.enabledNetwork = Objects.requireNonNull(enabledNetwork);
    }

    public DisabledNetwork getDisabledNetwork() {
        return disabledNetwork;
    }

    public EnabledNetwork getEnabledNetwork() {
        return enabledNetwork;
    }

    public void apply() {
        disabledNetwork.apply();
        enabledNetwork.apply();
    }
}
