/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.DistributedSlackContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public class AreaInterchangeControlOuterloop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AreaInterchangeControlOuterloop.class);

    public static final String NAME = "AreaInterchangeControl";

    private final double areaInterchangePMaxMismatch;

    private final ActivePowerDistribution activePowerDistribution;

    /**
     * The part of the total slack active power mismatch that should be added to the Area's net interchange mismatch, ie the part of the slack that should be distributed in the Area.
     */

    private final Map<LfArea, Double> areaSlackDistributionParticipationFactor;

    public AreaInterchangeControlOuterloop(ActivePowerDistribution activePowerDistribution, double areaInterchangePMaxMismatch) {
        this.activePowerDistribution = Objects.requireNonNull(activePowerDistribution);
        this.areaInterchangePMaxMismatch = areaInterchangePMaxMismatch;
        this.areaSlackDistributionParticipationFactor = new HashMap<>();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        var contextData = new DistributedSlackContextData();
        context.setData(contextData);
        LfNetwork network = context.getNetwork();
        allocateSlackDistributionParticipationFactors(network);
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        List<LfArea> areas = context.getNetwork().getAreas();
        double slackBusActivePowerMismatch = context.getLastSolverResult().getSlackBusActivePowerMismatch();

        Map<LfArea, Double> areaInterchangeMismatches = areas.stream()
                .collect(Collectors.toMap(area -> area, area -> getInterchangeMismatchWithSlack(area, slackBusActivePowerMismatch)));
        List<LfArea> areasToBalance = areaInterchangeMismatches.entrySet().stream()
                .filter(entry -> {
                    double areaActivePowerMismatch = entry.getValue();
                    double absMismatch = Math.abs(areaActivePowerMismatch);
                    return absMismatch > this.areaInterchangePMaxMismatch / areas.size() / PerUnit.SB && absMismatch > ActivePowerDistribution.P_RESIDUE_EPS;
                })
                .map(Map.Entry::getKey)
                .toList();
        boolean shouldBalance = !areasToBalance.isEmpty();

        if (!shouldBalance) {
            // Balancing takes the slack mismatch of the Areas into account. Now that the balancing is done, we check only the interchange power flow mismatch.
            // Doing this we make sure that the Areas' interchange targets have been reached and that the slack is correctly distributed.
            Map<LfArea, Double> remainingMismatches = areas.stream().filter(area -> {
                double areaInterchangeMismatch = getInterchangeMismatch(area);
                double absMismatch = Math.abs(areaInterchangeMismatch);
                return absMismatch > this.areaInterchangePMaxMismatch / PerUnit.SB && absMismatch > ActivePowerDistribution.P_RESIDUE_EPS;
            }).collect(Collectors.toMap(area -> area, this::getInterchangeMismatch));

            if (remainingMismatches.isEmpty()) {
                LOGGER.debug("Already balanced");
            } else {
                remainingMismatches.forEach((area, mismatch) ->
                        LOGGER.error("Failed area interchange control: Area {} is not balanced, remains {} MW mismatch", area.getId(), mismatch * PerUnit.SB));
            }
            return new OuterLoopResult(this, OuterLoopStatus.STABLE);
        }
        double totalDistributedActivePower = 0.0;
        int totalIterations = 0;
        boolean movedBuses = false;
        Map<LfArea, Double> remainingMismatchMap = new HashMap<>();
        for (LfArea area : areasToBalance) {
            double areaActivePowerMismatch = getInterchangeMismatchWithSlack(area, slackBusActivePowerMismatch);
            ActivePowerDistribution.Result result = activePowerDistribution.run(area.getBuses(), areaActivePowerMismatch);
            double remainingMismatch = result.remainingMismatch();
            double distributedActivePower = areaActivePowerMismatch - remainingMismatch;
            totalDistributedActivePower += distributedActivePower;
            movedBuses |= result.movedBuses();
            totalIterations += result.iteration();
            if (Math.abs(remainingMismatch) > ActivePowerDistribution.P_RESIDUE_EPS) {
                remainingMismatchMap.put(area, remainingMismatch);
            }
        }

        ReportNode iterationReportNode = Reports.createOuterLoopIterationReporter(reportNode, context.getOuterLoopTotalIterations() + 1);
        DistributedSlackContextData contextData = (DistributedSlackContextData) context.getData();
        contextData.addDistributedActivePower(totalDistributedActivePower);
        if (!remainingMismatchMap.isEmpty()) {
            String areaMismatchesString = mismatchesToString(remainingMismatchMap);
            Reports.reportAreaMismatchDistributionFailure(iterationReportNode, areaMismatchesString, totalIterations);
            return distributionFailureResult(context, areaMismatchesString, movedBuses, contextData, totalDistributedActivePower);
        } else {
            reportAndLogSuccess(iterationReportNode, areaInterchangeMismatches, areasToBalance.size(), totalIterations);
            return new OuterLoopResult(this, OuterLoopStatus.UNSTABLE);
        }
    }

    private OuterLoopResult distributionFailureResult(AcOuterLoopContext context, String areaMismatchesString, boolean movedBuses, DistributedSlackContextData contextData, double totalDistributedActivePower) {
        String statusText = MessageFormat.format("Failed to distribute interchange active power mismatch. Remaining mismatches: [{0}]",
                areaMismatchesString);
        OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior = context.getLoadFlowContext().getParameters().getSlackDistributionFailureBehavior();
        if (OpenLoadFlowParameters.SlackDistributionFailureBehavior.DISTRIBUTE_ON_REFERENCE_GENERATOR == slackDistributionFailureBehavior) {
            LOGGER.error("Distribute on reference generator is not supported in AreaInterchangeControlOuterloop, falling back to FAIL mode");
            slackDistributionFailureBehavior = OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL;
        }

        switch (slackDistributionFailureBehavior) {
            case THROW ->
                throw new PowsyblException(statusText);

            case LEAVE_ON_SLACK_BUS -> {
                LOGGER.warn("{}", statusText);
                return new OuterLoopResult(this, movedBuses ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE);
            }
            case FAIL -> {
                LOGGER.error("{}", statusText);
                // Mismatches reported in LoadFlowResult on slack bus(es) are the mismatches of the last NR run.
                // Since we will not be re-running an NR, revert distributedActivePower reporting which would otherwise be misleading.
                // Said differently, we report that we didn't distribute anything, and this is indeed consistent with the network state.
                contextData.addDistributedActivePower(-totalDistributedActivePower);
                return new OuterLoopResult(this, OuterLoopStatus.FAILED, statusText);
            }
            default -> throw new IllegalArgumentException("Unknown slackDistributionFailureBehavior");
        }
    }

    private static String mismatchesToString(Map<LfArea, Double> remainingMismatchMap) {
        return remainingMismatchMap.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getId()))
                .map(entry -> String.format(Locale.US, "%s: %.2f MW", entry.getKey().getId(), entry.getValue() * PerUnit.SB))
                .collect(Collectors.joining(", "));
    }

    private double getInterchangeMismatch(LfArea area) {
        return area.getInterchangeTarget() - area.getInterchange();
    }

    private double getInterchangeMismatchWithSlack(LfArea area, double slackBusActivePowerMismatch) {
        return area.getInterchange() - area.getInterchangeTarget() + getSlackInjection(area, slackBusActivePowerMismatch);
    }

    private double getSlackInjection(LfArea area, double slackBusActivePowerMismatch) {
        return areaSlackDistributionParticipationFactor.getOrDefault(area, 0.0) * slackBusActivePowerMismatch;
    }

    private static void reportAndLogSuccess(ReportNode reportNode, Map<LfArea, Double> areaInterchangeMismatches, int areasCount, int iterationCount) {
        String initialMismatches = mismatchesToString(areaInterchangeMismatches);
        Reports.reportAreaMismatchDistributionSuccess(reportNode, initialMismatches, areasCount, iterationCount);
        LOGGER.info("Area Interchange mismatches [{}] distributed in {} distribution iteration(s)",
                initialMismatches, iterationCount);
    }

    private void allocateSlackDistributionParticipationFactors(LfNetwork lfNetwork) {
        List<LfBus> slackBuses = lfNetwork.getSlackBuses();
        int totalSlackBusCount = slackBuses.size();
        for (LfBus slackBus : slackBuses) {
            Optional<LfArea> areaOpt = slackBus.getArea();
            if (areaOpt.isPresent()) {
                areaSlackDistributionParticipationFactor.put(areaOpt.get(), areaSlackDistributionParticipationFactor.getOrDefault(areaOpt.get(), 0.0) + 1.0 / totalSlackBusCount);
            } else {
//                When a bus is connected to one or multiple Areas but the flow through the bus is not considered for those areas' interchange power flow,
//                its slack injection should be considered for the slack of some Areas that it is connected to.
                Set<LfBranch> connectedBranches = new HashSet<>(slackBus.getBranches());
                Set<LfArea> connectedAreas = connectedBranches.stream()
                        .flatMap(branch -> Stream.of(branch.getBus1(), branch.getBus2()))
                        .filter(Objects::nonNull)
                        .map(LfBus::getArea)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet());

                Set<LfArea> areasSharingSlack = connectedAreas.stream()
                                .filter(area -> area.getBoundaries().stream().noneMatch(boundary -> connectedBranches.contains(boundary.getBranch())))
                                .collect(Collectors.toSet());
                if (!areasSharingSlack.isEmpty()) {
                    areasSharingSlack.forEach(area -> areaSlackDistributionParticipationFactor.put(area, areaSlackDistributionParticipationFactor.getOrDefault(area, 0.0) + 1.0 / areasSharingSlack.size() / totalSlackBusCount));
                    LOGGER.warn("Slack bus {} is not in any Area and is connected to Areas: {}. Areas {} are not considering the flow through this bus for their interchange flow. The slack will be distributed between those areas.",
                            slackBus.getId(), connectedAreas.stream().map(LfArea::getId).toList(), areasSharingSlack.stream().map(LfArea::getId).toList());
                }

            }
        }

    }

}
