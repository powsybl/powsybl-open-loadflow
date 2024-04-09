package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MonitoringTapOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringTapOuterLoop.class);

    class ContextData {
        Map<String, Integer> outOfRangeMap = new HashMap<>();
    }

    @Override
    public String getName() {
        return "Monitoring Tap Outer Loop";
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        context.setData(new ContextData());
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, ReportNode reportNode) {
        ContextData contextData = (ContextData) context.getData();

        OuterLoopStatus status = OuterLoopStatus.STABLE;

        // if a controller is out of range more than 3 times it is blocked
        for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            if (controllerBranch.isVoltageControlEnabled()) {
                PiModel piModel = controllerBranch.getPiModel();
                double r1 = piModel.getR1();
                if (r1 < piModel.getMinR1() || r1 > piModel.getMaxR1()) {
                    int outOfRangeCount = contextData.outOfRangeMap.compute(controllerBranch.getId(), (k, v) -> v == null ? 1 : v + 1);
                    if (outOfRangeCount > 2) {
                        LOGGER.info("Transformer " + controllerBranch.getId() + " tap frozen");
                        piModel.roundR1ToClosestTap();
                        controllerBranch.setVoltageControlEnabled(false);
                        status = OuterLoopStatus.UNSTABLE;
                    }
                }
            }
        }

        return status;
    }
}
