/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.network.TransformerReactivePowerControl;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
abstract class AbstractTransformerReactivePowerControlOuterLoop implements AcOuterLoop {

    private static final String TYPE = "TransformerReactivePowerControl";

    private static final double MIN_TARGET_DEADBAND_MVAR = 0.1;

    @Override
    public String getType() {
        return TYPE;
    }

    protected static double getHalfTargetDeadband(TransformerReactivePowerControl reactivePowerControl) {
        return reactivePowerControl.getTargetDeadband().orElse(MIN_TARGET_DEADBAND_MVAR) / 2;
    }

}
