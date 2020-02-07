/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.openloadflow.network.AbstractLfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.PiModel;
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

    protected LfDanglingLineBranch(DanglingLine danglingLine, LfBus bus1, LfBus bus2) {
        super(bus1, bus2, new PiModel(danglingLine.getR(), danglingLine.getX())
                            .setG1(danglingLine.getG() / 2)
                            .setG2(danglingLine.getG() / 2)
                            .setB1(danglingLine.getB() / 2)
                            .setB2(danglingLine.getB() / 2),
                danglingLine.getId(),
                danglingLine.getTerminal().getVoltageLevel().getNominalV(),
                danglingLine.getTerminal().getVoltageLevel().getNominalV());
        this.danglingLine = danglingLine;
    }

    public static LfDanglingLineBranch create(DanglingLine danglingLine, LfBus bus1, LfBus bus2) {
        Objects.requireNonNull(danglingLine);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        return new LfDanglingLineBranch(danglingLine, bus1, bus2);
    }

    @Override
    public String getId() {
        return danglingLine.getId();
    }

    @Override
    public void setP1(Evaluable p1) {
        // nothing to do
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p = Objects.requireNonNull(p2);
    }

    @Override
    public void setQ1(Evaluable q1) {
        // nothing to do
    }

    @Override
    public void setQ2(Evaluable q2) {
        this.q = Objects.requireNonNull(q2);
    }

    @Override
    public void updateState() {
        danglingLine.getTerminal().setP(-p.eval() * PerUnit.SB);
        danglingLine.getTerminal().setQ(-q.eval() * PerUnit.SB);
    }
}
