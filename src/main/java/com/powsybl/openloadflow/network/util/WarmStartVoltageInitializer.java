/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.network.util;

import com.powsybl.openloadflow.ac.outerloop.AcActivePowerDistributionOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.AcOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.HvdcWarmStartOuterloop;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * This voltage initializer initializes variables from previous values
 * but in addition it sets the AC emulation HVDC active set point to previous inhections
 * and freezes the AC emulation.
 * This enables to simulate a slow motion of the HVDC emulation -- that may cause
 * convergence issue if the macimum transmissible Active power is reached on some lines
 * after a contingence.
 * This Voltage Initializer also adds an outerloop that resets the frozen HVDC to AC emulation
 * after a first successful resolution (and slack distribution if available)
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
public final class WarmStartVoltageInitializer {

    private WarmStartVoltageInitializer() {

    }

    public static List<AcOuterLoop> updateOuterLoopList(List<AcOuterLoop> outerLoopList) {
        // Do nothing is the loop is already present
        if (outerLoopList.stream().anyMatch(o -> o instanceof HvdcWarmStartOuterloop)) {
            return outerLoopList;
        }
        List<AcOuterLoop> result = new ArrayList<>(outerLoopList);
        // Place a WarmStartOuterLoop after the slackDistribution OuterLoop
        int index = IntStream.range(0, outerLoopList.size())
                .filter(i -> outerLoopList.get(i) instanceof AcActivePowerDistributionOuterLoop)
                .findFirst()
                .orElse(-1);
        result.add(index + 1, new HvdcWarmStartOuterloop());
        return result;
    }
}
