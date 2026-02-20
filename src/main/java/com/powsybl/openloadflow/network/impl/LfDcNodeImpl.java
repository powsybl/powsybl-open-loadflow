/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DcNode;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;

import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class LfDcNodeImpl extends AbstractLfDcNode {

    private final Ref<DcNode> dcNodeRef;

    private boolean isGrounded = false;

    public LfDcNodeImpl(DcNode dcNode, LfNetwork network, double nominalV, LfNetworkParameters parameters) {
        super(network, nominalV, dcNode.getV());
        this.dcNodeRef = Ref.create(dcNode, parameters.isCacheEnabled());
    }

    public static LfDcNodeImpl create(DcNode dcNode, LfNetwork network, LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(dcNode);
        Objects.requireNonNull(parameters);
        return new LfDcNodeImpl(dcNode, network, dcNode.getNominalV(), parameters);
    }

    private DcNode getDcNode() {
        return dcNodeRef.get();
    }

    @Override
    public String getId() {
        return getDcNode().getId();
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
        var dcNode = getDcNode();
        dcNode.setV(v);
    }
}
