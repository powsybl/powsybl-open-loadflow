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
import com.powsybl.openloadflow.network.LfCurrentLimitAutomaton;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AutomatonOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomatonOuterLoop.class);

    @Override
    public String getType() {
        return "Automaton";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        for (LfBranch branch : context.getNetwork().getBranches()) {
            if (branch.getBus1() != null && branch.getBus2() != null) {
                List<LfCurrentLimitAutomaton> currentLimitAutomata = branch.getCurrentLimitAutomata();
                if (!currentLimitAutomata.isEmpty()) {
                    double i1 = branch.getI1().eval();
                    double limit = branch.getLimits1(LimitType.CURRENT).stream()
                            .map(LfBranch.LfLimit::getValue)
                            .min(Double::compare)
                            .orElse(Double.MAX_VALUE);
                    if (i1 > limit) {
                        double ib = PerUnit.ib(branch.getBus1().getNominalV());
                        for (LfCurrentLimitAutomaton cla : currentLimitAutomata) {
                            LOGGER.debug("Current limit automaton of line '{}' activated ({} > {}), {} switch '{}'",
                                    branch.getId(), i1 * ib, limit * ib, cla.isSwitchOpen() ? "open" : "close",
                                    cla.getSwitchToOperate().getId());
                            cla.getSwitchToOperate().setDisabled(cla.isSwitchOpen());
                            status = OuterLoopStatus.UNSTABLE;
                        }
                    }
                }
            }
        }
        return status;
    }
}
