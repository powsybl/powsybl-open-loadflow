/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowResult {

    private final LfNetwork network;

    private final boolean ok;

    public DcLoadFlowResult(LfNetwork network, boolean ok) {
        this.network = Objects.requireNonNull(network);
        this.ok = ok;
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public boolean isOk() {
        return ok;
    }
}
