/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.tap.GroupVoltageControlManager;
import com.powsybl.openloadflow.ac.outerloop.tap.TransformerRatioManager;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.VoltageControl;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class TransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    public static final String NAME = "TransformerVoltageControl";

    private enum Step {
        NOT_STARTED,
        TUNING,
        DISCRETIZED
    }

    private final double maxControlledNominalVoltageOverride;

    private static final class ContextData {

        private TransformerRatioManager transformerRatioManager;

        private GroupVoltageControlManager groupVoltageControlManager;

        private Step step = Step.NOT_STARTED;

    }

    private final boolean stable;

    public TransformerVoltageControlOuterLoop(boolean stable, double maxControlledNominalVoltageOverride) {
        this.stable = stable;
        this.maxControlledNominalVoltageOverride = maxControlledNominalVoltageOverride;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        ContextData contextData = new ContextData();
        context.setData(contextData);

        for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            controllerBranch.setVoltageControlEnabled(false);
        }

        // All transformer voltage control are disabled for the first equation system resolution.
        contextData.groupVoltageControlManager = new GroupVoltageControlManager(context.getNetwork(), maxControlledNominalVoltageOverride);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {

        var contextData = (ContextData) context.getData();

        return switch (contextData.step) {
            case NOT_STARTED -> initStep(context.getNetwork(), contextData);
            case TUNING -> tuningStep(context.getNetwork(), contextData);
            case DISCRETIZED -> new OuterLoopResult(this, OuterLoopStatus.STABLE);
        };

    }

    /**
     * At first outer loop iteration, the voltage control of generators that controlled at nominal voltage of
     * the set controlledNominalVoltages are disabled.
     * The transformer voltage controls are enabled ant their continuous ratio is computed$
     * @return a stable status if all taps are already tuned for tension control, unstable otherwise
     */
    private OuterLoopResult initStep(LfNetwork network, ContextData contextData) {
        boolean needRun = false;
        for (LfBranch branch : network.<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
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

        contextData.transformerRatioManager = new TransformerRatioManager(network, stable);

        if (!needRun) {
            contextData.step = Step.DISCRETIZED;
            return new OuterLoopResult(this, OuterLoopStatus.STABLE);
        }
        contextData.groupVoltageControlManager.stopTensionControlBelowLimit(network);

        // In stable mode, Group maintaining tension but in PQ mode are ignored
        network.fixTransformerVoltageControls();

        contextData.step = Step.TUNING;
        return new OuterLoopResult(this, OuterLoopStatus.UNSTABLE);
    }

    /**
     * During tuning step, transformers with ratio outside of range are discretized. Iterate until all transformers
     * with continuous ratio are within their operating range , discretize then then switch to DISCTERIZED state
     */
    private OuterLoopResult tuningStep(LfNetwork network, ContextData contextData) {
        boolean outOfBoundTap = false;

        for (LfBranch controllerBranch : network.<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            if (contextData.transformerRatioManager.freezeIfGroupAtBounds(controllerBranch)) {
                outOfBoundTap = true;
            }
        }

        if (!outOfBoundTap) {
            // No out of bound tap -  descretize
            updateContinuousRatio(network, contextData);

            roundVoltageRatios(network);
            contextData.groupVoltageControlManager.restartGroupTensionControl();

            contextData.step = Step.DISCRETIZED;
        }
        // In any case the loop must run again
        return new OuterLoopResult(this, OuterLoopStatus.UNSTABLE);
    }

    private void updateContinuousRatio(LfNetwork network, ContextData contextData) {
        for (LfBus bus : network.getControlledBuses(VoltageControl.Type.TRANSFORMER)) {
            bus.getTransformerVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .ifPresent(voltageControl -> {
                        voltageControl.getMergedControllerElements().stream()
                                .filter(b -> !b.isDisabled())
                                .filter(LfBranch::isVoltageControlEnabled)
                                .forEach(b -> contextData.transformerRatioManager.updateContinuousRatio(b));
                    });
        }
    }

}
