/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.VoltageControl;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class SimpleTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    public static final String NAME = "SimpleTransformerVoltageControl";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            if (controllerBranch.isConnectedAtBothSides()) {
                controllerBranch.setVoltageControlEnabled(true);
            }
        }
        context.getNetwork().fixTransformerVoltageControls();
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        if (context.getIteration() == 0) {
            status = roundVoltageRatios(context.getNetwork());
        }
        return new OuterLoopResult(this, status);
    }
}
