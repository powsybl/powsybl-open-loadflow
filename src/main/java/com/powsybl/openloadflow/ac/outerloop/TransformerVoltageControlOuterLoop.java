/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

        private double maxControlledNominalVoltage = Double.MIN_VALUE;

        private final List<LfBus> busesWithVoltageControlDisabled = new ArrayList<>();

        private Step step = Step.NOT_STARTED;

        private double getMaxControlledNominalVoltage() {
            return maxControlledNominalVoltage;
        }

        private void setMaxControlledNominalVoltage(double maxControlledNominalVoltage) {
            this.maxControlledNominalVoltage = maxControlledNominalVoltage;
        }

        private List<LfBus> getBusesWithVoltageControlDisabled() {
            return busesWithVoltageControlDisabled;
        }
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        context.setData(new ContextData());

        for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            controllerBranch.setVoltageControlEnabled(false);
        }

        // All transformer voltage control are disabled for the first equation system resolution.
        double[] maxControlledNominalVoltage = new double[1];
        maxControlledNominalVoltage[0] = Double.MIN_VALUE;
        for (LfBus bus : context.getNetwork().getBuses()) {
            if (!bus.isDisabled() && bus.isTransformerVoltageControlled()) {
                maxControlledNominalVoltage[0] = Math.max(maxControlledNominalVoltage[0], bus.getNominalV());
            }
        }
        ((ContextData) context.getData()).setMaxControlledNominalVoltage(maxControlledNominalVoltage[0]);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, ReportNode reportNode) {

        var contextData = (ContextData) context.getData();

        double maxControlledNominalVoltage = contextData.getMaxControlledNominalVoltage();

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
                if (!needRun) {
                    contextData.step = Step.DISCRETIZED;
                    return OuterLoopStatus.STABLE;
                }
                for (LfBus bus : context.getNetwork().getControlledBuses(VoltageControl.Type.GENERATOR)) {
                    if (bus.getNominalV() <= maxControlledNominalVoltage) {
                        var voltageControl = bus.getGeneratorVoltageControl().orElseThrow();
                        for (LfBus controllerBus : voltageControl.getMergedControllerElements()) {
                            if (controllerBus.isGeneratorVoltageControlEnabled()) {
                                controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                                controllerBus.setGeneratorVoltageControlEnabled(false);
                                contextData.getBusesWithVoltageControlDisabled().add(controllerBus);
                            }
                        }
                    }
                }
                context.getNetwork().fixTransformerVoltageControls(false);
                contextData.step = Step.TUNING;
                return OuterLoopStatus.UNSTABLE;
            }
            case TUNING: {
                boolean outOfBoundTap = false;
                for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
                    // round the rho shift to the closest tap
                    PiModel piModel = controllerBranch.getPiModel();
                    double r1 = piModel.getR1();
                    if (r1 < piModel.getMinR1() || r1 > piModel.getMaxR1()) {
                        LOGGER.info("Transformer " + controllerBranch.getId() + " tap frozen");
                        piModel.roundR1ToClosestTap();
                        controllerBranch.setVoltageControlEnabled(false);
                        outOfBoundTap = true;
                    }
                }
                if (!outOfBoundTap) {
                    // No out of bound tap -  descretize
                    roundVoltageRatios(context);
                    for (LfBus controllerBus : contextData.getBusesWithVoltageControlDisabled()) {
                        controllerBus.setGenerationTargetQ(0);
                        controllerBus.setGeneratorVoltageControlEnabled(true);
                    }
                    contextData.step = Step.DISCRETIZED;
                }
                return OuterLoopStatus.UNSTABLE;
            }
            case DISCRETIZED:
                return OuterLoopStatus.STABLE;
        }

        // Should never happen
        return null;

    }
}
