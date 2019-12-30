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
public class LfLeg2or3Branch extends AbstractFictitiousBranch<ThreeWindingsTransformer> {

    private final ThreeWindingsTransformer.Leg2or3 leg2or3;

    protected LfLeg2or3Branch(LfBus bus2or3, LfBus bus0, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg2or3 leg2or3) {
        super(twt, bus2or3, bus0, new PiModel(leg2or3.getR(), leg2or3.getX())
                                .setR1(Transformers.getRatio2or3(twt, leg2or3)),
                leg2or3.getTerminal().getVoltageLevel().getNominalV(),
                twt.getLeg1().getTerminal().getVoltageLevel().getNominalV());
        this.leg2or3 = leg2or3;
    }

    public static LfLeg2or3Branch create(LfBus bus2or3, LfBus bus0, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg2or3 leg2or3) {
        Objects.requireNonNull(bus0);
        Objects.requireNonNull(twt);
        Objects.requireNonNull(leg2or3);
        return new LfLeg2or3Branch(bus2or3, bus0, twt, leg2or3);
    }

    @Override
    public String getId() {
        return branch.getId() + " leg 2 or 3";
    }

    @Override
    public void updateState() {
        leg2or3.getTerminal().setP(p.eval() * PerUnit.SB);
        leg2or3.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
