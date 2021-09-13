/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.TieLine;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfTieLineBus extends AbstractLfBus {

    private final TieLine tieLine;

    private final double nominalV;

    public LfTieLineBus(LfNetwork network, TieLine tieLine) {
        super(network, Networks.getPropertyV(tieLine), Networks.getPropertyAngle(tieLine));
        this.tieLine = Objects.requireNonNull(tieLine);
        nominalV = tieLine.getTerminal1().getVoltageLevel().getNominalV();
    }

    public static String getId(TieLine tieLine) {
        return tieLine.getId() + "_BUS";
    }

    @Override
    public String getId() {
        return getId(tieLine);
    }

    @Override
    public String getVoltageLevelId() {
        // side 1 is arbitrary...
        return tieLine.getTerminal1().getVoltageLevel().getId();
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
    public void updateState(boolean reactiveLimits, boolean writeSlackBus, boolean distributedOnConformLoad, boolean loadPowerFactorConstant) {
        Networks.setPropertyV(tieLine, v.eval() * getNominalV());
        Networks.setPropertyAngle(tieLine, angle);

        super.updateState(reactiveLimits, writeSlackBus, false, false);
    }
}
