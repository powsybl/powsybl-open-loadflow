/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.VoltageControl;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
abstract class AbstractSimpleTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {
    @Override
    public void initialize(AcOuterLoopContext context) {
        for (LfBranch controllerBranch : context.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            if (controllerBranch.isConnectedAtBothSides()) {
                controllerBranch.setVoltageControlEnabled(true);
            }
        }
        context.getNetwork().fixTransformerVoltageControls();
    }
}
