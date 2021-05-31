/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfLoads extends AbstractElement {

    private final List<Load> loads = new ArrayList<>();

    private final List<Double> participationFactors = new ArrayList<>();

    private final List<Double> powerFactors = new ArrayList<>();

    private final List<Double> p0s = new ArrayList<>();

    private double absVariableLoadTargetP = 0;

    private boolean participationFactorsNormalized;

    protected LfLoads(LfNetwork network) {
        super(network);
    }

    public void add(Load load, boolean distributedOnConformLoad) {
        loads.add(load);
        double value;
        if (distributedOnConformLoad) {
            value = load.getExtension(LoadDetail.class) == null ? 0. : Math.abs(load.getExtension(LoadDetail.class).getVariableActivePower());
            absVariableLoadTargetP += value;
        } else {
            value = Math.abs(load.getP0());
            absVariableLoadTargetP += value;
        }
        participationFactors.add(value);
        powerFactors.add(load.getP0() != 0 ? load.getQ0() / load.getP0() : 1);
        p0s.add(load.getP0() / PerUnit.SB);
    }

    private List<Double> getParticipationFactors() {
        if (!participationFactorsNormalized) {
            normalizeParticipationFactors();
        }
        return participationFactors;
    }

    private void normalizeParticipationFactors() {
        if (absVariableLoadTargetP != 0) {
            for (int i = 0; i < participationFactors.size(); i++) {
                participationFactors.set(i, participationFactors.get(i) / absVariableLoadTargetP);
            }
        }
        participationFactorsNormalized = true;
    }

    public double getAbsVariableLoadTargetP() {
        return absVariableLoadTargetP;
    }

    public double getLoadCount() {
        return loads.size();
    }

    public void updateState(double diffLoadTargetP, boolean loadPowerFactorConstant) {
        for (int i = 0; i < loads.size(); i++) {
            double updatedP0 = (p0s.get(i) + diffLoadTargetP * getParticipationFactors().get(i)) * PerUnit.SB;
            double updatedQ0 = loadPowerFactorConstant ? powerFactors.get(i) * updatedP0 : loads.get(i).getQ0();
            loads.get(i).getTerminal().setP(updatedP0);
            loads.get(i).getTerminal().setQ(updatedQ0);
        }
    }

    public double getLoadTargetQ(double diffLoadTargetP) {
        double newLoadTargetQ = 0;
        for (int i = 0; i < loads.size(); i++) {
            newLoadTargetQ += powerFactors.get(i) * (p0s.get(i) + diffLoadTargetP * getParticipationFactors().get(i));
        }
        return newLoadTargetQ;
    }
}
