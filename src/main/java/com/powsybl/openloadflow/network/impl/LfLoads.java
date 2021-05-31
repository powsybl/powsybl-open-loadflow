package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LfLoads extends AbstractElement {

    protected final List<Double> participationFactors = new ArrayList<>();

    protected final List<Double> powerFactors = new ArrayList<>();

    protected final List<Double> p0s = new ArrayList<>();

    protected double sumAbsVariableActivePowers = 0;

    protected int loadCount = 0;

    protected double initialLoadTargetP = 0;

    protected LfLoads(LfNetwork network) {
        super(network);
    }

    public void add(Load load, boolean distributedOnConformLoad) {
        loadCount++;
        double value;
        if (distributedOnConformLoad) {
            value = load.getExtension(LoadDetail.class) == null ? 0. : Math.abs(load.getExtension(LoadDetail.class).getVariableActivePower());
            sumAbsVariableActivePowers += value;
        } else {
            value = Math.abs(load.getP0());
            sumAbsVariableActivePowers += value;
        }
        initialLoadTargetP += load.getP0() / PerUnit.SB;
        participationFactors.add(value);
        powerFactors.add(load.getP0() != 0 ? load.getQ0() / load.getP0() : 1);
        p0s.add(load.getP0() / PerUnit.SB);
    }

    public List<Double> getParticipationFactors() {
        return sumAbsVariableActivePowers != 0 ? participationFactors.stream().map(p -> p / sumAbsVariableActivePowers).collect(Collectors.toList()) : participationFactors;
    }

    public List<Double> getPowerFactors() {
        return this.powerFactors;
    }

    public List<Double> getP0s() {
        return this.p0s;
    }

    public double getAbsVariableLoadTargetP() {
        return sumAbsVariableActivePowers;
    }

    public double getLoadCount() {
        return loadCount;
    }

    public double getInitialLoadTargetP() {
        return initialLoadTargetP;
    }
}
