/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.VoltageControl;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ContinuousTransformerVoltageControlOuterLoop extends AbstractSimpleTransformerVoltageControlOuterLoop {

    public static final String NAME = "ContinuousTransformerVoltageControl";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        if (context.getIteration() == 0) {
            LfNetwork network = context.getNetwork();
            for (LfBranch controllerBranch : network.<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
                controllerBranch.setVoltageControlEnabled(false);

                // clip the rho shift to min or max if needed
                PiModel piModel = controllerBranch.getPiModel();
                double initialR1 = piModel.getR1();
                if (piModel.clipR1()) {
                    status = OuterLoopStatus.UNSTABLE;
                    LOGGER.trace("Clip voltage ratio of '{}': {} -> {}", controllerBranch.getId(), initialR1, piModel.getR1());
                }
            }
        }
        return new OuterLoopResult(this, status);
    }
}
