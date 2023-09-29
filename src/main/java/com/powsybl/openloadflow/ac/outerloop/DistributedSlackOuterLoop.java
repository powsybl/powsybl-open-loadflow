/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DistributedSlackOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedSlackOuterLoop.class);

    public static final String NAME = "DistributedSlack";

    private final double slackBusPMaxMismatch;

    private final ActivePowerDistribution activePowerDistribution;

    private final boolean throwsExceptionInCaseOfFailure;

    public DistributedSlackOuterLoop(ActivePowerDistribution activePowerDistribution, boolean throwsExceptionInCaseOfFailure, double slackBusPMaxMismatch) {
        this.activePowerDistribution = Objects.requireNonNull(activePowerDistribution);
        this.throwsExceptionInCaseOfFailure = throwsExceptionInCaseOfFailure;
        this.slackBusPMaxMismatch = slackBusPMaxMismatch;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        double slackBusActivePowerMismatch = context.getLastNewtonRaphsonResult().getSlackBusActivePowerMismatch();
        if (Math.abs(slackBusActivePowerMismatch) > slackBusPMaxMismatch / PerUnit.SB) {

            ActivePowerDistribution.Result result = activePowerDistribution.run(context.getNetwork(), slackBusActivePowerMismatch);

            if (Math.abs(result.getRemainingMismatch()) > ActivePowerDistribution.P_RESIDUE_EPS) {
                Reports.reportMismatchDistributionFailure(reporter, context.getIteration(), result.getRemainingMismatch() * PerUnit.SB);

                if (throwsExceptionInCaseOfFailure) {
                    throw new PowsyblException("Failed to distribute slack bus active power mismatch, "
                            + result.getRemainingMismatch() * PerUnit.SB + " MW remains");
                }

                LOGGER.error("Failed to distribute slack bus active power mismatch, {} MW remains", result.getRemainingMismatch() * PerUnit.SB);

                return OuterLoopStatus.STABLE;
            } else {
                Reports.reportMismatchDistributionSuccess(reporter, context.getIteration(), slackBusActivePowerMismatch * PerUnit.SB, result.getIteration());

                LOGGER.info("Slack bus active power ({} MW) distributed in {} iterations",
                        slackBusActivePowerMismatch * PerUnit.SB, result.getIteration());

                return OuterLoopStatus.UNSTABLE;
            }
        }

        Reports.reportNoMismatchDistribution(reporter, context.getIteration());

        LOGGER.debug("Already balanced");

        return OuterLoopStatus.STABLE;
    }
}
