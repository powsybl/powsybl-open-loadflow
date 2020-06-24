/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBus extends AbstractLfBus {

    private final DanglingLine danglingLine;

    private final double nominalV;

    public LfDanglingLineBus(DanglingLine danglingLine) {
        super(Networks.getPropertyV(danglingLine), Networks.getPropertyAngle(danglingLine));
        this.danglingLine = Objects.requireNonNull(danglingLine);
        nominalV = danglingLine.getTerminal().getVoltageLevel().getNominalV();
        loadTargetP += danglingLine.getP0();
        loadTargetQ += danglingLine.getQ0();
        if (danglingLine.isGeneratorVoltageRegulationOn()) {
            this.targetV = danglingLine.getGeneratorTargetV();
            this.voltageControl = true;
            this.voltageControlCapacility = true;
        } else {
            if (!Double.isNaN(danglingLine.getGeneratorTargetQ())) {
                generationTargetQ += danglingLine.getGeneratorTargetQ();
            }
        }
        if (!Double.isNaN(danglingLine.getGeneratorTargetP())) {
            generators.add(new LfDanglingLineGenerator(danglingLine));
        }
    }

    @Override
    public String getId() {
        return danglingLine.getId() + "_BUS";
    }

    @Override
    public boolean isFictitious() {
        return true;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public void updateState(boolean reactiveLimits) {
        Networks.setPropertyV(danglingLine, v);
        Networks.setPropertyAngle(danglingLine, angle);

        super.updateState(reactiveLimits);
    }
}
