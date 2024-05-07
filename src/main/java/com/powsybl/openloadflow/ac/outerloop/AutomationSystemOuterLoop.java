/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfSwitch;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AutomationSystemOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationSystemOuterLoop.class);

    public static final String NAME = "AutomationSystem";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        LfNetwork network = context.getNetwork();
        Set<LfBranch> branchesToOpen = new HashSet<>();
        Set<LfBranch> branchesToClose = new HashSet<>();
        for (LfOverloadManagementSystem system : network.getOverloadManagementSystems()) {
            LfBranch branchToMonitor = system.getMonitoredBranch();
            if (branchToMonitor.isConnectedAtBothSides()) {
                double i1 = branchToMonitor.getI1().eval();
                double threshold = system.getThreshold();
                if (i1 > threshold) {
                    double ib = PerUnit.ib(branchToMonitor.getBus1().getNominalV());
                    LfSwitch switchToOperate = system.getSwitchToOperate();
                    if (system.isSwitchOpen() && switchToOperate.isConnectedAtBothSides()) {
                        LOGGER.debug("Line '{}' is overloaded ({} A > {} A), open switch '{}'",
                                branchToMonitor.getId(), i1 * ib, threshold * ib, switchToOperate.getId());
                        branchesToOpen.add(switchToOperate);
                    } else if (!system.isSwitchOpen() && !switchToOperate.isConnectedAtBothSides()) {
                        LOGGER.debug("Line '{}' is overloaded ({} A > {} A), close switch '{}'",
                                branchToMonitor.getId(), i1 * ib, threshold * ib, switchToOperate.getId());
                        branchesToClose.add(switchToOperate);
                    }
                }
            }
        }

        if (branchesToOpen.size() + branchesToClose.size() > 0) {
            GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();
            branchesToOpen.forEach(connectivity::removeEdge);
            branchesToClose.forEach(branch -> connectivity.addEdge(branch.getBus1(), branch.getBus2(), branch));
            LfAction.updateBusesAndBranchStatus(connectivity);
            status = OuterLoopStatus.UNSTABLE;
        }

        return new OuterLoopResult(this, status);
    }
}
