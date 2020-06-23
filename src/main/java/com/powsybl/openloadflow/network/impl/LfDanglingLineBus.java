/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.openloadflow.network.AbstractFictitiousLfBus;
import com.powsybl.openloadflow.network.PerUnit;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBus extends AbstractFictitiousLfBus {

    private final DanglingLine danglingLine;

    private final double nominalV;

    public LfDanglingLineBus(DanglingLine danglingLine) {
        super(Networks.getPropertyV(danglingLine), Networks.getPropertyAngle(danglingLine));
        this.danglingLine = Objects.requireNonNull(danglingLine);
        nominalV = danglingLine.getTerminal().getVoltageLevel().getNominalV();
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
    public double getGenerationTargetP() {
        return !Double.isNaN(danglingLine.getGeneratorTargetP()) ? danglingLine.getGeneratorTargetP() / PerUnit.SB : 0;
    }

    @Override
    public double getGenerationTargetQ() {
        return !Double.isNaN(danglingLine.getGeneratorTargetQ()) ? danglingLine.getGeneratorTargetP() / PerUnit.SB : 0;
    }

    @Override
    public double getTargetV() {
        return danglingLine.getGeneratorTargetV();
    }

    @Override
    public boolean hasVoltageControl() {
        return danglingLine.isGeneratorVoltageRegulationOn();
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public void updateState(boolean reactiveLimits) {
        Networks.setPropertyV(danglingLine, v);
        Networks.setPropertyAngle(danglingLine, angle);
    }
}
