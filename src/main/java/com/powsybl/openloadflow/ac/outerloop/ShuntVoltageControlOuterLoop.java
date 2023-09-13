/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class ShuntVoltageControlOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShuntVoltageControlOuterLoop.class);

    @Override
    public String getType() {
        return "Shunt voltage control";
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        context.getNetwork().<LfShunt>getControllerElements(VoltageControl.Type.SHUNT)
                .forEach(controllerShunt -> controllerShunt.setVoltageControlEnabled(true));
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        if (context.getIteration() == 0) {
            for (LfShunt controllerShunt : context.getNetwork().<LfShunt>getControllerElements(VoltageControl.Type.SHUNT)) {
                controllerShunt.setVoltageControlEnabled(false);

                // round the susceptance to the closest section
                double b = controllerShunt.getB();
                controllerShunt.dispatchB();
                LOGGER.trace("Round susceptance of '{}': {} -> {}", controllerShunt.getId(), b, controllerShunt.getB());

                status = OuterLoopStatus.UNSTABLE;
            }
        }
        return status;
    }
}
