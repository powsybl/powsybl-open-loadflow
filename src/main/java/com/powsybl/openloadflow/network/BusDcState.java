/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public class BusDcState extends ElementState<LfBus> {

    private final Map<String, Double> generatorsTargetP;
    private final Map<String, Boolean> participatingGenerators;
    private final Map<String, Boolean> disablingStatusGenerators;
    private final List<LoadDcState> loadStates;

    private final boolean isSlack;

    private final boolean isReference;

    protected static class LoadDcState {

        private double loadTargetP;
        private double absVariableLoadTargetP;
        private Map<String, Boolean> loadsDisablingStatus;

        protected LoadDcState save(LfLoad load) {
            loadTargetP = load.getTargetP();
            absVariableLoadTargetP = load.getAbsVariableTargetP();
            loadsDisablingStatus = new HashMap<>(load.getOriginalLoadsDisablingStatus());
            return this;
        }

        protected void restore(LfLoad load) {
            load.setTargetP(loadTargetP);
            load.setAbsVariableTargetP(absVariableLoadTargetP);
            load.setOriginalLoadsDisablingStatus(loadsDisablingStatus);
        }
    }

    public BusDcState(LfBus bus) {
        super(bus);
        this.generatorsTargetP = bus.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::getTargetP));
        this.participatingGenerators = bus.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::isParticipating));
        this.disablingStatusGenerators = bus.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::isDisabled));
        loadStates = bus.getLoads().stream().map(load -> createLoadState().save(load)).toList();
        this.isSlack = bus.isSlack();
        this.isReference = bus.isReference();
    }

    protected LoadDcState createLoadState() {
        return new LoadDcState();
    }

    @Override
    public void restore() {
        super.restore();
        element.getGenerators().forEach(g -> g.setTargetP(generatorsTargetP.get(g.getId())));
        element.getGenerators().forEach(g -> g.setParticipating(participatingGenerators.get(g.getId())));
        element.getGenerators().forEach(g -> g.setDisabled(disablingStatusGenerators.get(g.getId())));
        for (int i = 0; i < loadStates.size(); i++) {
            LfLoad load = element.getLoads().get(i);
            loadStates.get(i).restore(load);
        }
        element.setSlack(this.isSlack); // needed or not?
        element.setReference(this.isReference); // needed or not?
    }

    public static BusDcState save(LfBus bus) {
        return new BusDcState(bus);
    }
}
