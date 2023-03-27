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
import com.powsybl.openloadflow.network.LfAggregatedLoads;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class LfAggregatedLoadsImpl extends AbstractPropertyBag implements LfAggregatedLoads {

    private final List<Ref<Load>> loadsRefs = new ArrayList<>();

    private double[] participationFactors;

    private double absVariableLoadTargetP;

    private final boolean distributedOnConformLoad;

    private boolean initialized;

    private Map<String, Boolean> loadsStatus = new LinkedHashMap<>();

    LfAggregatedLoadsImpl(boolean distributedOnConformLoad) {
        this.distributedOnConformLoad = distributedOnConformLoad;
    }

    @Override
    public List<String> getOriginalIds() {
        return loadsRefs.stream().map(r -> r.get().getId()).collect(Collectors.toList());
    }

    void add(Load load, LfNetworkParameters parameters) {
        loadsRefs.add(Ref.create(load, parameters.isCacheEnabled()));
        loadsStatus.put(load.getId(), false);
        initialized = false;
    }

    @Override
    public double getAbsVariableLoadTargetP() {
        init();
        return absVariableLoadTargetP;
    }

    @Override
    public void setAbsVariableLoadTargetP(double absVariableLoadTargetP) {
        this.absVariableLoadTargetP = absVariableLoadTargetP;
    }

    private void init() {
        if (initialized) {
            return;
        }

        participationFactors = new double[loadsRefs.size()];
        absVariableLoadTargetP = 0;
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            double value;
            if (distributedOnConformLoad) {
                value = load.getExtension(LoadDetail.class) == null ? 0. : Math.abs(load.getExtension(LoadDetail.class).getVariableActivePower());
            } else {
                value = Math.abs(load.getP0());
            }
            absVariableLoadTargetP += value;
            participationFactors[i] = value;
        }

        if (absVariableLoadTargetP != 0) {
            for (int i = 0; i < participationFactors.length; i++) {
                participationFactors[i] /= absVariableLoadTargetP;
            }
        }
        absVariableLoadTargetP = absVariableLoadTargetP / PerUnit.SB;
        initialized = true;
    }

    @Override
    public double getLoadCount() {
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
    public double getLoadTargetQ(double diffLoadTargetP) {
        init();
        double newLoadTargetQ = 0;
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            double updatedP0 = load.getP0() / PerUnit.SB + diffLoadTargetP * participationFactors[i];
            newLoadTargetQ += getPowerFactor(load) * updatedP0;
        }
        return newLoadTargetQ;
    }

    @Override
    public boolean isDisabled(String originalId) {
        return loadsStatus.get(originalId);
    }

    @Override
    public void setDisabled(String originalId, boolean disabled) {
        loadsStatus.put(originalId, disabled);
    }

    @Override
    public Map<String, Boolean> getLoadsDisablingStatus() {
        return loadsStatus;
    }

    @Override
    public void setLoadsDisablingStatus(Map<String, Boolean> loadsStatus) {
        this.loadsStatus = loadsStatus;
    }

    private static double getPowerFactor(Load load) {
        return load.getP0() != 0 ? load.getQ0() / load.getP0() : 1;
    }

}
