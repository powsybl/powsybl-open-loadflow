/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Evaluable;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractFictitiousLfBranch extends AbstractImpedantLfBranch {

    protected AbstractFictitiousLfBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel) {
        super(network, bus1, bus2, piModel);
    }

    @Override
    public void setI2(Evaluable i2) {
        // nothing to do
    }

    @Override
    public Evaluable getI2() {
        return NAN;
    }
}
