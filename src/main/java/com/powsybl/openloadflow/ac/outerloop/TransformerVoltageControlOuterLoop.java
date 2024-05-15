/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.tap.TransformerRatioManager;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfVscConverterStation;
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

        private TransformerRatioManager transformerRatioManager;

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

    private final boolean stable;
    private final int htLimit;

    public TransformerVoltageControlOuterLoop(boolean stable, int htLImit) {
        this.stable = stable;
        this.htLimit = htLImit;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        context.setData(new ContextData());

        for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            controllerBranch.setVoltageControlEnabled(false);
        }

        // All transformer voltage control are disabled for the first equation system resolution.
        double maxControlledNominalVoltage = Double.MIN_VALUE;
        if (htLimit < 0) {
            // Compute the default value
            for (LfBus bus : context.getNetwork().getBuses()) {
                if (!bus.isDisabled() && bus.isTransformerVoltageControlled()) {
                    maxControlledNominalVoltage = Math.max(maxControlledNominalVoltage, bus.getNominalV());
                }
            }
        }
        ((ContextData) context.getData()).setMaxControlledNominalVoltage(htLimit < 0 ? maxControlledNominalVoltage : htLimit);
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

                contextData.transformerRatioManager = new TransformerRatioManager(context, stable);

                if (!needRun) {
                    contextData.step = Step.DISCRETIZED;
                    return OuterLoopStatus.STABLE;
                }
                for (LfBus bus : context.getNetwork().getControlledBuses(VoltageControl.Type.GENERATOR)) {
                    if (bus.getNominalV() < maxControlledNominalVoltage) {
                        var voltageControl = bus.getGeneratorVoltageControl().orElseThrow();
                        for (LfBus controllerBus : voltageControl.getMergedControllerElements()) {
                            if (controllerBus.isGeneratorVoltageControlEnabled()) {
                                if (!isBusBehindTHTTransfo(controllerBus, maxControlledNominalVoltage)) {
                                    controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                                    controllerBus.setGeneratorVoltageControlEnabled(false);
                                    contextData.getBusesWithVoltageControlDisabled().add(controllerBus);
                                }
                            }
                        }
                    }
                }

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
                    for (LfBus controllerBus : contextData.getBusesWithVoltageControlDisabled()) {
                        controllerBus.setGenerationTargetQ(0);
                        controllerBus.setGeneratorVoltageControlEnabled(true);
                    }
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

    private boolean isBusBehindTHTTransfo(LfBus bus, double thtLimit) {
        if (bus.getBranches().size() != 1) {
            return false;
        }
        LfBranch b = bus.getBranches().get(0);
        if (!b.isConnectedAtBothSides()) {
            return false;
        }
        // Always keep VSC stations
        if (bus.getGenerators().stream().filter(g -> g instanceof LfVscConverterStation).findAny().isPresent()) {
            return true;
        }

        return Math.max(b.getBus1().getNominalV(), b.getBus2().getNominalV()) >= thtLimit;
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
