/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.network.LfBus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public class AreaInterchangeControlContextData extends DistributedSlackContextData {

    private final Set<LfBus> busesWithoutArea;

    /**
     * The part of the total slack active power mismatch that should be added to the Area's net interchange mismatch, ie the part of the slack that should be distributed in the Area.
     */
    private final Map<String, Double> areaSlackDistributionParticipationFactor;

    public AreaInterchangeControlContextData(Set<LfBus> busesWithoutArea, Map<String, Double> areaSlackDistributionParticipationFactor) {
        super();
        this.busesWithoutArea = new HashSet<>(busesWithoutArea);
        this.areaSlackDistributionParticipationFactor = new HashMap<>(areaSlackDistributionParticipationFactor);
    }

    public Set<LfBus> getBusesWithoutArea() {
        return busesWithoutArea;
    }

    public Map<String, Double> getAreaSlackDistributionParticipationFactor() {
        return areaSlackDistributionParticipationFactor;
    }

}
