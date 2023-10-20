/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBus extends AbstractLfBus {

    private final Ref<DanglingLine> danglingLineRef;

    private final double nominalV;

    public LfDanglingLineBus(LfNetwork network, DanglingLine danglingLine, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, Networks.getPropertyV(danglingLine), Math.toRadians(Networks.getPropertyAngle(danglingLine)), false);
        this.danglingLineRef = Ref.create(danglingLine, parameters.isCacheEnabled());
        nominalV = danglingLine.getTerminal().getVoltageLevel().getNominalV();
        getOrCreateLfLoad(null, parameters).add(danglingLine);
        DanglingLine.Generation generation = danglingLine.getGeneration();
        if (generation != null) {
            add(LfDanglingLineGenerator.create(danglingLine, network, getId(), parameters, report));
        }
    }

    private DanglingLine getDanglingLine() {
        return danglingLineRef.get();
    }

    public static String getId(DanglingLine danglingLine) {
        return danglingLine.getId() + "_BUS";
    }

    @Override
    public List<String> getOriginalIds() {
        return List.of(getDanglingLine().getId());
    }

    @Override
    public String getId() {
        return getId(getDanglingLine());
    }

    @Override
    public String getVoltageLevelId() {
        return getDanglingLine().getTerminal().getVoltageLevel().getId();
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
        var danglingLine = getDanglingLine();
        Networks.setPropertyV(danglingLine, v);
        Networks.setPropertyAngle(danglingLine, Math.toDegrees(angle));

        super.updateState(parameters);
    }
}
