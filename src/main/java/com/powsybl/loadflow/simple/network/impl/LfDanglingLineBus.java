/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.loadflow.simple.network.AbstractFictitiousLfBus;
import com.powsybl.loadflow.simple.network.PerUnit;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBus extends AbstractFictitiousLfBus {

    private final DanglingLine danglingLine;

    public LfDanglingLineBus(DanglingLine danglingLine, int num) {
        super(num);
        this.danglingLine = Objects.requireNonNull(danglingLine);
    }

    @Override
    public String getId() {
        return danglingLine.getId() + "_BUS";
    }

    @Override
    public double getLoadTargetP() {
        return danglingLine.getP0() / PerUnit.SB;
    }

    @Override
    public double getLoadTargetQ() {
        return danglingLine.getQ0() / PerUnit.SB;
    }

    @Override
    public double getNominalV() {
        return danglingLine.getTerminal().getVoltageLevel().getNominalV();
    }

    @Override
    public int getNeighbors() {
        return 1;
    }
}
