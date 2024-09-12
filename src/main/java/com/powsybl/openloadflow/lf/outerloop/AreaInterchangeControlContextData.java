package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.network.LfBus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AreaInterchangeControlContextData extends DistributedSlackContextData {

    private final Set<LfBus> busesWithoutArea;

    /**
     * The part of the total slack active power mismatch that should be added to the Area's net interchange mismatch, ie the part of the slack that should be distributed in the Area.
     */

    private Map<String, Double> areaSlackDistributionParticipationFactor;

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
