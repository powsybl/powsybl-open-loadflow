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
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractDistributedSlackOuterLoop<T> implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDistributedSlackOuterLoop.class);

    /**
     * Slack active power residue epsilon: 10^-5 in p.u => 10^-3 in Mw
     */
    public static final double SLACK_P_RESIDUE_EPS = Math.pow(10, -5);

    protected static class ParticipatingElement<T> {

        final T element;

        double factor;

        ParticipatingElement(T element, double factor) {
            this.element = element;
            this.factor = factor;
        }
    }

    private final boolean throwsExceptionInCaseOfFailure;

    protected AbstractDistributedSlackOuterLoop(boolean throwsExceptionInCaseOfFailure) {
        this.throwsExceptionInCaseOfFailure = throwsExceptionInCaseOfFailure;
    }

    protected abstract List<ParticipatingElement<T>> getParticipatingElements(LfNetwork network);

    protected void normalizeParticipationFactors(List<ParticipatingElement<T>> participatingElements, String elementType) {
        double factorSum = participatingElements.stream()
                .mapToDouble(participatingGenerator -> participatingGenerator.factor)
                .sum();
        if (factorSum == 0) {
            throw new PowsyblException("No more " + elementType + " participating to slack distribution");
        }
        for (ParticipatingElement<T> participatingElement : participatingElements) {
            participatingElement.factor /= factorSum;
        }
    }

    protected abstract double run(List<ParticipatingElement<T>> participatingElements, int iteration, double remainingMismatch);

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        double slackBusActivePowerMismatch = context.getLastNewtonRaphsonResult().getSlackBusActivePowerMismatch();
        if (Math.abs(slackBusActivePowerMismatch) > DefaultNewtonRaphsonStoppingCriteria.CONV_EPS_PER_EQ) {
            LfNetwork network = context.getNetwork();

            List<ParticipatingElement<T>> participatingElements = getParticipatingElements(network);

            int iteration = 0;
            double remainingMismatch = slackBusActivePowerMismatch;
            while (!participatingElements.isEmpty()
                    && Math.abs(remainingMismatch) > SLACK_P_RESIDUE_EPS) {

                remainingMismatch -= run(participatingElements, iteration, remainingMismatch);

                iteration++;
            }

            if (Math.abs(remainingMismatch) > SLACK_P_RESIDUE_EPS) {
                if (throwsExceptionInCaseOfFailure) {
                    throw new PowsyblException("Failed to distribute slack bus active power mismatch, "
                            + remainingMismatch * PerUnit.SB + " MW remains");
                }

                LOGGER.error("Failed to distribute slack bus active power mismatch, {} MW remains", remainingMismatch * PerUnit.SB);

                return OuterLoopStatus.STABLE;
            } else {
                LOGGER.info("Slack bus active power ({} MW) distributed in {} iterations",
                        slackBusActivePowerMismatch * PerUnit.SB, iteration);

                return OuterLoopStatus.UNSTABLE;
            }
        }

        LOGGER.debug("Already balanced");

        return OuterLoopStatus.STABLE;
    }
}
