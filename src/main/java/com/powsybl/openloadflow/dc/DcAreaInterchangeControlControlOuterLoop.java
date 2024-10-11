package com.powsybl.openloadflow.dc;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.lf.outerloop.AbstractAreaInterchangeControlOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.AreaInterchangeControlContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DcAreaInterchangeControlControlOuterLoop extends AbstractAreaInterchangeControlOuterLoop<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcOuterLoopContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcAreaInterchangeControlControlOuterLoop.class);

    protected DcAreaInterchangeControlControlOuterLoop(ActivePowerDistribution activePowerDistribution, double slackBusPMaxMismatch, double areaInterchangePMaxMismatch) {
        super(activePowerDistribution, slackBusPMaxMismatch, areaInterchangePMaxMismatch, LOGGER);
    }

    @Override
    public String getName() {
        return "DcAreaInterchangeControl";
    }

    @Override
    public void initialize(DcOuterLoopContext context) {
        LfNetwork network = context.getNetwork();
        var contextData = new AreaInterchangeControlContextData(listBusesWithoutArea(network), allocateSlackDistributionParticipationFactors(network));
        context.setData(contextData);
    }

    @Override
    public OuterLoopResult check(DcOuterLoopContext context, ReportNode reportNode) {
        List<LfBus> buses = context.getNetwork().getBuses();
        double slackBusActivePowerMismatch = DcLoadFlowEngine.getActivePowerMismatch(buses);
        return check(context, reportNode, slackBusActivePowerMismatch);
    }

    @Override
    protected OuterLoopResult buildOuterLoopResult(Map<String, Pair<Set<LfBus>, Double>> areas, Map<String, ActivePowerDistribution.Result> resultByArea, ReportNode reportNode, DcOuterLoopContext context) {
        Map<String, Double> remainingMismatchByArea = resultByArea.entrySet().stream()
                .filter(e -> !lessThanInterchangeMaxMismatch(e.getValue().remainingMismatch()))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().remainingMismatch()));
        double totalDistributedActivePower = resultByArea.entrySet().stream().mapToDouble(e -> areas.get(e.getKey()).getRight() - e.getValue().remainingMismatch()).sum();
        boolean movedBuses = resultByArea.values().stream().map(ActivePowerDistribution.Result::movedBuses).reduce(false, (a, b) -> a || b);
        Map<String, Integer> iterationsByArea = resultByArea.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().iteration()));

        ReportNode iterationReportNode = Reports.createOuterLoopIterationReporter(reportNode, 0); // FIXME don't know what to do here
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
            return handleDistributionFailure(context, contextData, movedBuses, totalDistributedActivePower, Double.NaN, FAILED_TO_DISTRIBUTE_INTERCHANGE_ACTIVE_POWER_MISMATCH);
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

    @Override
    public OpenLoadFlowParameters.SlackDistributionFailureBehavior getSlackDistributionFailureBehavior(DcOuterLoopContext context) {
        return OpenLoadFlowParameters.SlackDistributionFailureBehavior.LEAVE_ON_SLACK_BUS;
    }
}
