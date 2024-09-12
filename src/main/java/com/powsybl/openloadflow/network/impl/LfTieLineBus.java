/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.TieLine;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;

import java.util.List;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public class LfTieLineBus extends AbstractLfBus {

    private final Ref<TieLine> tieLineRef;

    private final double nominalV;

    public LfTieLineBus(LfNetwork network, TieLine tieLine, LfNetworkParameters parameters) {
        super(network, Networks.getPropertyV(tieLine.getDanglingLine1()), Math.toRadians(Networks.getPropertyAngle(tieLine.getDanglingLine1())), false);
        this.tieLineRef = Ref.create(tieLine, parameters.isCacheEnabled());
        double nominalV1 = tieLine.getDanglingLine1().getTerminal().getVoltageLevel().getNominalV();
        double nominalV2 = tieLine.getDanglingLine2().getTerminal().getVoltageLevel().getNominalV();
        nominalV = Math.max(nominalV1, nominalV2);
    }

    private TieLine getTieLine() {
        return tieLineRef.get();
    }

    public static String getId(TieLine tieLine) {
        return tieLine.getId() + "_BUS";
    }

    @Override
    public List<String> getOriginalIds() {
        return List.of(getTieLine().getId());
    }

    @Override
    public String getId() {
        return getId(getTieLine());
    }

    @Override
    public String getVoltageLevelId() {
        return getTieLine().getDanglingLine1().getTerminal().getVoltageLevel().getId();
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
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var danglingLine = getTieLine();
        Networks.setPropertyV(danglingLine, v);
        Networks.setPropertyAngle(danglingLine, Math.toDegrees(angle));
        super.updateState(parameters);
    }
}
