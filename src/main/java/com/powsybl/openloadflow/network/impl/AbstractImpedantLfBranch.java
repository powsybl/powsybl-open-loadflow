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

    protected Evaluable openP1 = NAN;

    protected Evaluable openQ1 = NAN;

    protected Evaluable openI1 = NAN;

    protected Evaluable openP2 = NAN;

    protected Evaluable openQ2 = NAN;

    protected Evaluable openI2 = NAN;

    protected Evaluable closedP1 = NAN;

    protected Evaluable closedQ1 = NAN;

    protected Evaluable closedI1 = NAN;

    protected Evaluable closedP2 = NAN;

    protected Evaluable closedQ2 = NAN;

    protected Evaluable closedI2 = NAN;

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

    @Override
    public Evaluable getOpenP1() {
        return openP1;
    }

    @Override
    public void setOpenP1(Evaluable openP1) {
        this.openP1 = Objects.requireNonNull(openP1);
    }

    @Override
    public Evaluable getOpenQ1() {
        return openQ1;
    }

    @Override
    public void setOpenQ1(Evaluable openQ1) {
        this.openQ1 = Objects.requireNonNull(openQ1);
    }

    @Override
    public Evaluable getOpenI1() {
        return openI1;
    }

    @Override
    public void setOpenI1(Evaluable openI1) {
        this.openI1 = Objects.requireNonNull(openI1);
    }

    @Override
    public Evaluable getOpenP2() {
        return openP2;
    }

    @Override
    public void setOpenP2(Evaluable openP2) {
        this.openP2 = Objects.requireNonNull(openP2);
    }

    @Override
    public Evaluable getOpenQ2() {
        return openQ2;
    }

    @Override
    public void setOpenQ2(Evaluable openQ2) {
        this.openQ2 = Objects.requireNonNull(openQ2);
    }

    @Override
    public Evaluable getOpenI2() {
        return openI2;
    }

    @Override
    public void setOpenI2(Evaluable openI2) {
        this.openI2 = Objects.requireNonNull(openI2);
    }

    @Override
    public Evaluable getClosedP1() {
        return closedP1;
    }

    @Override
    public void setClosedP1(Evaluable closedP1) {
        this.closedP1 = Objects.requireNonNull(closedP1);
    }

    @Override
    public Evaluable getClosedQ1() {
        return closedQ1;
    }

    @Override
    public void setClosedQ1(Evaluable closedQ1) {
        this.closedQ1 = Objects.requireNonNull(closedQ1);
    }

    @Override
    public Evaluable getClosedI1() {
        return closedI1;
    }

    @Override
    public void setClosedI1(Evaluable closedI1) {
        this.closedI1 = Objects.requireNonNull(closedI1);
    }

    @Override
    public Evaluable getClosedP2() {
        return closedP2;
    }

    @Override
    public void setClosedP2(Evaluable closedP2) {
        this.closedP2 = Objects.requireNonNull(closedP2);
    }

    @Override
    public Evaluable getClosedQ2() {
        return closedQ2;
    }

    @Override
    public void setClosedQ2(Evaluable closedQ2) {
        this.closedQ2 = Objects.requireNonNull(closedQ2);
    }

    @Override
    public Evaluable getClosedI2() {
        return closedI2;
    }

    @Override
    public void setClosedI2(Evaluable closedI2) {
        this.closedI2 = Objects.requireNonNull(closedI2);
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
