/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network.impl;

import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.loadflow.simple.network.AbstractFictitiousBranch;
import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.PerUnit;
import com.powsybl.loadflow.simple.network.PiModel;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfLeg1Branch extends AbstractFictitiousBranch {

    private final ThreeWindingsTransformer.Leg1 leg1;

    protected LfLeg1Branch(LfBus bus1, LfBus bus0, ThreeWindingsTransformer.Leg1 leg1) {
        super(bus1, bus0, new PiModel(leg1.getR(), leg1.getX())
                            .setG2(leg1.getG())
                            .setB2(leg1.getB()),
                leg1.getTerminal().getVoltageLevel().getNominalV(),
                leg1.getTerminal().getVoltageLevel().getNominalV());
        this.leg1 = leg1;
    }

    public static LfLeg1Branch create(LfBus bus1, LfBus bus0, ThreeWindingsTransformer.Leg1 leg1) {
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus0);
        Objects.requireNonNull(leg1);
        return new LfLeg1Branch(bus1, bus0, leg1);
    }

    @Override
    public void updateState() {
        leg1.getTerminal().setP(p.eval() * PerUnit.SB);
        leg1.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
