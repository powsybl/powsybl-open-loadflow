/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.TransformerVoltageControl;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class TransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    public static final String MAX_CONTROLLED_NOMINAL_VOLTAGE = "maxControlledNominalVoltage";

    @Override
    public void initialize(LfNetwork network) {
        // All transformer voltage control are disabled for the first equation system resolution.
        double[] maxControlledNominalVoltage = new double[1];
        maxControlledNominalVoltage[0] = Double.MIN_VALUE;
        for (LfBranch branch : network.getBranches()) {
            branch.getVoltageControl().ifPresent(voltageControl -> {
                branch.setVoltageControlEnabled(false);
                maxControlledNominalVoltage[0] = Math.max(maxControlledNominalVoltage[0], voltageControl.getControlled().getNominalV());
            });
        }
        network.setUserObject(MAX_CONTROLLED_NOMINAL_VOLTAGE, maxControlledNominalVoltage[0]);
    }

    @Override
    public String getType() {
        return "Transformer voltage control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        double maxControlledNominalVoltage = (Double) context.getNetwork().getUserObject(MAX_CONTROLLED_NOMINAL_VOLTAGE);

        // At first outer loop iteration, the voltage control of generators that controlled at nominal voltage of
        // the set controlledNominalVoltages are disabled.
        // The transformer voltage controls are enabled.
        if (context.getIteration() == 0) {
            for (LfBus bus : context.getNetwork().getBuses()) {
                if (bus.isVoltageControlled() && bus.getNominalV() <= maxControlledNominalVoltage) {
                    bus.getVoltageControl().ifPresent(voltageControl -> {
                        voltageControl.getControllerBuses().forEach(controllerBus -> {
                            controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                            controllerBus.setVoltageControlEnabled(false);
                        });
                    });
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
            for (LfBranch branch : context.getNetwork().getBranches()) {
                TransformerVoltageControl voltageControl = branch.getVoltageControl().orElse(null);
                if (voltageControl != null) {
                    branch.setVoltageControlEnabled(true);
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
            checkControl(context.getNetwork());
        }

        // At second outer loop iteration, the transformers are rounded. The generator voltage controls that were
        // disabled previously are enabled.
        if (context.getIteration() == 1) {
            status = roundVoltageRatios(context.getNetwork());
            for (LfBus bus : context.getNetwork().getBuses()) {
                if (bus.hasVoltageControllerCapability() && bus.getNominalV() <= maxControlledNominalVoltage) {
                    bus.setGenerationTargetQ(0);
                    bus.setVoltageControlEnabled(true);
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }

        return status;
    }
}
