/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractImpedantLfBranch extends AbstractLfBranch {

    protected Evaluable p1 = NAN;

    protected Evaluable q1 = NAN;

    protected Evaluable i1 = NAN;

    protected Evaluable p2 = NAN;

    protected Evaluable q2 = NAN;

    protected Evaluable i2 = NAN;

    protected AbstractImpedantLfBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, LfNetworkParameters parameters) {
        super(network, bus1, bus2, piModel, parameters);
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    @Override
    public Evaluable getP1() {
        return p1;
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p2 = Objects.requireNonNull(p2);
    }

    @Override
    public Evaluable getP2() {
        return p2;
    }

    @Override
    public void setQ1(Evaluable q1) {
        this.q1 = Objects.requireNonNull(q1);
    }

    @Override
    public Evaluable getQ1() {
        return q1;
    }

    @Override
    public void setQ2(Evaluable q2) {
        this.q2 = Objects.requireNonNull(q2);
    }

    @Override
    public Evaluable getQ2() {
        return q2;
    }

    @Override
    public void setI1(Evaluable i1) {
        this.i1 = Objects.requireNonNull(i1);
    }

    @Override
    public Evaluable getI1() {
        return i1;
    }

    @Override
    public void setI2(Evaluable i2) {
        this.i2 = Objects.requireNonNull(i2);
    }

    @Override
    public Evaluable getI2() {
        return i2;
    }

    protected double getV1() {
        return getBus1() != null ? getBus1().getV() : Double.NaN;
    }

    protected double getV2() {
        return getBus2() != null ? getBus2().getV() : Double.NaN;
    }

    protected double getAngle1() {
        return getBus1() != null ? getBus1().getAngle() : Double.NaN;
    }

    protected double getAngle2() {
        return getBus2() != null ? getBus2().getAngle() : Double.NaN;
    }
}
