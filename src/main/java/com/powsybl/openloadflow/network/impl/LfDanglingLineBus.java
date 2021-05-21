/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBus extends AbstractLfBus {

    private final DanglingLine danglingLine;

    private final double nominalV;

    public LfDanglingLineBus(LfNetwork network, DanglingLine danglingLine, LfNetworkLoadingReport report) {
        super(network, Networks.getPropertyV(danglingLine), Networks.getPropertyAngle(danglingLine));
        this.danglingLine = Objects.requireNonNull(danglingLine);
        nominalV = danglingLine.getTerminal().getVoltageLevel().getNominalV();
        loadTargetP += danglingLine.getP0();
        loadTargetQ += danglingLine.getQ0();
        DanglingLine.Generation generation = danglingLine.getGeneration();
        if (generation != null) {
            add(new LfDanglingLineGenerator(danglingLine, getId(), report));
        }
    }

    @Override
    public String getId() {
        return danglingLine.getId() + "_BUS";
    }

    @Override
    public String getVoltageLevelId() {
        return danglingLine.getTerminal().getVoltageLevel().getId();
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
    public double getLowVoltageLimit() {
        return Double.NaN;
    }

    @Override
    public double getHighVoltageLimit() {
        return Double.NaN;
    }

    @Override
    public void updateState(boolean reactiveLimits, boolean writeSlackBus, boolean distributedOnConformLoad, boolean loadPowerFactorConstant) {
        Networks.setPropertyV(danglingLine, v.eval() * getNominalV());
        Networks.setPropertyAngle(danglingLine, angle);

        super.updateState(reactiveLimits, writeSlackBus, false, false);
    }

    @Override
    public boolean isParticipating() {
        return false;
    }
}
