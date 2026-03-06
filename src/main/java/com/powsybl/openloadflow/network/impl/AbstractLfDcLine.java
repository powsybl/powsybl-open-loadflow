/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractLfDcLine extends AbstractElement implements LfDcLine {

    protected final LfDcBus dcBus1;

    protected final LfDcBus dcBus2;

    protected Evaluable p1 = NAN;

    protected Evaluable i1 = NAN;

    protected Evaluable p2 = NAN;

    protected Evaluable i2 = NAN;

    private final double r;

    protected AbstractLfDcLine(LfNetwork network, LfDcBus dcBus1, LfDcBus dcBus2, double r) {
        super(network);
        this.dcBus1 = dcBus1;
        this.dcBus2 = dcBus2;
        this.r = r;
    }

    @Override
    public LfDcBus getDcBus1() {
        return dcBus1;
    }

    @Override
    public LfDcBus getDcBus2() {
        return dcBus2;
    }

    @Override
    public ElementType getType() {
        return ElementType.DC_LINE;
    }

    @Override
    public double getR() {
        return r;
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p2 = Objects.requireNonNull(p2);
    }

    @Override
    public void setI1(Evaluable i1) {
        this.i1 = Objects.requireNonNull(i1);
    }

    @Override
    public void setI2(Evaluable i2) {
        this.i2 = Objects.requireNonNull(i2);
    }
}
