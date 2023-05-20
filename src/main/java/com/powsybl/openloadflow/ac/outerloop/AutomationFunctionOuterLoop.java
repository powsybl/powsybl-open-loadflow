/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.openloadflow.ac.OuterLoop;
import com.powsybl.openloadflow.ac.OuterLoopContext;
import com.powsybl.openloadflow.ac.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfOverloadManagementFunction;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AutomationFunctionOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationFunctionOuterLoop.class);

    @Override
    public String getType() {
        return "AutomationFunction";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        for (LfOverloadManagementFunction function : context.getNetwork().getOverloadManagementFunctions()) {
            LfBranch branch = function.getBranchToMonitor();
            if (branch.isConnectedAtBothSides()) {
                double i1 = branch.getI1().eval();
                double limit = branch.getLimits1(LimitType.CURRENT).stream()
                        .map(LfBranch.LfLimit::getValue)
                        .min(Double::compare)
                        .orElse(Double.MAX_VALUE);
                if (i1 > limit) {
                    double ib = PerUnit.ib(branch.getBus1().getNominalV());
                    LOGGER.debug("Line '{}' is overloaded ({} > {}), {} switch '{}'",
                            branch.getId(), i1 * ib, limit * ib, function.isSwitchOpen() ? "open" : "close",
                            function.getSwitchToOperate().getId());
                    function.getSwitchToOperate().setDisabled(function.isSwitchOpen());
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }
        return status;
    }
}
