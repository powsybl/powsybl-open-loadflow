/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.tap.GroupVoltageControlManager;
import com.powsybl.openloadflow.ac.outerloop.tap.TransformerRatioManager;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class TransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    public static final String NAME = "TransformerVoltageControl";

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerVoltageControlOuterLoop.class);

    private enum Step {
        NOT_STARTED,
        TUNING,
        DISCRETIZED
    }

    private static final class ContextData {

        private TransformerRatioManager transformerRatioManager;

        private GroupVoltageControlManager groupVoltageControlManager;

        private Step step = Step.NOT_STARTED;

    }

    private final boolean stable;
    private final int thtLimit;

    public TransformerVoltageControlOuterLoop(boolean stable, int thtLimit) {
        this.stable = stable;
        this.thtLimit = thtLimit;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        ContextData contextData = new ContextData();
        context.setData(contextData);

        for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            controllerBranch.setVoltageControlEnabled(false);
        }

        // All transformer voltage control are disabled for the first equation system resolution.
        contextData.groupVoltageControlManager = new GroupVoltageControlManager(context.getNetwork(), thtLimit);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, ReportNode reportNode) {

        var contextData = (ContextData) context.getData();

        switch (contextData.step) {
            // At first outer loop iteration, the voltage control of generators that controlled at nominal voltage of
            // the set controlledNominalVoltages are disabled.
            // The transformer voltage controls are enabled.
            case NOT_STARTED : {
                boolean needRun = false;
                for (LfBranch branch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
                    if (branch.getVoltageControl().isPresent()) {
                        TransformerVoltageControl voltageControl = branch.getVoltageControl().orElseThrow();
                        double targetV = voltageControl.getTargetValue();
                        double v = voltageControl.getControlledBus().getV();
                        double diffV = targetV - v;
                        double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
                        if (Math.abs(diffV) > halfTargetDeadband && branch.isConnectedAtBothSides()) {
                            branch.setVoltageControlEnabled(true);
                            needRun = true;
                        }
                    }
                }

                contextData.transformerRatioManager = new TransformerRatioManager(context, stable);

                if (!needRun) {
                    contextData.step = Step.DISCRETIZED;
                    return OuterLoopStatus.STABLE;
                }
                contextData.groupVoltageControlManager.stopHTGroupTensionControl(context.getNetwork());

                // In stable mode, Group maintaining tension but in PQ mode are ignored
                context.getNetwork().fixTransformerVoltageControls(!stable);

                contextData.step = Step.TUNING;
                return OuterLoopStatus.UNSTABLE;
            }
            case TUNING: {
                boolean outOfBoundTap = false;

                for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
                    if (contextData.transformerRatioManager.freezeIfGroupAtBounds(controllerBranch)) {
                        outOfBoundTap = true;
                    }
                }

                if (!outOfBoundTap) {
                    // No out of bound tap -  descretize
                    updateContinousRatio(context);

                    roundVoltageRatios(context);
                    contextData.groupVoltageControlManager.restartHTGroupTensionControl();

                    contextData.step = Step.DISCRETIZED;
                }
                // In any case the loop must run again
                return OuterLoopStatus.UNSTABLE;
            }
            case DISCRETIZED:
                return OuterLoopStatus.STABLE;
        }

        // Should never happen
        return null;

    }

    private void updateContinousRatio(AcOuterLoopContext context) {
        ContextData contextData = (ContextData) context.getData();
        for (LfBus bus : context.getNetwork().getControlledBuses(VoltageControl.Type.TRANSFORMER)) {
            bus.getTransformerVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .ifPresent(voltageControl -> {
                        System.out.println(bus.getId());
                        voltageControl.getMergedControllerElements().stream()
                                .filter(b -> !b.isDisabled())
                                .filter(b -> b.isVoltageControlEnabled())
                                .forEach(b -> contextData.transformerRatioManager.updateContinousRatio(b));
                    });
        }
    }

}
