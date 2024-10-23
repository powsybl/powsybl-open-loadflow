package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.lf.outerloop.AbstractAreaInterchangeControlOuterLoop;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DcAreaInterchangeControlControlOuterLoop extends AbstractAreaInterchangeControlOuterLoop<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcOuterLoopContext> implements DcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcAreaInterchangeControlControlOuterLoop.class);

    protected DcAreaInterchangeControlControlOuterLoop(ActivePowerDistribution activePowerDistribution, double slackBusPMaxMismatch, double areaInterchangePMaxMismatch) {
        super(activePowerDistribution, null, slackBusPMaxMismatch, areaInterchangePMaxMismatch, LOGGER);
    }

    @Override
    public String getName() {
        return "DcAreaInterchangeControl";
    }

    @Override
    public double getSlackBusActivePowerMismatch(DcOuterLoopContext context) {
        List<LfBus> buses = context.getNetwork().getBuses();
        return DcLoadFlowEngine.getActivePowerMismatch(buses);
    }
}
