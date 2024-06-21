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
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.VoltageControl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
public class GeneratorVoltageControlManager {

    private final double minNominalVoltageLimit;
    private final List<LfBus> disabledControllerBuses = new ArrayList<>();

    public GeneratorVoltageControlManager(LfNetwork network, double limitOverride) {
        this.minNominalVoltageLimit = limitOverride < 0 ? computeDefaultMinNominalVoltageLimit(network) : limitOverride;
    }

    private static double computeDefaultMinNominalVoltageLimit(LfNetwork network) {
        double defaultMinNominalVoltage = Double.MIN_VALUE;
        for (LfBus bus : network.getBuses()) {
            if (!bus.isDisabled()
                    && bus.isTransformerVoltageControlled()
                    && isTransformerVoltageControlsValidForMaxControlledNominalVoltageCalculation(bus.getTransformerVoltageControl().orElse(null))) {
                defaultMinNominalVoltage = Math.max(defaultMinNominalVoltage, bus.getNominalV());
            }
        }
        return defaultMinNominalVoltage;
    }

    private static boolean isTransformerVoltageControlsValidForMaxControlledNominalVoltageCalculation(TransformerVoltageControl transformerVoltageControl) {
        // are removed from this automatic algorithm the transformer voltage control that are between two nominal
        // voltages equivalents.
        if (transformerVoltageControl != null) {
            for (LfBranch branch : transformerVoltageControl.getControllerElements()) {
                if (!branch.isConnectedAtBothSides()
                        || branch.getBus1().getNominalV() == branch.getBus2().getNominalV()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Disables the voltage control of generators if controlled bus nominal voltage is under the limit.
     */
    public void disableGeneratorVoltageControlsUnderMaxControlledNominalVoltage(LfNetwork network) {
        disabledControllerBuses.clear(); // (in case someone wants to call this multiple time in instance lifecycle
                                         // although this class as not designed nor tested with this use case in mind)
        for (LfBus bus : network.getControlledBuses(VoltageControl.Type.GENERATOR)) {
            if (bus.getNominalV() < minNominalVoltageLimit) {
                var voltageControl = bus.getGeneratorVoltageControl().orElseThrow();
                for (LfBus controllerBus : voltageControl.getMergedControllerElements()) {
                    if (controllerBus.isGeneratorVoltageControlEnabled() && !isBusBehindVeryHighVoltageTransfo(controllerBus, minNominalVoltageLimit)) {
                        controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                        controllerBus.setGeneratorVoltageControlEnabled(false);
                        disabledControllerBuses.add(controllerBus);
                    }
                }
            }
        }
    }

    /**
     * Enables the voltage control of generators if controlled bus nominal voltage is under the limit.
     */
    public void enableGeneratorVoltageControlsUnderMaxControlledNominalVoltage() {
        for (LfBus controllerBus : disabledControllerBuses) {
            controllerBus.setGenerationTargetQ(0);
            controllerBus.setGeneratorVoltageControlEnabled(true);
        }
    }

    private boolean isBusBehindVeryHighVoltageTransfo(LfBus bus, double limit) {
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

        return Math.max(b.getBus1().getNominalV(), b.getBus2().getNominalV()) >= limit;
    }
}
