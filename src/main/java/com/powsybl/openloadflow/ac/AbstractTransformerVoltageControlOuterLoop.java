/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public abstract class AbstractTransformerVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTransformerVoltageControlOuterLoop.class);

    protected OuterLoopStatus roundVoltageRatios(LfNetwork network) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        for (LfBranch branch : network.getBranches()) {
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
        return status;
    }

    @Override
    public void cleanup(LfNetwork network) {
        for (LfBranch branch : network.getBranches()) {
            branch.getVoltageControl().ifPresent(voltageControl -> branch.setVoltageControlEnabled(true));
        }
    }

    protected void checkControl(LfNetwork network) {
        var connectivity = network.getConnectivity();
        for (LfBranch branch : network.getBranches()) {
            if (branch.isVoltageController()) {
                TransformerVoltageControl voltageControl = branch.getVoltageControl().orElse(null);
                LfBus controlledBus = voltageControl.getControlled();
                connectivity.cut(branch.getBus1(), branch.getBus2());
                Set<LfBus> componentOnNotControlledSide = null;
                if (controlledBus.equals(branch.getBus1())) {
                    componentOnNotControlledSide = connectivity.getConnectedComponent(branch.getBus2());
                } else if (controlledBus.equals(branch.getBus2())) {
                    componentOnNotControlledSide = connectivity.getConnectedComponent(branch.getBus1());
                } else {
                    // I don't know.
                }
                if (componentOnNotControlledSide != null) {
                    Optional<LfBus> generatorControlledBus = componentOnNotControlledSide.stream().filter(LfBus::isVoltageControlled).findAny();
                    if (!generatorControlledBus.isPresent()) {
                        branch.setVoltageControlEnabled(false);
                        LOGGER.error("Transformer {} with voltage control on is disabled", branch.getId());
                    }
                }
            }
            connectivity.reset();
        }
    }
}
