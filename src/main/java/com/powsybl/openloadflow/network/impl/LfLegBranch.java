/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ThreeWindingsTransformer;
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
public class LfLegBranch extends AbstractLfBranch {

    private final ThreeWindingsTransformer twt;

    private final ThreeWindingsTransformer.Leg leg;

    private Evaluable p = NAN;

    private Evaluable q = NAN;

    protected LfLegBranch(LfBus bus1, LfBus bus0, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        super(bus1, bus0, new PiModel(Transformers.getR(leg), Transformers.getX(leg))
                        .setG1(Transformers.getG1(leg))
                        .setB1(Transformers.getB1(leg))
                        .setR1(Transformers.getRatioLeg(twt, leg))
                        .setA1(Transformers.getAngleLeg(leg)),
                twt.getId(),
                leg.getTerminal().getVoltageLevel().getNominalV(),
                twt.getRatedU0()); // Star bus.
        this.twt = twt;
        this.leg = leg;
    }

    public static LfLegBranch create(LfBus bus1, LfBus bus0, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        Objects.requireNonNull(bus0);
        Objects.requireNonNull(twt);
        Objects.requireNonNull(leg);
        return new LfLegBranch(bus1, bus0, twt, leg);
    }

    private int getLegNum() {
        if (leg == twt.getLeg1()) {
            return 1;
        } else if (leg == twt.getLeg2()) {
            return 2;
        } else {
            return 3;
        }
    }

    @Override
    public String getId() {
        return twt.getId() + "_leg_" + getLegNum();
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
    public void updateState() {
        leg.getTerminal().setP(p.eval() * PerUnit.SB);
        leg.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
