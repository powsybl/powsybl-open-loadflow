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
public class LfLeg1Branch extends AbstractFictitiousBranch<ThreeWindingsTransformer> {

    protected LfLeg1Branch(LfBus bus1, LfBus bus0, ThreeWindingsTransformer twt) {
        super(twt, bus1, bus0, new PiModel(twt.getLeg1().getR(), twt.getLeg1().getX())
                            .setG2(twt.getLeg1().getG())
                            .setB2(twt.getLeg1().getB()),
                twt.getLeg1().getTerminal().getVoltageLevel().getNominalV(),
                twt.getLeg1().getTerminal().getVoltageLevel().getNominalV());
    }

    public static LfLeg1Branch create(LfBus bus1, LfBus bus0, ThreeWindingsTransformer twt) {
        Objects.requireNonNull(bus0);
        Objects.requireNonNull(twt);
        return new LfLeg1Branch(bus1, bus0, twt);
    }

    @Override
    public String getId() {
        return branch.getId() + " leg1";
    }

    @Override
    public void updateState() {
        branch.getLeg1().getTerminal().setP(p.eval() * PerUnit.SB);
        branch.getLeg1().getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
