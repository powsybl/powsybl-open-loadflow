/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractFictitiousLfBranch extends AbstractLfBranch {

    protected Evaluable p = NAN;

    protected Evaluable q = NAN;

    protected Evaluable i = NAN;

    protected AbstractFictitiousLfBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel) {
        super(network, bus1, bus2, piModel);
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p = Objects.requireNonNull(p1);
    }

    @Override
    public Evaluable getP1() {
        return p;
    }

    @Override
    public void setP2(Evaluable p2) {
        // nothing to do
    }

    @Override
    public Evaluable getP2() {
        return NAN;
    }

    @Override
    public void setQ1(Evaluable q1) {
        this.q = Objects.requireNonNull(q1);
    }

    @Override
    public Evaluable getQ1() {
        return q;
    }

    @Override
    public void setQ2(Evaluable q2) {
        // nothing to do
    }

    @Override
    public Evaluable getQ2() {
        return NAN;
    }

    @Override
    public void setI1(Evaluable i1) {
        this.i = Objects.requireNonNull(i1);
    }

    @Override
    public Evaluable getI1() {
        return i;
    }

    @Override
    public void setI2(Evaluable i2) {
        // nothing to do
    }

    @Override
    public Evaluable getI2() {
        return NAN;
    }

}
