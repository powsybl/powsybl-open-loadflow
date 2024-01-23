package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.openloadflow.network.LfNetwork;

public final class LfHvdcUtils {

    private LfHvdcUtils() {
    }

    public static boolean isHvdcDandlingInIdm(HvdcConverterStation<?> station, LfNetwork network) {

        if (isIsolated(station.getTerminal().getBusBreakerView().getBus(), network)) {
            return true;
        } else {
            return station.getOtherConverterStation().map(otherConverterStation -> {
                Bus bus = otherConverterStation.getTerminal().getBusView().getBus();
                return isIsolated(bus, network);
            }).orElse(true); // it means there is no HVDC line connected to station
        }
    }

    private static boolean isIsolated(Bus bus, LfNetwork network) {
        if (bus == null) {
            return true;
        }
        if (network != null && network.getBusById(bus.getId()) != null) {
            // connectivity for that bus to be determined by LfNetwork
            return false;
        }
        // Isolated if only connected to the station
        return bus.getConnectedTerminalCount() == 1;
    }

}
