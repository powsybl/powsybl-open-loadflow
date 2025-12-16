/**
 * Copyright (c) 2023-2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.AbstractHvdcAcEmulationFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.lf.outerloop.AbstractHvdcAcEmulationOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfHvdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
public class AcHvdcAcEmulationOuterLoop
        extends AbstractHvdcAcEmulationOuterLoop<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext, AcOuterLoopContext>
        implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcHvdcAcEmulationOuterLoop.class);
    public static final String NAME = "AcHvdcAcEmulation";

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
                    double pMaxController = hvdc.getAcEmulationControl().getPMaxFromCS1toCS2();
                    double lossController = hvdc.getConverterStation1().getLossFactor() / 100;
                    double lossNonController = hvdc.getConverterStation2().getLossFactor() / 100;
                    double pMaxNonController = -AbstractHvdcAcEmulationFlowEquationTerm.getAbsActivePowerWithLosses(pMaxController, lossController, lossNonController, hvdc.getR());
                    hvdc.getAcEmulationControl().switchToSaturationMode(TwoSides.ONE, pMaxController, pMaxNonController);
                    return true;
                }
            } else {
                double p2 = hvdc.getP2().eval();
                if (p2 > hvdc.getAcEmulationControl().getPMaxFromCS2toCS1()) {
                    double pMaxController = hvdc.getAcEmulationControl().getPMaxFromCS2toCS1();
                    double lossController = hvdc.getConverterStation2().getLossFactor() / 100;
                    double lossNonController = hvdc.getConverterStation1().getLossFactor() / 100;
                    double pMaxNonController = -AbstractHvdcAcEmulationFlowEquationTerm.getAbsActivePowerWithLosses(pMaxController, lossController, lossNonController, hvdc.getR());
                    hvdc.getAcEmulationControl().switchToSaturationMode(TwoSides.TWO, pMaxController, pMaxNonController);
                    return true;
                }
            }
        } else if (acEmulationControl.getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.SATURATION_MODE_FROM_CS1_TO_CS2) {
            // If the HVDC was in saturated mode from CS1 to CS2 but goes back within active power limits -> switching to linear mode
            double p1 = hvdc.getP1().eval();
            if (p1 < hvdc.getAcEmulationControl().getPMaxFromCS1toCS2() && p1 > 0) {
                TO DO
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
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
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

    @Override
    public boolean isNeeded(AcLoadFlowContext context) {
        // Needed if the network contains an lfHVDC in AC Emulation mode
        return context.getNetwork().getHvdcs().stream().anyMatch(LfHvdc::isAcEmulation);
    }
}
