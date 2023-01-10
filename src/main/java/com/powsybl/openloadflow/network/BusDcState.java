/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class BusDcState extends ElementState<LfBus> {

    private final double loadTargetP;
    private final Map<String, Double> generatorsTargetP;
    private final Map<String, Boolean> participatingGenerators;
    private final double absVariableLoadTargetP;
    private final Map<String, Boolean> loadsDisablingStatus;

    public BusDcState(LfBus bus) {
        super(bus);
        this.loadTargetP = bus.getLoadTargetP();
        this.generatorsTargetP = bus.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::getTargetP));
        this.participatingGenerators = bus.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::isParticipating));
        this.absVariableLoadTargetP = bus.getAggregatedLoads().getAbsVariableLoadTargetP();
        this.loadsDisablingStatus = bus.getAggregatedLoads().getLoadsDisablingStatus();
    }

    @Override
    public void restore() {
        super.restore();
        element.setLoadTargetP(loadTargetP);
        element.getGenerators().forEach(g -> g.setTargetP(generatorsTargetP.get(g.getId())));
        element.getGenerators().forEach(g -> g.setParticipating(participatingGenerators.get(g.getId())));
        element.getAggregatedLoads().setAbsVariableLoadTargetP(absVariableLoadTargetP);
        element.getAggregatedLoads().setLoadsDisablingStatus(loadsDisablingStatus);
    }

    public static BusDcState save(LfBus bus) {
        return new BusDcState(bus);
    }
}
