/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.OpenLoadFlowReportConstants;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DistributedSlackOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedSlackOuterLoop.class);

    private final double slackBusPMaxMismatch;

    private final ActivePowerDistribution activePowerDistribution;

    private final boolean throwsExceptionInCaseOfFailure;

    public DistributedSlackOuterLoop(ActivePowerDistribution activePowerDistribution, boolean throwsExceptionInCaseOfFailure, double slackBusPMaxMismatch) {
        this.activePowerDistribution = Objects.requireNonNull(activePowerDistribution);
        this.throwsExceptionInCaseOfFailure = throwsExceptionInCaseOfFailure;
        this.slackBusPMaxMismatch = slackBusPMaxMismatch;
    }

    @Override
    public String getType() {
        return "Distributed slack on " + activePowerDistribution.getElementType();
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        double slackBusActivePowerMismatch = context.getLastNewtonRaphsonResult().getSlackBusActivePowerMismatch();
        if (Math.abs(slackBusActivePowerMismatch) > slackBusPMaxMismatch / PerUnit.SB) {

            ActivePowerDistribution.Result result = activePowerDistribution.run(context.getNetwork(), slackBusActivePowerMismatch);

            if (Math.abs(result.getRemainingMismatch()) > ActivePowerDistribution.P_RESIDUE_EPS) {
                reporter.report(Report.builder()
                    .withKey("mismatchDistributionFailure")
                    .withDefaultMessage("Iteration ${iteration}: failed to distribute slack bus active power mismatch, ${mismatch} MW remains")
                    .withValue("iteration", context.getIteration())
                    .withTypedValue("mismatch", result.getRemainingMismatch() * PerUnit.SB, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                    .withSeverity(OpenLoadFlowReportConstants.ERROR_SEVERITY)
                    .build());

                if (throwsExceptionInCaseOfFailure) {
                    throw new PowsyblException("Failed to distribute slack bus active power mismatch, "
                            + result.getRemainingMismatch() * PerUnit.SB + " MW remains");
                }

                LOGGER.error("Failed to distribute slack bus active power mismatch, {} MW remains", result.getRemainingMismatch() * PerUnit.SB);

                return OuterLoopStatus.STABLE;
            } else {
                reporter.report(Report.builder()
                    .withKey("mismatchDistributionSuccess")
                    .withDefaultMessage("Iteration ${iteration}: slack bus active power (${initialMismatch} MW) distributed in ${nbIterations} iterations")
                    .withValue("iteration", context.getIteration())
                    .withTypedValue("initialMismatch", slackBusActivePowerMismatch * PerUnit.SB, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                    .withValue("nbIterations", result.getIteration())
                    .withSeverity(OpenLoadFlowReportConstants.INFO_SEVERITY)
                    .build());
                LOGGER.info("Slack bus active power ({} MW) distributed in {} iterations",
                        slackBusActivePowerMismatch * PerUnit.SB, result.getIteration());

                return OuterLoopStatus.UNSTABLE;
            }
        }

        reporter.report(Report.builder()
            .withKey("NoMismatchDistribution")
            .withDefaultMessage("Iteration ${iteration}: already balanced")
            .withValue("iteration", context.getIteration())
            .withSeverity(OpenLoadFlowReportConstants.INFO_SEVERITY)
            .build());
        LOGGER.debug("Already balanced");

        return OuterLoopStatus.STABLE;
    }
}
