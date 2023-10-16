/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfOverloadManagementSystem;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AutomationSystemOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationSystemOuterLoop.class);

    public static final String NAME = "AutomationSystem";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        for (LfOverloadManagementSystem system : context.getNetwork().getOverloadManagementSystems()) {
            LfBranch branch = system.getBranchToMonitor();
            if (branch.isConnectedAtBothSides()) {
                double i1 = branch.getI1().eval();
                double threshold = system.getThreshold();
                if (i1 > threshold) {
                    double ib = PerUnit.ib(branch.getBus1().getNominalV());
                    LOGGER.debug("Line '{}' is overloaded ({} A > {} A), {} switch '{}'",
                            branch.getId(), i1 * ib, threshold * ib, system.isSwitchOpen() ? "open" : "close",
                            system.getSwitchToOperate().getId());
                    system.getSwitchToOperate().setDisabled(system.isSwitchOpen());
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }
        return status;
    }
}
