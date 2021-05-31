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

    private final List<Load> loads = new ArrayList<>();

    private final List<Double> participationFactors = new ArrayList<>();

    private final List<Double> powerFactors = new ArrayList<>();

    private final List<Double> p0s = new ArrayList<>();

    private double sumAbsVariableActivePowers = 0;

    private double initialLoadTargetP = 0;

    protected LfLoads(LfNetwork network) {
        super(network);
    }

    public void add(Load load, boolean distributedOnConformLoad) {
        loads.add(load);
        double absVariableActivePower;
        if (distributedOnConformLoad) {
            absVariableActivePower = load.getExtension(LoadDetail.class) == null ? 0. : Math.abs(load.getExtension(LoadDetail.class).getVariableActivePower());
            sumAbsVariableActivePowers += absVariableActivePower;
        } else {
            absVariableActivePower = Math.abs(load.getP0());
            sumAbsVariableActivePowers += absVariableActivePower;
        }
        initialLoadTargetP += load.getP0() / PerUnit.SB;
        participationFactors.add(absVariableActivePower);
        powerFactors.add(load.getP0() != 0 ? load.getQ0() / load.getP0() : 1);
        p0s.add(load.getP0() / PerUnit.SB);
    }

    public List<Double> getParticipationFactors() {
        return sumAbsVariableActivePowers != 0 ? participationFactors.stream().map(p -> p / sumAbsVariableActivePowers).collect(Collectors.toList()) : participationFactors;
    }

    public double getAbsVariableLoadTargetP() {
        return sumAbsVariableActivePowers;
    }

    public double getLoadCount() {
        return loads.size();
    }

    public void updateState(double loadTargetP, boolean loadPowerFactorConstant) {
        double diffTargetP = getLoadCount() > 0 ? loadTargetP - initialLoadTargetP * PerUnit.SB : 0;
        double updatedP0;
        double updatedQ0;
        for (int i = 0; i < getLoadCount(); i++) {
            double diffP = diffTargetP * getParticipationFactors().get(i);
            updatedP0 = p0s.get(i) * PerUnit.SB + diffP;
            updatedQ0 = loadPowerFactorConstant ? powerFactors.get(i) * updatedP0 : loads.get(i).getQ0();
            loads.get(i).getTerminal().setP(updatedP0);
            loads.get(i).getTerminal().setQ(updatedQ0);
        }
    }

    public double getLoadTargetQ(double newLoadTargetP) {
        double newLoadTargetQ = 0;
        for (int i = 0; i < getLoadCount(); i++) {
            newLoadTargetQ += powerFactors.get(i) * (p0s.get(i) + (newLoadTargetP - initialLoadTargetP) * getParticipationFactors().get(i));
        }
        return newLoadTargetQ;
    }
}
