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
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.lf.outerloop.AbstractAreaInterchangeControlOuterLoop;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public class AcAreaInterchangeControlOuterLoop extends AbstractAreaInterchangeControlOuterLoop<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext, AcOuterLoopContext> implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcAreaInterchangeControlOuterLoop.class);

    public static final String NAME = "AcAreaInterchangeControl";

    private final AcOuterLoop noAreaOuterLoop;

    public AcAreaInterchangeControlOuterLoop(ActivePowerDistribution activePowerDistribution, double slackBusPMaxMismatch, double areaInterchangePMaxMismatch) {
        super(activePowerDistribution, slackBusPMaxMismatch, areaInterchangePMaxMismatch, LOGGER);
        this.noAreaOuterLoop = new DistributedSlackOuterLoop(activePowerDistribution, slackBusPMaxMismatch);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        LfNetwork network = context.getNetwork();
        if (!network.hasArea()) {
            noAreaOuterLoop.initialize(context);
            return;
        }
        var contextData = new AreaInterchangeControlContextData(listBusesWithoutArea(network), allocateSlackDistributionParticipationFactors(network));
        context.setData(contextData);
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        LfNetwork network = context.getNetwork();
        if (!network.hasArea()) {
            return noAreaOuterLoop.check(context, reportNode);
        }
        double slackBusActivePowerMismatch = context.getLastSolverResult().getSlackBusActivePowerMismatch();
        return check(context, reportNode, slackBusActivePowerMismatch);
    }

    @Override
    protected OuterLoopResult buildOuterLoopResult(Map<String, Pair<Set<LfBus>, Double>> areas, Map<String, ActivePowerDistribution.Result> resultByArea, ReportNode reportNode, AcOuterLoopContext context) {
        Map<String, Double> remainingMismatchByArea = resultByArea.entrySet().stream()
                .filter(e -> !lessThanInterchangeMaxMismatch(e.getValue().remainingMismatch()))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().remainingMismatch()));
        double totalDistributedActivePower = resultByArea.entrySet().stream().mapToDouble(e -> areas.get(e.getKey()).getRight() - e.getValue().remainingMismatch()).sum();
        boolean movedBuses = resultByArea.values().stream().map(ActivePowerDistribution.Result::movedBuses).reduce(false, (a, b) -> a || b);
        Map<String, Integer> iterationsByArea = resultByArea.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().iteration()));

        ReportNode iterationReportNode = Reports.createOuterLoopIterationReporter(reportNode, context.getOuterLoopTotalIterations() + 1);
        AreaInterchangeControlContextData contextData = (AreaInterchangeControlContextData) context.getData();
        contextData.addDistributedActivePower(totalDistributedActivePower);
        if (!remainingMismatchByArea.isEmpty()) {
            LOGGER.error(FAILED_TO_DISTRIBUTE_INTERCHANGE_ACTIVE_POWER_MISMATCH);
            ReportNode failureReportNode = Reports.reportAreaInterchangeControlDistributionFailure(iterationReportNode);
            remainingMismatchByArea.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    LOGGER.error("Remaining mismatch for Area {}: {} MW", entry.getKey(), entry.getValue() * PerUnit.SB);
                    Reports.reportAreaInterchangeControlAreaMismatch(failureReportNode, entry.getKey(), entry.getValue() * PerUnit.SB);
                }
            );
            return distributionFailureResult(context, movedBuses, contextData, totalDistributedActivePower);
        } else {
            if (movedBuses) {
                areas.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                        LOGGER.info("Area {} interchange mismatch ({} MW) distributed in {} distribution iteration(s)", entry.getKey(), entry.getValue().getValue() * PerUnit.SB, iterationsByArea.get(entry.getKey()));
                        Reports.reportAreaInterchangeControlAreaDistributionSuccess(iterationReportNode, entry.getKey(), entry.getValue().getValue() * PerUnit.SB, iterationsByArea.get(entry.getKey()));
                    }
                );
                return new OuterLoopResult(this, OuterLoopStatus.UNSTABLE);
            } else {
                return new OuterLoopResult(this, OuterLoopStatus.STABLE);
            }
        }
    }

    private OuterLoopResult distributionFailureResult(AcOuterLoopContext context, boolean movedBuses, AreaInterchangeControlContextData contextData, double totalDistributedActivePower) {
        OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior = context.getLoadFlowContext().getParameters().getSlackDistributionFailureBehavior();
        if (OpenLoadFlowParameters.SlackDistributionFailureBehavior.DISTRIBUTE_ON_REFERENCE_GENERATOR == slackDistributionFailureBehavior) {
            LOGGER.error("Distribute on reference generator is not supported in AcAreaInterchangeControlOuterLoop, falling back to FAIL mode");
            slackDistributionFailureBehavior = OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL;
        }

        switch (slackDistributionFailureBehavior) {
            case THROW ->
                throw new PowsyblException(FAILED_TO_DISTRIBUTE_INTERCHANGE_ACTIVE_POWER_MISMATCH);

            case LEAVE_ON_SLACK_BUS -> {
                return new OuterLoopResult(this, movedBuses ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE);
            }
            case FAIL -> {
                // Mismatches reported in LoadFlowResult on slack bus(es) are the mismatches of the last NR run.
                // Since we will not be re-running an NR, revert distributedActivePower reporting which would otherwise be misleading.
                // Said differently, we report that we didn't distribute anything, and this is indeed consistent with the network state.
                contextData.addDistributedActivePower(-totalDistributedActivePower);
                return new OuterLoopResult(this, OuterLoopStatus.FAILED, FAILED_TO_DISTRIBUTE_INTERCHANGE_ACTIVE_POWER_MISMATCH);
            }
            default -> throw new IllegalArgumentException("Unknown slackDistributionFailureBehavior");
        }
    }

}
