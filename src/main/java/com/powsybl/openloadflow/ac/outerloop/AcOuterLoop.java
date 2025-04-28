/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.lf.outerloop.OuterLoop;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface AcOuterLoop extends OuterLoop<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext, AcOuterLoopContext> {

    /**
     * Returns true if the outerloop can fix unrealistic states. For an outerloop returning True, unrealistic states should not interrupt
     * the solving process before this outerloop is stabilized.
     */
    default boolean canFixUnrealisticState() {
        return false;
    }

}
