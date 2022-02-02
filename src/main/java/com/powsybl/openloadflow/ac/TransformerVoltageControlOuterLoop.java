/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class TransformerVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerVoltageControlOuterLoop.class);

    private Set<Double> controlledNominalVoltages = new HashSet<>();

    @Override
    public void initialize(LfNetwork network) {
        // All transformer voltage control are disabled for the first equation system resolution.
        for (LfBranch branch : network.getBranches()) {
            TransformerVoltageControl voltageControl = branch.getVoltageControl().orElse(null);
            if (voltageControl != null) {
                branch.setVoltageControlEnabled(false);
                controlledNominalVoltages.add(voltageControl.getControlled().getNominalV());
            }
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
                if (bus.isVoltageControlled() && bus.getVoltageControl().isPresent() && controlledNominalVoltages.contains(bus.getNominalV())) {
                    bus.getVoltageControl().get().getControllerBuses().stream().forEach(controllerBus -> {
                        controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                        controllerBus.setVoltageControlEnabled(false);
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
        }

        // At second outer loop iteration, the transformers are rounded. The generator voltage controls that were
        // disabled previously are enabled.
        if (context.getIteration() == 1) {
            for (LfBranch branch : context.getNetwork().getBranches()) {
                TransformerVoltageControl voltageControl = branch.getVoltageControl().orElse(null);
                if (voltageControl != null) {
                    branch.setVoltageControlEnabled(false);

                    // round the rho shift to the closest tap
                    PiModel piModel = branch.getPiModel();
                    double r1Value = piModel.getR1();
                    piModel.roundR1ToClosestTap();
                    double roundedR1Value = piModel.getR1();
                    LOGGER.trace("Round voltage ratio of '{}': {} -> {}", branch.getId(), r1Value, roundedR1Value);
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
            for (LfBus bus : context.getNetwork().getBuses()) {
                if (bus.hasVoltageControllerCapability() && controlledNominalVoltages.contains(bus.getNominalV())) {
                    bus.setGenerationTargetQ(0);
                    bus.setVoltageControlEnabled(true);
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }
        return status;
    }

    @Override
    public void cleanup(LfNetwork network) {
        for (LfBranch branch : network.getBranches()) {
            branch.getVoltageControl().ifPresent(voltageControl -> branch.setVoltageControlEnabled(true));
        }
    }
}
