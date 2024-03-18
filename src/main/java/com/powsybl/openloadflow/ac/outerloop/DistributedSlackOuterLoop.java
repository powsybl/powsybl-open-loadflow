/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.DistributedSlackContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DistributedSlackOuterLoop implements AcOuterLoop {

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
        var contextData = new DistributedSlackContextData();
        context.setData(contextData);
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        double slackBusActivePowerMismatch = context.getLastSolverResult().getSlackBusActivePowerMismatch();
        boolean shouldDistributeSlack = Math.abs(slackBusActivePowerMismatch) > slackBusPMaxMismatch / PerUnit.SB;

        if (!shouldDistributeSlack) {
            LOGGER.debug("Already balanced");
            return OuterLoopStatus.STABLE;
        }

        Reporter iterationReporter = Reports.createOuterLoopIterationReporter(reporter, context.getOuterLoopTotalIterations() + 1);
        ActivePowerDistribution.Result result = activePowerDistribution.run(context.getNetwork(), slackBusActivePowerMismatch);
        double remainingMismatch = result.remainingMismatch();
        double distributedActivePower = slackBusActivePowerMismatch - remainingMismatch;
        DistributedSlackContextData contextData = (DistributedSlackContextData) context.getData();
        contextData.addDistributedActivePower(distributedActivePower);
        if (Math.abs(remainingMismatch) > ActivePowerDistribution.P_RESIDUE_EPS) {
            OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior = context.getLoadFlowContext().getParameters().getSlackDistributionFailureBehavior();
            LfGenerator referenceGenerator = context.getNetwork().getReferenceGenerator();
            if (OpenLoadFlowParameters.SlackDistributionFailureBehavior.DISTRIBUTE_ON_REFERENCE_GENERATOR == slackDistributionFailureBehavior) {
                if (referenceGenerator == null) {
                    // no reference generator, fall back internally to FAIL mode
                    slackDistributionFailureBehavior = OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL;
                    Reports.reportMismatchDistributionFailure(iterationReporter, remainingMismatch * PerUnit.SB);
                }
            } else {
                Reports.reportMismatchDistributionFailure(iterationReporter, remainingMismatch * PerUnit.SB);
            }

            switch (slackDistributionFailureBehavior) {
                case THROW ->
                    throw new PowsyblException("Failed to distribute slack bus active power mismatch, "
                            + remainingMismatch * PerUnit.SB + " MW remains");
                case LEAVE_ON_SLACK_BUS -> {
                    LOGGER.warn("Failed to distribute slack bus active power mismatch, {} MW remains",
                            remainingMismatch * PerUnit.SB);
                    return result.movedBuses() ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE;
                }
                case DISTRIBUTE_ON_REFERENCE_GENERATOR -> {
                    Objects.requireNonNull(referenceGenerator, () -> "No reference generator in " + context.getNetwork());
                    // remaining goes to reference generator, without any limit consideration
                    LOGGER.debug("{} MW distributed to reference generator '{}'",
                            remainingMismatch * PerUnit.SB, referenceGenerator.getId());
                    contextData.addDistributedActivePower(remainingMismatch);
                    referenceGenerator.setTargetP(referenceGenerator.getTargetP() + remainingMismatch);
                    // create a new result with iteration++, 0.0 mismatch and movedBuses to true
                    result = new ActivePowerDistribution.Result(result.iteration() + 1, 0.0, true);
                    reportAndLogSuccess(iterationReporter, slackBusActivePowerMismatch, result);
                    return OuterLoopStatus.UNSTABLE;
                }
                case FAIL -> {
                    LOGGER.error("Failed to distribute slack bus active power mismatch, {} MW remains",
                            remainingMismatch * PerUnit.SB);
                    // Mismatches reported in LoadFlowResult on slack bus(es) are the mismatches of the last NR run.
                    // Since we will not be re-running an NR, revert distributedActivePower reporting which would otherwise be misleading.
                    // Said differently, we report that we didn't distribute anything, and this is indeed consistent with the network state.
                    contextData.addDistributedActivePower(-distributedActivePower);
                    return OuterLoopStatus.FAILED;
                }
                default -> throw new IllegalArgumentException("Unknown slackDistributionFailureBehavior");
            }
        } else {
            reportAndLogSuccess(iterationReporter, slackBusActivePowerMismatch, result);
            return OuterLoopStatus.UNSTABLE;
        }
    }

    private static void reportAndLogSuccess(Reporter reporter, double slackBusActivePowerMismatch, ActivePowerDistribution.Result result) {
        Reports.reportMismatchDistributionSuccess(reporter, slackBusActivePowerMismatch * PerUnit.SB, result.iteration());

        LOGGER.info("Slack bus active power ({} MW) distributed in {} distribution iteration(s)",
                slackBusActivePowerMismatch * PerUnit.SB, result.iteration());
    }
}
