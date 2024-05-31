/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop.tap;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfVscConverterStation;
import com.powsybl.openloadflow.network.VoltageControl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
public class GroupVoltageControlManager {

    private final double thtLimit;
    private final List<LfBus> busesWithVoltageControlDisabled = new ArrayList<>();

    public GroupVoltageControlManager(LfNetwork network, double thtLimit) {
        double myThtLimit = thtLimit;
        if (myThtLimit < 0) {
            // Automatic mode
            for (LfBus bus : network.getBuses()) {
                if (!bus.isDisabled() && bus.isTransformerVoltageControlled()) {
                    myThtLimit = Math.max(thtLimit, bus.getNominalV());
                }
            }
        }
        this.thtLimit = myThtLimit;
    }

    public double getThtLimit() {
        return thtLimit;
    }

    /**
     *  Disables the voltage control of generators with nominal voltage below the tht limit
     * @return true if at least one generator is disabled. False if the model is not modified
     */
    public boolean stopHTGroupTensionControl(LfNetwork network) {

        boolean result = false;

        for (LfBus bus : network.getControlledBuses(VoltageControl.Type.GENERATOR)) {
            if (bus.getNominalV() < thtLimit) {
                var voltageControl = bus.getGeneratorVoltageControl().orElseThrow();
                for (LfBus controllerBus : voltageControl.getMergedControllerElements()) {
                    if (controllerBus.isGeneratorVoltageControlEnabled() && !isBusBehindTHTTransfo(controllerBus, thtLimit)) {
                        controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                        controllerBus.setGeneratorVoltageControlEnabled(false);
                        busesWithVoltageControlDisabled.add(controllerBus);
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    public void restartHTGroupTensionControl() {
        for (LfBus controllerBus : busesWithVoltageControlDisabled) {
            controllerBus.setGenerationTargetQ(0);
            controllerBus.setGeneratorVoltageControlEnabled(true);
        }
    }

    private boolean isBusBehindTHTTransfo(LfBus bus, double thtLimit) {
        if (bus.getBranches().size() != 1) {
            return false;
        }
        LfBranch b = bus.getBranches().get(0);
        if (!b.isConnectedAtBothSides()) {
            return false;
        }
        // Always keep VSC stations
        if (bus.getGenerators().stream().anyMatch(LfVscConverterStation.class::isInstance)) {
            return true;
        }

        return Math.max(b.getBus1().getNominalV(), b.getBus2().getNominalV()) >= thtLimit;
    }
}
