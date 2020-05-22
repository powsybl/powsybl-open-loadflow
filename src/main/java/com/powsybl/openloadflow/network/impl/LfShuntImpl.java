/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfShuntImpl implements LfShunt {

    private final ShuntCompensator shuntCompensator;

    static class SusceptanceModelImpl implements SusceptanceModel {

        private final double b;

        SusceptanceModelImpl(double b) {
            this.b = b;
        }

        @Override
        public double getB() {
            return b;
        }
    }

    private final SusceptanceModel model;

    private Evaluable q = NAN;

    public LfShuntImpl(ShuntCompensator shuntCompensator) {
        this.shuntCompensator = Objects.requireNonNull(shuntCompensator);
        double nominalV = shuntCompensator.getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        double b = shuntCompensator.getCurrentB() * zb;
        model = new SusceptanceModelImpl(b);
    }

    @Override
    public SusceptanceModel getModel() {
        return model;
    }

    @Override
    public void setQ(Evaluable q) {
        this.q = Objects.requireNonNull(q);
    }

    @Override
    public void updateState() {
        shuntCompensator.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
