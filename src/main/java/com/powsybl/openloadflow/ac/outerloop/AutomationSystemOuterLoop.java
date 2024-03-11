/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
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
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        LfNetwork network = context.getNetwork();
        Set<LfBranch> branchesToOpen = new HashSet<>();
        Set<LfBranch> branchesToClose = new HashSet<>();
        for (LfOverloadManagementSystem system : network.getOverloadManagementSystems()) {
            LfBranch branchToMonitor = system.getMonitoredBranch();
            if (branchToMonitor.isConnectedAtBothSides()) {
                double i1 = branchToMonitor.getI1().eval(); // FIXME Side
                for (LfOverloadManagementSystem.LfBranchTripping branchTripping : system.getBranchTrippingList()) {
                    double threshold = branchTripping.threshold();
                    LfBranch branchToOperate = branchTripping.branchToOperate();
                    if (i1 > threshold && branchTripping.branchOpen() != branchToOperate.isDisabled()) {
                        double ib = PerUnit.ib(branchToMonitor.getBus1().getNominalV());
                        if (branchTripping.branchOpen() && branchToOperate.isConnectedAtBothSides()) {
                            LOGGER.debug("Branch '{}' is overloaded ({} A > {} A), open branch '{}'",
                                    branchToMonitor.getId(), i1 * ib, threshold * ib, branchToOperate.getId());
                            branchesToOpen.add(branchToOperate);
                            break;
                        } else if (!branchTripping.branchOpen() && branchToOperate.isConnectedAtBothSides()) {
                            LOGGER.debug("Branch '{}' is overloaded ({} A > {} A), close branch '{}'",
                                    branchToMonitor.getId(), i1 * ib, threshold * ib, branchToOperate.getId());
                            branchesToClose.add(branchToOperate);
                            break;
                        }
                    }
                }
            }
        }

        if (branchesToOpen.size() + branchesToClose.size() > 0) {
            // we have an issue with connectivity here: we have to start temporary changes and undo them just after to
            // be able to retrieve the enabled and disabled elements compared to state just after restoring the initial topology
            // (restoreInitialTopology method).
            network.getConnectivity().startTemporaryChanges();
            GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();
            branchesToOpen.forEach(connectivity::removeEdge);
            branchesToClose.forEach(branch -> connectivity.addEdge(branch.getBus1(), branch.getBus2(), branch));
            LfAction.updateBusesAndBranchStatus(connectivity);
            network.getConnectivity().undoTemporaryChanges();
            // we have now to really change the network connectivity.
            branchesToOpen.forEach(connectivity::removeEdge);
            branchesToClose.forEach(branch -> connectivity.addEdge(branch.getBus1(), branch.getBus2(), branch));
            status = OuterLoopStatus.UNSTABLE;
        }

        return status;
    }
}
