/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DcBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;

import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class LfDcBusImpl extends AbstractLfDcBus {

    private final Ref<DcBus> dcBusRef;

    private boolean isGrounded = false;

    public LfDcBusImpl(DcBus dcBus, LfNetwork network, double nominalV, LfNetworkParameters parameters) {
        super(network, nominalV, dcBus.getV());
        this.dcBusRef = Ref.create(dcBus, parameters.isCacheEnabled());
    }

    public static LfDcBusImpl create(DcBus dcBus, LfNetwork network, LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(dcBus);
        Objects.requireNonNull(parameters);
        // Previous check has already validated that all DC nodes have the same nominal voltage
        double nominalV = dcBus.getDcNodeStream().toList().getFirst().getNominalV();
        return new LfDcBusImpl(dcBus, network, nominalV, parameters);
    }

    private DcBus getDcBus() {
        return dcBusRef.get();
    }

    @Override
    public String getId() {
        return getDcBus().getId();
    }

    @Override
    public boolean isGrounded() {
        return isGrounded;
    }

    @Override
    public void setGround(boolean isGrounded) {
        this.isGrounded = isGrounded;
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var dcBus = getDcBus();
        dcBus.setV(v);
    }
}
