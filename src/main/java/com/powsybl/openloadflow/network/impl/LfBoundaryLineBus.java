/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.contingency.violations.ViolationLocation;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfBoundaryLineBus extends AbstractLfBus {

    private final Ref<BoundaryLine> boundaryLineRef;

    private final double nominalV;

    public LfBoundaryLineBus(LfNetwork network, BoundaryLine boundaryLine, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, Networks.getPropertyV(boundaryLine), Math.toRadians(Networks.getPropertyAngle(boundaryLine)), parameters);
        this.distributedOnConformLoad = false; // AbstractLfBus sets by default distributedOnConformLoad = true, we set it to false for LfBoundaryLineBus
        this.boundaryLineRef = Ref.create(boundaryLine, parameters.isCacheEnabled());
        nominalV = boundaryLine.getTerminal().getVoltageLevel().getNominalV();
        getOrCreateLfLoad(null, parameters).add(boundaryLine);
        BoundaryLine.Generation generation = boundaryLine.getGeneration();
        if (generation != null) {
            add(LfBoundaryLineGenerator.create(boundaryLine, network, getId(), parameters, report));
        }
    }

    private BoundaryLine getBoundaryLine() {
        return boundaryLineRef.get();
    }

    public static String getId(BoundaryLine boundaryLine) {
        return boundaryLine.getId() + "_BUS";
    }

    @Override
    public List<String> getOriginalIds() {
        return List.of(getBoundaryLine().getId());
    }

    @Override
    public String getId() {
        return getId(getBoundaryLine());
    }

    @Override
    public String getVoltageLevelId() {
        return getBoundaryLine().getTerminal().getVoltageLevel().getId();
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
        var boundaryLine = getBoundaryLine();
        Networks.setPropertyV(boundaryLine, v);
        Networks.setPropertyAngle(boundaryLine, Math.toDegrees(angle));

        super.updateState(parameters);
    }

    @Override
    public ViolationLocation getViolationLocation() {
        return null;
    }
}
