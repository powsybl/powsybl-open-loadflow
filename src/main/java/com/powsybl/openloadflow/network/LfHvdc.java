/*
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public interface LfHvdc extends LfElement {

    class AcEmulationControl {
        public enum AcEmulationStatus {
            LINEAR_MODE,
            SATURATION_MODE_FROM_CS1_TO_CS2,
            SATURATION_MODE_FROM_CS2_TO_CS1,
            FROZEN;
        }

        private final LfHvdc hvdc;
        private final double droop;
        private final double p0;
        private final double pMaxFromCS1toCS2;
        private final double pMaxFromCS2toCS1;
        private AcEmulationStatus acEmulationStatus = AcEmulationStatus.LINEAR_MODE;

        public AcEmulationControl(LfHvdc hvdc, double droop, double p0, double pMaxFromCS1toCS2, double pMaxFromCS2toCS1) {
            this.hvdc = hvdc;
            this.droop = droop;
            this.p0 = p0;
            this.pMaxFromCS1toCS2 = pMaxFromCS1toCS2;
            this.pMaxFromCS2toCS1 = pMaxFromCS2toCS1;
        }

        public double getDroop() {
            return droop / PerUnit.SB;
        }

        public double getP0() {
            return p0 / PerUnit.SB;
        }

        public double getPMaxFromCS1toCS2() {
            return pMaxFromCS1toCS2 / PerUnit.SB;
        }

        public double getPMaxFromCS2toCS1() {
            return pMaxFromCS2toCS1 / PerUnit.SB;
        }

        public AcEmulationStatus getAcEmulationStatus() {
            return acEmulationStatus;
        }

        public void setAcEmulationStatus(AcEmulationStatus status) {
            acEmulationStatus = status;
        }

        public void switchToSaturationMode(TwoSides feedingSide, double pMaxController, double pMaxNonController) {
            if (feedingSide == TwoSides.ONE) {
                hvdc.getConverterStation1().setTargetP(pMaxController);
                hvdc.getConverterStation2().setTargetP(pMaxNonController);
                hvdc.updateAcEmulationStatus(AcEmulationStatus.SATURATION_MODE_FROM_CS1_TO_CS2);
            } else {
                hvdc.getConverterStation2().setTargetP(pMaxController);
                hvdc.getConverterStation1().setTargetP(pMaxNonController);
                hvdc.updateAcEmulationStatus(AcEmulationStatus.SATURATION_MODE_FROM_CS2_TO_CS1);
            }
        }

        public void switchToLinearMode() {
            hvdc.getConverterStation1().setTargetP(0);
            hvdc.getConverterStation2().setTargetP(0);
            hvdc.updateAcEmulationStatus(AcEmulationStatus.LINEAR_MODE);
        }

        public double switchToFrozenState() {
            if (!Double.isNaN(hvdc.getBus1().getAngle()) && !Double.isNaN(hvdc.getBus2().getAngle())) {
                if (hvdc.getAcEmulationControl().getAcEmulationStatus() == AcEmulationStatus.LINEAR_MODE) {
                    double p1 = hvdc.getP1().eval();
                    double p2 = hvdc.getP2().eval();
                    hvdc.getConverterStation1().setTargetP(p1);
                    hvdc.getConverterStation2().setTargetP(p2);
                }
                hvdc.updateAcEmulationStatus(AcEmulationStatus.FROZEN);
                return hvdc.getConverterStation1().getTargetP();
            }
            return Double.NaN; // Might happen if an HVDC is reconnected by an action. In this case the freeze should be ignored
        }
    }

    LfBus getBus1();

    LfBus getBus2();

    LfBus getOtherBus(LfBus bus);

    // P1 and P2 are only used for linear mode of AC emulation (fixed setpoint, saturated mode of AC emulation, or frozen AC emulation are in the target vector)

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    double getR();

    boolean isAcEmulation();

    void setAcEmulation(boolean acEmulation);

    LfVscConverterStation getConverterStation1();

    LfVscConverterStation getConverterStation2();

    void setConverterStation1(LfVscConverterStation converterStation1);

    void setConverterStation2(LfVscConverterStation converterStation2);

    AcEmulationControl getAcEmulationControl();

    void setAcEmulationControl(AcEmulationControl acEmulationControl);

    void updateAcEmulationStatus(AcEmulationControl.AcEmulationStatus acEmulationStatus);

    void updateState();
}
