/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.network.AbstractPropertyBag;
import com.powsybl.openloadflow.network.LfLoad;
import com.powsybl.openloadflow.network.LfLoadModel;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class LfLoadImpl extends AbstractPropertyBag implements LfLoad {

    private final List<Ref<Load>> loadsRefs = new ArrayList<>();

    private final List<Double> loadsAbsVariableTargetP = new ArrayList<>();

    private double targetP = 0;

    private double targetQ = 0;

    private double absVariableTargetP = 0;

    private final boolean distributedOnConformLoad;

    private Map<String, Boolean> loadsDisablingStatus = new LinkedHashMap<>();

    private final LfLoadModel model;

    LfLoadImpl(boolean distributedOnConformLoad, LfLoadModel model) {
        this.distributedOnConformLoad = distributedOnConformLoad;
        this.model = model;
    }

    @Override
    public boolean isDistributedOnConformLoad() {
        return distributedOnConformLoad;
    }

    @Override
    public List<String> getOriginalIds() {
        return loadsRefs.stream().map(r -> r.get().getId()).collect(Collectors.toList());
    }

    void add(Load load, LfNetworkParameters parameters) {
        loadsRefs.add(Ref.create(load, parameters.isCacheEnabled()));
        loadsDisablingStatus.put(load.getId(), false);
        targetP += load.getP0() / PerUnit.SB;
        targetQ += load.getQ0() / PerUnit.SB;
        double absTargetP = getAbsVariableTargetP(load);
        loadsAbsVariableTargetP.add(absTargetP);
        absVariableTargetP += absTargetP;
    }

    @Override
    public double getTargetP() {
        return targetP;
    }

    @Override
    public double calculateNewTargetQ() {
        return targetQ;
    }

    @Override
    public double getAbsVariableTargetP() {
        return absVariableTargetP;
    }

    @Override
    public void setAbsVariableTargetP(double absVariableTargetP) {
        this.absVariableTargetP = absVariableTargetP;
    }

    private double getAbsVariableTargetP(Load load) {
        double varP;
        if (distributedOnConformLoad) {
            varP = load.getExtension(LoadDetail.class) == null ? 0 : load.getExtension(LoadDetail.class).getVariableActivePower();
        } else {
            varP = load.getP0();
        }
        return Math.abs(varP) / PerUnit.SB;
    }

    @Override
    public double getOriginalLoadCount() {
        return loadsRefs.size();
    }

    private double getParticipationFactor(int i) {
        return absVariableTargetP != 0 ? loadsAbsVariableTargetP.get(i) / absVariableTargetP : 0;
    }

    void updateState(double diffLoadTargetP, boolean loadPowerFactorConstant) {
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            double updatedP0 = load.getP0() + diffLoadTargetP * getParticipationFactor(i) * PerUnit.SB; // diff is in PU
            double updatedQ0 = loadPowerFactorConstant ? getPowerFactor(load) * updatedP0 : load.getQ0();
            load.getTerminal().setP(updatedP0);
            load.getTerminal().setQ(updatedQ0);
        }
    }

    @Override
    public double calculateNewTargetQ(double diffTargetP) {
        double newLoadTargetQ = 0;
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            double updatedP0 = load.getP0() / PerUnit.SB + diffTargetP * getParticipationFactor(i);
            newLoadTargetQ += getPowerFactor(load) * updatedP0;
        }
        return newLoadTargetQ;
    }

    @Override
    public boolean isOriginalLoadDisabled(String originalId) {
        return loadsDisablingStatus.get(originalId);
    }

    @Override
    public void setOriginalLoadDisabled(String originalId, boolean disabled) {
        loadsDisablingStatus.put(originalId, disabled);
    }

    @Override
    public Map<String, Boolean> getOriginalLoadsDisablingStatus() {
        return loadsDisablingStatus;
    }

    @Override
    public void setOriginalLoadsDisablingStatus(Map<String, Boolean> originalLoadsDisablingStatus) {
        this.loadsDisablingStatus = Objects.requireNonNull(originalLoadsDisablingStatus);
    }

    private static double getPowerFactor(Load load) {
        return load.getP0() != 0 ? load.getQ0() / load.getP0() : 1;
    }

    @Override
    public Optional<LfLoadModel> getModel() {
        return Optional.ofNullable(model);
    }
}
