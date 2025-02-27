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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HvdcWarmStartOuterloop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(HvdcWarmStartOuterloop.class);

    private static final int MAX_STEPS = 10;
    public static final String NAME = "HvdcWarmStart";

    private enum Step {
        CHECK,
        COMPENSATE,
        COMPLETE
    }

    private static final class ContextData {

        private Step step = Step.CHECK;
        private final Map<String, Boolean> angleSign = new HashMap<>();

        boolean signChanged(String key, double delta) {
            boolean deltaPos = delta > 0;
            Boolean previous = angleSign.get(key);
            angleSign.put(key, deltaPos);
            return previous == null ? false : !previous.equals(deltaPos);
        }
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
            case CHECK -> checkFrozenHvdcs(context, reportNode);
            case COMPENSATE -> requestCompensation(context);
            case COMPLETE -> new OuterLoopResult(this, OuterLoopStatus.STABLE);
        };
    }

    private OuterLoopResult checkFrozenHvdcs(AcOuterLoopContext context, ReportNode reportNode) {

        ContextData contextData = (ContextData) context.getData();

        List<LfHvdc> frozenHvdc = context.getNetwork().getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .filter(LfHvdc::isFrozen)
                .toList();

        if (frozenHvdc.isEmpty()) {
            contextData.step = Step.COMPLETE;
            return new OuterLoopResult(this, OuterLoopStatus.STABLE);
        } else {
            for (LfHvdc lfHvdc : frozenHvdc) {
                double deltaStep = lfHvdc.getOperatingAngle() / MAX_STEPS;
                double delta = lfHvdc.getAngleMismatch();
                boolean signChanged = contextData.signChanged(lfHvdc.getId(), delta);
                // Math.PI means that HVDC would be at PMax or PMin if in AC emulation
                if (signChanged || Math.abs(delta) < Math.PI) {
                    Reports.reportUnfreezeHvdc(reportNode, lfHvdc.getId(), LOGGER);
                    lfHvdc.unFreeze();
                } else {
                    double newValue = lfHvdc.updateFrozenValue(deltaStep * Math.signum(delta));
                    Reports.reportUpdateFrozenHvdc(reportNode, lfHvdc.getId(), newValue, LOGGER);
                }
            }

            contextData.step = Step.COMPENSATE;
            return new OuterLoopResult(this, OuterLoopStatus.UNSTABLE);
        }
    }

    private OuterLoopResult requestCompensation(AcOuterLoopContext context) {
        ContextData contextData = (ContextData) context.getData();
        contextData.step = Step.CHECK;
        return new OuterLoopResult(this, OuterLoopStatus.STABLE);
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
