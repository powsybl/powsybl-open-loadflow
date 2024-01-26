package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.openloadflow.network.LfNetworkParameters;

public final class LfHvdcUtils {

    private LfHvdcUtils() {
    }

    public static boolean isHvdcDanglingInIidm(HvdcConverterStation<?> station, LfNetworkParameters parameters) {

        if (isIsolated(station.getTerminal(), parameters)) {
            return true;
        } else {
            return station.getOtherConverterStation().map(otherConverterStation -> {
                Terminal otherTerminal = otherConverterStation.getTerminal();
                return isIsolated(otherTerminal, parameters);
            }).orElse(true); // it means there is no HVDC line connected to station
        }
    }

    private static boolean isIsolated(Terminal terminal, LfNetworkParameters parameters) {
        Bus bus = parameters.isBreakers() ? terminal.getBusBreakerView().getBus() : terminal.getBusView().getBus();
        if (bus == null) {
            return true;
        }

        // The criteria should as close as possible to Networks.isIsolatedBusForHvdc - only connected to the station
        return bus.getConnectedTerminalStream()
                .map(Terminal::getConnectable)
                .noneMatch(c -> !(c instanceof HvdcConverterStation<?> || c instanceof BusbarSection));
    }
}
