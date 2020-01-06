/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.openloadflow.network.AbstractFictitiousBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.PiModel;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfLegBranch extends AbstractFictitiousBranch {

    private final ThreeWindingsTransformer twt;

    private final ThreeWindingsTransformer.Leg leg;

    protected LfLegBranch(LfBus bus1, LfBus bus0, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        super(twt, bus1, bus0, new PiModel(leg.getR(), leg.getX())
                            .setR1(Transformers.getRatioLeg(twt, leg))
                            .setG2(leg.getG())
                            .setB2(leg.getB()),
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

    @Override
    public String getId() {
        return branch.getId() + " leg";
    }

    @Override
    public void updateState() {
        branch.getLeg1().getTerminal().setP(p.eval() * PerUnit.SB);
        branch.getLeg1().getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
