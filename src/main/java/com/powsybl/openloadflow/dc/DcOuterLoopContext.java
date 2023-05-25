/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.lf.outerloop.AbstractOuterLoopContext;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */

public class DcOuterLoopContext extends AbstractOuterLoopContext<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext> {

    private int iteration;

    DcOuterLoopContext(LfNetwork network) {
        super(network);
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }
}
