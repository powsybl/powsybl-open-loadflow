/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.OuterLoopContext;
import com.powsybl.openloadflow.IncrementalContextData;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public class DCPhaseShifterControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DCPhaseShifterControlOuterLoop.class);

    private static final int MAX_DIRECTION_CHANGE = 2;
    private static final int MAX_TAP_SHIFT = Integer.MAX_VALUE;
    private static final double MIN_TARGET_DEADBAND = 1 / PerUnit.SB; // 1 MW
    private static final double SENSI_EPS = 1e-6;
    private static final double PHASE_SHIFT_CROSS_IMPACT_MARGIN = 0.75;

    public String getType() {
        return "DC phase shifter control";
    }

    public void initialize(OuterLoopContext context) {
        var contextData = new IncrementalContextData();
        context.setData(contextData);

        /*List<LfBranch> controllerBranches = getControllerBranches(context.getNetwork());
        for (LfBranch controllerBranch : controllerBranches) {
            contextData.getControllersContexts().put(controllerBranch.getId(), new IncrementalContextData.ControllerContext(MAX_DIRECTION_CHANGE));
        }

        fixPhaseShifterNecessaryForConnectivity(context.getNetwork(), controllerBranches);*/
    }
}
