/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.loadflow.simple.network.AbstractFictitiousBranch;
import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.PerUnit;
import com.powsybl.loadflow.simple.network.PiModel;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBranch extends AbstractFictitiousBranch {

    private final DanglingLine danglingLine;

    protected LfDanglingLineBranch(DanglingLine danglingLine, LfBus bus1, LfBus bus2) {
        super(bus1, bus2, new PiModel(danglingLine.getR(), danglingLine.getX())
                            .setG1(danglingLine.getG() / 2)
                            .setG2(danglingLine.getG() / 2)
                            .setB1(danglingLine.getB() / 2)
                            .setB2(danglingLine.getB() / 2),
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
    public void updateState() {
        danglingLine.getTerminal().setP(p.eval() * PerUnit.SB);
        danglingLine.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
