/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.openloadflow.network.AbstractFictitiousBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.PiModel;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBranch extends AbstractFictitiousBranch<DanglingLine> {

    protected LfDanglingLineBranch(DanglingLine danglingLine, LfBus bus1, LfBus bus2, PiModel piModel) {
        super(danglingLine, bus1, bus2, piModel);
    }

    public static LfDanglingLineBranch create(DanglingLine danglingLine, LfBus bus1, LfBus bus2) {
        Objects.requireNonNull(danglingLine);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        double nominalV = danglingLine.getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        PiModel piModel = null;
        if (danglingLine.getR() != 0 || danglingLine.getX() != 0) {
            piModel = new PiModel(danglingLine.getR() / zb, danglingLine.getX() / zb)
                    .setG1(danglingLine.getG() * zb / 2)
                    .setG2(danglingLine.getG() * zb / 2)
                    .setB1(danglingLine.getB() * zb / 2)
                    .setB2(danglingLine.getB() * zb / 2);
        }
        return new LfDanglingLineBranch(danglingLine, bus1, bus2, piModel);
    }

    @Override
    public void updateState() {
        branch.getTerminal().setP(p.eval() * PerUnit.SB);
        branch.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
