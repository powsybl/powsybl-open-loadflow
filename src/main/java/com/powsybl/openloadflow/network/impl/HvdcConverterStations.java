/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.VscConverterStation;

import java.util.Objects;

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

    public static double getActivePowerSetpointMultiplier(HvdcConverterStation<?> station) {
        boolean isConverterStationRectifier = isRectifier(station);
        double sign;
        if (station instanceof LccConverterStation) { // load convention.
            sign = isConverterStationRectifier ? 1 : -1;
        } else if (station instanceof VscConverterStation) { // generator convention.
            sign = isConverterStationRectifier ? -1 : 1;
        } else {
            throw new PowsyblException("Unknown HVDC converter station type: " + station.getClass().getSimpleName());
        }
        return sign * (1 + (isConverterStationRectifier ? 1 : -1) * station.getLossFactor() / 100);
    }

    /**
     * Gets active power for an LCC station in per-unit.
     */
    public static double getLccConverterStationLoadTargetP(LccConverterStation lccCs, HvdcLine line) {
        // The active power setpoint is always positive.
        // If the converter station is at side 1 and is rectifier, p should be positive.
        // If the converter station is at side 1 and is inverter, p should be negative.
        // If the converter station is at side 2 and is rectifier, p should be positive.
        // If the converter station is at side 2 and is inverter, p should be negative.
        return line.getActivePowerSetpoint() * HvdcConverterStations.getActivePowerSetpointMultiplier(lccCs); // A LCC station has active losses.
    }

    /**
     * Gets reactive power for an LCC station in per-unit.
     */
    public static double getLccConverterStationLoadTargetQ(LccConverterStation lccCs, HvdcLine line) {
        // The active power setpoint is always positive.
        // If the converter station is at side 1 and is rectifier, p should be positive.
        // If the converter station is at side 1 and is inverter, p should be negative.
        // If the converter station is at side 2 and is rectifier, p should be positive.
        // If the converter station is at side 2 and is inverter, p should be negative.
        double pCs = getLccConverterStationLoadTargetP(lccCs, line);
        return Math.abs(pCs * Math.tan(Math.acos(lccCs.getPowerFactor()))); // A LCC station always consumes reactive power.
    }

    public static double getHvdcLineTargetP(VscConverterStation vscCs) {
        // The active power setpoint is always positive.
        // If the converter station is at side 1 and is rectifier, targetP should be negative.
        // If the converter station is at side 1 and is inverter, targetP should be positive.
        // If the converter station is at side 2 and is rectifier, targetP should be negative.
        // If the converter station is at side 2 and is inverter, targetP should be positive.
        HvdcLine line = vscCs.getHvdcLine();
        return line.getActivePowerSetpoint() * HvdcConverterStations.getActivePowerSetpointMultiplier(vscCs);
    }

    public static HvdcConverterStation<?> getOtherConversionStation(HvdcConverterStation<?> station) {
        HvdcLine line = station.getHvdcLine();
        return line.getConverterStation1() == station ? line.getConverterStation2() : line.getConverterStation1();
    }
}
