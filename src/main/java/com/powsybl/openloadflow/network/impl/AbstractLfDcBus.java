/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractLfDcBus extends AbstractElement implements LfDcBus {

    protected double v;

    protected final double nominalV;

    // Initial voltage used by the UniformValueVoltageInitializer. Only set for DC buses connected to a converter.
    private Double initialVoltage = null;

    protected AbstractLfDcBus(LfNetwork network, double nominalV, double v) {
        super(network);
        this.nominalV = nominalV;
        this.v = v;
    }

    @Override
    public ElementType getType() {
        return ElementType.DC_BUS;
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
    public void setInitialVoltage(double v) {
        initialVoltage = v;
    }

    @Override
    public double getInitialVoltage() {
        assert initialVoltage != null;
        return initialVoltage;
    }

    @Override
    public boolean isInitialVoltageSet() {
        return initialVoltage != null;
    }
}
