/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
public class HvdcWarmStartOuterloop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(HvdcWarmStartOuterloop.class);

    public static final String NAME = "HvdcWarmStart";

    private enum Step {
        UNFREEZE,
        COMPLETE
    }

    private static final class ContextData {

        private Step step = Step.UNFREEZE;
        private HashMap<String, Double> angles = new HashMap<>();
        private HashMap<String, Double> voltage = new HashMap<>();

        private ContextData(LfNetwork network) {
            network.getBuses()
                    .stream().filter(b -> !b.isDisabled())
                    .forEach(b -> {
                        angles.put(b.getId(), Double.isNaN(b.getAngle()) ? 0 : b.getAngle());
                        voltage.put(b.getId(), Double.isNaN(b.getV()) ? 1 : b.getV());
                    });
        }

    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        ContextData contextData = new ContextData(context.getNetwork());
        context.setData(contextData);
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        ContextData contextData = (ContextData) context.getData();
        return switch (contextData.step) {
            case UNFREEZE -> unfreezeHvdcs(context, contextData, reportNode);
            case COMPLETE -> new OuterLoopResult(this, OuterLoopStatus.STABLE);
        };
    }

    private OuterLoopResult unfreezeHvdcs(AcOuterLoopContext context, ContextData contextData, ReportNode reportNode) {

        List<LfHvdc> frozenHvdc = context.getNetwork().getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .filter(LfHvdc::isFrozen)
                .toList();

        if (!frozenHvdc.isEmpty()) {

            for (LfHvdc lfHvdc : frozenHvdc) {
                Reports.reportUnfreezeHvdc(reportNode, lfHvdc.getId(), LOGGER);
                lfHvdc.unFreeze();
            }

            // Return to initial state (we are in a possibly non physical state after first partial resolution)
            context.getNetwork().getBuses()
                    .stream()
                    .filter(b -> !b.isDisabled())
                    .forEach(b -> {
                        b.setAngle(contextData.angles.get(b.getId()));
                        b.setV(contextData.voltage.get(b.getId()));
                    });
        }

        return new OuterLoopResult(this, frozenHvdc.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE);

    }

    @Override
    public void cleanup(AcOuterLoopContext context) {
        // Ensures all hvdc links are in unfrozen state
        // Can be needed in case of solve failure
        context.getNetwork().getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .forEach(LfHvdc::unFreeze);
    }
}
