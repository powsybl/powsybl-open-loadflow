/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outer loop that ensures AC-DC converters operates within their power limits, changing their control mode if needed.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public class AcDcConverterPowerLimitsOuterLoop implements AcOuterLoop {

    public static final String NAME = "AcDcConverterPowerLimits";
    private final Set<Integer> numDccs = new LinkedHashSet<>();  // FIXME: Set or list

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getType() {
        return AcOuterLoop.super.getType();
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        LfNetwork network = context.getNetwork();
        numDccs.addAll(network.getDcBuses().stream().map(LfDcBus::getNumDCC).sorted().collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        return new OuterLoopResult(this, OuterLoopStatus.STABLE);
    }

    @Override
    public void cleanup(AcOuterLoopContext context) {
        AcOuterLoop.super.cleanup(context);
    }

    @Override
    public boolean isNeeded(AcLoadFlowContext context) {
        // Needed if the network contains an AC-DC converter.
        return !context.getNetwork().getVoltageSourceConverters().isEmpty();
    }

    @Override
    public boolean canFixUnrealisticState() {
        return AcOuterLoop.super.canFixUnrealisticState();
    }
}
