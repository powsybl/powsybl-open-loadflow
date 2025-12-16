/**
 * Copyright (c) 2023-2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.lf.outerloop.AbstractHvdcAcEmulationOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfHvdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
public class DcHvdcAcEmulationOuterLoop
        extends AbstractHvdcAcEmulationOuterLoop<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcOuterLoopContext>
        implements DcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(com.powsybl.openloadflow.ac.outerloop.AcHvdcAcEmulationOuterLoop.class);
    public static final String NAME = "DcHvdcAcEmulation";

    @Override
    public String getName() {
        return NAME;
    }

    private boolean checkAcEmulationMode(LfHvdc hvdc) {
        LfHvdc.AcEmulationControl acEmulationControl = hvdc.getAcEmulationControl();

        if (acEmulationControl.getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.LINEAR_MODE) {
            // If the HVDC was in linear mode but overpasses P_max -> switching to saturated mode
            double p1 = hvdc.getP1().eval();
            if (p1 >= 0) {
                if (p1 > hvdc.getAcEmulationControl().getPMaxFromCS1toCS2()) {
                    hvdc.getAcEmulationControl().switchToSaturationMode(TwoSides.ONE,
                            hvdc.getAcEmulationControl().getPMaxFromCS1toCS2(),
                            -hvdc.getAcEmulationControl().getPMaxFromCS1toCS2());
                    return true;
                }
            } else {
                double p2 = hvdc.getP2().eval();
                if (p2 > hvdc.getAcEmulationControl().getPMaxFromCS2toCS1()) {
                    hvdc.getAcEmulationControl().switchToSaturationMode(TwoSides.TWO,
                            hvdc.getAcEmulationControl().getPMaxFromCS2toCS1(),
                           -hvdc.getAcEmulationControl().getPMaxFromCS2toCS1());
                    return true;
                }
            }
        } else if (acEmulationControl.getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.SATURATION_MODE_FROM_CS1_TO_CS2) {
            // If the HVDC was in saturated mode but goes back within active power limits -> switching to linear mode
            double p1 = hvdc.getP1().eval();
            if (p1 >= 0) {
                if (p1 < hvdc.getAcEmulationControl().getPMaxFromCS1toCS2()) {
                    hvdc.getAcEmulationControl().switchToLinearMode();
                    return true;
                }
            } else {
                double p2 = hvdc.getP2().eval();
                if (p2 < hvdc.getAcEmulationControl().getPMaxFromCS2toCS1()) {
                    hvdc.getAcEmulationControl().switchToLinearMode();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public OuterLoopResult check(DcOuterLoopContext context, ReportNode reportNode) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        ContextData contextData = (ContextData) context.getData();

        for (LfHvdc hvdc : context.getNetwork().getHvdcs()) {
            if (!hvdc.isAcEmulation() || hvdc.getBus1().isDisabled() || hvdc.getBus2().isDisabled() || hvdc.isDisabled()) {
                continue;
            }
            String hvdcId = hvdc.getId();
            if (contextData.getModeSwitchCount(hvdcId) < MAX_MODE_SWITCH) {
                if (checkAcEmulationMode(hvdc)) {
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }

        return new OuterLoopResult(this, status);
    }

}
