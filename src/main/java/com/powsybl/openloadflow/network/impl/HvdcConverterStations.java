/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.util.HvdcUtils;

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
        return line.getConverterStation1() == station && line.getConvertersMode() == HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER
                || line.getConverterStation2() == station && line.getConvertersMode() == HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER;
    }

    /**
     * Gets targetP of an VSC converter station or load target P for a LCC converter station.
     */
    public static double getConverterStationTargetP(HvdcConverterStation<?> station, boolean breakers) {
        // For a VSC converter station, we are in generator convention.
        boolean disconnectedAtOtherSide = station.getOtherConverterStation().map(otherConverterStation -> {
            Bus bus = Networks.getBus(otherConverterStation.getTerminal(), breakers);
            return bus == null;
        }).orElse(true); // it means there is no HVDC line connected to station
        return disconnectedAtOtherSide ? 0.0 : HvdcUtils.getConverterStationTargetP(station);
    }

    /**
     * Gets reactive power for an LCC converter station.
     */
    public static double getLccConverterStationLoadTargetQ(LccConverterStation lccCs, boolean breakers) {
        // Load convention.
        boolean disconnectedAtOtherSide = lccCs.getOtherConverterStation().map(otherConverterStation -> {
            Bus bus = Networks.getBus(otherConverterStation.getTerminal(), breakers);
            return bus == null;
        }).orElse(true); // it means there is no HVDC line connected to station
        return disconnectedAtOtherSide ? 0.0 : HvdcUtils.getLccConverterStationLoadTargetQ(lccCs);
    }

    public static double getActivePowerSetpointMultiplier(HvdcConverterStation<?> station) {
        // For sensitivity analysis, we need the multiplier by converter station for an increase of 1MW
        // of the HVDC active power setpoint.
        // VSC injection follow here a load sign convention as LCC injection.
        // As a first approximation, we don't take into account the losses due to HVDC line itself.
        boolean isConverterStationRectifier = isRectifier(station);
        Optional<? extends HvdcConverterStation<?>> otherStation = station.getOtherConverterStation();
        if (otherStation.isPresent()) {
            if (isConverterStationRectifier) {
                return -1;
            } else {
                return 1 - (station.getLossFactor() + otherStation.get().getLossFactor()) / 100;
            }
        }
        return 0.0;
    }
}
