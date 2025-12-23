/**
 * Copyright (c) 2023-2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;

import static com.powsybl.openloadflow.network.LfHvdc.AcEmulationControl.AcEmulationStatus.*;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
public abstract class AbstractHvdcAcEmulationLimitsOuterLoop<V extends Enum<V> & Quantity,
        E extends Enum<E> & Quantity,
        P extends AbstractLoadFlowParameters<P>,
        C extends LoadFlowContext<V, E, P>,
        O extends OuterLoopContext<V, E, P, C>> implements OuterLoop<V, E, P, C, O> {

    protected static boolean checkAcEmulationMode(LfHvdc hvdc, boolean computeLoss, Logger logger, ReportNode reportNode) {
        LfHvdc.AcEmulationControl acEmulationControl = hvdc.getAcEmulationControl();

        if (acEmulationControl.getAcEmulationStatus() == LINEAR_MODE) {
            // If the HVDC was in linear mode but overpasses P_max -> switching to saturated mode
            double p1 = hvdc.getP1().eval();
            if (p1 >= 0) {
                if (p1 > hvdc.getAcEmulationControl().getPMaxFromCS1toCS2()) {
                    hvdc.getAcEmulationControl().switchToSaturationFromCS1toCS2(computeLoss);
                    Reports.reportAcEmulationFromLinearToSaturated(reportNode, hvdc.getId(),
                            hvdc.getConverterStation1().getId(),
                            hvdc.getConverterStation2().getId(),
                            hvdc.getAcEmulationControl().getPMaxFromCS1toCS2() * PerUnit.SB,
                            logger);
                    return true;
                }
            } else {
                double p2 = hvdc.getP2().eval();
                if (p2 > hvdc.getAcEmulationControl().getPMaxFromCS2toCS1()) {
                    hvdc.getAcEmulationControl().switchToSaturationFromCS2toCS1(computeLoss);
                    Reports.reportAcEmulationFromLinearToSaturated(reportNode, hvdc.getId(),
                            hvdc.getConverterStation2().getId(),
                            hvdc.getConverterStation1().getId(),
                            hvdc.getAcEmulationControl().getPMaxFromCS2toCS1() * PerUnit.SB,
                            logger);
                    return true;
                }
            }
        } else if (acEmulationControl.getAcEmulationStatus() == SATURATION_MODE_FROM_CS1_TO_CS2
                || acEmulationControl.getAcEmulationStatus() == SATURATION_MODE_FROM_CS2_TO_CS1) {
            double p1 = hvdc.getP1().eval();
            double p2 = hvdc.getP2().eval();

            if (p1 > 0 && p1 < hvdc.getAcEmulationControl().getPMaxFromCS1toCS2()
                    || p2 > 0 && p2 < hvdc.getAcEmulationControl().getPMaxFromCS2toCS1()) {
                // If the HVDC was in saturation mode but goes back within active power limits -> switching to linear mode
                hvdc.getAcEmulationControl().switchToLinearMode();
                Reports.reportAcEmulationBackToLinear(reportNode, hvdc.getId(), logger);
                return true;
            } else if (p1 < 0 && acEmulationControl.getAcEmulationStatus() == SATURATION_MODE_FROM_CS1_TO_CS2) {
                // If the HVDC was in saturation mode CS1toCS2 but goes over opposite Pmax -> switching to saturation mode CS2toCS1
                hvdc.getAcEmulationControl().switchToSaturationFromCS2toCS1(computeLoss);
                Reports.reportAcEmulationSaturationSideSwitch(reportNode, hvdc.getId(),
                        hvdc.getConverterStation2().getId(),
                        hvdc.getConverterStation1().getId(),
                        hvdc.getAcEmulationControl().getPMaxFromCS2toCS1() * PerUnit.SB,
                        logger);
                return true;
            } else if (p2 < 0 && acEmulationControl.getAcEmulationStatus() == SATURATION_MODE_FROM_CS2_TO_CS1) {
                // If the HVDC was in saturation mode CS2toCS1 but goes over opposite Pmax -> switching to saturation mode CS1toCS2
                hvdc.getAcEmulationControl().switchToSaturationFromCS1toCS2(computeLoss);
                Reports.reportAcEmulationSaturationSideSwitch(reportNode, hvdc.getId(),
                        hvdc.getConverterStation1().getId(),
                        hvdc.getConverterStation2().getId(),
                        hvdc.getAcEmulationControl().getPMaxFromCS1toCS2() * PerUnit.SB,
                        logger);
                return true;
            }
        }
        return false;
    }
}
