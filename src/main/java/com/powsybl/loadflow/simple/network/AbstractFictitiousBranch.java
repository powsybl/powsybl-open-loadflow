/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import com.powsybl.loadflow.simple.util.Evaluable;

import java.util.Objects;

import static com.powsybl.loadflow.simple.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractFictitiousBranch extends AbstractLfBranch {

    protected Evaluable p = NAN;

    protected Evaluable q = NAN;

    protected AbstractFictitiousBranch(LfBus bus1, LfBus bus2, PiModel piModel, double nominalV1, double nominalV2) {
        super(bus1, bus2, piModel, nominalV1, nominalV2);
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p = Objects.requireNonNull(p1);
    }

    @Override
    public void setP2(Evaluable p2) {
        // nothing to do
    }

    @Override
    public void setQ1(Evaluable q1) {
        this.q = Objects.requireNonNull(q1);
    }

    @Override
    public void setQ2(Evaluable q2) {
        // nothing to do
    }
}
