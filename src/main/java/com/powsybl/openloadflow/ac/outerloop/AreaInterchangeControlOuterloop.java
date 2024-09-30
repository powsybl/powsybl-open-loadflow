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
import com.powsybl.openloadflow.lf.outerloop.AreaInterchangeControlContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.tuple.Pair;
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

    private static final String DEFAULT_NO_AREA_NAME = "NO_AREA";

    private final double areaInterchangePMaxMismatch;

    private final ActivePowerDistribution activePowerDistribution;

    public AreaInterchangeControlOuterloop(ActivePowerDistribution activePowerDistribution, double areaInterchangePMaxMismatch) {
        this.activePowerDistribution = Objects.requireNonNull(activePowerDistribution);
        this.areaInterchangePMaxMismatch = areaInterchangePMaxMismatch;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        LfNetwork network = context.getNetwork();
        var contextData = new AreaInterchangeControlContextData(listBusesWithoutArea(network), allocateSlackDistributionParticipationFactors(network));
        context.setData(contextData);
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        List<LfArea> areas = context.getNetwork().getAreas();
        double slackBusActivePowerMismatch = context.getLastSolverResult().getSlackBusActivePowerMismatch();
        AreaInterchangeControlContextData contextData = (AreaInterchangeControlContextData) context.getData();
        Map<String, Double> areaSlackDistributionParticipationFactor = contextData.getAreaSlackDistributionParticipationFactor();

        // First, we balance the areas that have a mismatch in their interchange power flow, and take the slack mismatch into account.
        Map<LfArea, Double> areaInterchangeWithSlackMismatches = areas.stream()
                .collect(Collectors.toMap(area -> area, area -> getInterchangeMismatchWithSlack(area, slackBusActivePowerMismatch, areaSlackDistributionParticipationFactor)));
        List<LfArea> areasToBalance = areaInterchangeWithSlackMismatches.entrySet().stream()
                .filter(entry -> {
                    double areaActivePowerMismatch = entry.getValue();
                    return !lessThanMaxMismatch(areaActivePowerMismatch);
                })
                .map(Map.Entry::getKey)
                .toList();

        if (areasToBalance.isEmpty()) {
            // Balancing takes the slack mismatch of the Areas into account. Now that the balancing is done, we check only the interchange power flow mismatch.
            // Doing this we make sure that the Areas' interchange targets have been reached and that the slack is correctly distributed.
            Map<String, Double> areaInterchangeMismatches = areas.stream().filter(area -> {
                double areaInterchangeMismatch = getInterchangeMismatch(area);
                return !lessThanMaxMismatch(areaInterchangeMismatch);
            }).collect(Collectors.toMap(LfArea::getId, this::getInterchangeMismatch));

            if (areaInterchangeMismatches.isEmpty() && lessThanMaxMismatch(getSlackInjection(DEFAULT_NO_AREA_NAME, slackBusActivePowerMismatch, areaSlackDistributionParticipationFactor))) {
                LOGGER.debug("Already balanced");
            } else {
                // If some mismatch remains, we distribute it on the buses without area
                double mismatchToDistributeOnBusesWithoutArea = -areaInterchangeMismatches.values().stream().mapToDouble(m -> m).sum() + getSlackInjection(DEFAULT_NO_AREA_NAME, slackBusActivePowerMismatch, areaSlackDistributionParticipationFactor);
                Map<String, Pair<Set<LfBus>, Double>> remainingMismatchMap = new HashMap<>();
                Set<LfBus> busesWithoutArea = contextData.getBusesWithoutArea();
                remainingMismatchMap.put(DEFAULT_NO_AREA_NAME, Pair.of(busesWithoutArea, mismatchToDistributeOnBusesWithoutArea));
                Map<String, ActivePowerDistribution.Result> resultByArea = distributeActivePower(remainingMismatchMap);

                // If some mismatch remains (when there is no buses without area that participate for example) and the network has areas, we distribute equally among the areas.
                double mismatchToSplitAmongAreas = resultByArea.get(DEFAULT_NO_AREA_NAME).remainingMismatch();
                if (lessThanMaxMismatch(mismatchToSplitAmongAreas) || areas.isEmpty()) {
                    return buildOuterLoopResult(remainingMismatchMap, resultByArea, reportNode, context);
                } else {
                    remainingMismatchMap = areas.stream().collect(Collectors.toMap(LfArea::getId, area -> Pair.of(area.getBuses(), mismatchToSplitAmongAreas / areas.size())));
                    resultByArea = distributeActivePower(remainingMismatchMap);
                    return buildOuterLoopResult(remainingMismatchMap, resultByArea, reportNode, context);
                }
            }
            return new OuterLoopResult(this, OuterLoopStatus.STABLE);
        }
        Map<String, Pair<Set<LfBus>, Double>> areasMap = areasToBalance.stream()
                .collect(Collectors.toMap(LfArea::getId, area -> Pair.of(area.getBuses(), getInterchangeMismatchWithSlack(area, slackBusActivePowerMismatch, areaSlackDistributionParticipationFactor))));
        Map<String, ActivePowerDistribution.Result> resultByArea = distributeActivePower(areasMap);
        return buildOuterLoopResult(areasMap, resultByArea, reportNode, context);
    }

    private OuterLoopResult buildOuterLoopResult(Map<String, Pair<Set<LfBus>, Double>> areas, Map<String, ActivePowerDistribution.Result> resultByArea, ReportNode reportNode, AcOuterLoopContext context) {
        Map<String, Double> remainingMismatchByArea = resultByArea.entrySet().stream().filter(e -> Math.abs(e.getValue().remainingMismatch()) > ActivePowerDistribution.P_RESIDUE_EPS).collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().remainingMismatch()));
        double totalDistributedActivePower = resultByArea.entrySet().stream().mapToDouble(e -> areas.get(e.getKey()).getRight() - e.getValue().remainingMismatch()).sum();
        boolean movedBuses = resultByArea.values().stream().map(ActivePowerDistribution.Result::movedBuses).reduce(false, (a, b) -> a || b);
        Map<String, Integer> iterationsByArea = resultByArea.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().iteration()));

        ReportNode iterationReportNode = Reports.createOuterLoopIterationReporter(reportNode, context.getOuterLoopTotalIterations() + 1);
        AreaInterchangeControlContextData contextData = (AreaInterchangeControlContextData) context.getData();
        contextData.addDistributedActivePower(totalDistributedActivePower);
        if (!remainingMismatchByArea.isEmpty()) {
            String areaMismatchesString = mismatchesToString(remainingMismatchByArea, iterationsByArea);
            Reports.reportAreaMismatchDistributionFailure(iterationReportNode, areaMismatchesString);
            return distributionFailureResult(context, areaMismatchesString, movedBuses, contextData, totalDistributedActivePower);
        } else {
            if (movedBuses) {
                Map<String, Double> interchangeMismatchesById = areas.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
                reportAndLogSuccess(iterationReportNode, interchangeMismatchesById, areas.size(), iterationsByArea);
                return new OuterLoopResult(this, OuterLoopStatus.UNSTABLE);
            } else {
                return new OuterLoopResult(this, OuterLoopStatus.STABLE);
            }
        }
    }

    private Map<String, ActivePowerDistribution.Result> distributeActivePower(Map<String, Pair<Set<LfBus>, Double>> areas) {
        Map<String, ActivePowerDistribution.Result> resultByArea = new HashMap<>();
        for (Map.Entry<String, Pair<Set<LfBus>, Double>> e : areas.entrySet()) {
            double areaActivePowerMismatch = e.getValue().getRight();
            LfGenerator referenceGenerator = getReferenceGenerator(e.getValue().getKey());
            ActivePowerDistribution.Result result = activePowerDistribution.run(referenceGenerator, e.getValue().getLeft(), areaActivePowerMismatch);
            resultByArea.put(e.getKey(), result);
        }
        return resultByArea;
    }

    boolean lessThanMaxMismatch(double mismatch) {
        return Math.abs(mismatch) <= this.areaInterchangePMaxMismatch / PerUnit.SB || Math.abs(mismatch) <= ActivePowerDistribution.P_RESIDUE_EPS;
    }

    private static LfGenerator getReferenceGenerator(Set<LfBus> buses) {
        return buses.stream()
                .filter(LfBus::isReference)
                .flatMap(lfBus -> lfBus.getGenerators().stream())
                .filter(LfGenerator::isReference)
                .findFirst()
                .orElse(null);
    }

    private OuterLoopResult distributionFailureResult(AcOuterLoopContext context, String areaMismatchesString, boolean movedBuses, AreaInterchangeControlContextData contextData, double totalDistributedActivePower) {
        String statusText = MessageFormat.format("Failed to distribute interchange active power mismatch. Remaining mismatches (with iterations): [{0}]",
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

    private static String mismatchesToString(Map<String, Double> mismatchByArea, Map<String, Integer> iterationsByArea) {
        return mismatchByArea.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> String.format(Locale.US, "%s: %.2f MW (%d it.)", entry.getKey(), entry.getValue() * PerUnit.SB, iterationsByArea.get(entry.getKey())))
                .collect(Collectors.joining(", "));
    }

    private double getInterchangeMismatch(LfArea area) {
        return area.getInterchange() - area.getInterchangeTarget();
    }

    private double getInterchangeMismatchWithSlack(LfArea area, double slackBusActivePowerMismatch, Map<String, Double> areaSlackDistributionParticipationFactor) {
        return area.getInterchange() - area.getInterchangeTarget() + getSlackInjection(area.getId(), slackBusActivePowerMismatch, areaSlackDistributionParticipationFactor);
    }

    private double getSlackInjection(String areaId, double slackBusActivePowerMismatch, Map<String, Double> areaSlackDistributionParticipationFactor) {
        return areaSlackDistributionParticipationFactor.getOrDefault(areaId, 0.0) * slackBusActivePowerMismatch;
    }

    private static void reportAndLogSuccess(ReportNode reportNode, Map<String, Double> areaInterchangeMismatches, int areasCount, Map<String, Integer> iterationsByArea) {
        String distributedMismatches = mismatchesToString(areaInterchangeMismatches, iterationsByArea);
        Reports.reportAreaMismatchDistributionSuccess(reportNode, distributedMismatches, areasCount);
        LOGGER.info("Distributed area interchange mismatches (with iterations) : [{}]",
                distributedMismatches);
    }

    private Set<LfBus> listBusesWithoutArea(LfNetwork network) {
        return network.getBuses().stream()
                .filter(b -> b.getArea().isEmpty())
                .filter(b -> !b.isFictitious())
                .collect(Collectors.toSet());
    }

    private Map<String, Double> allocateSlackDistributionParticipationFactors(LfNetwork lfNetwork) {
        Map<String, Double> areaSlackDistributionParticipationFactor = new HashMap<>();
        List<LfBus> slackBuses = lfNetwork.getSlackBuses();
        int totalSlackBusCount = slackBuses.size();
        for (LfBus slackBus : slackBuses) {
            Optional<LfArea> areaOpt = slackBus.getArea();
            if (areaOpt.isPresent()) {
                areaSlackDistributionParticipationFactor.put(areaOpt.get().getId(), areaSlackDistributionParticipationFactor.getOrDefault(areaOpt.get().getId(), 0.0) + 1.0 / totalSlackBusCount);
            } else {
                // When a bus is connected to one or multiple Areas but the flow through the bus is not considered for those areas' interchange power flow,
                // its slack injection should be considered for the slack of some Areas that it is connected to.
                Set<LfBranch> connectedBranches = new HashSet<>(slackBus.getBranches());
                Set<LfArea> connectedAreas = connectedBranches.stream()
                        .flatMap(branch -> Stream.of(branch.getBus1(), branch.getBus2()))
                        .filter(Objects::nonNull)
                        .map(LfBus::getArea)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet());

                // If the slack bus is on a boundary point considered for net position,
                // it will resolve naturally because deviations caused by slack are already present on tie line flows
                // no need to include in any area net position calculation
                Set<LfArea> areasSharingSlack = connectedAreas.stream()
                                .filter(area -> area.getBoundaries().stream().noneMatch(boundary -> connectedBranches.contains(boundary.getBranch())))
                                .collect(Collectors.toSet());
                if (!areasSharingSlack.isEmpty()) {
                    areasSharingSlack.forEach(area -> areaSlackDistributionParticipationFactor.put(area.getId(), areaSlackDistributionParticipationFactor.getOrDefault(area.getId(), 0.0) + 1.0 / areasSharingSlack.size() / totalSlackBusCount));
                    LOGGER.warn("Slack bus {} is not in any Area and is connected to Areas: {}. Areas {} are not considering the flow through this bus for their interchange flow. The slack will be distributed between those areas.",
                            slackBus.getId(), connectedAreas.stream().map(LfArea::getId).toList(), areasSharingSlack.stream().map(LfArea::getId).toList());
                } else {
                    areaSlackDistributionParticipationFactor.put(DEFAULT_NO_AREA_NAME, areaSlackDistributionParticipationFactor.getOrDefault(DEFAULT_NO_AREA_NAME, 0.0) + 1.0 / totalSlackBusCount);
                }

            }
        }
        return areaSlackDistributionParticipationFactor;
    }

}
