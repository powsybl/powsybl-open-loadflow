/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverResult;
import com.powsybl.openloadflow.lf.outerloop.AbstractOuterLoopContext;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcOuterLoopContext extends AbstractOuterLoopContext<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext> {

    private int iteration; // current iterations of this single outer loop type

    private AcSolverResult lastSolverResult;

    AcOuterLoopContext(LfNetwork network) {
        super(network);
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public AcSolverResult getLastSolverResult() {
        return lastSolverResult;
    }

    public void setLastSolverResult(AcSolverResult lastSolverResult) {
        this.lastSolverResult = lastSolverResult;
    }

}
