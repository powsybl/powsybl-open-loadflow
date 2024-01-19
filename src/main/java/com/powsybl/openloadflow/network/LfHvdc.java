/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.util.HvdcUtils;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Optional;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public interface LfHvdc extends LfElement {

    Optional<LfBus> getBus1();

    Optional<LfBus> getBus2();

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    double getDroop();

    double getP0();

    boolean isAcEmulationEnabled();
    boolean isAcEmulationActive();

    Optional<LfVscConverterStation> getConverterStation1();

    Optional<LfVscConverterStation> getConverterStation2();

    void setConverterStation1(LfVscConverterStation converterStation1);

    void setConverterStation2(LfVscConverterStation converterStation2);

    void updateState();

    boolean canTransferActivePower();

    static double getTargetPInIdmTopology(HvdcConverterStation<?> station) {

        if (isIsolated(station.getTerminal().getBusBreakerView().getBus())) {
            return 0d;
        }
        boolean otherBuseIsolated = station.getOtherConverterStation().map(otherConverterStation -> {
            Bus bus = otherConverterStation.getTerminal().getBusView().getBus();
            return isIsolated(bus);
        }).orElse(true); // it means there is no HVDC line connected to station

        if (otherBuseIsolated) {
            return 0d;
        }

        return HvdcUtils.getConverterStationTargetP(station);
    }

    private static boolean isIsolated(Bus bus) {
        if (bus == null) {
            return true;
        }
        // Isolated if only connected to the station
        return bus.getConnectedTerminalCount() == 1;
    }
}
