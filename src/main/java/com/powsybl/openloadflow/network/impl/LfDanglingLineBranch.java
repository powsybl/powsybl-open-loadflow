/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBranch extends AbstractLfBranch {

    private final DanglingLine danglingLine;

    private Evaluable p = NAN;

    private Evaluable q = NAN;

    protected LfDanglingLineBranch(LfBus bus1, LfBus bus2, PiModel piModel, DanglingLine danglingLine) {
        super(bus1, bus2, piModel);
        this.danglingLine = danglingLine;
    }

    public static LfDanglingLineBranch create(DanglingLine danglingLine, LfBus bus1, LfBus bus2) {
        Objects.requireNonNull(danglingLine);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        double nominalV = danglingLine.getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        PiModel piModel = new SimplePiModel()
                .setR(danglingLine.getR() / zb)
                .setX(danglingLine.getX() / zb)
                .setG1(danglingLine.getG() / 2 * zb)
                .setG2(danglingLine.getG() / 2 * zb)
                .setB1(danglingLine.getB() / 2 * zb)
                .setB2(danglingLine.getB() / 2 * zb);
        return new LfDanglingLineBranch(bus1, bus2, piModel, danglingLine);
    }

    @Override
    public String getId() {
        return danglingLine.getId();
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p = Objects.requireNonNull(p1);
    }

    @Override
    public void setP2(Evaluable p2) {
        // nothing to do
    }

    @Override
    public void setQ1(Evaluable q1) {
        this.q = Objects.requireNonNull(q1);
    }

    @Override
    public void setQ2(Evaluable q2) {
        // nothing to do
    }

    @Override
    public double getI1() {
        return getBus1() != null ? Math.hypot(p.eval(), q.eval())
            / (Math.sqrt(3.) * getBus1().getV() / 1000) : Double.NaN;
    }

    @Override
    public double getI2() {
        return Double.NaN;
    }

    @Override
    public double getPermanentLimit1() {
        return danglingLine.getCurrentLimits() != null ? danglingLine.getCurrentLimits().getPermanentLimit() * getBus1().getNominalV() / PerUnit.SB : Double.NaN;
    }

    @Override
    public double getPermanentLimit2() {
        return Double.NaN;
    }

    @Override
    public void updateState(boolean phaseRegulation) {
        danglingLine.getTerminal().setP(p.eval() * PerUnit.SB);
        danglingLine.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
