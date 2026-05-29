/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.lf.outerloop.ActivePowerDistributionOuterLoop;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public interface AcActivePowerDistributionOuterLoop extends ActivePowerDistributionOuterLoop<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext, AcOuterLoopContext> {

    /**
     * Return the active power that has been redistributed between generators or loads by the outer loop in a given
     * synchronous component.
     *
     * @param context Outer loop context, storing data about the last load flow iteration.
     * @param numSC The id of the synchronous component whose distributed active power should be returned.
     * @return The active power redistributed within the synchronous component by the outer loop. In pu.
     */
    double getDistributedActivePower(AcOuterLoopContext context, int numSC);
}
