/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.util.HvdcUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class HvdcConverterStations {

    private HvdcConverterStations() {
    }

    public static double getActivePowerSetpointMultiplier(HvdcConverterStation<?> station) {
        // For sensitivity analysis, we need the multiplier by converter station for an increase of 1MW
        // of the HVDC active power setpoint.
        // VSC injection follow here a load sign convention as LCC injection.
        // As a first approximation, we don't take into account the losses due to HVDC line itself.
        boolean isConverterStationRectifier = HvdcUtils.isRectifier(station);
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

    public static boolean isVsc(Identifiable<?> identifiable) {
        Objects.requireNonNull(identifiable);
        return identifiable.getType() == IdentifiableType.HVDC_CONVERTER_STATION
                && ((HvdcConverterStation<?>) identifiable).getHvdcType() == HvdcConverterStation.HvdcType.VSC;
    }

    public static boolean isHvdcDanglingInIidm(HvdcConverterStation<?> station) {

        if (isIsolated(station.getTerminal())) {
            return true;
        } else {
            return station.getOtherConverterStation().map(otherConverterStation -> {
                Terminal otherTerminal = otherConverterStation.getTerminal();
                return isIsolated(otherTerminal);
            }).orElse(true); // it means there is no HVDC line connected to station
        }
    }

    private static boolean isIsolated(Terminal terminal) {
        Bus bus = terminal.getBusView().getBus();
        if (bus == null) {
            return true;
        }

        // The criteria should be as close as possible to Networks.isIsolatedBusForHvdc - only connected to the station or a fictitious load
        return bus.getConnectedTerminalStream()
                .map(Terminal::getConnectable)
                .noneMatch(c -> !(c instanceof HvdcConverterStation<?> || c instanceof BusbarSection || isFictitiousLoad(c)));
    }

    private static boolean isFictitiousLoad(Connectable<?> c) {
        return c instanceof Load load && LfLoadImpl.isLoadFictitious(load);
    }

    // TO DO : use HvdcUtils.getAbsoluteValueInverterPAc if made public (and adds as argument activePowerSetpoint)
    public static double getAbsoluteValueInverterPAc(double activePowerSetpoint, double rectifierLossFactor, double inverterLossFactor, HvdcLine hvdcLine) {
        double rectifierPDc = activePowerSetpoint * (1.0 - rectifierLossFactor / 100.0);
        double inverterPDc = rectifierPDc - HvdcUtils.getHvdcLineLosses(rectifierPDc, hvdcLine.getNominalV(), hvdcLine.getR());
        return inverterPDc * (1.0 - inverterLossFactor / 100.0);
    }

    // TO DO : use HvdcUtils.getLccConverterStationLoadTargetQ if made public (with converterPAc as argument)
    public static double getLccConverterStationLoadTargetQ(double converterPAc, double powerFactor) {
        return Math.abs(converterPAc * Math.tan(Math.acos(powerFactor)));
    }
}
