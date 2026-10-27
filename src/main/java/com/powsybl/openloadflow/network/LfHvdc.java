/*
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.equations.AbstractHvdcAcEmulationFlowEquationTerm;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public interface LfHvdc extends LfElement, LfCopyable<LfHvdc, LfNetwork> {

    class AcEmulationControl {
        public enum AcEmulationStatus {
            LINEAR_MODE,
            SATURATION_MODE_FROM_CS1_TO_CS2,
            SATURATION_MODE_FROM_CS2_TO_CS1,
            FROZEN
        }

        private final LfHvdc hvdc;
        private final double droop;
        private final double p0;
        private final double pMaxFromCS1toCS2;
        private final double pMaxFromCS2toCS1;
        private AcEmulationStatus acEmulationStatus = AcEmulationStatus.LINEAR_MODE;
        private double computedAcEmulationP1;
        private double computedAcEmulationP2;

        public AcEmulationControl(LfHvdc hvdc, double droop, double p0, double pMaxFromCS1toCS2, double pMaxFromCS2toCS1) {
            this.hvdc = hvdc;
            this.droop = droop / PerUnit.SB;
            this.p0 = p0 / PerUnit.SB;
            this.pMaxFromCS1toCS2 = pMaxFromCS1toCS2 / PerUnit.SB;
            this.pMaxFromCS2toCS1 = pMaxFromCS2toCS1 / PerUnit.SB;
            this.computedAcEmulationP1 = Double.NaN;
            this.computedAcEmulationP2 = Double.NaN;
        }

        public AcEmulationControl(LfHvdc hvdc, AcEmulationControl other) {
            this.hvdc = hvdc;
            this.droop = other.droop;
            this.p0 = other.p0;
            this.pMaxFromCS1toCS2 = other.pMaxFromCS1toCS2;
            this.pMaxFromCS2toCS1 = other.pMaxFromCS2toCS1;
            this.acEmulationStatus = other.acEmulationStatus;
            this.computedAcEmulationP1 = other.computedAcEmulationP1;
            this.computedAcEmulationP2 = other.computedAcEmulationP2;
        }

        public double getDroop() {
            return droop;
        }

        public double getP0() {
            return p0;
        }

        public double getPMaxFromCS1toCS2() {
            return pMaxFromCS1toCS2;
        }

        public double getPMaxFromCS2toCS1() {
            return pMaxFromCS2toCS1;
        }

        public AcEmulationStatus getAcEmulationStatus() {
            return acEmulationStatus;
        }

        public void setAcEmulationStatus(AcEmulationStatus status) {
            acEmulationStatus = status;
        }

        private static void setVscTargetP(double pController, LfVscConverterStation controllerStation, LfVscConverterStation nonControllerStation, double lineResistance, boolean computeLoss) {
            // pController > 0 and pNonController < 0
            double lossController = controllerStation.getLossFactor() / 100;
            double lossNonController = nonControllerStation.getLossFactor() / 100;
            double pNonController = computeLoss ?
                    -AbstractHvdcAcEmulationFlowEquationTerm.getAbsActivePowerWithLosses(pController, lossController, lossNonController, lineResistance)
                    : -pController;
            // VSC are in load convention
            controllerStation.setTargetP(-pController);
            nonControllerStation.setTargetP(-pNonController);
        }

        public void switchToSaturationFromCS1toCS2(boolean computeLoss) {
            setVscTargetP(getPMaxFromCS1toCS2(), hvdc.getConverterStation1(), hvdc.getConverterStation2(), hvdc.getR(), computeLoss);
            hvdc.updateAcEmulationStatus(AcEmulationStatus.SATURATION_MODE_FROM_CS1_TO_CS2);
        }

        public void switchToSaturationFromCS2toCS1(boolean computeLoss) {
            setVscTargetP(getPMaxFromCS2toCS1(), hvdc.getConverterStation2(), hvdc.getConverterStation1(), hvdc.getR(), computeLoss);
            hvdc.updateAcEmulationStatus(AcEmulationStatus.SATURATION_MODE_FROM_CS2_TO_CS1);
        }

        public void switchToLinearMode() {
            hvdc.getConverterStation1().setTargetP(0);
            hvdc.getConverterStation2().setTargetP(0);
            hvdc.updateAcEmulationStatus(AcEmulationStatus.LINEAR_MODE);
        }

        public void computeAcEmulationActivePowerFlow() {
            if (!hvdc.isDisabled()) {
                computedAcEmulationP1 = hvdc.getP1().eval();
                computedAcEmulationP2 = hvdc.getP2().eval();
            } else {
                throw new PowsyblException("AC Emulation active power flow cannot be computed on a disabled HVDC");
            }
        }

        public double getComputedAcEmulationP1() {
            return computedAcEmulationP1;
        }

        public double getComputedAcEmulationP2() {
            return computedAcEmulationP2;
        }

        public void setComputedAcEmulationP1(double computedAcEmulationP1) {
            this.computedAcEmulationP1 = computedAcEmulationP1;
        }

        public void setComputedAcEmulationP2(double computedAcEmulationP2) {
            this.computedAcEmulationP2 = computedAcEmulationP2;
        }

        public void switchToFrozenState(boolean computeLoss) {
            computeAcEmulationActivePowerFlow();
            // Checking if linear mode overpasses operating limits
            if (computedAcEmulationP1 > getPMaxFromCS1toCS2()) {
                setVscTargetP(getPMaxFromCS1toCS2(), hvdc.getConverterStation1(), hvdc.getConverterStation2(), hvdc.getR(), computeLoss);
            } else if (computedAcEmulationP2 > getPMaxFromCS2toCS1()) {
                setVscTargetP(getPMaxFromCS2toCS1(), hvdc.getConverterStation2(), hvdc.getConverterStation1(), hvdc.getR(), computeLoss);
            } else {
                // If not, freezing at current linear position
                hvdc.getConverterStation1().setTargetP(-computedAcEmulationP1);
                hvdc.getConverterStation2().setTargetP(-computedAcEmulationP2);
            }
            hvdc.updateAcEmulationStatus(AcEmulationStatus.FROZEN);
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

    void updateAcEmulationStatus(AcEmulationControl.AcEmulationStatus acEmulationStatus);

    void updateState();
}
