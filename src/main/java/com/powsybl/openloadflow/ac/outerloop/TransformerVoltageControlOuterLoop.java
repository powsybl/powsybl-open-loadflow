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
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.VoltageControl;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class TransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    public static final String NAME = "TransformerVoltageControl";

    private Double maxControlledNominalVoltageOverride;

    private static final class ContextData {

        private double maxControlledNominalVoltage = Double.MIN_VALUE;

        private final List<LfBus> busesWithVoltageControlDisabled = new ArrayList<>();

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

    public TransformerVoltageControlOuterLoop(Double generatorVoltageControlMinNominalVoltage) {
        if (generatorVoltageControlMinNominalVoltage != -1.0) {
            this.maxControlledNominalVoltageOverride = generatorVoltageControlMinNominalVoltage;
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
            if (!bus.isDisabled() && bus.isTransformerVoltageControlled() && keepTransformerVoltageControls(bus.getTransformerVoltageControl())) {
                maxControlledNominalVoltage[0] = Math.max(maxControlledNominalVoltage[0], bus.getNominalV());
            }
        }
        ((ContextData) context.getData())
                .setMaxControlledNominalVoltage(maxControlledNominalVoltageOverride != null ? maxControlledNominalVoltageOverride : maxControlledNominalVoltage[0]);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        final MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        var contextData = (ContextData) context.getData();

        // At first outer loop iteration, the voltage control of generators that controlled at nominal voltage of
        // the set controlledNominalVoltages are disabled.
        // The transformer voltage controls are enabled.
        if (context.getIteration() == 0) {
            firstOuterLoop(context, contextData, status);
        }

        // At second outer loop iteration, the transformers are rounded. The generator voltage controls that were
        // disabled previously are enabled.
        if (context.getIteration() == 1) {
            secondOuterLoop(context, status, contextData);
        }

        return new OuterLoopResult(this, status.getValue());
    }

    private static void firstOuterLoop(AcOuterLoopContext context, ContextData contextData, MutableObject<OuterLoopStatus> status) {
        double maxControlledNominalVoltage = contextData.getMaxControlledNominalVoltage();
        for (LfBus bus : context.getNetwork().getControlledBuses(VoltageControl.Type.GENERATOR)) {
            if (bus.getNominalV() <= maxControlledNominalVoltage) {
                var voltageControl = bus.getGeneratorVoltageControl().orElseThrow();
                voltageControl.getMergedControllerElements().forEach(controllerBus -> {
                    if (controllerBus.isGeneratorVoltageControlEnabled()) {
                        controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                        controllerBus.setGeneratorVoltageControlEnabled(false);
                        contextData.getBusesWithVoltageControlDisabled().add(controllerBus);
                    }
                });
                status.setValue(OuterLoopStatus.UNSTABLE);
            }
        }
        for (LfBranch branch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            branch.getVoltageControl().ifPresent(voltageControl -> {
                double targetV = voltageControl.getTargetValue();
                double v = voltageControl.getControlledBus().getV();
                double diffV = targetV - v;
                double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
                if (Math.abs(diffV) > halfTargetDeadband && branch.isConnectedAtBothSides()) {
                    branch.setVoltageControlEnabled(true);
                    status.setValue(OuterLoopStatus.UNSTABLE);
                }
            });
        }
        context.getNetwork().fixTransformerVoltageControls();
    }

    private void secondOuterLoop(AcOuterLoopContext context, MutableObject<OuterLoopStatus> status, ContextData contextData) {
        status.setValue(roundVoltageRatios(context));
        for (LfBus controllerBus : contextData.getBusesWithVoltageControlDisabled()) {
            controllerBus.setGenerationTargetQ(0);
            controllerBus.setGeneratorVoltageControlEnabled(true);
            status.setValue(OuterLoopStatus.UNSTABLE);
        }
    }

    private boolean keepTransformerVoltageControls(Optional<TransformerVoltageControl> transformerVoltageControl) {
        // are removed from this automatic algorithm the transformer voltage control that are between two nominal
        // voltages equivalents.
        if (transformerVoltageControl.isPresent()) {
            for (LfBranch branch : transformerVoltageControl.get().getControllerElements()) {
                if (!branch.isConnectedAtBothSides()) {
                    return false;
                } else if (branch.getBus1().getNominalV() == branch.getBus2().getNominalV()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
