/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class HvdcConverterStations {

    private HvdcConverterStations() {
    }

    public static boolean isRectifier(HvdcConverterStation<?> station) {
        Objects.requireNonNull(station);
        HvdcLine line = station.getHvdcLine();
        return (line.getConverterStation1() == station && line.getConvertersMode() == HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER)
                || (line.getConverterStation2() == station && line.getConvertersMode() == HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER);
    }

    public static double getSign(HvdcConverterStation<?> station) {
        // This method gives the sign of PAc.
        boolean isConverterStationRectifier = isRectifier(station);
        double sign;
        if (station instanceof LccConverterStation) { // load convention.
            sign = isConverterStationRectifier ? 1 : -1;
        } else if (station instanceof VscConverterStation) { // generator convention.
            sign = isConverterStationRectifier ? -1 : 1;
        } else {
            throw new PowsyblException("Unknown HVDC converter station type: " + station.getClass().getSimpleName());
        }
        return sign;
    }

    /**
     * Gets targetP of an VSC converter station or load target P for a LCC converter station.
     */
    public static double getConverterStationTargetP(HvdcConverterStation<?> station, boolean breakers) {
        // For a VSC converter station, we are in generator convention.
        // If the converter station is at side 1 and is rectifier, targetP should be negative.
        // If the converter station is at side 1 and is inverter, targetP should be positive.
        // If the converter station is at side 2 and is rectifier, targetP should be negative.
        // If the converter station is at side 2 and is inverter, targetP should be positive.
        // for a LCC converter station, we are in load convention.
        // If the converter station is at side 1 and is rectifier, p should be positive.
        // If the converter station is at side 1 and is inverter, p should be negative.
        // If the converter station is at side 2 and is rectifier, p should be positive.
        // If the converter station is at side 2 and is inverter, p should be negative.
        boolean disconnectedAtOtherSide = false;
        Optional<? extends HvdcConverterStation<?>> otherConverterStation = station.getOtherConverterStation();
        if (otherConverterStation.isPresent()) {
            Bus bus = Networks.getBus(otherConverterStation.get().getTerminal(), breakers);
            disconnectedAtOtherSide = bus == null;
        }
        return disconnectedAtOtherSide ? 0.0 : getSign(station) * getAbsoluteValuePAc(station);
    }

    /**
     * Gets reactive power for an LCC converter station.
     */
    public static double getLccConverterStationLoadTargetQ(LccConverterStation lccCs, boolean breakers) {
        // Load convention.
        // If the converter station is at side 1 and is rectifier, p should be positive.
        // If the converter station is at side 1 and is inverter, p should be negative.
        // If the converter station is at side 2 and is rectifier, p should be positive.
        // If the converter station is at side 2 and is inverter, p should be negative.
        double pCs = getConverterStationTargetP(lccCs, breakers);
        return Math.abs(pCs * Math.tan(Math.acos(lccCs.getPowerFactor()))); // A LCC station always consumes reactive power.
    }

    private static double getAbsoluteValuePAc(HvdcConverterStation<?> station) {
        boolean isConverterStationRectifier = isRectifier(station);
        if (isConverterStationRectifier) {
            return station.getHvdcLine().getActivePowerSetpoint();
        } else {
            // the converter station is inverter.
            HvdcConverterStation<?> otherStation = getOtherConversionStation(station);
            return getAbsoluteValueInverterPAc(otherStation.getLossFactor(), station.getLossFactor(), station.getHvdcLine());
        }
    }

    private static double getHvdcLineLosses(double rectifierPDc, double nominalV, double r) {
        // This method computes the losses due to the HVDC line.
        // The active power value on rectifier DC side is known as the HVDC active power set point minus the losses related
        // to AC/DC conversion (rectifier conversion), the voltage is approximated to the nominal voltage as attribute of the HVDC line.
        // In an HVDC, as a branch with two sides, the difference between pDc1 and pDc2 can be computed with the assumptions:
        // I = (V1 - V2) / R and pDc1 = I * V1 and pDc2 = I * V2 and V1 = nominalV
        // we simply obtain that the absolute value of the difference is equal to R * pDc1 * pDc1 / (V1 * V1) if side 1 is rectifier side.
        return r * rectifierPDc * rectifierPDc / (nominalV * nominalV);
    }

    private static double getAbsoluteValueInverterPAc(double rectifierLossFactor, double inverterLossFactor,
                                                      HvdcLine hvdcLine) {
        // On inverter side, absolute value of PAc of a VSC converter station should be computed in three step:
        // 1) compute the losses related to the rectifier conversion.
        // 2) compute the losses related to the HVDC line itself (R i^2).
        // 3) compute the losses related to the inverter conversion.
        double rectifierPDc = hvdcLine.getActivePowerSetpoint() * (1 - rectifierLossFactor / 100); // rectifierPDc positive.
        double inverterPDc = rectifierPDc - getHvdcLineLosses(rectifierPDc, hvdcLine.getNominalV(), hvdcLine.getR());
        return inverterPDc * (1 - inverterLossFactor / 100); // always positive.
    }

    private static HvdcConverterStation<?> getOtherConversionStation(HvdcConverterStation<?> station) {
        HvdcLine line = station.getHvdcLine();
        return line.getConverterStation1() == station ? line.getConverterStation2() : line.getConverterStation1();
    }

    public static double getActivePowerSetpointMultiplier(HvdcConverterStation<?> station) {
        // For sensitivity analysis, we need the multiplier by converter station for an increase of 1MW
        // of the HVDC active power setpoint.
        // VSC injection follow here a load sign convention as LCC injection.
        // As a first approximation, we don't take into account the losses due to HVDC line itself.
        boolean isConverterStationRectifier = isRectifier(station);
        if (isConverterStationRectifier) {
            return -1;
        } else {
            return 1 - (station.getLossFactor() + getOtherConversionStation(station).getLossFactor()) / 100;
        }
    }
}
