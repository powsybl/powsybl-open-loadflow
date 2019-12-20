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

    protected LfLeg1Branch(ThreeWindingsTransformer twt, LfBus bus1, LfBus bus0, PiModel piModel) {
        super(twt, bus1, bus0, piModel);
    }

    public static LfLeg1Branch create(ThreeWindingsTransformer twt, LfBus bus1, LfBus bus0) {
        Objects.requireNonNull(bus0);
        Objects.requireNonNull(twt);
        double nominalV = twt.getLeg1().getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        ThreeWindingsTransformer.Leg1 leg1 = twt.getLeg1();
        PiModel piModel = null;
        if (leg1.getR() != 0 || leg1.getX() != 0) {
            piModel = new PiModel(leg1.getR() / zb, leg1.getX() / zb)
                    .setG2(leg1.getG() * zb)
                    .setB2(leg1.getB() * zb);
        }
        return new LfLeg1Branch(twt, bus1, bus0, piModel);
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
