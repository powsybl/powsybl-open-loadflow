/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.na.equations.ac.AcEquationType;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.network.LfNetwork;
import com.powsybl.openloadflow.ac.solver.AcSolverResult;
import com.powsybl.openloadflow.lf.outerloop.AbstractOuterLoopContext;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcOuterLoopContext extends AbstractOuterLoopContext<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext> {

    private AcSolverResult lastSolverResult;

    AcOuterLoopContext(LfNetwork network) {
        super(network);
    }

    public AcSolverResult getLastSolverResult() {
        return lastSolverResult;
    }

    public void setLastSolverResult(AcSolverResult lastSolverResult) {
        this.lastSolverResult = lastSolverResult;
    }

}
