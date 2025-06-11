/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.outerloop.AcActivePowerDistributionOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.AcOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.HvdcWarmStartOuterloop;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class WarmStartVoltageInitializer extends PreviousValueVoltageInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(WarmStartVoltageInitializer.class);

    public WarmStartVoltageInitializer(boolean defaultToUniformValues) {
        super(defaultToUniformValues);
    }

    @Override
    public void afterInit(LfNetwork network, ReportNode reportNode) {
        network.getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .filter(lfHvdc -> !lfHvdc.isDisabled())
                .forEach(lfHvdc -> {
                    double setPointBus1 = lfHvdc.freezeFromCurrentAngles();
                    if (!Double.isNaN(setPointBus1)) {
                        Reports.reportFreezeHvdc(reportNode, lfHvdc.getId(), setPointBus1 * PerUnit.SB, LOGGER);
                    }
                });
    }

    @Override
    public List<AcOuterLoop> updateOuterLoopList(LfNetwork network, List<AcOuterLoop> outerLoopList) {
        if (network.getHvdcs().stream().anyMatch(LfHvdc::isAcEmulation)) {
            List<AcOuterLoop> result = new ArrayList<>(outerLoopList);
            // Place a WarmStartOuterLoop after the slackDistribution OuterLoop
            int index = IntStream.range(0, outerLoopList.size())
                    .filter(i -> outerLoopList.get(i) instanceof AcActivePowerDistributionOuterLoop)
                    .findFirst()
                    .orElse(-1);
            result.add(index + 1, new HvdcWarmStartOuterloop());
            return result;
        } else {
            return outerLoopList;
        }
    }
}
