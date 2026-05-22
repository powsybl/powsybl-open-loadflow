/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.lf.outerloop.AbstractActivePowerDistributionOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.DistributedSlackContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfSynchronousNetwork;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DistributedSlackOuterLoop
    extends AbstractActivePowerDistributionOuterLoop<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext, AcOuterLoopContext>
    implements AcOuterLoop, AcActivePowerDistributionOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedSlackOuterLoop.class);

    public static final String NAME = "DistributedSlack";

    private final double slackBusPMaxMismatch;

    private final ActivePowerDistribution activePowerDistribution;

    public DistributedSlackOuterLoop(ActivePowerDistribution activePowerDistribution, double slackBusPMaxMismatch) {
        this.activePowerDistribution = Objects.requireNonNull(activePowerDistribution);
        this.slackBusPMaxMismatch = slackBusPMaxMismatch;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        context.setData(new DistributedSlackContextData());
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        // FIXME Noisy reports
        Map<Integer, Double> slackMismatchPerSynchronousComponent = context.getLastSolverResult().getSlackBusActivePowerMismatch();
        OuterLoopStatus globalStatus = OuterLoopStatus.STABLE;
        for (LfSynchronousNetwork lfScNetwork : context.getNetwork().getSynchronousNetworks()) {
//            ReportNode scReport = Reports.createLfSynchronousNetworkReportNode(reportNode, lfScNetwork.getNumSC());
            OuterLoopResult result = check(lfScNetwork, slackMismatchPerSynchronousComponent.get(lfScNetwork.getNumSC()), context, reportNode);
            if (result.status() == OuterLoopStatus.FAILED) {
                return result; // We do not wait for the outer loops on other synchronous components
            } else if (result.status() == OuterLoopStatus.UNSTABLE) {
                globalStatus = OuterLoopStatus.UNSTABLE;
            }
        }
        return new OuterLoopResult(this, globalStatus);

    }

    public OuterLoopResult check(LfSynchronousNetwork lfScNetwork, double slackBusActivePowerMismatch, AcOuterLoopContext context, ReportNode reportNode) {
        double absMismatch = Math.abs(slackBusActivePowerMismatch);
        boolean shouldDistributeSlack = absMismatch > slackBusPMaxMismatch / PerUnit.SB && absMismatch > ActivePowerDistribution.P_RESIDUE_EPS;

        if (!shouldDistributeSlack) {
            LOGGER.debug("Already balanced");
            return new OuterLoopResult(this, OuterLoopStatus.STABLE);
        }
        ReportNode iterationReportNode = Reports.createOuterLoopIterationReporter(reportNode, context.getOuterLoopTotalIterations() + 1);
        ActivePowerDistribution.Result result = activePowerDistribution.run(lfScNetwork, slackBusActivePowerMismatch);
        ActivePowerDistribution.ResultWithFailureBehaviorHandling resultWbh = ActivePowerDistribution.handleDistributionFailureBehavior(
            context.getLoadFlowContext().getParameters().getSlackDistributionFailureBehavior(),
            lfScNetwork.getReferenceGenerator(),
            slackBusActivePowerMismatch,
            result,
            "Failed to distribute slack bus active power mismatch, %.2f MW remains"
        );
        double remainingMismatch = resultWbh.remainingMismatch();
        double distributedActivePower = slackBusActivePowerMismatch - remainingMismatch;
        if (Math.abs(remainingMismatch) > slackBusPMaxMismatch / PerUnit.SB) {
            Reports.reportMismatchDistributionFailure(iterationReportNode, remainingMismatch * PerUnit.SB);
        } else {
            if (Math.abs(remainingMismatch) > ActivePowerDistribution.P_RESIDUE_EPS) {
                Reports.reportResidualDistributionMismatch(reportNode, remainingMismatch * PerUnit.SB);
            }
            ActivePowerDistribution.reportAndLogSuccess(iterationReportNode, slackBusActivePowerMismatch, resultWbh);
        }
        DistributedSlackContextData contextData = (DistributedSlackContextData) context.getData();
        contextData.addDistributedActivePower(lfScNetwork.getNumSC(), distributedActivePower);
        if (resultWbh.failed()) {
            contextData.addDistributedActivePower(lfScNetwork.getNumSC(), -resultWbh.failedDistributedActivePower());
            return new OuterLoopResult(this, OuterLoopStatus.FAILED, resultWbh.failedMessage());
        } else {
            return new OuterLoopResult(this, resultWbh.movedBuses() ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE);
        }
    }

    @Override
    public double getSlackBusActivePowerMismatch(AcOuterLoopContext context) {
        return context.getLastSolverResult().getSlackBusActivePowerMismatch().values().stream().reduce(0., Double::sum);
    }

    @Override
    public double getDistributedActivePower(AcOuterLoopContext context, int numSC) {
        return ((DistributedSlackContextData) context.getData()).getDistributedActivePower(numSC);
    }
}
