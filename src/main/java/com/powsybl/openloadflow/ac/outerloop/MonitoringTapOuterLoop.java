package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.tap.TransformerRatioManager;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MonitoringTapOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringTapOuterLoop.class);

    private final TransformerRatioManager transformerRatioManager;

    class ContextData {
        Map<String, Integer> outOfRangeMap = new HashMap<>();
        List<String> frozenWithMoreChances = new ArrayList<>();
    }

    public MonitoringTapOuterLoop(TransformerRatioManager transformerRatioManager) {
        this.transformerRatioManager = transformerRatioManager;
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

        if (!contextData.frozenWithMoreChances.isEmpty()) {
            contextData.frozenWithMoreChances.forEach(id -> context.getNetwork().getBranchById(id).setVoltageControlEnabled(true));
            contextData.frozenWithMoreChances.clear();
            status = OuterLoopStatus.UNSTABLE;
        }

        // if a controller is out of range more than 3 times it is blocked
        for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            if (transformerRatioManager.freezeIfGroupAtBounds(controllerBranch)) {
                status = OuterLoopStatus.UNSTABLE;
                int outOfRangeCount = contextData.outOfRangeMap.compute(controllerBranch.getId(), (k, v) -> v == null ? 0 : v + 1);
                LOGGER.info("Transformer " + controllerBranch.getId() + " tap frozen");
                if (outOfRangeCount < 3) {
                    contextData.frozenWithMoreChances.add(controllerBranch.getId());
                }
            }
        }

        return status;
    }
}
