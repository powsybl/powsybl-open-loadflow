/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfLoadImpl extends AbstractLfInjection implements LfLoad {

    private final LfBus bus;

    private final LfLoadModel loadModel;

    private final List<Ref<Load>> loadsRefs = new ArrayList<>();

    private final List<Ref<LccConverterStation>> lccCsRefs = new ArrayList<>();

    private double targetQ = 0;

    private boolean ensurePowerFactorConstantByLoad = false;

    private final List<Double> loadsAbsVariableTargetP = new ArrayList<>();

    private double absVariableTargetP = 0;

    private final boolean distributedOnConformLoad;

    private Map<String, Boolean> loadsDisablingStatus = new LinkedHashMap<>();

    LfLoadImpl(LfBus bus, boolean distributedOnConformLoad, LfLoadModel loadModel) {
        super(0, 0);
        this.bus = Objects.requireNonNull(bus);
        this.distributedOnConformLoad = distributedOnConformLoad;
        this.loadModel = loadModel;
    }

    @Override
    public String getId() {
        return bus.getId() + "_load";
    }

    @Override
    public List<String> getOriginalIds() {
        return Stream.concat(loadsRefs.stream().map(r -> r.get().getId()),
                             lccCsRefs.stream().map(r -> r.get().getId()))
                .toList();
    }

    @Override
    public LfBus getBus() {
        return bus;
    }

    @Override
    public LfLoadModel getLoadModel() {
        return loadModel;
    }

    void add(Load load, LfNetworkParameters parameters) {
        loadsRefs.add(Ref.create(load, parameters.isCacheEnabled()));
        loadsDisablingStatus.put(load.getId(), false);
        double p0 = load.getP0();
        targetP += p0 / PerUnit.SB;
        initialTargetP += p0 / PerUnit.SB;
        targetQ += load.getQ0() / PerUnit.SB;
        boolean hasVariableActivePower = false;
        if (parameters.isDistributedOnConformLoad()) {
            LoadDetail loadDetail = load.getExtension(LoadDetail.class);
            if (loadDetail != null) {
                hasVariableActivePower = loadDetail.getFixedActivePower() != load.getP0();
            }
        }
        if (p0 < 0 || hasVariableActivePower) {
            ensurePowerFactorConstantByLoad = true;
        }
        double absTargetP = getAbsVariableTargetP(load);
        loadsAbsVariableTargetP.add(absTargetP);
        absVariableTargetP += absTargetP;
    }

    void add(LccConverterStation lccCs, LfNetworkParameters parameters) {
        // note that LCC converter station are out of the slack distribution.
        lccCsRefs.add(Ref.create(lccCs, parameters.isCacheEnabled()));
        double lccTargetP = HvdcConverterStations.getConverterStationTargetP(lccCs, parameters.isBreakers());
        this.targetP += lccTargetP / PerUnit.SB;
        initialTargetP += lccTargetP / PerUnit.SB;
        targetQ += HvdcConverterStations.getLccConverterStationLoadTargetQ(lccCs, parameters.isBreakers()) / PerUnit.SB;
    }

    public void add(DanglingLine danglingLine) {
        targetP += danglingLine.getP0() / PerUnit.SB;
        targetQ += danglingLine.getQ0() / PerUnit.SB;
    }

    @Override
    public void setTargetP(double targetP) {
        if (targetP != this.targetP) {
            double oldTargetP = this.targetP;
            this.targetP = targetP;
            for (LfNetworkListener listener : bus.getNetwork().getListeners()) {
                listener.onLoadActivePowerTargetChange(this, oldTargetP, targetP);
            }
        }
    }

    @Override
    public double getTargetQ() {
        return targetQ;
    }

    @Override
    public void setTargetQ(double targetQ) {
        if (targetQ != this.targetQ) {
            double oldTargetQ = this.targetQ;
            this.targetQ = targetQ;
            for (LfNetworkListener listener : bus.getNetwork().getListeners()) {
                listener.onLoadReactivePowerTargetChange(this, oldTargetQ, targetQ);
            }
        }
    }

    @Override
    public boolean ensurePowerFactorConstantByLoad() {
        return ensurePowerFactorConstantByLoad;
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
        // FIXME
        // After a load contingency or a load action, only the global variable targetP is updated.
        // The list loadsAbsVariableTargetP never changes. It is not an issue for security analysis as the network is
        // never updated. Excepted if loadPowerFactorConstant is true, the new targetQ could be wrong after a load contingency
        // or a load action.
        return absVariableTargetP != 0 ? loadsAbsVariableTargetP.get(i) / absVariableTargetP : 0;
    }

    @Override
    public void updateState(double diffLoadTargetP, boolean loadPowerFactorConstant, boolean breakers) {
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            double diffP0 = diffLoadTargetP * getParticipationFactor(i) * PerUnit.SB;
            double updatedP0 = load.getP0() + diffP0;
            double updatedQ0 = load.getQ0() + (loadPowerFactorConstant ? getPowerFactor(load) * diffP0 : 0.0);
            load.getTerminal()
                    .setP(updatedP0)
                    .setQ(updatedQ0);
        }

        // update lcc converter station power
        for (Ref<LccConverterStation> lccCsRef : lccCsRefs) {
            LccConverterStation lccCs = lccCsRef.get();
            double pCs = HvdcConverterStations.getConverterStationTargetP(lccCs, breakers); // A LCC station has active losses.
            double qCs = HvdcConverterStations.getLccConverterStationLoadTargetQ(lccCs, breakers); // A LCC station always consumes reactive power.
            lccCs.getTerminal()
                    .setP(pCs)
                    .setQ(qCs);
        }
    }

    @Override
    public double calculateNewTargetQ(double diffTargetP) {
        double newLoadTargetQ = 0;
        for (int i = 0; i < loadsRefs.size(); i++) {
            Load load = loadsRefs.get(i).get();
            double updatedQ0 = load.getQ0() / PerUnit.SB + getPowerFactor(load) * diffTargetP * getParticipationFactor(i);
            newLoadTargetQ += updatedQ0;
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
