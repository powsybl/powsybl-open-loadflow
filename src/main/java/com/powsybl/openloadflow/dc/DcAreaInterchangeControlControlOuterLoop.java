package com.powsybl.openloadflow.dc;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.lf.outerloop.AbstractAreaInterchangeControlOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.AreaInterchangeControlContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DcAreaInterchangeControlControlOuterLoop extends AbstractAreaInterchangeControlOuterLoop<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcOuterLoopContext> implements DcOuterLoop {

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
    public OpenLoadFlowParameters.SlackDistributionFailureBehavior getSlackDistributionFailureBehavior(DcOuterLoopContext context) {
        return OpenLoadFlowParameters.SlackDistributionFailureBehavior.LEAVE_ON_SLACK_BUS;
    }
}
