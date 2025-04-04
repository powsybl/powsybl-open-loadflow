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
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        ContextData contextData = new ContextData();
        context.setData(contextData);
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        ContextData contextData = (ContextData) context.getData();
        return switch (contextData.step) {
            case UNFREEZE -> unfreezeHvdcs(context, reportNode);
            case COMPLETE -> new OuterLoopResult(this, OuterLoopStatus.STABLE);
        };
    }

    private OuterLoopResult unfreezeHvdcs(AcOuterLoopContext context, ReportNode reportNode) {

        List<LfHvdc> frozenHvdc = context.getNetwork().getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .filter(LfHvdc::isFrozen)
                .toList();

        for (LfHvdc lfHvdc : frozenHvdc) {
            Reports.reportUnfreezeHvdc(reportNode, lfHvdc.getId(), LOGGER);
            if (lfHvdc.unFreezeAndReportSaturationStatus()) {
                double angle = (lfHvdc.getBus1().getAngle() + lfHvdc.getBus2().getAngle()) / 2;
                lfHvdc.getBus1().setAngle(angle);
                lfHvdc.getBus2().setAngle(angle);
            }
        }

        return new OuterLoopResult(this, frozenHvdc.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE);

    }

    @Override
    public void cleanup(AcOuterLoopContext context) {
        // Ensures all hvdc links are in unfrozen state
        // Can be needed in case of solve failure
        context.getNetwork().getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .forEach(LfHvdc::unFreezeAndReportSaturationStatus);
    }
}
