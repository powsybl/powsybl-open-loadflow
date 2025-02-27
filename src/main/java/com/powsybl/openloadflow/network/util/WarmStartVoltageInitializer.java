/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.network.util;

import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * This voltage initializer initializes variables from previous values
 * but in addition it initializes the AC emulation HVDC to previous inhections
 * and freezes the AC emulation.
 * This enables to simulate a slow motion of the HVDC emulation -- that may cause
 * convergence issue if the macimum transmissible Active power is reached on some lines
 * after a contingence.
 */
public class WarmStartVoltageInitializer extends PreviousValueVoltageInitializer {

    public WarmStartVoltageInitializer(boolean defaultToUnformValues) {
        super(defaultToUnformValues);
    }

    @Override
    public void afterInit(LfNetwork network) {
        network.getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .forEach(LfHvdc::freezeFromCurrentAngles);
    }

}
