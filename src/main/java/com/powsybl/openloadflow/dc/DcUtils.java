/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * SPDX-License-Identifier: MPL-2.0
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.network.util.ParticipatingElement.normalizeParticipationFactors;

/**
 * FIXME: no idea if it is the good design for these classes
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public final class DcUtils {

    private DcUtils() {
    }

    public static boolean isDistributedSlackOnGenerators(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P);
    }

    public static boolean isDistributedSlackOnLoads(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
    }

    public static List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses, LoadFlowParameters.BalanceType balanceType, OpenLoadFlowParameters openLoadFlowParameters) {
        ActivePowerDistribution.Step step = ActivePowerDistribution.getStep(balanceType, openLoadFlowParameters.isLoadPowerFactorConstant(), openLoadFlowParameters.isUseActiveLimits());
        List<ParticipatingElement> participatingElements = step.getParticipatingElements(buses);
        ParticipatingElement.normalizeParticipationFactors(participatingElements);
        return participatingElements;
    }

    public static List<ParticipatingElement> getNewNormalizedParticipationFactors(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt,
                                                                                  LfContingency lfContingency, List<ParticipatingElement> participatingElements) {
        LfNetwork lfNetwork = loadFlowContext.getNetwork();
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
        List<ParticipatingElement> newParticipatingElements;
        if (isDistributedSlackOnGenerators(loadFlowContext.getParameters())) {
            // deep copy of participatingElements, removing the participating LfGeneratorImpl whose targetP has been set to 0
            Set<LfGenerator> participatingGeneratorsToRemove = lfContingency.getLostGenerators();
            newParticipatingElements = participatingElements.stream()
                    .filter(participatingElement -> !(participatingElement.getElement() instanceof LfGenerator lfGenerator
                            && participatingGeneratorsToRemove.contains(lfGenerator)))
                    .map(participatingElement -> new ParticipatingElement(participatingElement.getElement(), participatingElement.getFactor()))
                    .toList();
            normalizeParticipationFactors(newParticipatingElements);
        } else { // slack distribution on loads
            newParticipatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
        }
        return newParticipatingElements;
    }

    /**
     * Compute flow rhs taking into account slack distribution.
     */
    public static DenseMatrix getPreContingencyFlowRhs(DcLoadFlowContext loadFlowContext,
                                                       List<ParticipatingElement> participatingElements,
                                                       DisabledNetwork disabledNetwork) {
        List<BusState> busStates = Collections.emptyList();
        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            busStates = ElementState.save(participatingElements.stream()
                    .map(ParticipatingElement::getLfBus)
                    .collect(Collectors.toSet()), BusState::save);
        }

        double[] preContingencyFlowRhs = getDcLoadFlowTargetVector(loadFlowContext, disabledNetwork);

        if (parameters.isDistributedSlack()) {
            ElementState.restore(busStates);
        }

        return new DenseMatrix(preContingencyFlowRhs.length, 1, preContingencyFlowRhs);
    }

    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling, that only
     * returns the target vector of the equation system.
     */
    private static double[] getDcLoadFlowTargetVector(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork) {

        Collection<LfBus> remainingBuses;
        if (disabledNetwork.getBuses().isEmpty()) {
            remainingBuses = loadFlowContext.getNetwork().getBuses();
        } else {
            remainingBuses = new LinkedHashSet<>(loadFlowContext.getNetwork().getBuses());
            remainingBuses.removeAll(disabledNetwork.getBuses());
        }

        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            DcLoadFlowEngine.distributeSlack(remainingBuses, parameters.getBalanceType(), parameters.getNetworkParameters().isUseActiveLimits());
        }

        // we need to copy the target array because:
        //  - in case of disabled buses or branches some elements could be overwritten to zero
        //  - JacobianMatrix.solveTransposed take as an input the second member and reuse the array
        //    to fill with the solution
        // so we need to copy to later the target as it is and reusable for next run
        var targetVectorArray = loadFlowContext.getTargetVector().getArray().clone();

        if (!disabledNetwork.getBuses().isEmpty()) {
            // set buses injections and transformers to 0
            disabledNetwork.getBuses().stream()
                    .flatMap(lfBus -> loadFlowContext.getEquationSystem().getEquation(lfBus.getNum(), DcEquationType.BUS_TARGET_P).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        if (!disabledNetwork.getBranches().isEmpty()) {
            // set transformer phase shift to 0
            disabledNetwork.getBranches().stream()
                    .flatMap(lfBranch -> loadFlowContext.getEquationSystem().getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        return targetVectorArray;
    }
}
