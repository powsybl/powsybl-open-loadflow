/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public interface LfArea extends PropertyBag {
    String getId();

    double getInterchangeTarget();

    void setInterchangeTarget(double interchangeTarget);

    double getInterchange();

    Set<LfBus> getBuses();

    void addBus(LfBus bus);

    void addBoundaryP(Supplier<Evaluable> p);

    double getSlackInjection(double slackBusActivePowerMismatch);

    void addExternalBusSlackParticipationFactor(LfBus bus, double shareFactor);

    /**
     * Some buses are not in the Area, but their slack should be considered for the slack of the Area
     * (This happens for example when a bus is connected to multiple Areas but the flow through the bus is not considered for those areas' interchange power flow).
     * The slack of this buses can need to be shared with other Areas with a factor for each area.
     * @return A map of the external buses considered for the slack of the Area with the share factor of the Area
     */
    Map<LfBus, Double> getExternalBusesSlackParticipationFactors();

    LfNetwork getNetwork();

}
