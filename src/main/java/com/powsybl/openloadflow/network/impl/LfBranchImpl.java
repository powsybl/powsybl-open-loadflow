/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.TwoWindingsTransformer;
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
public class LfBranchImpl extends AbstractLfBranch<Branch> {

    private Evaluable p1 = NAN;

    private Evaluable p2 = NAN;

    private Evaluable q1 = NAN;

    private Evaluable q2 = NAN;

    protected LfBranchImpl(Branch branch, LfBus bus1, LfBus bus2, PiModel piModel) {
        super(branch, bus1, bus2, piModel);
    }

    public static LfBranchImpl create(Branch branch, LfBus bus1, LfBus bus2) {
        Objects.requireNonNull(branch);
        double nominalV1 = branch.getTerminal1().getVoltageLevel().getNominalV();
        double nominalV2 = branch.getTerminal2().getVoltageLevel().getNominalV();
        double zb = nominalV2 * nominalV2 / PerUnit.SB;
        PiModel piModel = null;
        if (branch instanceof Line) {
            Line line = (Line) branch;
            if (line.getR() != 0 || line.getX() != 0) {
                piModel = new PiModel(line.getR() / zb, line.getX() / zb)
                        .setG1(line.getG1() * zb)
                        .setG2(line.getG2() * zb)
                        .setB1(line.getB1() * zb)
                        .setB2(line.getB2() * zb);
            }
        } else if (branch instanceof TwoWindingsTransformer) {
            TwoWindingsTransformer twt = (TwoWindingsTransformer) branch;
            if (twt.getR() != 0 || twt.getX() != 0) {
                piModel = new PiModel(Transformers.getR(twt) / zb, Transformers.getX(twt) / zb)
                        .setG1(Transformers.getG1(twt) * zb)
                        .setB1(Transformers.getB1(twt) * zb)
                        .setR1(Transformers.getRatio(twt) / nominalV2 * nominalV1)
                        .setA1(Transformers.getAngle(twt));
            }
        } else {
            throw new PowsyblException("Unsupported type of branch for flow equations for branch: " + branch.getId());
        }
        return new LfBranchImpl(branch, bus1, bus2, piModel);
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
    public void setQ1(Evaluable q1) {
        this.q1 = Objects.requireNonNull(q1);
    }

    @Override
    public void setQ2(Evaluable q2) {
        this.q2 = Objects.requireNonNull(q2);
    }

    @Override
    public void updateState() {
        branch.getTerminal1().setP(p1.eval() * PerUnit.SB);
        branch.getTerminal1().setQ(q1.eval() * PerUnit.SB);
        branch.getTerminal2().setP(p2.eval() * PerUnit.SB);
        branch.getTerminal2().setQ(q2.eval() * PerUnit.SB);
    }
}
