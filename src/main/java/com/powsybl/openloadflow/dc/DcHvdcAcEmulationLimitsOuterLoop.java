/**
 * Copyright (c) 2023-2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.lf.outerloop.AbstractHvdcAcEmulationLimitsOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfHvdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
public class DcHvdcAcEmulationLimitsOuterLoop
        extends AbstractHvdcAcEmulationLimitsOuterLoop<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcOuterLoopContext>
        implements DcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcHvdcAcEmulationLimitsOuterLoop.class);
    public static final String NAME = "DcHvdcAcEmulationLimits";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopResult check(DcOuterLoopContext context, ReportNode reportNode) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        for (LfHvdc hvdc : context.getNetwork().getHvdcs()) {
            if (!hvdc.isAcEmulation() || hvdc.getBus1().isDisabled() || hvdc.getBus2().isDisabled() || hvdc.isDisabled()) {
                continue;
            }
            if (checkAcEmulationMode(hvdc, false, LOGGER, reportNode)) {
                LOGGER.trace("Hvdc '{}' AC emulation state is changed to {}", hvdc.getId(), hvdc.getAcEmulationControl().getAcEmulationStatus());
                status = OuterLoopStatus.UNSTABLE;
            }
        }
        return new OuterLoopResult(this, status);
    }
}
