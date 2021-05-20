/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.network.*;

public final class LfLoadImpl extends AbstractElement implements LfLoad {

    private final Load load;

    private final double p0;

    private double participationFactor;

    private double powerFactor;

    public LfLoadImpl(Load load, LfNetwork network) {
        super(network);
        this.load = load;
        this.p0 = load.getP0() / PerUnit.SB;
        this.powerFactor = load.getP0() != 0 ? load.getQ0() / load.getP0() : 1;
    }

    @Override
    public double getP0() {
        return p0;
    }

    @Override
    public double getUpdatedQ0() {
        return load.getTerminal().getQ();
    }

    @Override
    public double getParticipationFactor(boolean distributedOnConformLoad, double absLoadTargetP, double absVariableLoadTargetP) {
        if (distributedOnConformLoad) {
            this.participationFactor = load.getExtension(LoadDetail.class) == null ? 0. : Math.abs(load.getExtension(LoadDetail.class).getVariableActivePower()) / absVariableLoadTargetP;
        } else {
            this.participationFactor = Math.abs(p0 * PerUnit.SB) / absLoadTargetP;
        }
        return this.participationFactor;
    }

    @Override
    public double getPowerFactor() {
        return this.powerFactor;
    }

    @Override
    public String getId() {
        return load.getId();
    }

    @Override
    public ElementType getType() {
        return null;
    }

    @Override
    public void updateState(double diffP, boolean loadPowerFactorConstant) {
        double updatedP0 = p0 * PerUnit.SB + diffP;
        double updateQ0 = loadPowerFactorConstant ? this.powerFactor * updatedP0 : load.getQ0();
        load.getTerminal()
                .setP(updatedP0)
                .setQ(updateQ0);
    }
}
