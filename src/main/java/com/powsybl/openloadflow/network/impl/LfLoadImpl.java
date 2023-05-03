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
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class LfLoadImpl extends AbstractPropertyBag implements LfLoad {

    private final List<Ref<Load>> loadsRefs = new ArrayList<>();

    private double[] participationFactors;

    private double targetP;

    private double targetQ;

    private double absVariableTargetP;

    private final boolean distributedOnConformLoad;

    private boolean initialized;

    private Map<String, Boolean> loadsDisablingStatus = new LinkedHashMap<>();

    LfLoadImpl(boolean distributedOnConformLoad) {
        this.distributedOnConformLoad = distributedOnConformLoad;
    }

    @Override
    public List<String> getOriginalIds() {
        return loadsRefs.stream().map(r -> r.get().getId()).collect(Collectors.toList());
    }

    void add(Load load, LfNetworkParameters parameters) {
        loadsRefs.add(Ref.create(load, parameters.isCacheEnabled()));
        loadsDisablingStatus.put(load.getId(), false);
        initialized = false;
    }

    @Override
    public double getTargetP() {
        init();
        return targetP;
    }

    @Override
    public double getTargetQ() {
        init();
        return targetQ;
    }

    @Override
    public double getAbsVariableTargetP() {
        init();
        return absVariableTargetP;
    }

    @Override
    public void setAbsVariableTargetP(double absVariableTargetP) {
        this.absVariableTargetP = absVariableTargetP;
    }

    private void init() {
        if (initialized) {
            return;
        }

        participationFactors = new double[loadsRefs.size()];
        targetP = 0;
        targetQ = 0;
        absVariableTargetP = 0;
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            targetP += load.getP0() / PerUnit.SB;
            targetQ += load.getQ0() / PerUnit.SB;
            double absValue;
            if (distributedOnConformLoad) {
                absValue = load.getExtension(LoadDetail.class) == null ? 0. : Math.abs(load.getExtension(LoadDetail.class).getVariableActivePower());
            } else {
                absValue = Math.abs(load.getP0());
            }
            absVariableTargetP += absValue;
            participationFactors[i] = absValue;
        }

        if (absVariableTargetP != 0) {
            for (int i = 0; i < participationFactors.length; i++) {
                participationFactors[i] /= absVariableTargetP;
            }
        }
        absVariableTargetP = absVariableTargetP / PerUnit.SB;
        initialized = true;
    }

    @Override
    public double getOriginalLoadCount() {
        return loadsRefs.size();
    }

    void updateState(double diffLoadTargetP, boolean loadPowerFactorConstant) {
        init();
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            double updatedP0 = (load.getP0() / PerUnit.SB + diffLoadTargetP * participationFactors[i]) * PerUnit.SB;
            double updatedQ0 = loadPowerFactorConstant ? getPowerFactor(load) * updatedP0 : load.getQ0();
            load.getTerminal().setP(updatedP0);
            load.getTerminal().setQ(updatedQ0);
        }
    }

    @Override
    public double getTargetQ(double diffTargetP) {
        init();
        double newLoadTargetQ = 0;
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            double updatedP0 = load.getP0() / PerUnit.SB + diffTargetP * participationFactors[i];
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

}
