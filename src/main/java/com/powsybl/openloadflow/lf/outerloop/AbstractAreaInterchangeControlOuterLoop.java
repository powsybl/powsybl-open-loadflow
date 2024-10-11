package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.LfArea;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractAreaInterchangeControlOuterLoop<A extends Enum<A> & Quantity, B extends Enum<B> & Quantity, C extends AbstractLoadFlowParameters<?>, D extends LoadFlowContext<A, B, C>, E extends OuterLoopContext<A, B, C, D>> implements OuterLoop<A, B, C, D, E> {

    protected static final String FAILED_TO_DISTRIBUTE_INTERCHANGE_ACTIVE_POWER_MISMATCH = "Failed to distribute interchange active power mismatch";
    private final Logger logger;

    protected static final String DEFAULT_NO_AREA_NAME = "NO_AREA";

    protected final double slackBusPMaxMismatch;
    protected final double areaInterchangePMaxMismatch;
    protected final ActivePowerDistribution activePowerDistribution;

    protected AbstractAreaInterchangeControlOuterLoop(ActivePowerDistribution activePowerDistribution, double slackBusPMaxMismatch, double areaInterchangePMaxMismatch, Logger logger) {
        this.activePowerDistribution = Objects.requireNonNull(activePowerDistribution);
        this.slackBusPMaxMismatch = slackBusPMaxMismatch;
        this.areaInterchangePMaxMismatch = areaInterchangePMaxMismatch;
        this.logger = logger;
    }

    protected OuterLoopResult check(E context, ReportNode reportNode, double slackBusActivePowerMismatch) {
        LfNetwork network = context.getNetwork();
        AreaInterchangeControlContextData contextData = (AreaInterchangeControlContextData) context.getData();
        Map<String, Double> areaSlackDistributionParticipationFactor = contextData.getAreaSlackDistributionParticipationFactor();

        // First, we balance the areas that have a mismatch in their interchange power flow, and take the slack mismatch into account.
        Map<LfArea, Double> areaInterchangeWithSlackMismatches = network.getAreaStream()
                .collect(Collectors.toMap(area -> area, area -> getInterchangeMismatchWithSlack(area, slackBusActivePowerMismatch, areaSlackDistributionParticipationFactor)));
        List<LfArea> areasToBalance = areaInterchangeWithSlackMismatches.entrySet().stream()
                .filter(entry -> {
                    double areaActivePowerMismatch = entry.getValue();
                    return !lessThanInterchangeMaxMismatch(areaActivePowerMismatch);
                })
                .map(Map.Entry::getKey)
                .toList();

        if (areasToBalance.isEmpty()) {
            // Balancing takes the slack mismatch of the Areas into account. Now that the balancing is done, we check only the interchange power flow mismatch.
            // Doing this we make sure that the Areas' interchange targets have been reached and that the slack is correctly distributed.
            Map<String, Double> areaInterchangeMismatches = network.getAreaStream()
                    .filter(area -> {
                        double areaInterchangeMismatch = getInterchangeMismatch(area);
                        return !lessThanInterchangeMaxMismatch(areaInterchangeMismatch);
                    }).collect(Collectors.toMap(LfArea::getId, this::getInterchangeMismatch));

            if (areaInterchangeMismatches.isEmpty() && lessThanSlackBusMaxMismatch(slackBusActivePowerMismatch)) {
                logger.debug("Already balanced");
            } else {
                // If some mismatch remains, we distribute the slack bus active power on the buses without area
                // Corner case: if there is less slack than the slackBusPMaxMismatch, but there are areas with mismatch.
                // We consider that to still distribute the remaining slack will continue to reduce difference between interchange mismatch with slack and interchange mismatch.
                // Which should at the end of the day end up by not having interchange mismatches.
                Set<LfBus> busesWithoutArea = contextData.getBusesWithoutArea();
                Map<String, Pair<Set<LfBus>, Double>> remainingMismatchMap = new HashMap<>();
                remainingMismatchMap.put(DEFAULT_NO_AREA_NAME, Pair.of(busesWithoutArea, slackBusActivePowerMismatch));
                Map<String, ActivePowerDistribution.Result> resultNoArea = distributeActivePower(remainingMismatchMap);

                // If some mismatch remains (when there is no buses without area that participate for example), we distribute equally among the areas.
                double mismatchToSplitAmongAreas = resultNoArea.get(DEFAULT_NO_AREA_NAME).remainingMismatch();
                if (lessThanSlackBusMaxMismatch(mismatchToSplitAmongAreas)) {
                    return buildOuterLoopResult(remainingMismatchMap, resultNoArea, reportNode, context);
                } else {
                    int areasCount = (int) network.getAreaStream().count();
                    remainingMismatchMap = network.getAreaStream().collect(Collectors.toMap(LfArea::getId, area -> Pair.of(area.getBuses(), mismatchToSplitAmongAreas / areasCount)));
                    Map<String, ActivePowerDistribution.Result> resultByArea = distributeActivePower(remainingMismatchMap);
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

    protected Map<String, ActivePowerDistribution.Result> distributeActivePower(Map<String, Pair<Set<LfBus>, Double>> areas) {
        Map<String, ActivePowerDistribution.Result> resultByArea = new HashMap<>();
        for (Map.Entry<String, Pair<Set<LfBus>, Double>> e : areas.entrySet()) {
            double areaActivePowerMismatch = e.getValue().getRight();
            ActivePowerDistribution.Result result = activePowerDistribution.run(null, e.getValue().getLeft(), areaActivePowerMismatch);
            resultByArea.put(e.getKey(), result);
        }
        return resultByArea;
    }

    protected boolean lessThanInterchangeMaxMismatch(double mismatch) {
        return Math.abs(mismatch) <= this.areaInterchangePMaxMismatch / PerUnit.SB || Math.abs(mismatch) <= ActivePowerDistribution.P_RESIDUE_EPS;
    }

    boolean lessThanSlackBusMaxMismatch(double mismatch) {
        return Math.abs(mismatch) <= this.slackBusPMaxMismatch / PerUnit.SB || Math.abs(mismatch) <= ActivePowerDistribution.P_RESIDUE_EPS;
    }

    protected double getInterchangeMismatch(LfArea area) {
        return area.getInterchange() - area.getInterchangeTarget();
    }

    protected double getInterchangeMismatchWithSlack(LfArea area, double slackBusActivePowerMismatch, Map<String, Double> areaSlackDistributionParticipationFactor) {
        return area.getInterchange() - area.getInterchangeTarget() + getSlackInjection(area.getId(), slackBusActivePowerMismatch, areaSlackDistributionParticipationFactor);
    }

    protected double getSlackInjection(String areaId, double slackBusActivePowerMismatch, Map<String, Double> areaSlackDistributionParticipationFactor) {
        return areaSlackDistributionParticipationFactor.getOrDefault(areaId, 0.0) * slackBusActivePowerMismatch;
    }

    protected OuterLoopResult buildOuterLoopResult(Map<String, Pair<Set<LfBus>, Double>> areas, Map<String, ActivePowerDistribution.Result> resultByArea, ReportNode reportNode, E context) {
        return null;
    }

    protected Set<LfBus> listBusesWithoutArea(LfNetwork network) {
        return network.getBuses().stream()
                .filter(b -> b.getArea().isEmpty())
                .filter(b -> !b.isFictitious())
                .collect(Collectors.toSet());
    }

    protected Map<String, Double> allocateSlackDistributionParticipationFactors(LfNetwork lfNetwork) {
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
                    logger.warn("Slack bus {} is not in any Area and is connected to Areas: {}. Areas {} are not considering the flow through this bus for their interchange flow. The slack will be distributed between those areas.",
                            slackBus.getId(), connectedAreas.stream().map(LfArea::getId).toList(), areasSharingSlack.stream().map(LfArea::getId).toList());
                } else {
                    areaSlackDistributionParticipationFactor.put(DEFAULT_NO_AREA_NAME, areaSlackDistributionParticipationFactor.getOrDefault(DEFAULT_NO_AREA_NAME, 0.0) + 1.0 / totalSlackBusCount);
                }

            }
        }
        return areaSlackDistributionParticipationFactor;
    }

}
