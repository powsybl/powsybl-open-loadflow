/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowResult {

    private final List<LfNetwork> networks;

    private final boolean ok;

    public DcLoadFlowResult(List<LfNetwork> networks, boolean ok) {
        this.networks = Objects.requireNonNull(networks);
        this.ok = ok;
    }

    public List<LfNetwork> getNetworks() {
        return networks;
    }

    public boolean isOk() {
        return ok;
    }
}
