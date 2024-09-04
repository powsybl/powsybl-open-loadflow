/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class ActivePowerDistribution {

    /**
     * Active power residue epsilon: 10^-5 in p.u => 10^-3 in Mw
     */
    public static final double P_RESIDUE_EPS = Math.pow(10, -5);

    public interface Step {

        String getElementType();

        List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses);

        double run(List<ParticipatingElement> participatingElements, int iteration, double remainingMismatch);
    }

    public record Result(int iteration, double remainingMismatch, boolean movedBuses) { }

    private final ActivePowerDistribution.Step step;

    private ActivePowerDistribution(Step step) {
        this.step = Objects.requireNonNull(step);
    }

    public String getElementType() {
        return step.getElementType();
    }

    public Result run(LfNetwork network, double activePowerMismatch) {
        return run(network.getReferenceGenerator(), network.getBuses(), activePowerMismatch);
    }

    public Result run(LfGenerator referenceGenerator, Collection<LfBus> buses, double activePowerMismatch) {
        List<ParticipatingElement> participatingElements = step.getParticipatingElements(buses);
        final Map<ParticipatingElement, Double> initialP = participatingElements.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), ParticipatingElement::getTargetP));

        int iteration = 0;
        double remainingMismatch = activePowerMismatch;

        if (referenceGenerator != null) {
            remainingMismatch -= referenceGenerator.getInitialTargetP() - referenceGenerator.getTargetP();
            referenceGenerator.setTargetP(referenceGenerator.getInitialTargetP());
        }

        while (!participatingElements.isEmpty()
                && Math.abs(remainingMismatch) > P_RESIDUE_EPS) {

            if (ParticipatingElement.participationFactorNorm(participatingElements) > 0.0) {
                double done = step.run(participatingElements, iteration, remainingMismatch);
                remainingMismatch -= done;
            } else {
                break;
            }
            iteration++;
        }

        final boolean movedBuses = initialP.entrySet().stream()
                .anyMatch(e -> Math.abs(e.getKey().getTargetP() - e.getValue()) > P_RESIDUE_EPS);

        return new Result(iteration, remainingMismatch, movedBuses);
    }

    public static ActivePowerDistribution create(LoadFlowParameters.BalanceType balanceType, boolean loadPowerFactorConstant, boolean useActiveLimits) {
        return new ActivePowerDistribution(getStep(balanceType, loadPowerFactorConstant, useActiveLimits));
    }

    public static Step getStep(LoadFlowParameters.BalanceType balanceType, boolean loadPowerFactorConstant, boolean useActiveLimits) {
        Objects.requireNonNull(balanceType);
        return switch (balanceType) {
            case PROPORTIONAL_TO_LOAD, PROPORTIONAL_TO_CONFORM_LOAD ->
                    new LoadActivePowerDistributionStep(loadPowerFactorConstant);
            case PROPORTIONAL_TO_GENERATION_P_MAX ->
                    new GenerationActivePowerDistributionStep(GenerationActivePowerDistributionStep.ParticipationType.MAX, useActiveLimits);
            case PROPORTIONAL_TO_GENERATION_P ->
                    new GenerationActivePowerDistributionStep(GenerationActivePowerDistributionStep.ParticipationType.TARGET, useActiveLimits);
            case PROPORTIONAL_TO_GENERATION_PARTICIPATION_FACTOR ->
                    new GenerationActivePowerDistributionStep(GenerationActivePowerDistributionStep.ParticipationType.PARTICIPATION_FACTOR, useActiveLimits);
            case PROPORTIONAL_TO_GENERATION_REMAINING_MARGIN ->
                    new GenerationActivePowerDistributionStep(GenerationActivePowerDistributionStep.ParticipationType.REMAINING_MARGIN, useActiveLimits);
        };
    }
}
