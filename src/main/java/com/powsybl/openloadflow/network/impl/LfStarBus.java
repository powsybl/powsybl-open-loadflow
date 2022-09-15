/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfStarBus extends AbstractLfBus {

    private final ThreeWindingsTransformer t3wt;

    private final double nominalV;

    public LfStarBus(LfNetwork network, ThreeWindingsTransformer t3wt) {
        super(network, Networks.getPropertyV(t3wt), Networks.getPropertyAngle(t3wt), false);
        this.t3wt = t3wt;
        nominalV = t3wt.getRatedU0();
    }

    public static String getId(String id) {
        return id + "_BUS0";
    }

    @Override
    public String getId() {
        return getId(t3wt.getId());
    }

    @Override
    public List<String> getOriginalIds() {
        return List.of(t3wt.getId());
    }

    @Override
    public String getVoltageLevelId() {
        return t3wt.getLeg1().getTerminal().getVoltageLevel().getId();
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
        Networks.setPropertyV(t3wt, v);
        Networks.setPropertyAngle(t3wt, angle);

        super.updateState(reactiveLimits, writeSlackBus, false, false);
    }
}
