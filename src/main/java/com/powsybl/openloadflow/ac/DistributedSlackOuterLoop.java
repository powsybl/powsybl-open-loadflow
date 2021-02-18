/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.nr.DefaultNewtonRaphsonStoppingCriteria;
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

    private final ActivePowerDistribution activePowerDistribution;

    private final boolean throwsExceptionInCaseOfFailure;

    public DistributedSlackOuterLoop(ActivePowerDistribution activePowerDistribution, boolean throwsExceptionInCaseOfFailure) {
        this.activePowerDistribution = Objects.requireNonNull(activePowerDistribution);
        this.throwsExceptionInCaseOfFailure = throwsExceptionInCaseOfFailure;
    }

    @Override
    public String getType() {
        return "Distributed slack on " + activePowerDistribution.getElementType();
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        double slackBusActivePowerMismatch = context.getLastNewtonRaphsonResult().getSlackBusActivePowerMismatch();
        if (Math.abs(slackBusActivePowerMismatch) > DefaultNewtonRaphsonStoppingCriteria.CONV_EPS_PER_EQ) {

            ActivePowerDistribution.Result result = activePowerDistribution.run(context.getNetwork(), slackBusActivePowerMismatch, context.getProfiler());

            if (Math.abs(result.getRemainingMismatch()) > ActivePowerDistribution.P_RESIDUE_EPS) {
                if (throwsExceptionInCaseOfFailure) {
                    throw new PowsyblException("Failed to distribute slack bus active power mismatch, "
                            + result.getRemainingMismatch() * PerUnit.SB + " MW remains");
                }

                LOGGER.error("Failed to distribute slack bus active power mismatch, {} MW remains", result.getRemainingMismatch() * PerUnit.SB);

                return OuterLoopStatus.STABLE;
            } else {
                LOGGER.info("Slack bus active power ({} MW) distributed in {} iterations",
                        slackBusActivePowerMismatch * PerUnit.SB, result.getIteration());

                return OuterLoopStatus.UNSTABLE;
            }
        }

        LOGGER.debug("Already balanced");

        return OuterLoopStatus.STABLE;
    }
}
