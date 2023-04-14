/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.OuterLoopContext;
import com.powsybl.openloadflow.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class SimpleTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    @Override
    public String getType() {
        return "Simple transformer voltage control";
    }

    @Override
    public void initialize(OuterLoopContext context) {
        for (LfBranch controllerBranch : getControllerBranches(context.getNetwork())) {
            controllerBranch.setVoltageControlEnabled(true);
        }
        context.getNetwork().fixTransformerVoltageControls();
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        if (context.getIteration() == 0) {
            status = roundVoltageRatios(context);
        }
        return status;
    }
}
