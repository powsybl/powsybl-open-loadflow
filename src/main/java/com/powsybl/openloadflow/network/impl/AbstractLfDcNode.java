/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.*;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractLfDcNode extends AbstractElement implements LfDcNode {

    protected double v;

    protected final double nominalV;

    protected boolean isNeutralPole = false;

    protected AbstractLfDcNode(LfNetwork network, double nominalV, double v) {
        super(network);
        this.nominalV = nominalV;
        this.v = v;
    }

    @Override
    public ElementType getType() {
        return ElementType.DC_NODE;
    }

    @Override
    public double getV() {
        return v / nominalV;
    }

    @Override
    public void setV(double v) {
        this.v = v * nominalV;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public boolean isNeutralPole() {
        return isNeutralPole;
    }

    @Override
    public void setNeutralPole(boolean isNeutralPole) {
        this.isNeutralPole = isNeutralPole;
    }
}
