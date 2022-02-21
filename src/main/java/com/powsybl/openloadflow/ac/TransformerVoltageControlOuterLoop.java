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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class TransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    private double maxControlledNominalVoltage = Double.MIN_VALUE;

    private List<LfBus> disabledControllerBuses = new ArrayList<>();

    @Override
    public void initialize(LfNetwork network) {
        // All transformer voltage control are disabled for the first equation system resolution.
        for (LfBranch branch : network.getBranches()) {
            branch.getVoltageControl().ifPresent(voltageControl -> {
                branch.setVoltageControlEnabled(false);
                maxControlledNominalVoltage = Math.max(maxControlledNominalVoltage, voltageControl.getControlled().getNominalV());
            });
        }
    }

    @Override
    public String getType() {
        return "Transformer voltage control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        // At first outer loop iteration, the voltage control of generators that controlled at nominal voltage of
        // the set controlledNominalVoltages are disabled.
        // The transformer voltage controls are enabled.
        if (context.getIteration() == 0) {
            for (LfBus bus : context.getNetwork().getBuses()) {
                if (bus.isVoltageControlled() && bus.getNominalV() <= maxControlledNominalVoltage) {
                    bus.getVoltageControl().ifPresent(voltageControl -> {
                        voltageControl.getControllerBuses().forEach(controllerBus -> {
                            controllerBus.setGenerationTargetQ(bus.getQ().eval());
                            controllerBus.setVoltageControlEnabled(false);
                            disabledControllerBuses.add(controllerBus);
                        });
                    });
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
            for (LfBranch branch : context.getNetwork().getBranches()) {
                TransformerVoltageControl voltageControl = branch.getVoltageControl().orElse(null);
                if (voltageControl != null && (Math.abs(voltageControl.getControlled().getV() - voltageControl.getTargetValue()) > voltageControl.getTargetDeadband() / 2)) {
                    branch.setVoltageControlEnabled(true);
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }

        // At second outer loop iteration, the transformers are rounded. The generator voltage controls that were
        // disabled previously are enabled.
        if (context.getIteration() == 1) {
            status = roundVoltageRatios(context.getNetwork());
            for (LfBus bus : disabledControllerBuses) {
                bus.setGenerationTargetQ(0);
                bus.setVoltageControlEnabled(true);
                status = OuterLoopStatus.UNSTABLE;
            }
        }

        return status;
    }
}
